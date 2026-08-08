package space.qouve.worldgenfeature.utils;

import space.qouve.worldgenfeature.StructurePopulator;
import space.qouve.worldgenfeature.WorldGenListener;
import space.qouve.worldgenfeature.models.ChunkSpawnKey;
import space.qouve.worldgenfeature.models.PendingSpawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sammelt {@link PendingSpawn}-Kandidaten, die auf dem Welt-Generierungs-Thread in
 * {@link StructurePopulator#populate} ermittelt wurden, pro Chunk. Die eigentliche
 * Verarbeitung (Behaviors + Placement) passiert erst später in
 * {@link WorldGenListener}, sobald der Chunk laut Bukkit wirklich fertig geladen ist -
 * hier wird also NICHTS aktiv nachgeladen oder erzwungen, nur zwischengespeichert.
 *
 * <p>Muss zwischen {@link StructurePopulator} (schreibt, vom Generierungs-Thread aus) und
 * {@link WorldGenListener} (liest/entfernt, vom Main-Thread aus) geteilt werden.</p>
 *
 * <p><b>Wichtig:</b> Ein Eintrag wird nur entfernt, wenn für den betreffenden Chunk
 * irgendwann ein {@code ChunkLoadEvent} mit {@code isNewChunk() == true} eintrifft. In
 * Randfällen (Pregeneration-Plugins, Chunks die nur als Nachbar-Abhängigkeit generiert
 * werden, o. ä.) kann das ausbleiben - ohne Gegenmaßnahme würde die Map dann unbegrenzt
 * wachsen und über Zeit spürbaren GC-Druck erzeugen. Deshalb tragen Einträge jetzt einen
 * Zeitstempel und {@link #purgeStale()} entfernt verwaiste Einträge, die nie gedrained
 * wurden.</p>
 */
public final class StructureSpawnQueue {

    private record Entry(List<PendingSpawn> spawns, long queuedAtMillis) {
    }

    // Verwaiste Einträge (nie per ChunkLoadEvent gedrained) gelten nach dieser Zeit als
    // stale und werden von purgeStale() entfernt.
    private static final long MAX_AGE_MILLIS = 5 * 60 * 1000L; // 5 Minuten

    private final ConcurrentHashMap<ChunkSpawnKey, Entry> pending = new ConcurrentHashMap<>();

    /**
     * Fügt Kandidaten für einen Chunk hinzu. Thread-sicher, darf vom Generierungs-Thread
     * aus aufgerufen werden.
     */
    public void queue(ChunkSpawnKey key, List<PendingSpawn> candidates) {
        if (candidates.isEmpty()) return;

        long now = System.currentTimeMillis();
        pending.compute(key, (k, existing) -> {
            List<PendingSpawn> list = (existing != null) ? new ArrayList<>(existing.spawns()) : new ArrayList<>();
            list.addAll(candidates);
            return new Entry(list, now);
        });
    }

    /**
     * Entfernt und liefert alle wartenden Kandidaten für einen Chunk (leere Liste, falls
     * keine vorhanden sind). Gedacht für den Aufruf vom Main-Thread aus, sobald der Chunk
     * fertig geladen ist.
     */
    public List<PendingSpawn> drain(ChunkSpawnKey key) {
        Entry entry = pending.remove(key);
        return (entry != null) ? entry.spawns() : Collections.emptyList();
    }

    /**
     * Entfernt Einträge, die älter als {@link #MAX_AGE_MILLIS} sind und offenbar nie ein
     * passendes ChunkLoadEvent bekommen haben. Sollte periodisch aufgerufen werden (z. B.
     * alle 1-5 Minuten per Bukkit-Repeating-Task).
     *
     * @return Anzahl der entfernten (verwaisten) Chunk-Einträge
     */
    public int purgeStale() {
        long cutoff = System.currentTimeMillis() - MAX_AGE_MILLIS;
        int before = pending.size();
        pending.entrySet().removeIf(e -> e.getValue().queuedAtMillis() < cutoff);
        return before - pending.size();
    }

    /**
     * Aktuelle Anzahl offener Chunk-Einträge - nützlich für Debug-/Monitoring-Zwecke, um
     * unkontrolliertes Wachstum frühzeitig zu erkennen.
     */
    public int size() {
        return pending.size();
    }
}