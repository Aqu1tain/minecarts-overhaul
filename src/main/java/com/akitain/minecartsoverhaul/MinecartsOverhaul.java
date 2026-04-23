package com.akitain.minecartsoverhaul;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinecartsOverhaul implements ModInitializer {

    public static final String MOD_ID = "minecarts-overhaul";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Minecarts Overhaul loaded");
    }
}
