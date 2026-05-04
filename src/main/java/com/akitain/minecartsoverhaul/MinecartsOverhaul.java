package com.akitain.minecartsoverhaul;

import com.akitain.minecartsoverhaul.network.TrainPayload;
import com.akitain.minecartsoverhaul.registry.block.CopperRailBlock;
import com.akitain.minecartsoverhaul.registry.block.OxidizableRailBlock;
import com.akitain.minecartsoverhaul.registry.item.PatinaItem;
import com.akitain.minecartsoverhaul.registry.other.DispencerMinecartEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class MinecartsOverhaul implements ModInitializer {

    public static final String MOD_ID = "minecarts-overhaul";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Block COPPER_RAIL = oxidizableRail("copper_rail", WeatheringCopper.WeatherState.UNAFFECTED);
    public static final Block EXPOSED_COPPER_RAIL = oxidizableRail("exposed_copper_rail", WeatheringCopper.WeatherState.EXPOSED);
    public static final Block WEATHERED_COPPER_RAIL = oxidizableRail("weathered_copper_rail", WeatheringCopper.WeatherState.WEATHERED);
    public static final Block OXIDIZED_COPPER_RAIL = oxidizableRail("oxidized_copper_rail", WeatheringCopper.WeatherState.OXIDIZED);
    public static final Block WAXED_COPPER_RAIL = waxedRail("waxed_copper_rail", WeatheringCopper.WeatherState.UNAFFECTED);
    public static final Block WAXED_EXPOSED_COPPER_RAIL = waxedRail("waxed_exposed_copper_rail", WeatheringCopper.WeatherState.EXPOSED);
    public static final Block WAXED_WEATHERED_COPPER_RAIL = waxedRail("waxed_weathered_copper_rail", WeatheringCopper.WeatherState.WEATHERED);
    public static final Block WAXED_OXIDIZED_COPPER_RAIL = waxedRail("waxed_oxidized_copper_rail", WeatheringCopper.WeatherState.OXIDIZED);

    public static final Item COPPER_RAIL_ITEM = registerBlockItem(COPPER_RAIL);
    public static final Item EXPOSED_COPPER_RAIL_ITEM = registerBlockItem(EXPOSED_COPPER_RAIL);
    public static final Item WEATHERED_COPPER_RAIL_ITEM = registerBlockItem(WEATHERED_COPPER_RAIL);
    public static final Item OXIDIZED_COPPER_RAIL_ITEM = registerBlockItem(OXIDIZED_COPPER_RAIL);
    public static final Item WAXED_COPPER_RAIL_ITEM = registerBlockItem(WAXED_COPPER_RAIL);
    public static final Item WAXED_EXPOSED_COPPER_RAIL_ITEM = registerBlockItem(WAXED_EXPOSED_COPPER_RAIL);
    public static final Item WAXED_WEATHERED_COPPER_RAIL_ITEM = registerBlockItem(WAXED_WEATHERED_COPPER_RAIL);
    public static final Item WAXED_OXIDIZED_COPPER_RAIL_ITEM = registerBlockItem(WAXED_OXIDIZED_COPPER_RAIL);

    public static final EntityType<DispencerMinecartEntity> DISPENCER_MINECART_ENTITY_TYPE = registerEntityType(
            "dispenser_minecart",
            EntityType.Builder.of(DispencerMinecartEntity::new, MobCategory.MISC)
                    .noLootTable()
                    .sized(0.98F, 0.7F)
                    .passengerAttachments(0.1875F)
                    .clientTrackingRange(8));

    public static final Item DISPENSER_MINECART = registerItem("dispenser_minecart",
            settings -> new MinecartItem(DISPENCER_MINECART_ENTITY_TYPE, settings),
            new Item.Properties().stacksTo(1));

    public static final Item PATINA = registerItem("patina", PatinaItem::new, new Item.Properties());

    private static final Item[] RAIL_ITEMS_IN_ORDER = {
            COPPER_RAIL_ITEM, EXPOSED_COPPER_RAIL_ITEM, WEATHERED_COPPER_RAIL_ITEM, OXIDIZED_COPPER_RAIL_ITEM,
            WAXED_COPPER_RAIL_ITEM, WAXED_EXPOSED_COPPER_RAIL_ITEM, WAXED_WEATHERED_COPPER_RAIL_ITEM, WAXED_OXIDIZED_COPPER_RAIL_ITEM,
    };

    @Override
    public void onInitialize() {
        TrainPayload.register();
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(MinecartsOverhaul::addRedstoneTabEntries);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.INGREDIENTS).register(entries ->
                entries.addAfter(Items.COPPER_INGOT, PATINA));
        LOGGER.info("Minecarts Overhaul loaded");
    }

    private static void addRedstoneTabEntries(FabricItemGroupEntries entries) {
        entries.addAfter(Items.HOPPER_MINECART, DISPENSER_MINECART);
        ItemLike anchor = Items.POWERED_RAIL;
        for (Item rail : RAIL_ITEMS_IN_ORDER) {
            entries.addAfter(anchor, rail);
            anchor = rail;
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static Block oxidizableRail(String name, WeatheringCopper.WeatherState level) {
        return registerRail(name, settings -> new OxidizableRailBlock(level, settings));
    }

    private static Block waxedRail(String name, WeatheringCopper.WeatherState level) {
        return registerRail(name, settings -> new CopperRailBlock(level, settings));
    }

    private static Block registerRail(String name, Function<BlockBehaviour.Properties, Block> factory) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id(name));
        BlockBehaviour.Properties settings = BlockBehaviour.Properties.ofFullCopy(Blocks.POWERED_RAIL).setId(key);
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(settings));
    }

    private static Item registerBlockItem(Block block) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, blockId);
        BlockItem item = new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix());
        // 1.21.11 stopped auto-linking BlockItem to Block.asItem(); without this pick-block returns AIR.
        item.registerBlocks(Item.BY_BLOCK, item);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    private static Item registerItem(String name, Function<Item.Properties, Item> factory, Item.Properties settings) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(settings.setId(key)));
    }

    private static <T extends Entity> EntityType<T> registerEntityType(String name, EntityType.Builder<T> builder) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, id(name));
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
    }
}
