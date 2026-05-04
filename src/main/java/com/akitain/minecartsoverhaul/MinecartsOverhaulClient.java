package com.akitain.minecartsoverhaul;

import com.akitain.minecartsoverhaul.network.TrainPayload;
import com.akitain.minecartsoverhaul.registry.other.FixedFurnaceMinecartEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.entity.MinecartEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

public class MinecartsOverhaulClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        BlockRenderLayerMap.putBlocks(
                BlockRenderLayer.CUTOUT,
                MinecartsOverhaul.COPPER_RAIL,
                MinecartsOverhaul.EXPOSED_COPPER_RAIL,
                MinecartsOverhaul.WEATHERED_COPPER_RAIL,
                MinecartsOverhaul.OXIDIZED_COPPER_RAIL,
                MinecartsOverhaul.WAXED_COPPER_RAIL,
                MinecartsOverhaul.WAXED_EXPOSED_COPPER_RAIL,
                MinecartsOverhaul.WAXED_WEATHERED_COPPER_RAIL,
                MinecartsOverhaul.WAXED_OXIDIZED_COPPER_RAIL
        );

        EntityRendererRegistry.register(
                MinecartsOverhaul.DISPENCER_MINECART_ENTITY_TYPE,
                ctx -> new MinecartEntityRenderer(ctx, EntityModelLayers.MINECART)
        );

        ClientPlayNetworking.registerGlobalReceiver(TrainPayload.PACKET_ID, (payload, context) ->
                context.client().execute(() -> {
                    ClientWorld world = context.client().world;
                    if (world == null || payload.train().isEmpty()) return;
                    Entity entity = world.getEntity(payload.train().get(0));
                    if (entity instanceof FixedFurnaceMinecartEntity furnace) {
                        furnace.setTrain(payload.train());
                    }
                })
        );
    }
}
