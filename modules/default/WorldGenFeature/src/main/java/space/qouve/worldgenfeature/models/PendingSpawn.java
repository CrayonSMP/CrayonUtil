package space.qouve.worldgenfeature.models;

/**
 * Ein auf dem Generierungs-Thread ermittelter Struktur-Kandidat, der erst verarbeitet
 * wird (Behaviors + Placement), sobald der zugehörige Chunk sicher fertig geladen ist
 * (siehe {@link StructureChunkLoadListener}).
 */
public record PendingSpawn(WorldGenStructure structure, int x, int y, int z, boolean overrideAir) {
}
