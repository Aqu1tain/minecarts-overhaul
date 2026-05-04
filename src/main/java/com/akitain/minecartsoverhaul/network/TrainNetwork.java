package com.akitain.minecartsoverhaul.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.encoding.VarInts;

import java.util.ArrayList;
import java.util.UUID;

public final class TrainNetwork {

    private TrainNetwork() {}

    public static final PacketCodec<PacketByteBuf, ArrayList<UUID>> ARRAY_CODEC = new PacketCodec<>() {
        @Override
        public ArrayList<UUID> decode(PacketByteBuf buf) {
            int length = VarInts.read(buf);
            ArrayList<UUID> uuids = new ArrayList<>(length);
            for (int i = 0; i < length; i++) uuids.add(buf.readUuid());
            return uuids;
        }

        @Override
        public void encode(PacketByteBuf buf, ArrayList<UUID> uuids) {
            VarInts.write(buf, uuids.size());
            for (UUID uuid : uuids) buf.writeUuid(uuid);
        }
    };
}
