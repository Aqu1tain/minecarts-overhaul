package com.akitain.minecartsoverhaul.mixin.minecart;

import com.google.common.collect.ImmutableBiMap;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.akitain.minecartsoverhaul.MinecartsOverhaul;
import net.minecraft.block.Block;
import net.minecraft.block.Oxidizable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Oxidizable.class)
public interface OxidizableMixin {
    @ModifyExpressionValue(
            method = "method_34740", at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableBiMap;builder()Lcom/google/common/collect/ImmutableBiMap$Builder;",
            ordinal = 0
    )
    )
    private static ImmutableBiMap.Builder<Block, Block> addCopperRails(ImmutableBiMap.Builder<Block, Block> original) {
        return original
                .put(MinecartsOverhaul.COPPER_RAIL, MinecartsOverhaul.EXPOSED_COPPER_RAIL)
                .put(MinecartsOverhaul.EXPOSED_COPPER_RAIL, MinecartsOverhaul.WEATHERED_COPPER_RAIL)
                .put(MinecartsOverhaul.WEATHERED_COPPER_RAIL, MinecartsOverhaul.OXIDIZED_COPPER_RAIL);
    }
}
