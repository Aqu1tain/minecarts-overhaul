package com.akitain.minecartsoverhaul.mixin.minecart;

import com.akitain.minecartsoverhaul.MinecartsOverhaul;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

@Mixin(AxeItem.class)
public class AxeItemMixin {

    @Unique
    private static final float SCRAPE_DROP_CHANCE = 0.3f;
    @Unique
    private static final String SCRAPE_LOOT_TABLE = "gameplay/other/scrape";

    // Piggy-backs on the vanilla axe-strip path: when an axe successfully scrapes oxidation off a
    // full copper cube, roll the scrape loot table so Patina can drop. Limited to full cubes
    // because rails, bars, grates, etc. don't fit the "block of copper" theme.
    @Inject(method = "evaluateNewBlockState", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/AxeItem;spawnSoundAndParticle(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/sounds/SoundEvent;I)V", ordinal = 0
    ))
    private void addScrapedCopper(Level world, BlockPos pos, @Nullable Player player,
                                  BlockState state, CallbackInfoReturnable<Optional<BlockState>> cir) {
        if (!(world instanceof ServerLevel serverWorld)) return;
        if (!state.isCollisionShapeFullBlock(world, pos)) return;
        if (world.getRandom().nextFloat() >= SCRAPE_DROP_CHANCE) return;
        ResourceKey<net.minecraft.world.level.storage.loot.LootTable> lootTable = ResourceKey.create(
                Registries.LOOT_TABLE, MinecartsOverhaul.id(SCRAPE_LOOT_TABLE));
        Block.dropFromBlockInteractLootTable(
                serverWorld,
                lootTable,
                state,
                world.getBlockEntity(pos),
                null,
                player,
                (w, stack) -> Block.popResource(w, pos, stack)
        );
    }
}
