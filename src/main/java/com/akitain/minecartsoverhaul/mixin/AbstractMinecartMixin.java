package com.akitain.minecartsoverhaul.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
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
