package com.akitain.minecartsoverhaul.mixin.minecart;

import com.akitain.minecartsoverhaul.registry.other.DispencerMinecartEntity;
import com.akitain.minecartsoverhaul.registry.other.FixedFurnaceMinecartEntity;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.entity.vehicle.ExperimentalMinecartController;
import net.minecraft.entity.vehicle.FurnaceMinecartEntity;
import net.minecraft.entity.vehicle.VehicleEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartEntityMixin extends VehicleEntity {

    private static final double GENTLE_FALL_Y_THRESHOLD = -0.7;
    private static final double GENTLE_FALL_Y_DRAG = 0.95;
    // Effectively disables the off-rail clamp; values that high are not reachable in practice
    // but keep momentum from being capped mid-jump after leaving a copper rail at speed.
    private static final double OFF_RAIL_MAX_SPEED = 40.0;
    private static final double PLACEMENT_Y_NUDGE = 0.001;
    private static final int CLIENT_TAG_CLEAR_AGE = 30;
    private static final String TAG_TRAIN = "train";
    private static final String TAG_TRAIN_MOVE = "trainMove";
    private static final String TAG_TRAIN_TP = "trainTP";
    private static final String TAG_TRAIN_NO_ENGINE = "trainNoEngine";

    @Shadow
    public abstract boolean isOnRail();

    public AbstractMinecartEntityMixin(EntityType<?> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "areMinecartImprovementsEnabled", at = @At(value = "HEAD"), cancellable = true)
    private static void improvedMinecarts(World world, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
        cir.cancel();
    }

    @Inject(method = "moveOffRail", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V", ordinal = 1
    ), cancellable = true)
    private void noAirDragInitially(ServerWorld world, CallbackInfo ci) {
        // Below the threshold the cart is in a real fall and vanilla drag should resume; above
        // it (mild downward or upward) we preserve horizontal momentum so a copper-rail launch
        // doesn't get scrubbed by air drag while clearing a small gap.
        if (this.getVelocity().getY() <= GENTLE_FALL_Y_THRESHOLD) return;
        this.setVelocity(this.getVelocity().multiply(1, GENTLE_FALL_Y_DRAG, 1));
        ci.cancel();
    }

    @Redirect(method = "moveOffRail", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;getMaxSpeed(Lnet/minecraft/server/world/ServerWorld;)D"))
    private double clampOffRailToHighCeiling(AbstractMinecartEntity instance, ServerWorld world) {
        return OFF_RAIL_MAX_SPEED;
    }

    @Redirect(method = "moveOffRail", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V", ordinal = 0))
    private void groundFriction(AbstractMinecartEntity instance, Vec3d vec3d) {
        // Vanilla applies a flat 0.5 friction; using the block's slipperiness lets ice keep the
        // cart sliding when it lands off-rail.
        double slipperiness = this.getEntityWorld().getBlockState(this.getVelocityAffectingPos()).getBlock().getSlipperiness();
        instance.setVelocity(instance.getVelocity().multiply(slipperiness));
    }

    @Redirect(method = "pushAwayFromMinecart", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;addVelocity(DDD)V"))
    private void furnaceMinecartsCantBePushedAdd(AbstractMinecartEntity instance, double x, double y, double z) {
        if (instance instanceof FurnaceMinecartEntity) return;
        instance.addVelocity(x, y, z);
    }

    @Redirect(method = "pushAwayFromMinecart", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"))
    private void furnaceMinecartsCantBePushedSet(AbstractMinecartEntity instance, Vec3d vec3d) {
        if (instance instanceof FurnaceMinecartEntity) return;
        instance.setVelocity(vec3d);
    }

    @Redirect(method = "pushAwayFrom", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/vehicle/AbstractMinecartEntity;addVelocity(DDD)V"))
    private void trainMinecartsCantBePushed(AbstractMinecartEntity instance, double x, double y, double z) {
        if (instance.getCommandTags().contains(TAG_TRAIN)) return;
        instance.addVelocity(x, y, z);
    }

    @Redirect(method = "create", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/vehicle/ExperimentalMinecartController;adjustToRail(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Z)V"
    ))
    private static <T extends AbstractMinecartEntity> void setSpawnRotation(ExperimentalMinecartController controller, BlockPos blockPos,
                                                                            BlockState blockState, boolean ignoreWeight,
                                                                            @Local T cart, @Local(argsOnly = true) PlayerEntity player) {
        controller.adjustToRail(blockPos, blockState, true);
        if (player == null) return;
        if (!(cart instanceof FurnaceMinecartEntity || cart instanceof DispencerMinecartEntity)) return;
        // Face the cart away from the player on placement: compute the player's reverse heading
        // and flip the cart 180 if that points behind the cart's natural rail-aligned yaw.
        float playerReverseYaw = (-player.headYaw - 90 + 720) % 360;
        if (Math.cos((playerReverseYaw - cart.getYaw()) * Math.PI / 180f) >= 0) return;
        cart.setYaw((cart.getYaw() + 180) % 360);
        // Tiny upward velocity so the rail-snap re-runs next tick with the new yaw.
        cart.setVelocity(0, PLACEMENT_Y_NUDGE, 0);
        cart.setYawFlipped(true);
    }

    @Inject(method = "readCustomData", at = @At("TAIL"))
    private void restoreTrainTagState(ReadView view, CallbackInfo ci) {
        // Saved trailers come back with `train` but not `trainMove`; re-add it so the locomotive's
        // first cascade tick after load can drive them.
        this.age = 0;
        this.removeCommandTag(TAG_TRAIN_NO_ENGINE);
        if (this.getCommandTags().contains(TAG_TRAIN)) this.addCommandTag(TAG_TRAIN_MOVE);
    }

    @Inject(method = "collidesWith", at = @At(value = "RETURN"), cancellable = true)
    private void removeTrainCollisions(Entity otherEntity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (!(otherEntity instanceof AbstractMinecartEntity)) return;
        cir.setReturnValue(shouldCollide(this, otherEntity));
    }

    @Unique
    private static boolean shouldCollide(Entity self, Entity other) {
        if (selfFurnaceContainsTrailer(self, other)) return false;
        if (!isTrainMember(self)) return true;
        if (((AbstractMinecartEntity) self).isOnRail()) return false;
        if (isTrainMember(other)) return false;
        if (other instanceof FixedFurnaceMinecartEntity locomotive && locomotive.getTrain().contains(self)) return false;
        return true;
    }

    @Unique
    private static boolean selfFurnaceContainsTrailer(Entity self, Entity other) {
        return self instanceof FixedFurnaceMinecartEntity locomotive && locomotive.getTrain().contains(other);
    }

    @Unique
    private static boolean isTrainMember(Entity e) {
        return e.getCommandTags().contains(TAG_TRAIN) || e.getCommandTags().contains(TAG_TRAIN_TP);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void clearClientTrainTags(CallbackInfo ci) {
        // Tags are sent server->client as part of the train sync; without this they'd accumulate
        // across reconnects since the client never sees the disconnect events that clear them.
        if (!this.getEntityWorld().isClient()) return;
        if (this.age != CLIENT_TAG_CLEAR_AGE) return;
        this.getCommandTags().clear();
    }
}
