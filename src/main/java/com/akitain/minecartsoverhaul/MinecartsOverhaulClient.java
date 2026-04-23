package com.akitain.minecartsoverhaul;

import com.akitain.minecartsoverhaul.entity.ModEntityTypes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.MinecartRenderer;

public class MinecartsOverhaulClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(
                ModEntityTypes.DISPENSER_MINECART,
                ctx -> new MinecartRenderer(ctx, ModelLayers.MINECART)
        );
    }
}
