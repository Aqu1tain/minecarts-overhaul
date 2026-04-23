package com.akitain.minecartsoverhaul.mixin;

import com.akitain.minecartsoverhaul.block.ModBlocks;
import com.google.common.collect.ImmutableBiMap;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WeatheringCopper.class)
public interface WeatheringCopperMixin {

    @ModifyExpressionValue(method = "lambda$static$0", at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableBiMap;builder()Lcom/google/common/collect/ImmutableBiMap$Builder;"
    ))
    private static ImmutableBiMap.Builder<Block, Block> registerCopperRailProgression(ImmutableBiMap.Builder<Block, Block> builder) {
        return builder
                .put(ModBlocks.COPPER_RAIL, ModBlocks.EXPOSED_COPPER_RAIL)
                .put(ModBlocks.EXPOSED_COPPER_RAIL, ModBlocks.WEATHERED_COPPER_RAIL)
                .put(ModBlocks.WEATHERED_COPPER_RAIL, ModBlocks.OXIDIZED_COPPER_RAIL);
    }
}
