package space.qouve.worldgenfeature.models;

import java.util.UUID;

/**
 * Welt-eindeutiger Schlüssel für einen Chunk, genutzt um wartende {@link PendingSpawn}s
 * dem richtigen Chunk zuzuordnen (Chunk-Koordinaten allein reichen nicht, die können sich
 * über mehrere Welten hinweg überschneiden).
 */
public record ChunkSpawnKey(UUID worldId, int chunkX, int chunkZ) {
}
