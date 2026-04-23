package com.akitain.minecartsoverhaul.mixin;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecartFurnace.class)
public abstract class MinecartFurnaceMixin {

    @Shadow private int fuel;
    @Shadow public Vec3 push;
    @Shadow protected abstract boolean hasFuel();

    @ModifyConstant(method = "getMaxSpeed", constant = @Constant(doubleValue = 0.5))
    private double uncapMaxSpeed(double original) {
        return 1.0;
    }

    @Inject(method = "addFuel", at = @At("HEAD"), cancellable = true)
    private void acceptAnyFuel(Vec3 interactingPos, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        MinecartFurnace self = (MinecartFurnace) (Object) this;
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
        MinecartFurnace self = (MinecartFurnace) (Object) this;
        if (hasFuel()) {
            float yawRad = (float) ((self.getYRot() + 360.0F) % 360.0F * Math.PI / 180.0);
            double pushX = Mth.cos(yawRad) / 40.0;
            double pushZ = Mth.sin(yawRad) / 40.0;
            cir.setReturnValue(velocity.add(pushX, 0.0, pushZ));
            return;
        }
        cir.setReturnValue(velocity.multiply(0.75, 0.0, 0.75));
    }
}
