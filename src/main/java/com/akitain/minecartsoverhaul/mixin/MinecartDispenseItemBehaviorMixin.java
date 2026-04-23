package com.akitain.minecartsoverhaul.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.MinecartDispenseItemBehavior;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecartDispenseItemBehavior.class)
public class MinecartDispenseItemBehaviorMixin {

    @Inject(method = "execute",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void faceAwayFromDispenser(BlockSource source, ItemStack stack, CallbackInfoReturnable<ItemStack> cir,
                                       @Local AbstractMinecart minecart) {
        if (!(minecart instanceof MinecartFurnace)) return;
        Direction facing = source.state().getValue(DispenserBlock.FACING);
        if (facing != Direction.NORTH && facing != Direction.WEST) return;
        minecart.setYRot((minecart.getYRot() + 180.0F) % 360.0F);
    }
}
