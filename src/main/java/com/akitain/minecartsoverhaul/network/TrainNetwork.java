package com.akitain.minecartsoverhaul.network;

import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;

public final class TrainNetwork {

    private TrainNetwork() {}

    public static final StreamCodec<FriendlyByteBuf, ArrayList<UUID>> ARRAY_CODEC = new StreamCodec<>() {
        @Override
        public ArrayList<UUID> decode(FriendlyByteBuf buf) {
            int length = VarInt.read(buf);
            ArrayList<UUID> uuids = new ArrayList<>(length);
            for (int i = 0; i < length; i++) uuids.add(buf.readUUID());
            return uuids;
        }

        @Override
        public void encode(FriendlyByteBuf buf, ArrayList<UUID> uuids) {
            VarInt.write(buf, uuids.size());
            for (UUID uuid : uuids) buf.writeUUID(uuid);
        }
    };
}
