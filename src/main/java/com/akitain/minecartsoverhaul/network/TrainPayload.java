package com.akitain.minecartsoverhaul.network;

import com.akitain.minecartsoverhaul.MinecartsOverhaul;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

public record TrainPayload(UUID locomotive, List<UUID> trailers) implements CustomPacketPayload {

    public static final Type<TrainPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MinecartsOverhaul.MOD_ID, "train"));

    public static final StreamCodec<FriendlyByteBuf, TrainPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TrainPayload::locomotive,
            UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), TrainPayload::trailers,
            TrainPayload::new
    );

    @Override
    public Type<TrainPayload> type() {
        return TYPE;
    }
}
