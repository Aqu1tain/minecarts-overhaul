package com.akitain.minecartsoverhaul.mixin.minecart;

import com.akitain.minecartsoverhaul.MinecartsOverhaul;
import com.google.common.collect.ImmutableBiMap;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Targets Yarn's intermediary `method_34723`, the static initializer that builds the unwaxed ->
// waxed bimap. Adding our rails to the same map lets vanilla `HoneycombItem.useOnBlock` and the
// reverse axe-scrape path recognise them automatically.
@Mixin(HoneycombItem.class)
public class HoneycombItemMixin {

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
