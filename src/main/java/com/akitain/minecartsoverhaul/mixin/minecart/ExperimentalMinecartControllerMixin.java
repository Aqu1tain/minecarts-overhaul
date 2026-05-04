package com.akitain.minecartsoverhaul.mixin.minecart;

import com.akitain.minecartsoverhaul.registry.block.CopperRailBlock;
import com.akitain.minecartsoverhaul.registry.other.FixedFurnaceMinecartEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PoweredRailBlock;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.ExperimentalMinecartController;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.entity.vehicle.MinecartController;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ExperimentalMinecartController.class)
public abstract class ExperimentalMinecartControllerMixin extends MinecartController {

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

    protected ExperimentalMinecartControllerMixin(AbstractMinecartEntity minecart) {
        super(minecart);
    }

    @Inject(method = "getMaxSpeed", at = @At("HEAD"), cancellable = true)
    private void copperSpeed(ServerWorld world, CallbackInfoReturnable<Double> cir) {
        cir.setReturnValue(computeMaxSpeedPerTick(world));
        cir.cancel();
    }

    private double computeMaxSpeedPerTick(ServerWorld world) {
        BlockState state = world.getBlockState(this.minecart.getBlockPos());
        double bps = baseSpeedBpsFor(state);
        if (this.minecart.isTouchingWater()) bps *= WATER_SPEED_FACTOR;
        double perTick = bps / TICKS_PER_SECOND;
        // Momentum floor: lets a high-speed cart cross a single low-tier rail without crashing
        // the cap to that tier's value in one tick.
        perTick = Math.max(perTick, this.minecart.getVelocity().horizontalLength() * MOMENTUM_FLOOR_FACTOR);
        if (state.getBlock() instanceof PoweredRailBlock && !(this.minecart instanceof FurnaceMinecartEntity)) {
            perTick = VANILLA_RAIL_SPEED_BPS / TICKS_PER_SECOND;
        }
        return perTick;
    }

    private static double baseSpeedBpsFor(BlockState state) {
        if (state.getBlock() instanceof CopperRailBlock) return CopperRailBlock.getMaxVelocity(state);
        if (state.getBlock() instanceof AbstractRailBlock) return VANILLA_RAIL_SPEED_BPS;
        return DEFAULT_OFF_RAIL_SPEED_BPS;
    }

    @Inject(method = "applySlopeVelocity", at = @At("HEAD"), cancellable = true)
    private void skipFurnaceSlopeSlowdown(Vec3d horizontalVelocity, RailShape railShape, CallbackInfoReturnable<Vec3d> cir) {
        if (!(this.minecart instanceof FurnaceMinecartEntity)) return;
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
        if (this.minecart.getCommandTags().contains(TAG_TRAIN_MOVE)) ci.cancel();
    }

    private void resetFallDistance() {
        for (Entity passenger : this.minecart.getPassengerList()) {
            passenger.fallDistance = 0;
            this.minecart.fallDistance = 0;
        }
    }

    private void expireStaleTrainTags() {
        if (this.minecart.age <= TRAIN_TAG_AGE_LIMIT) return;
        this.minecart.removeCommandTag(TAG_TRAIN);
        this.minecart.removeCommandTag(TAG_TRAIN_MOVE);
        this.minecart.removeCommandTag(TAG_TRAIN_TP);
    }

    @ModifyExpressionValue(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/ExperimentalMinecartController;calcNewHorizontalVelocity(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/vehicle/ExperimentalMinecartController$MoveIteration;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/block/enums/RailShape;)Lnet/minecraft/util/math/Vec3d;"))
    private Vec3d skipPoweredRailSlowdownForTrains(Vec3d original) {
        if (this.minecart.noClip || this.minecart.getCommandTags().contains(TAG_TRAIN)) {
            return this.getVelocity().getHorizontal();
        }
        return original;
    }

    @Inject(method = "moveOnRail", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isOf(Lnet/minecraft/block/Block;)Z"))
    private void recordPoweredRailLitFlag(ServerWorld world, CallbackInfo ci, @Local BlockState blockState) {
        if (!(this.minecart instanceof FixedFurnaceMinecartEntity locomotive)) return;
        if (!blockState.isOf(Blocks.POWERED_RAIL)) return;
        // Don't toggle lit here: locomotive consumes the flag on its own tick so the change is
        // applied once per powered rail crossed, not every sub-tick of moveOnRail.
        locomotive.powerRailSetLit = blockState.get(PoweredRailBlock.POWERED) ? LIT_ON : LIT_OFF;
    }

    @Inject(method = "getSpeedRetention", at = @At("HEAD"), cancellable = true)
    private void forceTrainConsistentRetention(CallbackInfoReturnable<Double> cir) {
        // Negative age = recently disconnected trailer (set by locomotive on cull) so it coasts
        // to a stop at the same rate as any train member instead of vanilla's ridden/empty split.
        if (this.minecart.age >= 0 && !this.minecart.getCommandTags().contains(TAG_TRAIN)) return;
        cir.setReturnValue(TRAIN_RETENTION);
        cir.cancel();
    }

    @Redirect(method = "pushAwayFromEntities", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;hasPassengers()Z"))
    private boolean treatRiddenAsEmptyForPush(AbstractMinecartEntity instance) {
        // Vanilla halves push force when a cart has passengers; returning false keeps full force
        // so trains don't compress at the locomotive when a player rides one of the trailers.
        return false;
    }
}
