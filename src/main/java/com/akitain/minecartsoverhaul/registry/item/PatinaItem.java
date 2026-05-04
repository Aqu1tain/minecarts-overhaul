package com.akitain.minecartsoverhaul.registry.item;

import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gameevent.GameEvent;

public class PatinaItem extends Item {

    public PatinaItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState current = world.getBlockState(pos);
        Optional<BlockState> nextState = nextOxidationState(current);
        if (nextState.isEmpty()) return InteractionResult.PASS;

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, stack);
        }
        stack.shrink(1);
        world.setBlock(pos, nextState.get(), Block.UPDATE_ALL_IMMEDIATE);
        world.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, nextState.get()));
        world.levelEvent(player, LevelEvent.PARTICLES_SCRAPE, pos, 0);
        emitChestPairScrape(world, current, pos, player);
        return InteractionResult.SUCCESS;
    }

    public static Optional<BlockState> nextOxidationState(BlockState state) {
        return WeatheringCopper.getNext(state.getBlock())
                .map(block -> block.withPropertiesOf(state));
    }

    /** @deprecated use {@link #nextOxidationState(BlockState)} */
    @Deprecated
    public static Optional<BlockState> getOxidizedState(BlockState state) {
        return nextOxidationState(state);
    }

    private static void emitChestPairScrape(Level world, BlockState scraped, BlockPos pos, Player player) {
        // Double chests render as two halves; the partner block needs the same scrape event so
        // both sides update visually.
        if (!(scraped.getBlock() instanceof ChestBlock)) return;
        if (scraped.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return;
        BlockPos partner = ChestBlock.getConnectedBlockPos(pos, scraped);
        world.gameEvent(GameEvent.BLOCK_CHANGE, partner, GameEvent.Context.of(player, world.getBlockState(partner)));
        world.levelEvent(player, LevelEvent.PARTICLES_SCRAPE, partner, 0);
    }
}
