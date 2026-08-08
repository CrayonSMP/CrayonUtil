package space.qouve.worldgenfeature.behaviors;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import space.qouve.worldgenfeature.models.WorldGenBehavior;
import space.qouve.worldgenfeature.models.WorldGenContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExtendToGroundBehavior extends WorldGenBehavior {

    public ExtendToGroundBehavior() {
        super("extend-to-ground");
    }

    @Override
    public boolean run(WorldGenContext context, ConfigurationSection section) throws IOException {
        if (section == null) return true;

        int maxDepth = section.getInt("max-depth", 30);

        Location baseLoc = context.location();
        World bukkitWorld = baseLoc.getWorld();
        if (bukkitWorld == null) return true;

        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(bukkitWorld);
        Clipboard clipboard = context.clipboard();
        if (clipboard == null) return true;

        BlockVector3 minimumPoint = clipboard.getMinimumPoint();
        BlockVector3 maximumPoint = clipboard.getMaximumPoint();
        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 pasteVector = BlockVector3.at(baseLoc.getBlockX(), baseLoc.getBlockY(), baseLoc.getBlockZ());

        // WICHTIG: Vorher wurde nur der Origin-Chunk der Struktur geprüft, aber die
        // Extend-Schleife unten läuft über die GESAMTE X/Z-Fläche des Schematics. Bei
        // Strukturen, die über eine Chunk-Grenze reichen, konnte bukkitWorld.getBlockAt()
        // in einem ungeprüften Nachbar-Chunk landen und diesen synchron auf dem Main-Thread
        // nachladen/generieren - derselbe Effekt wie das ursprüngliche Problem in
        // WorldEditUtil, nur hier unentdeckt. Jetzt wird der komplette X/Z-Fußabdruck der
        // Struktur auf Chunks projiziert und VOLLSTÄNDIG geprüft, bevor irgendetwas
        // angefasst wird.
        int worldMinX = pasteVector.x() + (minimumPoint.x() - origin.x());
        int worldMaxX = pasteVector.x() + (maximumPoint.x() - origin.x());
        int worldMinZ = pasteVector.z() + (minimumPoint.z() - origin.z());
        int worldMaxZ = pasteVector.z() + (maximumPoint.z() - origin.z());

        int minChunkX = worldMinX >> 4, maxChunkX = worldMaxX >> 4;
        int minChunkZ = worldMinZ >> 4, maxChunkZ = worldMaxZ >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (!bukkitWorld.isChunkLoaded(cx, cz)) {
                    return false;
                }
            }
        }

        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .build()) {

            int targetY = minimumPoint.y();

            // List statt Set: pro (x,z) gibt es hier ohnehin höchstens einen Eintrag,
            // Hashing bringt also keinen Vorteil, nur unnötigen Overhead.
            List<BlockVector3> bottomBlocks = new ArrayList<>();
            for (int x = minimumPoint.x(); x <= maximumPoint.x(); x++) {
                for (int z = minimumPoint.z(); z <= maximumPoint.z(); z++) {
                    BlockVector3 pt = BlockVector3.at(x, targetY, z);
                    BlockState blockState = clipboard.getBlock(pt);

                    if (blockState != null && blockState.getBlockType().getMaterial().isSolid()) {
                        bottomBlocks.add(pt);
                    }
                }
            }

            for (BlockVector3 bottomPt : bottomBlocks) {
                BlockVector3 offsetFromOrigin = bottomPt.subtract(origin);
                BlockVector3 absolutePastePos = pasteVector.add(offsetFromOrigin);

                BlockState blockToExtend = clipboard.getBlock(bottomPt);
                if (blockToExtend == null) continue;

                int startX = absolutePastePos.x();
                int startZ = absolutePastePos.z();
                int startY = absolutePastePos.y() - 1;

                for (int currentY = startY; currentY >= startY - maxDepth; currentY--) {
                    Block currentBukkitBlock = bukkitWorld.getBlockAt(startX, currentY, startZ);

                    if (currentBukkitBlock.getType().isSolid()) {
                        break;
                    }

                    BlockVector3 targetPos = BlockVector3.at(startX, currentY, startZ);
                    editSession.setBlock(targetPos, blockToExtend);
                }
            }
        } catch (Exception e) {
            context.feature().debug("Error in ExtendToGroundBehavior: " + e.getMessage());
            return false;
        }

        return true;
    }
}