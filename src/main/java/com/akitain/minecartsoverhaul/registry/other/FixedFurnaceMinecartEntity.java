package com.akitain.minecartsoverhaul.registry.other;

import com.akitain.minecartsoverhaul.network.TrainPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FixedFurnaceMinecartEntity extends MinecartFurnace {

    private static final float TRAILER_SPACING = 1.5f;
    private static final int MAX_TRAIN_SIZE = 8;
    private static final int FUEL_PULL_THRESHOLD = 100;
    private static final int FUEL_CAP = 32000;
    private static final double BROADCAST_RANGE = 100.0;
    private static final int HEARTBEAT_TICKS = 20;
    private static final double SNAP_DISTANCE_SQR = 4.0;
    private static final double DISCONNECT_DISTANCE_SQR = 9.0;
    private static final double GROUND_STOP_THRESHOLD = 0.01;
    private static final double PROPULSION_PER_TICK = 1.0 / 40.0;
    private static final double EMPTY_HORIZONTAL_DECAY = 0.75;
    // Negative age triggers ExperimentalMinecartControllerMixin.getSpeedRetention to coast at 0.975
    // so a freshly dropped trailer decays smoothly instead of snapping to vanilla retention.
    private static final int DISCONNECT_AGE_OFFSET = -50;
    // Bumps the cart past the 60-tick auto-cleanup window faster when it has clearly fallen behind.
    private static final int DRIFT_AGE_OFFSET = 10;
    // Skip re-attach scanning right after a portal so a locomotive doesn't grab carts mid-teleport.
    private static final int PORTAL_COOLDOWN_GUARD = 6;
    private static final String TAG_TRAIN = "train";
    private static final String TAG_TRAIN_MOVE = "trainMove";
    private static final String TAG_TRAIN_TP = "trainTP";

    // train[0] is always this locomotive; trailers follow in order.
    private final ArrayList<AbstractMinecart> train = new ArrayList<>();
    // Restored from NBT on load and applied on the first tick once entities resolve from UUIDs.
    private final ArrayList<UUID> pendingTrailerUuids = new ArrayList<>();
    private int fuel;
    // Sentinel set externally by ExperimentalMinecartControllerMixin when the cart enters a powered rail:
    //  1 = rail powered, -1 = rail unpowered, 0 = nothing pending. Consumed once per tick.
    public int powerRailSetLit = 0;

    public FixedFurnaceMinecartEntity(EntityType<? extends MinecartFurnace> entityType, Level world) {
        super(entityType, world);
    }

    public ArrayList<AbstractMinecart> getTrain() {
        return train;
    }

    public void setTrain(ArrayList<UUID> trainUuids) {
        if (trainUuids.isEmpty()) return;
        train.clear();
        for (int i = 0; i < trainUuids.size(); i++) {
            Entity entity = this.level().getEntity(trainUuids.get(i));
            if (!(entity instanceof AbstractMinecart minecart)) continue;
            minecart.tickCount = 0;
            minecart.entityTags().clear();
            // Stash the successor's UUID as a command tag so client-side code can walk the chain
            // without keeping its own cache. Cleared after 30 ticks by AbstractMinecartEntityMixin.
            int next = i + 1;
            if (next < trainUuids.size()) {
                minecart.entityTags().add(trainUuids.get(next).toString());
            }
            train.add(minecart);
        }
    }

    public static void sendToAround(PlayerList playerManager, @Nullable Player player,
                                    double x, double y, double z, double distance,
                                    ResourceKey<Level> worldKey, CustomPacketPayload payload) {
        double rangeSqr = distance * distance;
        for (ServerPlayer recipient : playerManager.getPlayers()) {
            if (recipient == player) continue;
            if (recipient.level().dimension() != worldKey) continue;
            double dx = x - recipient.getX();
            double dy = y - recipient.getY();
            double dz = z - recipient.getZ();
            if (dx * dx + dy * dy + dz * dz >= rangeSqr) continue;
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel serverWorld) {
            restorePendingTrain(serverWorld);
            broadcastIfHeartbeat(serverWorld);
        }

        super.tick();

        if (this.level() instanceof ServerLevel serverWorld) {
            ensureTrainContainsSelf();
            autoPullFuelFromFirstTrailer();
            applyPoweredRailLitToggle();
            consumeFuelAndUpdateLit();
            cullDisconnectedTrailers(serverWorld);
            runTrainCascade(serverWorld);
        }

        emitSmokeWhenLit();
    }

    private void restorePendingTrain(ServerLevel world) {
        if (pendingTrailerUuids.isEmpty()) return;
        train.clear();
        train.add(this);
        for (UUID uuid : pendingTrailerUuids) {
            Entity entity = world.getEntity(uuid);
            if (!(entity instanceof AbstractMinecart minecart)) continue;
            BlockState state = world.getBlockState(minecart.getCurrentBlockPosOrRailBelow());
            minecart.setOnRails(BaseRailBlock.isRail(state));
            minecart.addTag(TAG_TRAIN);
            minecart.addTag(TAG_TRAIN_MOVE);
            minecart.tickCount = 0;
            train.add(minecart);
        }
        pendingTrailerUuids.clear();
        broadcastTrain(world);
    }

    private void broadcastIfHeartbeat(ServerLevel world) {
        if (world.getGameTime() % HEARTBEAT_TICKS == 0) broadcastTrain(world);
    }

    private void ensureTrainContainsSelf() {
        if (train.isEmpty()) train.add(this);
    }

    private void autoPullFuelFromFirstTrailer() {
        if (train.size() <= 1 || fuel >= FUEL_PULL_THRESHOLD) return;
        NonNullList<ItemStack> inv = trailerInventory(train.get(1));
        if (inv == null) return;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.get(i);
            if (!this.level().fuelValues().isFuel(stack)) continue;
            int burnTicks = this.level().fuelValues().burnDuration(stack);
            if (stack.is(Items.LAVA_BUCKET)) {
                inv.set(i, Items.BUCKET.getDefaultInstance());
            } else {
                stack.shrink(1);
            }
            fuel += burnTicks;
            return;
        }
    }

    @Nullable
    private static NonNullList<ItemStack> trailerInventory(AbstractMinecart trailer) {
        if (trailer instanceof MinecartChest chest) return chest.getItemStacks();
        if (trailer instanceof MinecartHopper hopper) return hopper.getItemStacks();
        return null;
    }

    private void applyPoweredRailLitToggle() {
        if (powerRailSetLit == 0) return;
        if (fuel > 0) this.setHasFuel(powerRailSetLit == 1);
        powerRailSetLit = 0;
    }

    private void consumeFuelAndUpdateLit() {
        if (fuel > 0 && this.hasFuel()) fuel--;
        if (fuel <= 0) this.setHasFuel(false);
    }

    private void runTrainCascade(ServerLevel world) {
        AbstractMinecart probe = createProbe(world);
        placeProbeAt(probe, this);
        probe.setDeltaMovement(reverseYawVector(probe.getYRot(), TRAILER_SPACING));

        // stillOnRail latches false the moment any trailer is off-track so the rest of the chain
        // takes the off-rail branch in the same tick (avoids a half-on/half-off snake).
        // cascadeContinues latches false when a trailer drifts past the snap radius so the
        // remainder keeps its own momentum for one tick instead of being snapped forward.
        boolean stillOnRail = true;
        boolean cascadeContinues = true;
        for (int i = 1; i < train.size(); i++) {
            AbstractMinecart trailer = train.get(i);
            AbstractMinecart prev = train.get(i - 1);
            trailer.removeTag(TAG_TRAIN_MOVE);
            if (cascadeContinues) {
                CascadeStep step = cascadeOneTrailer(world, trailer, probe, stillOnRail);
                stillOnRail = step.stillOnRail;
                cascadeContinues = step.cascadeContinues;
            }
            if (trailer.position().distanceToSqr(prev.position()) > DISCONNECT_DISTANCE_SQR) {
                trailer.tickCount += DRIFT_AGE_OFFSET;
            }
        }

        if (this.getPortalCooldown() < PORTAL_COOLDOWN_GUARD) attachNewTrailers(world, probe);
        probe.remove(Entity.RemovalReason.DISCARDED);
    }

    private record CascadeStep(boolean stillOnRail, boolean cascadeContinues) {}

    private CascadeStep cascadeOneTrailer(ServerLevel world, AbstractMinecart trailer,
                                          AbstractMinecart probe, boolean stillOnRail) {
        BlockState state = world.getBlockState(trailer.getCurrentBlockPosOrRailBelow());
        boolean trailerOnRail = BaseRailBlock.isRail(state);
        trailer.setOnRails(trailerOnRail);
        boolean nowOnRail = stillOnRail && trailerOnRail;
        int previousAge = trailer.tickCount;
        trailer.tickCount = 0;

        if (!nowOnRail) {
            trailer.tick();
            applyYawAlignedHorizontalSpeed(trailer, this.getDeltaMovement().horizontalDistance());
            trailer.addTag(TAG_TRAIN_MOVE);
            return new CascadeStep(false, true);
        }

        trailer.addTag(TAG_TRAIN_MOVE);
        trailer.getBehavior().moveAlongTrack(world);

        if (!this.isOnRails()) {
            applyYawAlignedHorizontalSpeed(trailer, this.getDeltaMovement().horizontalDistance());
            return new CascadeStep(true, true);
        }

        probe.getBehavior().moveAlongTrack(world);
        if (trailer.position().distanceToSqr(probe.position()) >= SNAP_DISTANCE_SQR) {
            trailer.tickCount = previousAge + DRIFT_AGE_OFFSET;
            return new CascadeStep(true, false);
        }

        snapTrailerToProbe(trailer, probe);
        return new CascadeStep(true, true);
    }

    private static AbstractMinecart createProbe(ServerLevel world) {
        AbstractMinecart probe = new MinecartChest(EntityType.CHEST_MINECART, world);
        probe.noPhysics = true;
        probe.addTag(TAG_TRAIN);
        return probe;
    }

    private void snapTrailerToProbe(AbstractMinecart trailer, AbstractMinecart probe) {
        trailer.setPos(probe.position());
        trailer.setXRot(probe.getXRot());
        trailer.setYRot((probe.getYRot() + 360) % 360);
        Vec3 horizontal = probe.getDeltaMovement().horizontal().normalize()
                .scale(-this.getDeltaMovement().horizontalDistance());
        trailer.setDeltaMovement(horizontal.x, trailer.getDeltaMovement().y, horizontal.z);
    }

    private void applyYawAlignedHorizontalSpeed(AbstractMinecart m, double speed) {
        Vec3 horizontal = forwardYawVector(m.getYRot(), speed);
        m.setDeltaMovement(horizontal.x, m.getDeltaMovement().y, horizontal.z);
    }

    private static Vec3 reverseYawVector(float yawDeg, double magnitude) {
        return new Vec3(-magnitude, 0, 0).yRot((float) (yawDeg * Math.PI / 180f));
    }

    private static Vec3 forwardYawVector(float yawDeg, double magnitude) {
        return new Vec3(1, 0, 0).yRot((float) (yawDeg * Math.PI / 180f))
                .horizontal().normalize().scale(magnitude);
    }

    private void broadcastTrain(ServerLevel world) {
        ArrayList<UUID> ids = new ArrayList<>(train.size());
        for (AbstractMinecart m : train) ids.add(m.getUUID());
        TrainPayload payload = new TrainPayload(ids);
        sendToAround(world.getServer().getPlayerList(), null,
                this.getX(), this.getY(), this.getZ(),
                BROADCAST_RANGE, world.dimension(), payload);
    }

    private void placeProbeAt(AbstractMinecart probe, AbstractMinecart anchor) {
        probe.setPos(anchor.position());
        probe.setOnRails(true);
        probe.setXRot(anchor.getXRot());
        probe.setYRot((anchor.getYRot() + 360) % 360);
        probe.setDeltaMovement(anchor.getDeltaMovement());
    }

    private void attachNewTrailers(ServerLevel world, AbstractMinecart probe) {
        int i = train.size() - 1;
        while (i < train.size() && train.size() < MAX_TRAIN_SIZE) {
            AbstractMinecart anchor = train.get(i);
            if (anchor.isOnRails()) attachAdjacentOrProbed(world, anchor, probe);
            i++;
        }
    }

    private void attachAdjacentOrProbed(ServerLevel world, AbstractMinecart anchor,
                                        AbstractMinecart probe) {
        List<AbstractMinecart> overlapping = candidatesIn(world, anchor.getBoundingBox().deflate(0.2));
        if (!overlapping.isEmpty()) {
            for (AbstractMinecart candidate : overlapping) {
                if (train.size() >= MAX_TRAIN_SIZE) return;
                if (isOnRail(candidate)) attachTrailer(candidate, anchor);
            }
            return;
        }
        placeProbeAt(probe, anchor);
        probe.setDeltaMovement(reverseYawVector(probe.getYRot(), TRAILER_SPACING));
        probe.getBehavior().moveAlongTrack(world);
        List<AbstractMinecart> probed = candidatesIn(world, probe.getBoundingBox().deflate(0.2));
        if (probed.isEmpty()) return;
        AbstractMinecart candidate = probed.get(0);
        if (isOnRail(candidate)) attachTrailer(candidate, probe);
    }

    private List<AbstractMinecart> candidatesIn(ServerLevel world, net.minecraft.world.phys.AABB box) {
        return world.getEntitiesOfClass(AbstractMinecart.class, box,
                e -> e != null
                        && !(e instanceof MinecartFurnace)
                        && !e.entityTags().contains(TAG_TRAIN));
    }

    private boolean isOnRail(AbstractMinecart m) {
        return BaseRailBlock.isRail(this.level().getBlockState(m.getCurrentBlockPosOrRailBelow()));
    }

    private void attachTrailer(AbstractMinecart trailer, AbstractMinecart anchor) {
        trailer.setOnRails(true);
        trailer.addTag(TAG_TRAIN);
        trailer.addTag(TAG_TRAIN_MOVE);
        // +0.1 on Y nudges the trailer off the rail surface so the next physics tick reseats it
        // cleanly instead of clipping into the block below.
        trailer.setDeltaMovement(train.get(train.size() - 1).getDeltaMovement().add(0, 0.1, 0));
        trailer.setPos(anchor.position());
        trailer.setXRot(anchor.getXRot());
        if (trailer instanceof DispencerMinecartEntity dispenser) {
            float yawDelta = trailer.getYRot() - anchor.getYRot();
            if (Math.acos(Math.cos(yawDelta)) > Math.PI / 2) dispenser.setDispenseFlipped(!dispenser.isDispenseFlipped());
        }
        trailer.setYRot((anchor.getYRot() + 360) % 360);
        trailer.tickCount = 0;
        train.add(trailer);
        trailer.level().playSound(trailer, trailer.blockPosition(),
                SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private void cullDisconnectedTrailers(ServerLevel world) {
        for (int i = 1; i < train.size(); i++) {
            if (!shouldDisconnect(train.get(i))) continue;
            while (train.size() > i) {
                AbstractMinecart dropped = train.get(i);
                dropped.removeTag(TAG_TRAIN);
                dropped.removeTag(TAG_TRAIN_MOVE);
                dropped.tickCount = DISCONNECT_AGE_OFFSET;
                world.playSound(dropped, dropped.blockPosition(), SoundEvents.BAMBOO_BREAK,
                        SoundSource.BLOCKS, 1.0F, 1.0F);
                train.remove(i);
            }
            return;
        }
    }

    private static boolean shouldDisconnect(@Nullable AbstractMinecart trailer) {
        if (trailer == null) return true;
        if (trailer.isRemoved()) return true;
        if (trailer.onGround() && trailer.getDeltaMovement().horizontalDistance() < GROUND_STOP_THRESHOLD) return true;
        return !trailer.entityTags().contains(TAG_TRAIN);
    }

    private void emitSmokeWhenLit() {
        if (!this.hasFuel() || this.random.nextInt(4) != 0) return;
        this.level().addParticle(ParticleTypes.LARGE_SMOKE,
                this.getX(), this.getY() + 0.8, this.getZ(), 0.0, 0.0, 0.0);
    }

    @Override
    protected Vec3 applyNaturalSlowdown(Vec3 velocity) {
        if (!this.hasFuel()) return velocity.multiply(EMPTY_HORIZONTAL_DECAY, 0.0, EMPTY_HORIZONTAL_DECAY);
        Vec3 push = forwardYawVector((this.getYRot() + 360) % 360, 1.0);
        return this.getDeltaMovement().add(push.x * PROPULSION_PER_TICK, 0.0, push.z * PROPULSION_PER_TICK);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput view) {
        super.addAdditionalSaveData(view);
        view.putShort("Fuel", (short) this.fuel);
        view.putShort("TrainLength", (short) train.size());
        for (int i = 1; i < train.size(); i++) {
            view.putString("Train" + i, String.valueOf(train.get(i).getUUID()));
        }
        view.putBoolean("Lit", hasFuel());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput view) {
        super.readAdditionalSaveData(view);
        this.fuel = view.getShortOr("Fuel", (short) 0);
        int len = view.getShortOr("TrainLength", (short) 0);
        for (int i = 1; i < len; i++) {
            String raw = view.getStringOr("Train" + i, "");
            if (raw.isEmpty()) continue;
            pendingTrailerUuids.add(UUID.fromString(raw));
        }
        setHasFuel(view.getBooleanOr("Lit", false));
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (fuel > 0) this.setHasFuel(true);
        if (!this.level().fuelValues().isFuel(stack)) return InteractionResult.SUCCESS;
        int burnTicks = this.level().fuelValues().burnDuration(stack);
        if (fuel + burnTicks > FUEL_CAP) return InteractionResult.SUCCESS;
        fuel += burnTicks;
        this.setHasFuel(true);
        if (stack.is(Items.LAVA_BUCKET)) {
            if (player.hasInfiniteMaterials()) return InteractionResult.SUCCESS;
            ItemStack swapped = ItemUtils.createFilledResult(stack, player, Items.BUCKET.getDefaultInstance());
            player.setItemInHand(hand, swapped);
            return InteractionResult.SUCCESS;
        }
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        for (AbstractMinecart trailer : train) {
            if (trailer == null) continue;
            trailer.removeTag(TAG_TRAIN);
            trailer.removeTag(TAG_TRAIN_MOVE);
        }
        super.remove(reason);
    }

    @Override
    public Entity teleport(TeleportTransition teleportTarget) {
        if (this.level() instanceof ServerLevel serverWorld) {
            serverWorld.resetEmptyTime();
            serverWorld.getChunkSource().addTicketWithRadius(TicketType.PORTAL,
                    ChunkPos.containing(this.blockPosition()), 3);
        }
        for (AbstractMinecart trailer : train) {
            if (trailer == null) continue;
            trailer.removeTag(TAG_TRAIN);
            trailer.removeTag(TAG_TRAIN_MOVE);
            trailer.addTag(TAG_TRAIN_TP);
        }
        train.clear();
        return super.teleport(teleportTarget);
    }

    @Override
    protected double getMaxSpeed(ServerLevel world) {
        return super.getMaxSpeed(world) * (1 - 0.05 * train.size());
    }
}
