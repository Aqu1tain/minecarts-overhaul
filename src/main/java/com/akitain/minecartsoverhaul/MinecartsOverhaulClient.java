package com.akitain.minecartsoverhaul;

import com.akitain.minecartsoverhaul.network.TrainPayload;
import com.akitain.minecartsoverhaul.registry.other.FixedFurnaceMinecartEntity;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.MinecartRenderer;
import net.minecraft.world.entity.Entity;

public class MinecartsOverhaulClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        registerDispenserMinecartRenderer();
        registerTrainReceiver();
    }

    private static void registerDispenserMinecartRenderer() {
        EntityRendererRegistry.register(
                MinecartsOverhaul.DISPENCER_MINECART_ENTITY_TYPE,
                ctx -> new MinecartRenderer(ctx, ModelLayers.MINECART));
    }

    private static void registerTrainReceiver() {
        ClientPlayNetworking.registerGlobalReceiver(TrainPayload.PACKET_ID, (payload, context) ->
                context.client().execute(() -> applyTrainPayload(context.client(), payload)));
    }

    private static void applyTrainPayload(Minecraft client, TrainPayload payload) {
        ClientLevel world = client.level;
        if (world == null || payload.train().isEmpty()) return;
        Entity head = world.getEntity(payload.train().get(0));
        if (!(head instanceof FixedFurnaceMinecartEntity locomotive)) return;
        locomotive.setTrain(payload.train());
    }
}
