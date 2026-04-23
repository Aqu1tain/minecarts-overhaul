package com.akitain.minecartsoverhaul.mixin;

import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(MinecartFurnace.class)
public class MinecartFurnaceMixin {

    @ModifyConstant(method = "getMaxSpeed", constant = @Constant(doubleValue = 0.5))
    private double uncapMaxSpeed(double original) {
        return 1.0;
    }
}
