package space.qouve.worldgenfeature.models;

import java.util.UUID;

public record ChunkSpawnKey(UUID worldId, int chunkX, int chunkZ) {
}
