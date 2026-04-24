package com.akitain.minecartsoverhaul.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
public class AbstractMinecartMixin {

    @Inject(method = "useExperimentalMovement", at = @At("HEAD"), cancellable = true)
    private static void forceExperimentalMovement(Level level, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @Redirect(method = "comeOffTrack", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;getMaxSpeed(Lnet/minecraft/server/level/ServerLevel;)D"))
    private double uncapOffTrackSpeed(AbstractMinecart self, ServerLevel level) {
        return 40.0;
    }

    @Redirect(method = "pushOtherMinecart",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;push(DDD)V"))
    private void furnaceIsUnpushable(AbstractMinecart target, double x, double y, double z) {
        if (target instanceof MinecartFurnace) return;
        target.push(x, y, z);
    }

    @Redirect(method = "pushOtherMinecart",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void furnaceKeepsMomentum(AbstractMinecart target, Vec3 velocity) {
        if (target instanceof MinecartFurnace) return;
        target.setDeltaMovement(velocity);
    }

    @Redirect(method = "push(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;push(DDD)V"))
    private void trainIsUnpushable(AbstractMinecart target, double x, double y, double z) {
        if (target.entityTags().contains("train")) return;
        target.push(x, y, z);
    }

    @Inject(method = "canCollideWith", at = @At("HEAD"), cancellable = true)
    private void skipInternalTrainCollision(net.minecraft.world.entity.Entity other, CallbackInfoReturnable<Boolean> cir) {
        AbstractMinecart self = (AbstractMinecart) (Object) this;
        if (!(other instanceof AbstractMinecart otherCart)) return;
        boolean selfInTrain = self instanceof MinecartFurnace || self.entityTags().contains("train");
        boolean otherInTrain = otherCart instanceof MinecartFurnace || otherCart.entityTags().contains("train");
        if (selfInTrain && otherInTrain) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "createMinecart", at = @At("RETURN"))
    private static <T extends AbstractMinecart> void faceAwayFromPlacer(
            Level level, double x, double y, double z, net.minecraft.world.entity.EntityType<T> type,
            net.minecraft.world.entity.EntitySpawnReason reason, net.minecraft.world.item.ItemStack stack,
            Player player, CallbackInfoReturnable<T> cir,
            @Local T created) {
        if (player == null || created == null) return;
        if (!(created instanceof MinecartFurnace)) return;

        float targetYaw = (-player.getYHeadRot() - 90.0F + 720.0F) % 360.0F;
        float currentYaw = created.getYRot();
        if (Mth.cos((float) ((targetYaw - currentYaw) * Math.PI / 180.0)) < 0) {
            created.setYRot((currentYaw + 180.0F) % 360.0F);
        }
    }

    @Redirect(method = "comeOffTrack", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
            ordinal = 0))
    private void slideOnGround(AbstractMinecart self, Vec3 scaled) {
        float friction = self.level().getBlockState(self.getBlockPosBelowThatAffectsMyMovement()).getBlock().getFriction();
        self.setDeltaMovement(self.getDeltaMovement().multiply(friction, friction, friction));
    }

    @Redirect(method = "comeOffTrack", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/vehicle/minecart/AbstractMinecart;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",
            ordinal = 1))
    private void preserveHorizontalInAir(AbstractMinecart self, Vec3 scaled) {
        Vec3 current = self.getDeltaMovement();
        if (current.y > -0.7) {
            self.setDeltaMovement(current.multiply(1.0, 0.95, 1.0));
        } else {
            self.setDeltaMovement(scaled);
        }
    }
}
