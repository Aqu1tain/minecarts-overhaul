package com.akitain.minecartsoverhaul.mixin.minecart;

import com.google.common.collect.ImmutableBiMap;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.akitain.minecartsoverhaul.MinecartsOverhaul;
import net.minecraft.block.Block;
import net.minecraft.item.HoneycombItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HoneycombItem.class)
public class HoneycombItemMixin  {
    @ModifyExpressionValue(method = "method_34723", at = @At(
            value = "INVOKE",
            target = "Lcom/google/common/collect/ImmutableBiMap;builder()Lcom/google/common/collect/ImmutableBiMap$Builder;"
    ))
    private static ImmutableBiMap.Builder<Block, Block> addCopperRails(ImmutableBiMap.Builder<Block, Block> original) {
        return original
                .put(MinecartsOverhaul.COPPER_RAIL, MinecartsOverhaul.WAXED_COPPER_RAIL)
                .put(MinecartsOverhaul.EXPOSED_COPPER_RAIL, MinecartsOverhaul.WAXED_EXPOSED_COPPER_RAIL)
                .put(MinecartsOverhaul.WEATHERED_COPPER_RAIL, MinecartsOverhaul.WAXED_WEATHERED_COPPER_RAIL)
                .put(MinecartsOverhaul.OXIDIZED_COPPER_RAIL, MinecartsOverhaul.WAXED_OXIDIZED_COPPER_RAIL);
    }
}
