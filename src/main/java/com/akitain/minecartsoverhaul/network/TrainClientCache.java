package com.akitain.minecartsoverhaul.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrainClientCache {

    private static final Map<UUID, List<UUID>> CHAINS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> AHEAD = new ConcurrentHashMap<>();

    private TrainClientCache() {}

    public static void update(UUID locomotive, List<UUID> trailers) {
        if (trailers.isEmpty()) {
            List<UUID> previous = CHAINS.remove(locomotive);
            if (previous != null) {
                for (UUID uuid : previous) AHEAD.remove(uuid);
            }
        } else {
            CHAINS.put(locomotive, trailers);
            rebuildAheadMap();
        }
    }

    public static UUID aheadOf(UUID cart) {
        return AHEAD.get(cart);
    }

    private static void rebuildAheadMap() {
        Map<UUID, UUID> next = new HashMap<>();
        for (Map.Entry<UUID, List<UUID>> entry : CHAINS.entrySet()) {
            UUID anchor = entry.getKey();
            for (UUID trailer : entry.getValue()) {
                next.put(trailer, anchor);
                anchor = trailer;
            }
        }
        AHEAD.clear();
        AHEAD.putAll(next);
    }
}
