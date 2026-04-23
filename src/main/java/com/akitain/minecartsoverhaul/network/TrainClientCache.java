package com.akitain.minecartsoverhaul.network;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TrainClientCache {

    private static final long STALE_MILLIS = 3000L;

    private static final Map<UUID, List<UUID>> CHAINS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CHAIN_TIMESTAMPS = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> AHEAD = new ConcurrentHashMap<>();

    private TrainClientCache() {}

    public static void update(UUID locomotive, List<UUID> trailers) {
        if (trailers.isEmpty()) {
            removeChain(locomotive);
        } else {
            CHAINS.put(locomotive, trailers);
            CHAIN_TIMESTAMPS.put(locomotive, System.currentTimeMillis());
            rebuildAheadMap();
        }
    }

    public static UUID aheadOf(UUID cart) {
        evictStale();
        return AHEAD.get(cart);
    }

    private static void evictStale() {
        long threshold = System.currentTimeMillis() - STALE_MILLIS;
        boolean changed = false;
        for (Map.Entry<UUID, Long> entry : CHAIN_TIMESTAMPS.entrySet()) {
            if (entry.getValue() < threshold) {
                UUID locomotive = entry.getKey();
                CHAINS.remove(locomotive);
                CHAIN_TIMESTAMPS.remove(locomotive);
                changed = true;
            }
        }
        if (changed) rebuildAheadMap();
    }

    private static void removeChain(UUID locomotive) {
        CHAINS.remove(locomotive);
        CHAIN_TIMESTAMPS.remove(locomotive);
        rebuildAheadMap();
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
