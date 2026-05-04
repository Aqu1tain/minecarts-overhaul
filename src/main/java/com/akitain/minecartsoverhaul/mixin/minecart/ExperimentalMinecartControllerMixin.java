package com.akitain.minecartsoverhaul.mixin.minecart;

import com.akitain.minecartsoverhaul.registry.block.CopperRailBlock;
import com.akitain.minecartsoverhaul.registry.other.FixedFurnaceMinecartEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartBehavior;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NewMinecartBehavior.class)
public abstract class ExperimentalMinecartControllerMixin extends MinecartBehavior {

    private static final double VANILLA_RAIL_SPEED_BPS = 8.0;
    private static final double DEFAULT_OFF_RAIL_SPEED_BPS = 40.0;
    private static final double WATER_SPEED_FACTOR = 0.5;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double MOMENTUM_FLOOR_FACTOR = 0.9;
    private static final double TRAIN_RETENTION = 0.975;
    private static final int TRAIN_TAG_AGE_LIMIT = 60;
    private static final int LIT_ON = 1;
    private static final int LIT_OFF = -1;
    private static final String TAG_TRAIN = "train";
    private static final String TAG_TRAIN_MOVE = "trainMove";
    private static final String TAG_TRAIN_TP = "trainTP";

    protected ExperimentalMinecartControllerMixin(AbstractMinecart minecart) {
        super(minecart);
    }

    @Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
    private void copperSpeed(ServerLevel world, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(computeMaxSpeedPerTick(world));
        cir.cancel();
    }

    private double computeMaxSpeedPerTick(ServerLevel world) {
        BlockState state = world.getBlockState(this.minecart.blockPosition());
        double bps = baseSpeedBpsFor(state);
        if (this.minecart.isInWater()) bps *= WATER_SPEED_FACTOR;
        double perTick = bps / TICKS_PER_SECOND;
        // Momentum floor: lets a high-speed cart cross a single low-tier rail without crashing
        // the cap to that tier's value in one tick.
        perTick = Math.max(perTick, this.minecart.getDeltaMovement().horizontalDistance() * MOMENTUM_FLOOR_FACTOR);
        if (state.getBlock() instanceof PoweredRailBlock && !(this.minecart instanceof MinecartFurnace)) {
            perTick = VANILLA_RAIL_SPEED_BPS / TICKS_PER_SECOND;
        }
        return perTick;
    }

    private static double baseSpeedBpsFor(BlockState state) {
        if (state.getBlock() instanceof CopperRailBlock) return CopperRailBlock.getMaxVelocity(state);
        if (state.getBlock() instanceof BaseRailBlock) return VANILLA_RAIL_SPEED_BPS;
        return DEFAULT_OFF_RAIL_SPEED_BPS;
    }

    @Inject(method = "calculateSlopeSpeed", at = @At("HEAD"), cancellable = true)
    private void skipFurnaceSlopeSlowdown(Vec3 horizontalVelocity, RailShape railShape, CallbackInfoReturnable<Vec3> cir) {
        if (!(this.minecart instanceof MinecartFurnace)) return;
        cir.setReturnValue(horizontalVelocity);
        cir.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void cancelTickWhileLocomotiveDriven(CallbackInfo ci) {
        resetFallDistance();
        expireStaleTrainTags();
        // trainMove tag is set by the locomotive when it has just placed this trailer; the
        // locomotive owns the trailer's movement this tick so the controller's normal tick
        // would fight it.
        if (this.minecart.entityTags().contains(TAG_TRAIN_MOVE)) ci.cancel();
    }

    private void resetFallDistance() {
        for (Entity passenger : this.minecart.getPassengers()) {
            passenger.fallDistance = 0;
            this.minecart.fallDistance = 0;
        }
    }

    private void expireStaleTrainTags() {
        if (this.minecart.tickCount <= TRAIN_TAG_AGE_LIMIT) return;
        this.minecart.removeTag(TAG_TRAIN);
        this.minecart.removeTag(TAG_TRAIN_MOVE);
        this.minecart.removeTag(TAG_TRAIN_TP);
    }

    @ModifyExpressionValue(method = "moveAlongTrack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior;calculateTrackSpeed(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior$TrackIteration;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/properties/RailShape;)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 skipPoweredRailSlowdownForTrains(Vec3 original) {
        if (this.minecart.noPhysics || this.minecart.entityTags().contains(TAG_TRAIN)) {
            return this.getDeltaMovement().horizontal();
        }
        return original;
    }

    @Inject(method = "moveAlongTrack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isOf(Lnet/minecraft/world/level/block/Block;)Z"))
    private void recordPoweredRailLitFlag(ServerLevel world, CallbackInfo ci, @Local BlockState blockState) {
        if (!(this.minecart instanceof FixedFurnaceMinecartEntity locomotive)) return;
        if (!blockState.is(Blocks.POWERED_RAIL)) return;
        // Don't toggle lit here: locomotive consumes the flag on its own tick so the change is
        // applied once per powered rail crossed, not every sub-tick of moveOnRail.
        locomotive.powerRailSetLit = blockState.getValue(PoweredRailBlock.POWERED) ? LIT_ON : LIT_OFF;
    }

    @Inject(method = "getSlowdownFactor", at = @At("HEAD"), cancellable = true)
    private void forceTrainConsistentRetention(CallbackInfoReturnable<Double> cir) {
        // Negative age = recently disconnected trailer (set by locomotive on cull) so it coasts
        // to a stop at the same rate as any train member instead of vanilla's ridden/empty split.
        if (this.minecart.tickCount >= 0 && !this.minecart.entityTags().contains(TAG_TRAIN)) return;
        cir.setReturnValue(TRAIN_RETENTION);
        cir.cancel();
    }

    @Redirect(method = "pushEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;hasPassengers()Z"))
    private boolean treatRiddenAsEmptyForPush(AbstractMinecart instance) {
        // Vanilla halves push force when a cart has passengers; returning false keeps full force
        // so trains don't compress at the locomotive when a player rides one of the trailers.
        return false;
    }
}
