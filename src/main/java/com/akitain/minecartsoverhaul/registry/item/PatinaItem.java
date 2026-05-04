package com.akitain.minecartsoverhaul.registry.item;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.Oxidizable;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.event.GameEvent;

import java.util.Optional;

public class PatinaItem extends Item {

    public PatinaItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        BlockState current = world.getBlockState(pos);
        Optional<BlockState> nextState = nextOxidationState(current);
        if (nextState.isEmpty()) return ActionResult.PASS;

        PlayerEntity player = context.getPlayer();
        ItemStack stack = context.getStack();
        if (player instanceof ServerPlayerEntity serverPlayer) {
            Criteria.ITEM_USED_ON_BLOCK.trigger(serverPlayer, pos, stack);
        }
        stack.decrement(1);
        world.setBlockState(pos, nextState.get(), Block.NOTIFY_ALL_AND_REDRAW);
        world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(player, nextState.get()));
        world.syncWorldEvent(player, WorldEvents.BLOCK_SCRAPED, pos, 0);
        emitChestPairScrape(world, current, pos, player);
        return ActionResult.SUCCESS;
    }

    public static Optional<BlockState> nextOxidationState(BlockState state) {
        return Oxidizable.getIncreasedOxidationBlock(state.getBlock())
                .map(block -> block.getStateWithProperties(state));
    }

    /** @deprecated use {@link #nextOxidationState(BlockState)} */
    @Deprecated
    public static Optional<BlockState> getOxidizedState(BlockState state) {
        return nextOxidationState(state);
    }

    private static void emitChestPairScrape(World world, BlockState scraped, BlockPos pos, PlayerEntity player) {
        // Double chests render as two halves; the partner block needs the same scrape event so
        // both sides update visually.
        if (!(scraped.getBlock() instanceof ChestBlock)) return;
        if (scraped.get(ChestBlock.CHEST_TYPE) == ChestType.SINGLE) return;
        BlockPos partner = ChestBlock.getPosInFrontOf(pos, scraped);
        world.emitGameEvent(GameEvent.BLOCK_CHANGE, partner, GameEvent.Emitter.of(player, world.getBlockState(partner)));
        world.syncWorldEvent(player, WorldEvents.BLOCK_SCRAPED, partner, 0);
    }
}
