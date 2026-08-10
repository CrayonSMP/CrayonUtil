package space.qouve.worldgenfeature.utils;

import space.qouve.worldgenfeature.StructurePopulator;
import space.qouve.worldgenfeature.WorldGenListener;
import space.qouve.worldgenfeature.models.ChunkSpawnKey;
import space.qouve.worldgenfeature.models.PendingSpawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureSpawnQueue {

    private record Entry(List<PendingSpawn> spawns, long queuedAtMillis) {
    }

    private static final long MAX_AGE_MILLIS = 5 * 60 * 1000L; // 5 Minuten

    private final ConcurrentHashMap<ChunkSpawnKey, Entry> pending = new ConcurrentHashMap<>();

    public void queue(ChunkSpawnKey key, List<PendingSpawn> candidates) {
        if (candidates.isEmpty()) return;

        long now = System.currentTimeMillis();
        pending.compute(key, (k, existing) -> {
            List<PendingSpawn> list = (existing != null) ? new ArrayList<>(existing.spawns()) : new ArrayList<>();
            list.addAll(candidates);
            return new Entry(list, now);
        });
    }

    public List<PendingSpawn> drain(ChunkSpawnKey key) {
        Entry entry = pending.remove(key);
        return (entry != null) ? entry.spawns() : Collections.emptyList();
    }

    public int purgeStale() {
        long cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS;
        int before = pending.size();
        pending.entrySet().removeIf(e -> e.getValue().queuedAtMillis() < cutoff);
        return before - pending.size();
    }

    public int size() {
        return pending.size();
    }
}