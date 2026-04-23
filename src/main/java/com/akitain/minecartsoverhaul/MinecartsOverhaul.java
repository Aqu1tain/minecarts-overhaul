package com.akitain.minecartsoverhaul;

import com.akitain.minecartsoverhaul.block.ModBlocks;
import com.akitain.minecartsoverhaul.entity.ModEntityTypes;
import com.akitain.minecartsoverhaul.item.ModItems;
import com.akitain.minecartsoverhaul.network.TrainPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinecartsOverhaul implements ModInitializer {

    public static final String MOD_ID = "minecarts-overhaul";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModEntityTypes.register();
        ModItems.register();
        PayloadTypeRegistry.clientboundPlay().register(TrainPayload.TYPE, TrainPayload.CODEC);
        LOGGER.info("Minecarts Overhaul loaded");
    }
}
