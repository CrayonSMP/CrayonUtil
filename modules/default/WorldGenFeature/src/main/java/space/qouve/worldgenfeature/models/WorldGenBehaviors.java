package space.qouve.worldgenfeature.models;

import space.qouve.worldgenfeature.behaviors.*;

public final class WorldGenBehaviors {

    private WorldGenBehaviors() {}

    public static void registerAll() {
        new PatchSpawnBehavior();
        new HighestBlockBehavior();
        new HeightBehavior();
        new GroundBlockBehavior();
        new ExtendToGroundBehavior();
    }
}