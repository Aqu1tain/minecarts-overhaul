package com.akitain.minecartsoverhaul.mixin.minecart;

import com.akitain.minecartsoverhaul.registry.other.DispencerMinecartEntity;
import com.akitain.minecartsoverhaul.registry.other.FixedFurnaceMinecartEntity;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
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
    public abstract boolean isOnRails();

    public AbstractMinecartEntityMixin(EntityType<?> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "useExperimentalMovement", at = @At(value = "HEAD"), cancellable = true)
    private static void improvedMinecarts(Level world, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
        cir.cancel();
    }

    @Inject(method = "comeOffTrack", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", ordinal = 1
    ), cancellable = true)
    private void noAirDragInitially(ServerLevel world, CallbackInfo ci) {
        // Below the threshold the cart is in a real fall and vanilla drag should resume; above
        // it (mild downward or upward) we preserve horizontal momentum so a copper-rail launch
        // doesn't get scrubbed by air drag while clearing a small gap.
        if (this.getDeltaMovement().y() <= GENTLE_FALL_Y_THRESHOLD) return;
        this.setDeltaMovement(this.getDeltaMovement().multiply(1, GENTLE_FALL_Y_DRAG, 1));
        ci.cancel();
    }

    @Redirect(method = "comeOffTrack", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;getMaxSpeed(Lnet/minecraft/server/level/ServerLevel;)D"))
    private double clampOffRailToHighCeiling(AbstractMinecart instance, ServerLevel world) {
        return OFF_RAIL_MAX_SPEED;
    }

    @Redirect(method = "comeOffTrack", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", ordinal = 0))
    private void groundFriction(AbstractMinecart instance, Vec3 vec3d) {
        // Vanilla applies a flat 0.5 friction; using the block's slipperiness lets ice keep the
        // cart sliding when it lands off-rail.
        double slipperiness = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction();
        instance.setDeltaMovement(instance.getDeltaMovement().scale(slipperiness));
    }

    @Redirect(method = "pushOtherMinecart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;push(DDD)V"))
    private void furnaceMinecartsCantBePushedAdd(AbstractMinecart instance, double x, double y, double z) {
        if (instance instanceof MinecartFurnace) return;
        instance.push(x, y, z);
    }

    @Redirect(method = "pushOtherMinecart", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void furnaceMinecartsCantBePushedSet(AbstractMinecart instance, Vec3 vec3d) {
        if (instance instanceof MinecartFurnace) return;
        instance.setDeltaMovement(vec3d);
    }

    @Redirect(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;push(DDD)V"))
    private void trainMinecartsCantBePushed(AbstractMinecart instance, double x, double y, double z) {
        if (instance.entityTags().contains(TAG_TRAIN)) return;
        instance.push(x, y, z);
    }

    @Redirect(method = "createMinecart", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/minecart/NewMinecartBehavior;adjustToRails(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"
    ))
    private static <T extends AbstractMinecart> void setSpawnRotation(NewMinecartBehavior controller, BlockPos blockPos,
                                                                            BlockState blockState, boolean ignoreWeight,
                                                                            @Local T cart, @Local(argsOnly = true) Player player) {
        controller.adjustToRails(blockPos, blockState, true);
        if (player == null) return;
        if (!(cart instanceof MinecartFurnace || cart instanceof DispencerMinecartEntity)) return;
        // Face the cart away from the player on placement: compute the player's reverse heading
        // and flip the cart 180 if that points behind the cart's natural rail-aligned yaw.
        float playerReverseYaw = (-player.yHeadRot - 90 + 720) % 360;
        if (Math.cos((playerReverseYaw - cart.getYRot()) * Math.PI / 180f) >= 0) return;
        cart.setYRot((cart.getYRot() + 180) % 360);
        // Tiny upward velocity so the rail-snap re-runs next tick with the new yaw.
        cart.setDeltaMovement(0, PLACEMENT_Y_NUDGE, 0);
        cart.setFlipped(true);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void restoreTrainTagState(ValueInput view, CallbackInfo ci) {
        // Saved trailers come back with `train` but not `trainMove`; re-add it so the locomotive's
        // first cascade tick after load can drive them.
        this.tickCount = 0;
        this.removeTag(TAG_TRAIN_NO_ENGINE);
        if (this.entityTags().contains(TAG_TRAIN)) this.addTag(TAG_TRAIN_MOVE);
    }

    @Inject(method = "canCollideWith", at = @At(value = "RETURN"), cancellable = true)
    private void removeTrainCollisions(Entity otherEntity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        if (!(otherEntity instanceof AbstractMinecart)) return;
        cir.setReturnValue(shouldCollide(this, otherEntity));
    }

    @Unique
    private static boolean shouldCollide(Entity self, Entity other) {
        if (selfFurnaceContainsTrailer(self, other)) return false;
        if (!isTrainMember(self)) return true;
        if (((AbstractMinecart) self).isOnRails()) return false;
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
        return e.entityTags().contains(TAG_TRAIN) || e.entityTags().contains(TAG_TRAIN_TP);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void clearClientTrainTags(CallbackInfo ci) {
        // Tags are sent server->client as part of the train sync; without this they'd accumulate
        // across reconnects since the client never sees the disconnect events that clear them.
        if (!this.level().isClientSide()) return;
        if (this.tickCount != CLIENT_TAG_CLEAR_AGE) return;
        this.entityTags().clear();
    }
}
