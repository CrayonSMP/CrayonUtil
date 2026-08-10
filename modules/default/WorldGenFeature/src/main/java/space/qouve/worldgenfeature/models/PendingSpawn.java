package space.qouve.worldgenfeature.models;

public record PendingSpawn(WorldGenStructure structure, int x, int y, int z, boolean overrideAir) {
}
