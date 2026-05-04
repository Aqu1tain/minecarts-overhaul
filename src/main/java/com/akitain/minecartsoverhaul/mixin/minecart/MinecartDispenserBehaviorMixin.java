package com.akitain.minecartsoverhaul.mixin.minecart;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.MinecartDispenserBehavior;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecartDispenserBehavior.class)
public class MinecartDispenserBehaviorMixin {

    // Same nudge used in spawn-rotation: a tiny upward velocity forces the rail-snap to re-run
    // on the next tick with the new yaw applied.
    @Unique
    private static final float YAW_FLIP_Y_NUDGE = 0.001f;

    @Inject(method = "dispenseSilently", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;decrement(I)V"))
    private void furnaceMinecartFaceAwayFromDispenser(BlockPointer pointer, ItemStack stack, CallbackInfoReturnable<ItemStack> cir, @Local AbstractMinecartEntity cart) {
        // Mirror player-placement face-away semantics: only flip when the dispenser fires
        // North or West so carts always end up moving away from it instead of crashing back.
        Direction facing = pointer.state().get(DispenserBlock.FACING);
        if (facing != Direction.NORTH && facing != Direction.WEST) return;
        cart.setYaw((cart.getYaw() + 180) % 360);
        cart.setVelocity(0, YAW_FLIP_Y_NUDGE, 0);
        cart.setYawFlipped(true);
    }
}
