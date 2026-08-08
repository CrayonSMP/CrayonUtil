package space.qouve.worldgenfeature.behaviors;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import space.qouve.worldgenfeature.models.WorldGenBehavior;
import space.qouve.worldgenfeature.models.WorldGenContext;

import java.io.IOException;

public class SkyStructureBehavior extends WorldGenBehavior {

    public SkyStructureBehavior() {
        super("sky-structure");
    }

    @Override
    public boolean run(WorldGenContext context, ConfigurationSection section) throws IOException {
        if (section == null) return true;

        int minHeight = section.getInt("min-height", 90);
        int maxHeight = section.getInt("max-height", 220);
        int minDistanceFromGround = section.getInt("min-distance-from-ground", 20);
        int clearanceRadius = section.getInt("clearance-radius", 15);

        Location baseLoc = context.location();
        World bukkitWorld = baseLoc.getWorld();
        if (bukkitWorld == null) return false;

        int currentY = baseLoc.getBlockY();
        int cx = baseLoc.getBlockX();
        int cz = baseLoc.getBlockZ();

        // Chunk-Safety-Check
        if (!bukkitWorld.isChunkLoaded(cx >> 4, cz >> 4)) {
            return false;
        }

        boolean modified = false;

        if (currentY < minHeight) {
            currentY = minHeight;
            modified = true;
        } else if (currentY > maxHeight) {
            currentY = maxHeight;
            modified = true;
        }

        if (minDistanceFromGround > 0) {
            for (int yOffset = 1; yOffset <= minDistanceFromGround; yOffset++) {
                int scanY = currentY - yOffset;
                if (scanY < bukkitWorld.getMinHeight()) break;

                Block belowBlock = bukkitWorld.getBlockAt(cx, scanY, cz);
                if (belowBlock.getType().isSolid()) {
                    currentY = scanY + minDistanceFromGround + 1;
                    modified = true;
                    break;
                }
            }

            if (currentY > maxHeight) {
                currentY = maxHeight;
                modified = true;
            }
        }

        if (modified) {
            baseLoc.setY(currentY);
        }

        // Clearance-Radius prüfen (Schrittweite 4 bleibt erhalten für Performance)
        for (int x = -clearanceRadius; x <= clearanceRadius; x += 4) {
            for (int z = -clearanceRadius; z <= clearanceRadius; z += 4) {
                Block sideBlock = bukkitWorld.getBlockAt(cx + x, currentY, cz + z);
                if (sideBlock.getType().isSolid()) {
                    return false;
                }
            }
        }

        return true;
    }
}