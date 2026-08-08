package space.qouve.worldgenfeature.models;

import java.util.UUID;

public record StructureRecord(UUID uuid, String structureKey, long timestamp, int x, int y, int z) {
    public String serialize() {
        return uuid.toString() + ";" + structureKey + ";" + timestamp + ";" + x + ";" + y + ";" + z;
    }

    public static StructureRecord deserialize(String data) {
        String[] parts = data.split(";");
        return new StructureRecord(
                UUID.fromString(parts[0]),
                parts[1],
                Long.parseLong(parts[2]),
                Integer.parseInt(parts[3]),
                Integer.parseInt(parts[4]),
                Integer.parseInt(parts[5])
        );
    }
}