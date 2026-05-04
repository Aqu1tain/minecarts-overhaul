package com.akitain.minecartsoverhaul;

import com.akitain.minecartsoverhaul.network.TrainPayload;
import com.akitain.minecartsoverhaul.registry.block.CopperRailBlock;
import com.akitain.minecartsoverhaul.registry.block.OxidizableRailBlock;
import com.akitain.minecartsoverhaul.registry.item.PatinaItem;
import com.akitain.minecartsoverhaul.registry.other.DispencerMinecartEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.Oxidizable;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.MinecartItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class MinecartsOverhaul implements ModInitializer {

    public static final String MOD_ID = "minecarts-overhaul";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Block COPPER_RAIL = oxidizableRail("copper_rail", Oxidizable.OxidationLevel.UNAFFECTED);
    public static final Block EXPOSED_COPPER_RAIL = oxidizableRail("exposed_copper_rail", Oxidizable.OxidationLevel.EXPOSED);
    public static final Block WEATHERED_COPPER_RAIL = oxidizableRail("weathered_copper_rail", Oxidizable.OxidationLevel.WEATHERED);
    public static final Block OXIDIZED_COPPER_RAIL = oxidizableRail("oxidized_copper_rail", Oxidizable.OxidationLevel.OXIDIZED);
    public static final Block WAXED_COPPER_RAIL = waxedRail("waxed_copper_rail", Oxidizable.OxidationLevel.UNAFFECTED);
    public static final Block WAXED_EXPOSED_COPPER_RAIL = waxedRail("waxed_exposed_copper_rail", Oxidizable.OxidationLevel.EXPOSED);
    public static final Block WAXED_WEATHERED_COPPER_RAIL = waxedRail("waxed_weathered_copper_rail", Oxidizable.OxidationLevel.WEATHERED);
    public static final Block WAXED_OXIDIZED_COPPER_RAIL = waxedRail("waxed_oxidized_copper_rail", Oxidizable.OxidationLevel.OXIDIZED);

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
            EntityType.Builder.create(DispencerMinecartEntity::new, SpawnGroup.MISC)
                    .dropsNothing()
                    .dimensions(0.98F, 0.7F)
                    .passengerAttachments(0.1875F)
                    .maxTrackingRange(8));

    public static final Item DISPENSER_MINECART = registerItem("dispenser_minecart",
            settings -> new MinecartItem(DISPENCER_MINECART_ENTITY_TYPE, settings),
            new Item.Settings().maxCount(1));

    public static final Item PATINA = registerItem("patina", PatinaItem::new, new Item.Settings());

    private static final Item[] RAIL_ITEMS_IN_ORDER = {
            COPPER_RAIL_ITEM, EXPOSED_COPPER_RAIL_ITEM, WEATHERED_COPPER_RAIL_ITEM, OXIDIZED_COPPER_RAIL_ITEM,
            WAXED_COPPER_RAIL_ITEM, WAXED_EXPOSED_COPPER_RAIL_ITEM, WAXED_WEATHERED_COPPER_RAIL_ITEM, WAXED_OXIDIZED_COPPER_RAIL_ITEM,
    };

    @Override
    public void onInitialize() {
        TrainPayload.register();
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register(MinecartsOverhaul::addRedstoneTabEntries);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries ->
                entries.addAfter(Items.COPPER_INGOT, PATINA));
        LOGGER.info("Minecarts Overhaul loaded");
    }

    private static void addRedstoneTabEntries(FabricItemGroupEntries entries) {
        entries.addAfter(Items.HOPPER_MINECART, DISPENSER_MINECART);
        ItemConvertible anchor = Items.POWERED_RAIL;
        for (Item rail : RAIL_ITEMS_IN_ORDER) {
            entries.addAfter(anchor, rail);
            anchor = rail;
        }
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    private static Block oxidizableRail(String name, Oxidizable.OxidationLevel level) {
        return registerRail(name, settings -> new OxidizableRailBlock(level, settings));
    }

    private static Block waxedRail(String name, Oxidizable.OxidationLevel level) {
        return registerRail(name, settings -> new CopperRailBlock(level, settings));
    }

    private static Block registerRail(String name, Function<AbstractBlock.Settings, Block> factory) {
        RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, id(name));
        AbstractBlock.Settings settings = AbstractBlock.Settings.copy(Blocks.POWERED_RAIL).registryKey(key);
        return Registry.register(Registries.BLOCK, key, factory.apply(settings));
    }

    private static Item registerBlockItem(Block block) {
        Identifier blockId = Registries.BLOCK.getId(block);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, blockId);
        BlockItem item = new BlockItem(block, new Item.Settings().registryKey(key).useBlockPrefixedTranslationKey());
        // 1.21.11 stopped auto-linking BlockItem to Block.asItem(); without this pick-block returns AIR.
        item.appendBlocks(Item.BLOCK_ITEMS, item);
        return Registry.register(Registries.ITEM, key, item);
    }

    private static Item registerItem(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id(name));
        return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
    }

    private static <T extends Entity> EntityType<T> registerEntityType(String name, EntityType.Builder<T> builder) {
        RegistryKey<EntityType<?>> key = RegistryKey.of(RegistryKeys.ENTITY_TYPE, id(name));
        return Registry.register(Registries.ENTITY_TYPE, key, builder.build(key));
    }
}
