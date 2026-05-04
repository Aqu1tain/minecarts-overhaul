package com.akitain.minecartsoverhaul.mixin.minecart;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.MinecartDispenseItemBehavior;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecartDispenseItemBehavior.class)
public class MinecartDispenserBehaviorMixin {

    // Same nudge used in spawn-rotation: a tiny upward velocity forces the rail-snap to re-run
    // on the next tick with the new yaw applied.
    @Unique
    private static final float YAW_FLIP_Y_NUDGE = 0.001f;

    @Inject(method = "execute", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void furnaceMinecartFaceAwayFromDispenser(BlockSource pointer, ItemStack stack, CallbackInfoReturnable<ItemStack> cir, @Local AbstractMinecart cart) {
        // Mirror player-placement face-away semantics: only flip when the dispenser fires
        // North or West so carts always end up moving away from it instead of crashing back.
        Direction facing = pointer.state().getValue(DispenserBlock.FACING);
        if (facing != Direction.NORTH && facing != Direction.WEST) return;
        cart.setYRot((cart.getYRot() + 180) % 360);
        cart.setDeltaMovement(0, YAW_FLIP_Y_NUDGE, 0);
        cart.setFlipped(true);
    }
}
