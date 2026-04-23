package com.akitain.minecartsoverhaul;

import com.akitain.minecartsoverhaul.entity.ModEntityTypes;
import com.akitain.minecartsoverhaul.network.TrainClientCache;
import com.akitain.minecartsoverhaul.network.TrainPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
        ClientPlayNetworking.registerGlobalReceiver(TrainPayload.TYPE, (payload, context) ->
                TrainClientCache.update(payload.locomotive(), payload.trailers())
        );
    }
}
