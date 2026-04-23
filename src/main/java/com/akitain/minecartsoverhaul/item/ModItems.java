package com.akitain.minecartsoverhaul.item;

import com.akitain.minecartsoverhaul.MinecartsOverhaul;
import com.akitain.minecartsoverhaul.entity.ModEntityTypes;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MinecartItem;

public final class ModItems {

    public static final Item DISPENSER_MINECART = registerMinecartItem("dispenser_minecart", ModEntityTypes.DISPENSER_MINECART);

    private ModItems() {}

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            entries.insertAfter(Items.HOPPER_MINECART, DISPENSER_MINECART);
        });
    }

    private static Item registerMinecartItem(String name, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.vehicle.minecart.AbstractMinecart> type) {
        Identifier id = Identifier.fromNamespaceAndPath(MinecartsOverhaul.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        return Registry.register(BuiltInRegistries.ITEM, key,
                new MinecartItem(type, new Item.Properties().stacksTo(1).setId(key)));
    }
}
