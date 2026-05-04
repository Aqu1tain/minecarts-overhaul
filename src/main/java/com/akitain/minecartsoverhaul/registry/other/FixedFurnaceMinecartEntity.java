package com.akitain.minecartsoverhaul.registry.other;

import com.akitain.minecartsoverhaul.network.TrainPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FixedFurnaceMinecartEntity extends FurnaceMinecartEntity {

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
    private final ArrayList<AbstractMinecartEntity> train = new ArrayList<>();
    // Restored from NBT on load and applied on the first tick once entities resolve from UUIDs.
    private final ArrayList<UUID> pendingTrailerUuids = new ArrayList<>();
    private int fuel;
    // Sentinel set externally by ExperimentalMinecartControllerMixin when the cart enters a powered rail:
    //  1 = rail powered, -1 = rail unpowered, 0 = nothing pending. Consumed once per tick.
    public int powerRailSetLit = 0;

    public FixedFurnaceMinecartEntity(EntityType<? extends FurnaceMinecartEntity> entityType, World world) {
        super(entityType, world);
    }

    public ArrayList<AbstractMinecartEntity> getTrain() {
        return train;
    }

    public void setTrain(ArrayList<UUID> trainUuids) {
        if (trainUuids.isEmpty()) return;
        train.clear();
        for (int i = 0; i < trainUuids.size(); i++) {
            Entity entity = this.getEntityWorld().getEntity(trainUuids.get(i));
            if (!(entity instanceof AbstractMinecartEntity minecart)) continue;
            minecart.age = 0;
            minecart.getCommandTags().clear();
            // Stash the successor's UUID as a command tag so client-side code can walk the chain
            // without keeping its own cache. Cleared after 30 ticks by AbstractMinecartEntityMixin.
            int next = i + 1;
            if (next < trainUuids.size()) {
                minecart.getCommandTags().add(trainUuids.get(next).toString());
            }
            train.add(minecart);
        }
    }

    public static void sendToAround(PlayerManager playerManager, @Nullable PlayerEntity player,
                                    double x, double y, double z, double distance,
                                    RegistryKey<World> worldKey, CustomPayload payload) {
        double rangeSqr = distance * distance;
        for (ServerPlayerEntity recipient : playerManager.getPlayerList()) {
            if (recipient == player) continue;
            if (recipient.getEntityWorld().getRegistryKey() != worldKey) continue;
            double dx = x - recipient.getX();
            double dy = y - recipient.getY();
            double dz = z - recipient.getZ();
            if (dx * dx + dy * dy + dz * dz >= rangeSqr) continue;
            ServerPlayNetworking.send(recipient, payload);
        }
    }

    @Override
    public void tick() {
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            restorePendingTrain(serverWorld);
            broadcastIfHeartbeat(serverWorld);
        }

        super.tick();

        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            ensureTrainContainsSelf();
            autoPullFuelFromFirstTrailer();
            applyPoweredRailLitToggle();
            consumeFuelAndUpdateLit();
            cullDisconnectedTrailers(serverWorld);
            runTrainCascade(serverWorld);
        }

        emitSmokeWhenLit();
    }

    private void restorePendingTrain(ServerWorld world) {
        if (pendingTrailerUuids.isEmpty()) return;
        train.clear();
        train.add(this);
        for (UUID uuid : pendingTrailerUuids) {
            Entity entity = world.getEntity(uuid);
            if (!(entity instanceof AbstractMinecartEntity minecart)) continue;
            BlockState state = world.getBlockState(minecart.getRailOrMinecartPos());
            minecart.setOnRail(AbstractRailBlock.isRail(state));
            minecart.addCommandTag(TAG_TRAIN);
            minecart.addCommandTag(TAG_TRAIN_MOVE);
            minecart.age = 0;
            train.add(minecart);
        }
        pendingTrailerUuids.clear();
        broadcastTrain(world);
    }

    private void broadcastIfHeartbeat(ServerWorld world) {
        if (world.getTime() % HEARTBEAT_TICKS == 0) broadcastTrain(world);
    }

    private void ensureTrainContainsSelf() {
        if (train.isEmpty()) train.add(this);
    }

    private void autoPullFuelFromFirstTrailer() {
        if (train.size() <= 1 || fuel >= FUEL_PULL_THRESHOLD) return;
        DefaultedList<ItemStack> inv = trailerInventory(train.get(1));
        if (inv == null) return;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.get(i);
            if (!this.getEntityWorld().getFuelRegistry().isFuel(stack)) continue;
            int burnTicks = this.getEntityWorld().getFuelRegistry().getFuelTicks(stack);
            if (stack.isOf(Items.LAVA_BUCKET)) {
                inv.set(i, Items.BUCKET.getDefaultStack());
            } else {
                stack.decrement(1);
            }
            fuel += burnTicks;
            return;
        }
    }

    @Nullable
    private static DefaultedList<ItemStack> trailerInventory(AbstractMinecartEntity trailer) {
        if (trailer instanceof ChestMinecartEntity chest) return chest.getInventory();
        if (trailer instanceof HopperMinecartEntity hopper) return hopper.getInventory();
        return null;
    }

    private void applyPoweredRailLitToggle() {
        if (powerRailSetLit == 0) return;
        if (fuel > 0) this.setLit(powerRailSetLit == 1);
        powerRailSetLit = 0;
    }

    private void consumeFuelAndUpdateLit() {
        if (fuel > 0 && this.isLit()) fuel--;
        if (fuel <= 0) this.setLit(false);
    }

    private void runTrainCascade(ServerWorld world) {
        AbstractMinecartEntity probe = createProbe(world);
        placeProbeAt(probe, this);
        probe.setVelocity(reverseYawVector(probe.getYaw(), TRAILER_SPACING));

        // stillOnRail latches false the moment any trailer is off-track so the rest of the chain
        // takes the off-rail branch in the same tick (avoids a half-on/half-off snake).
        // cascadeContinues latches false when a trailer drifts past the snap radius so the
        // remainder keeps its own momentum for one tick instead of being snapped forward.
        boolean stillOnRail = true;
        boolean cascadeContinues = true;
        for (int i = 1; i < train.size(); i++) {
            AbstractMinecartEntity trailer = train.get(i);
            AbstractMinecartEntity prev = train.get(i - 1);
            trailer.removeCommandTag(TAG_TRAIN_MOVE);
            if (cascadeContinues) {
                CascadeStep step = cascadeOneTrailer(world, trailer, probe, stillOnRail);
                stillOnRail = step.stillOnRail;
                cascadeContinues = step.cascadeContinues;
            }
            if (trailer.getEntityPos().squaredDistanceTo(prev.getEntityPos()) > DISCONNECT_DISTANCE_SQR) {
                trailer.age += DRIFT_AGE_OFFSET;
            }
        }

        if (this.getPortalCooldown() < PORTAL_COOLDOWN_GUARD) attachNewTrailers(world, probe);
        probe.remove(Entity.RemovalReason.DISCARDED);
    }

    private record CascadeStep(boolean stillOnRail, boolean cascadeContinues) {}

    private CascadeStep cascadeOneTrailer(ServerWorld world, AbstractMinecartEntity trailer,
                                          AbstractMinecartEntity probe, boolean stillOnRail) {
        BlockState state = world.getBlockState(trailer.getRailOrMinecartPos());
        boolean trailerOnRail = AbstractRailBlock.isRail(state);
        trailer.setOnRail(trailerOnRail);
        boolean nowOnRail = stillOnRail && trailerOnRail;
        int previousAge = trailer.age;
        trailer.age = 0;

        if (!nowOnRail) {
            trailer.tick();
            applyYawAlignedHorizontalSpeed(trailer, this.getVelocity().horizontalLength());
            trailer.addCommandTag(TAG_TRAIN_MOVE);
            return new CascadeStep(false, true);
        }

        trailer.addCommandTag(TAG_TRAIN_MOVE);
        trailer.getController().moveOnRail(world);

        if (!this.isOnRail()) {
            applyYawAlignedHorizontalSpeed(trailer, this.getVelocity().horizontalLength());
            return new CascadeStep(true, true);
        }

        probe.getController().moveOnRail(world);
        if (trailer.getEntityPos().squaredDistanceTo(probe.getEntityPos()) >= SNAP_DISTANCE_SQR) {
            trailer.age = previousAge + DRIFT_AGE_OFFSET;
            return new CascadeStep(true, false);
        }

        snapTrailerToProbe(trailer, probe);
        return new CascadeStep(true, true);
    }

    private static AbstractMinecartEntity createProbe(ServerWorld world) {
        AbstractMinecartEntity probe = new ChestMinecartEntity(EntityType.CHEST_MINECART, world);
        probe.noClip = true;
        probe.addCommandTag(TAG_TRAIN);
        return probe;
    }

    private void snapTrailerToProbe(AbstractMinecartEntity trailer, AbstractMinecartEntity probe) {
        trailer.setPosition(probe.getEntityPos());
        trailer.setPitch(probe.getPitch());
        trailer.setYaw((probe.getYaw() + 360) % 360);
        Vec3d horizontal = probe.getVelocity().getHorizontal().normalize()
                .multiply(-this.getVelocity().horizontalLength());
        trailer.setVelocity(horizontal.x, trailer.getVelocity().y, horizontal.z);
    }

    private void applyYawAlignedHorizontalSpeed(AbstractMinecartEntity m, double speed) {
        Vec3d horizontal = forwardYawVector(m.getYaw(), speed);
        m.setVelocity(horizontal.x, m.getVelocity().y, horizontal.z);
    }

    private static Vec3d reverseYawVector(float yawDeg, double magnitude) {
        return new Vec3d(-magnitude, 0, 0).rotateY((float) (yawDeg * Math.PI / 180f));
    }

    private static Vec3d forwardYawVector(float yawDeg, double magnitude) {
        return new Vec3d(1, 0, 0).rotateY((float) (yawDeg * Math.PI / 180f))
                .getHorizontal().normalize().multiply(magnitude);
    }

    private void broadcastTrain(ServerWorld world) {
        ArrayList<UUID> ids = new ArrayList<>(train.size());
        for (AbstractMinecartEntity m : train) ids.add(m.getUuid());
        TrainPayload payload = new TrainPayload(ids);
        sendToAround(world.getServer().getPlayerManager(), null,
                this.getX(), this.getY(), this.getZ(),
                BROADCAST_RANGE, world.getRegistryKey(), payload);
    }

    private void placeProbeAt(AbstractMinecartEntity probe, AbstractMinecartEntity anchor) {
        probe.setPosition(anchor.getEntityPos());
        probe.setOnRail(true);
        probe.setPitch(anchor.getPitch());
        probe.setYaw((anchor.getYaw() + 360) % 360);
        probe.setVelocity(anchor.getVelocity());
    }

    private void attachNewTrailers(ServerWorld world, AbstractMinecartEntity probe) {
        int i = train.size() - 1;
        while (i < train.size() && train.size() < MAX_TRAIN_SIZE) {
            AbstractMinecartEntity anchor = train.get(i);
            if (anchor.isOnRail()) attachAdjacentOrProbed(world, anchor, probe);
            i++;
        }
    }

    private void attachAdjacentOrProbed(ServerWorld world, AbstractMinecartEntity anchor,
                                        AbstractMinecartEntity probe) {
        List<AbstractMinecartEntity> overlapping = candidatesIn(world, anchor.getBoundingBox().contract(0.2));
        if (!overlapping.isEmpty()) {
            for (AbstractMinecartEntity candidate : overlapping) {
                if (train.size() >= MAX_TRAIN_SIZE) return;
                if (isOnRail(candidate)) attachTrailer(candidate, anchor);
            }
            return;
        }
        placeProbeAt(probe, anchor);
        probe.setVelocity(reverseYawVector(probe.getYaw(), TRAILER_SPACING));
        probe.getController().moveOnRail(world);
        List<AbstractMinecartEntity> probed = candidatesIn(world, probe.getBoundingBox().contract(0.2));
        if (probed.isEmpty()) return;
        AbstractMinecartEntity candidate = probed.get(0);
        if (isOnRail(candidate)) attachTrailer(candidate, probe);
    }

    private List<AbstractMinecartEntity> candidatesIn(ServerWorld world, net.minecraft.util.math.Box box) {
        return world.getEntitiesByClass(AbstractMinecartEntity.class, box,
                e -> e != null
                        && !(e instanceof FurnaceMinecartEntity)
                        && !e.getCommandTags().contains(TAG_TRAIN));
    }

    private boolean isOnRail(AbstractMinecartEntity m) {
        return AbstractRailBlock.isRail(this.getEntityWorld().getBlockState(m.getRailOrMinecartPos()));
    }

    private void attachTrailer(AbstractMinecartEntity trailer, AbstractMinecartEntity anchor) {
        trailer.setOnRail(true);
        trailer.addCommandTag(TAG_TRAIN);
        trailer.addCommandTag(TAG_TRAIN_MOVE);
        // +0.1 on Y nudges the trailer off the rail surface so the next physics tick reseats it
        // cleanly instead of clipping into the block below.
        trailer.setVelocity(train.get(train.size() - 1).getVelocity().add(0, 0.1, 0));
        trailer.setPosition(anchor.getEntityPos());
        trailer.setPitch(anchor.getPitch());
        if (trailer instanceof DispencerMinecartEntity dispenser) {
            float yawDelta = trailer.getYaw() - anchor.getYaw();
            if (Math.acos(Math.cos(yawDelta)) > Math.PI / 2) dispenser.setFlipped(!dispenser.isFlipped());
        }
        trailer.setYaw((anchor.getYaw() + 360) % 360);
        trailer.age = 0;
        train.add(trailer);
        trailer.getEntityWorld().playSound(trailer, trailer.getBlockPos(),
                SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    private void cullDisconnectedTrailers(ServerWorld world) {
        for (int i = 1; i < train.size(); i++) {
            if (!shouldDisconnect(train.get(i))) continue;
            while (train.size() > i) {
                AbstractMinecartEntity dropped = train.get(i);
                dropped.removeCommandTag(TAG_TRAIN);
                dropped.removeCommandTag(TAG_TRAIN_MOVE);
                dropped.age = DISCONNECT_AGE_OFFSET;
                world.playSound(dropped, dropped.getBlockPos(), SoundEvents.BLOCK_BAMBOO_BREAK,
                        SoundCategory.BLOCKS, 1.0F, 1.0F);
                train.remove(i);
            }
            return;
        }
    }

    private static boolean shouldDisconnect(@Nullable AbstractMinecartEntity trailer) {
        if (trailer == null) return true;
        if (trailer.isRemoved()) return true;
        if (trailer.isOnGround() && trailer.getVelocity().horizontalLength() < GROUND_STOP_THRESHOLD) return true;
        return !trailer.getCommandTags().contains(TAG_TRAIN);
    }

    private void emitSmokeWhenLit() {
        if (!this.isLit() || this.random.nextInt(4) != 0) return;
        this.getEntityWorld().addParticleClient(ParticleTypes.LARGE_SMOKE,
                this.getX(), this.getY() + 0.8, this.getZ(), 0.0, 0.0, 0.0);
    }

    @Override
    protected Vec3d applySlowdown(Vec3d velocity) {
        if (!this.isLit()) return velocity.multiply(EMPTY_HORIZONTAL_DECAY, 0.0, EMPTY_HORIZONTAL_DECAY);
        Vec3d push = forwardYawVector((this.getYaw() + 360) % 360, 1.0);
        return this.getVelocity().add(push.x * PROPULSION_PER_TICK, 0.0, push.z * PROPULSION_PER_TICK);
    }

    @Override
    protected void writeCustomData(WriteView view) {
        super.writeCustomData(view);
        view.putShort("Fuel", (short) this.fuel);
        view.putShort("TrainLength", (short) train.size());
        for (int i = 1; i < train.size(); i++) {
            view.putString("Train" + i, String.valueOf(train.get(i).getUuid()));
        }
        view.putBoolean("Lit", isLit());
    }

    @Override
    protected void readCustomData(ReadView view) {
        super.readCustomData(view);
        this.fuel = view.getShort("Fuel", (short) 0);
        int len = view.getShort("TrainLength", (short) 0);
        for (int i = 1; i < len; i++) {
            String raw = view.getString("Train" + i, "");
            if (raw.isEmpty()) continue;
            pendingTrailerUuids.add(UUID.fromString(raw));
        }
        setLit(view.getBoolean("Lit", false));
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (fuel > 0) this.setLit(true);
        if (!this.getEntityWorld().getFuelRegistry().isFuel(stack)) return ActionResult.SUCCESS;
        int burnTicks = this.getEntityWorld().getFuelRegistry().getFuelTicks(stack);
        if (fuel + burnTicks > FUEL_CAP) return ActionResult.SUCCESS;
        fuel += burnTicks;
        this.setLit(true);
        if (stack.isOf(Items.LAVA_BUCKET)) {
            if (player.isInCreativeMode()) return ActionResult.SUCCESS;
            ItemStack swapped = ItemUsage.exchangeStack(stack, player, Items.BUCKET.getDefaultStack());
            player.setStackInHand(hand, swapped);
            return ActionResult.SUCCESS;
        }
        stack.decrementUnlessCreative(1, player);
        return ActionResult.SUCCESS;
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        for (AbstractMinecartEntity trailer : train) {
            if (trailer == null) continue;
            trailer.removeCommandTag(TAG_TRAIN);
            trailer.removeCommandTag(TAG_TRAIN_MOVE);
        }
        super.remove(reason);
    }

    @Override
    public Entity teleportTo(TeleportTarget teleportTarget) {
        if (this.getEntityWorld() instanceof ServerWorld serverWorld) {
            serverWorld.resetIdleTimeout();
            serverWorld.getChunkManager().addTicket(ChunkTicketType.PORTAL,
                    new ChunkPos(this.getBlockPos()), 3);
        }
        for (AbstractMinecartEntity trailer : train) {
            if (trailer == null) continue;
            trailer.removeCommandTag(TAG_TRAIN);
            trailer.removeCommandTag(TAG_TRAIN_MOVE);
            trailer.addCommandTag(TAG_TRAIN_TP);
        }
        train.clear();
        return super.teleportTo(teleportTarget);
    }

    @Override
    protected double getMaxSpeed(ServerWorld world) {
        return super.getMaxSpeed(world) * (1 - 0.05 * train.size());
    }
}
