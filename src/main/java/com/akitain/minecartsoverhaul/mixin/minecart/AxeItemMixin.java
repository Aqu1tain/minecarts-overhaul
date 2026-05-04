package com.akitain.minecartsoverhaul.mixin.minecart;

import com.akitain.minecartsoverhaul.MinecartsOverhaul;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(AxeItem.class)
public class AxeItemMixin {

    @Unique
    private static final float SCRAPE_DROP_CHANCE = 0.3f;
    @Unique
    private static final String SCRAPE_LOOT_TABLE = "gameplay/other/scrape";

    // Piggy-backs on the vanilla axe-strip path: when an axe successfully scrapes oxidation off a
    // full copper cube, roll the scrape loot table so Patina can drop. Limited to full cubes
    // because rails, bars, grates, etc. don't fit the "block of copper" theme.
    @Inject(method = "tryStrip", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/item/AxeItem;strip(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/block/BlockState;Lnet/minecraft/sound/SoundEvent;I)V", ordinal = 0
    ))
    private void addScrapedCopper(World world, BlockPos pos, @Nullable PlayerEntity player,
                                  BlockState state, CallbackInfoReturnable<Optional<BlockState>> cir) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (!state.isFullCube(world, pos)) return;
        if (world.random.nextFloat() >= SCRAPE_DROP_CHANCE) return;
        RegistryKey<net.minecraft.loot.LootTable> lootTable = RegistryKey.of(
                RegistryKeys.LOOT_TABLE, MinecartsOverhaul.id(SCRAPE_LOOT_TABLE));
        Block.generateBlockInteractLoot(
                serverWorld,
                lootTable,
                state,
                world.getBlockEntity(pos),
                null,
                player,
                (w, stack) -> Block.dropStack(w, pos, stack)
        );
    }
}
