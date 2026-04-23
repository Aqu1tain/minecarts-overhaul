package com.akitain.minecartsoverhaul.mixin;

import com.akitain.minecartsoverhaul.block.ModBlocks;
import com.google.common.collect.ImmutableBiMap;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HoneycombItem.class)
public class HoneycombItemMixin {

    @ModifyExpressionValue(method = "lambda$static$0", at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableBiMap;builder()Lcom/google/common/collect/ImmutableBiMap$Builder;"
    ))
    private static ImmutableBiMap.Builder<Block, Block> registerWaxableCopperRails(ImmutableBiMap.Builder<Block, Block> builder) {
        return builder
                .put(ModBlocks.COPPER_RAIL, ModBlocks.WAXED_COPPER_RAIL)
                .put(ModBlocks.EXPOSED_COPPER_RAIL, ModBlocks.WAXED_EXPOSED_COPPER_RAIL)
                .put(ModBlocks.WEATHERED_COPPER_RAIL, ModBlocks.WAXED_WEATHERED_COPPER_RAIL)
                .put(ModBlocks.OXIDIZED_COPPER_RAIL, ModBlocks.WAXED_OXIDIZED_COPPER_RAIL);
    }
}
