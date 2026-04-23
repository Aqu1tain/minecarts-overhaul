package com.akitain.minecartsoverhaul.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(MinecartFurnace.class)
public abstract class MinecartFurnaceMixin {

    @Unique private static final int MAX_TRAILERS = 7;
    @Unique private static final float TRAIN_DISTANCE = 1.5F;
    @Unique private static final double MAX_SNAP_DISTANCE_SQR = 4.0;

    @Unique private final List<AbstractMinecart> train = new ArrayList<>();
    @Unique private final List<UUID> pendingTrainUuids = new ArrayList<>();

    @Shadow private int fuel;
    @Shadow public Vec3 push;
    @Shadow protected abstract boolean hasFuel();
    @Shadow protected abstract void setHasFuel(boolean hasFuel);

    @ModifyConstant(method = "getMaxSpeed", constant = @Constant(doubleValue = 0.5))
    private double uncapMaxSpeed(double original) {
        return 1.0;
    }

    @ModifyReturnValue(method = "getMaxSpeed", at = @At("RETURN"))
    private double applyTrainSpeedPenalty(double original) {
        return original * (1.0 - 0.05 * (train.size() + 1));
    }

    @Inject(method = "addFuel", at = @At("HEAD"), cancellable = true)
    private void acceptAnyFuel(Vec3 interactingPos, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        MinecartFurnace self = self();
        int burnTicks = self.level().fuelValues().burnDuration(stack);
        if (burnTicks <= 0 || fuel + burnTicks > 32000) {
            cir.setReturnValue(false);
            return;
        }
        fuel += burnTicks;
        push = self.position().subtract(interactingPos).horizontal();
        cir.setReturnValue(true);
    }

    @Inject(method = "applyNaturalSlowdown", at = @At("HEAD"), cancellable = true)
    private void propelAlongYaw(Vec3 velocity, CallbackInfoReturnable<Vec3> cir) {
        MinecartFurnace self = self();
        if (hasFuel()) {
            float yawRad = (float) ((self.getYRot() + 360.0F) % 360.0F * Math.PI / 180.0);
            double pushX = Mth.cos(yawRad) / 40.0;
            double pushZ = -Mth.sin(yawRad) / 40.0;
            cir.setReturnValue(velocity.add(pushX, 0.0, pushZ));
            return;
        }
        cir.setReturnValue(velocity.multiply(0.75, 0.0, 0.75));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void updateTrain(CallbackInfo ci) {
        MinecartFurnace self = self();
        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        applyPoweredRailToggle(self);
        restorePendingTrain(serverLevel);
        disconnectBroken();
        moveTrailers(serverLevel);
        attachNearby(serverLevel);
    }

    @Unique
    private void applyPoweredRailToggle(MinecartFurnace self) {
        BlockState state = self.level().getBlockState(self.blockPosition());
        if (!(state.getBlock() instanceof PoweredRailBlock)) return;
        if (fuel <= 0) return;
        setHasFuel(state.getValue(PoweredRailBlock.POWERED));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void writeTrain(ValueOutput output, CallbackInfo ci) {
        output.putInt("TrainSize", train.size());
        for (int i = 0; i < train.size(); i++) {
            output.putString("Train" + i, train.get(i).getUUID().toString());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readTrain(ValueInput input, CallbackInfo ci) {
        pendingTrainUuids.clear();
        int size = input.getIntOr("TrainSize", 0);
        for (int i = 0; i < size; i++) {
            String raw = input.getStringOr("Train" + i, "");
            if (raw.isEmpty()) continue;
            try {
                pendingTrainUuids.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Unique
    private void restorePendingTrain(ServerLevel level) {
        if (pendingTrainUuids.isEmpty()) return;
        for (UUID uuid : pendingTrainUuids) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof AbstractMinecart trailer && !trailer.isRemoved()) {
                trailer.addTag("train");
                trailer.addTag("trainMove");
                trailer.setOnRails(true);
                train.add(trailer);
            }
        }
        pendingTrainUuids.clear();
    }

    @Unique
    private void disconnectBroken() {
        for (int i = 0; i < train.size(); i++) {
            AbstractMinecart trailer = train.get(i);
            if (isTrainBreakBoundary(trailer, i == 0 ? self() : train.get(i - 1))) {
                breakTrainAt(i);
                return;
            }
        }
    }

    @Unique
    private boolean isTrainBreakBoundary(AbstractMinecart trailer, AbstractMinecart previous) {
        if (trailer.isRemoved()) return true;
        if (!trailer.entityTags().contains("train")) return true;
        return trailer.onGround() && trailer.getDeltaMovement().horizontalDistance() < 0.01;
    }

    @Unique
    private void breakTrainAt(int index) {
        MinecartFurnace self = self();
        for (int i = train.size() - 1; i >= index; i--) {
            AbstractMinecart trailer = train.get(i);
            trailer.removeTag("train");
            trailer.removeTag("trainMove");
            self.level().playSound(null, trailer.blockPosition(), SoundEvents.BAMBOO_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
            train.remove(i);
        }
    }

    @Unique
    private void moveTrailers(ServerLevel level) {
        if (train.isEmpty()) return;

        MinecartFurnace self = self();
        double locomotiveSpeed = self.getDeltaMovement().horizontalDistance();
        AbstractMinecart probe = new MinecartChest(EntityType.CHEST_MINECART, level);
        probe.noPhysics = true;
        probe.addTag("train");

        AbstractMinecart anchor = self;
        boolean cascade = self.isOnRails();
        for (AbstractMinecart trailer : train) {
            trailer.removeTag("trainMove");
            if (!cascade) continue;
            if (!isOnRail(trailer)) {
                cascade = false;
                continue;
            }
            placeProbeBehind(probe, anchor);
            probe.getBehavior().moveAlongTrack(level);
            if (!snapTrailerToProbe(trailer, probe, locomotiveSpeed)) {
                cascade = false;
                continue;
            }
            trailer.addTag("trainMove");
            anchor = trailer;
        }

        probe.remove(Entity.RemovalReason.DISCARDED);
    }

    @Unique
    private void placeProbeBehind(AbstractMinecart probe, AbstractMinecart anchor) {
        probe.setPos(anchor.position());
        probe.setYRot(anchor.getYRot());
        probe.setXRot(anchor.getXRot());
        probe.setOnRails(true);
        float yawRad = (float) (anchor.getYRot() * Math.PI / 180.0);
        probe.setDeltaMovement(
                -TRAIN_DISTANCE * Mth.cos(yawRad),
                0.0,
                TRAIN_DISTANCE * Mth.sin(yawRad)
        );
    }

    @Unique
    private boolean snapTrailerToProbe(AbstractMinecart trailer, AbstractMinecart probe, double locomotiveSpeed) {
        if (trailer.position().distanceToSqr(probe.position()) > MAX_SNAP_DISTANCE_SQR) return false;

        Vec3 probeVelocity = probe.getDeltaMovement();
        Vec3 horizontal = new Vec3(probeVelocity.x, 0.0, probeVelocity.z);
        trailer.setPos(probe.position());
        trailer.setYRot((probe.getYRot() + 360.0F) % 360.0F);
        trailer.setXRot(probe.getXRot());

        if (horizontal.lengthSqr() < 1.0E-6) {
            trailer.setDeltaMovement(0.0, trailer.getDeltaMovement().y, 0.0);
            return true;
        }

        Vec3 forward = horizontal.normalize().scale(-locomotiveSpeed);
        trailer.setDeltaMovement(forward.x, trailer.getDeltaMovement().y, forward.z);
        return true;
    }

    @Unique
    private void attachNearby(ServerLevel level) {
        if (train.size() >= MAX_TRAILERS) return;

        MinecartFurnace self = self();
        AbstractMinecart anchor = train.isEmpty() ? self : train.get(train.size() - 1);
        if (!anchor.isOnRails()) return;

        AbstractMinecart probe = new MinecartChest(EntityType.CHEST_MINECART, level);
        probe.noPhysics = true;
        probe.addTag("train");
        placeProbeBehind(probe, anchor);
        probe.getBehavior().moveAlongTrack(level);

        for (AbstractMinecart candidate : findAttachCandidates(level, probe)) {
            if (train.size() >= MAX_TRAILERS) break;
            attach(candidate);
        }

        probe.remove(Entity.RemovalReason.DISCARDED);
    }

    @Unique
    private List<AbstractMinecart> findAttachCandidates(ServerLevel level, AbstractMinecart probe) {
        return level.getEntitiesOfClass(
                AbstractMinecart.class,
                probe.getBoundingBox().deflate(0.2),
                candidate -> candidate != null
                        && !(candidate instanceof MinecartFurnace)
                        && !candidate.entityTags().contains("train")
                        && isOnRail(candidate)
        );
    }

    @Unique
    private boolean isOnRail(AbstractMinecart cart) {
        BlockPos pos = cart.getCurrentBlockPosOrRailBelow();
        return BaseRailBlock.isRail(cart.level().getBlockState(pos));
    }

    @Unique
    private void attach(AbstractMinecart candidate) {
        MinecartFurnace self = self();
        candidate.addTag("train");
        candidate.addTag("trainMove");
        candidate.setOnRails(true);
        train.add(candidate);
        self.level().playSound(null, candidate.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Unique
    private MinecartFurnace self() {
        return (MinecartFurnace) (Object) this;
    }
}
