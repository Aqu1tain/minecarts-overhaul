package com.akitain.minecartsoverhaul.network;

import com.akitain.minecartsoverhaul.MinecartsOverhaul;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.UUID;

public record TrainPayload(ArrayList<UUID> train) implements CustomPayload {

    public static final Id<TrainPayload> PACKET_ID = new Id<>(MinecartsOverhaul.id("train"));

    public static final PacketCodec<RegistryByteBuf, TrainPayload> PACKET_CODEC = PacketCodec.tuple(
            TrainNetwork.ARRAY_CODEC, TrainPayload::train,
            TrainPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(PACKET_ID, PACKET_CODEC);
    }
}
