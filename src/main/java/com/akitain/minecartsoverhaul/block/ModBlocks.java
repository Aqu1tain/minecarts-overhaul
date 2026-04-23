package com.akitain.minecartsoverhaul.block;

import com.akitain.minecartsoverhaul.MinecartsOverhaul;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {

    public static final CopperRailBlock COPPER_RAIL = registerRail("copper_rail", WeatherState.UNAFFECTED, true, MapColor.COLOR_ORANGE);
    public static final CopperRailBlock EXPOSED_COPPER_RAIL = registerRail("exposed_copper_rail", WeatherState.EXPOSED, true, MapColor.TERRACOTTA_WHITE);
    public static final CopperRailBlock WEATHERED_COPPER_RAIL = registerRail("weathered_copper_rail", WeatherState.WEATHERED, true, MapColor.WARPED_STEM);
    public static final CopperRailBlock OXIDIZED_COPPER_RAIL = registerRail("oxidized_copper_rail", WeatherState.OXIDIZED, false, MapColor.WARPED_NYLIUM);

    public static final CopperRailBlock WAXED_COPPER_RAIL = registerRail("waxed_copper_rail", WeatherState.UNAFFECTED, false, MapColor.COLOR_ORANGE);
    public static final CopperRailBlock WAXED_EXPOSED_COPPER_RAIL = registerRail("waxed_exposed_copper_rail", WeatherState.EXPOSED, false, MapColor.TERRACOTTA_WHITE);
    public static final CopperRailBlock WAXED_WEATHERED_COPPER_RAIL = registerRail("waxed_weathered_copper_rail", WeatherState.WEATHERED, false, MapColor.WARPED_STEM);
    public static final CopperRailBlock WAXED_OXIDIZED_COPPER_RAIL = registerRail("waxed_oxidized_copper_rail", WeatherState.OXIDIZED, false, MapColor.WARPED_NYLIUM);

    private static final Block[] CREATIVE_ORDER = {
            COPPER_RAIL, EXPOSED_COPPER_RAIL, WEATHERED_COPPER_RAIL, OXIDIZED_COPPER_RAIL,
            WAXED_COPPER_RAIL, WAXED_EXPOSED_COPPER_RAIL, WAXED_WEATHERED_COPPER_RAIL, WAXED_OXIDIZED_COPPER_RAIL
    };

    private ModBlocks() {}

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            entries.insertAfter(Items.ACTIVATOR_RAIL, CREATIVE_ORDER);
        });
    }

    private static CopperRailBlock registerRail(String name, WeatherState state, boolean oxidizing, MapColor color) {
        Identifier id = Identifier.fromNamespaceAndPath(MinecartsOverhaul.MOD_ID, name);
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .mapColor(color)
                .noCollision()
                .strength(0.7F)
                .sound(SoundType.METAL)
                .pushReaction(PushReaction.DESTROY)
                .setId(blockKey);

        CopperRailBlock block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new CopperRailBlock(state, oxidizing, properties));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
        BlockItem item = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);
        Item.BY_BLOCK.put(block, item);
        return block;
    }
}
