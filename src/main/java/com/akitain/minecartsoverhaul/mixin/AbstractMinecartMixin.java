package com.akitain.minecartsoverhaul.mixin;

import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractMinecart.class)
public class AbstractMinecartMixin {

    @Inject(method = "useExperimentalMovement", at = @At("HEAD"), cancellable = true)
    private static void forceExperimentalMovement(Level level, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
