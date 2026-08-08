package space.qouve.worldgenfeature.models;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.math.transform.AffineTransform;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;

import space.qouve.worldgenfeature.WorldGenFeature;

import java.io.File;

/**
 * Holds all data needed for a single WorldEdit placement (structures and multi-patches).
 */
public record WorldGenContext(
        World world,
        Clipboard clipboard,
        File file,
        Location location,
        StructureRotation rotation,
        Mirror mirror,
        boolean overrideAir,
        String debugName,
        WorldGenFeature feature
) {

    // Creates the context before the clipboard is loaded.
    public static WorldGenContext of(World world, Location location, File file,
                                     StructureRotation rotation, Mirror mirror,
                                     boolean overrideAir, String debugName,
                                     WorldGenFeature feature) {
        return new WorldGenContext(world, null, file, location, rotation, mirror,
                overrideAir, debugName, feature);
    }

    // Returns a copy with the clipboard set.
    public WorldGenContext withClipboard(Clipboard clipboard) {
        return new WorldGenContext(world, clipboard, file, location, rotation, mirror,
                overrideAir, debugName, feature);
    }

    // Target position as BlockVector3.
    public BlockVector3 toBlockVector3() {
        return BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    // Builds the transform from rotation + mirror.
    public AffineTransform buildTransform() {
        AffineTransform transform = new AffineTransform();
        switch (rotation) {
            case CLOCKWISE_90        -> transform = transform.rotateY(-90);
            case CLOCKWISE_180       -> transform = transform.rotateY(-180);
            case COUNTERCLOCKWISE_90 -> transform = transform.rotateY(90);
            default -> {}
        }
        switch (mirror) {
            case LEFT_RIGHT -> transform = transform.scale(-1, 1, 1);
            case FRONT_BACK -> transform = transform.scale(1, 1, -1);
            default -> {}
        }
        return transform;
    }
}