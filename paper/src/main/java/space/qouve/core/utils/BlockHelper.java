package space.qouve.core.utils;

import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineFurniture;
import net.momirealms.craftengine.bukkit.entity.furniture.BukkitFurniture;
import net.momirealms.craftengine.core.block.BlockDefinition;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.UpdateFlags;
import net.momirealms.craftengine.core.entity.furniture.FurnitureDefinition;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.libraries.nbt.CompoundTag;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public class BlockHelper {

    private static final String MINECRAFT_NAMESPACE = "minecraft";

    public static boolean place(Location location, String keyString, boolean playSound) {
        String pureKey = keyString;
        String statesString = null;

        if (keyString.contains("[")) {
            pureKey = keyString.substring(0, keyString.indexOf("["));
            statesString = keyString.substring(keyString.indexOf("["));
        }

        Key key = Key.of(pureKey);

        if (isVanilla(key)) {
            return placeVanilla(location, key, statesString);
        }

        BlockDefinition blockDef = CraftEngineBlocks.byId(key);
        if (blockDef != null) {
            ImmutableBlockState state = resolveBlockState(blockDef, statesString);
            return CraftEngineBlocks.place(location, state, UpdateFlags.UPDATE_ALL, playSound);
        }

        FurnitureDefinition furnitureDef = CraftEngineFurniture.byId(key);
        if (furnitureDef != null) {
            BukkitFurniture furniture = CraftEngineFurniture.place(location, furnitureDef,
                    furnitureDef.anyVariantName(), playSound);
            return furniture != null;
        }

        return false;
    }

    public static boolean place(Location location, Key key, boolean playSound) {
        return place(location, key.toString(), playSound);
    }

    public static boolean placeFurniture(Location location, String keyString, String variant, boolean playSound) {
        return placeFurniture(location, Key.of(keyString), variant, playSound);
    }

    public static boolean placeFurniture(Location location, Key key, String variant, boolean playSound) {
        FurnitureDefinition furnitureDef = CraftEngineFurniture.byId(key);
        if (furnitureDef == null) return false;
        BukkitFurniture furniture = CraftEngineFurniture.place(location, furnitureDef, variant, playSound);
        return furniture != null;
    }

    public static boolean remove(Block block) {
        return remove(block, null, false, false);
    }

    public static boolean remove(Block block, Player player, boolean dropLoot, boolean playSound) {
        BukkitFurniture furniture = CraftEngineFurniture.rayTrace(
                block.getLocation().add(0.5, 0.5, 0.5), 1.0
        );
        if (furniture != null) {
            CraftEngineFurniture.remove(furniture, player, dropLoot, playSound);
            return true;
        }

        if (CraftEngineBlocks.isCustomBlock(block)) {
            return CraftEngineBlocks.remove(block, player, false, dropLoot, playSound);
        }

        if (block.getType() != Material.AIR) {
            if (dropLoot) {
                block.breakNaturally();
            } else {
                block.setType(Material.AIR);
            }
            return true;
        }

        return false;
    }

    public static String getPureKey(Block block) {
        if (CraftEngineBlocks.isCustomBlock(block)) {
            ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
            if (state != null && !state.isEmpty()) {
                return state.owner().value().id().toString();
            }
        }
        return block.getType().getKey().toString();
    }

    public static String getFullStateKey(Block block) {
        String vanillaState = block.getBlockData().getAsString();

        if (CraftEngineBlocks.isCustomBlock(block)) {
            String pureKey = getPureKey(block);
            if (vanillaState.contains("[")) {
                String states = vanillaState.substring(vanillaState.indexOf("["));
                return pureKey + states;
            }
            return pureKey;
        }

        return vanillaState;
    }

    @Deprecated
    public static String getKey(Block block) {
        return getPureKey(block);
    }

    public static boolean isBlock(Block block, String keyString) {
        return getPureKey(block).equals(keyString);
    }

    public static boolean isCustomBlock(Block block) {
        return CraftEngineBlocks.isCustomBlock(block);
    }

    public static ImmutableBlockState getCustomBlockState(Block block) {
        return CraftEngineBlocks.getCustomBlockState(block);
    }

    private static boolean isVanilla(Key key) {
        return MINECRAFT_NAMESPACE.equals(key.namespace());
    }

    private static boolean placeVanilla(Location location, Key key, String statesString) {
        try {
            Material material = Material.valueOf(key.value().toUpperCase());
            if (!material.isBlock()) return false;

            if (statesString != null) {
                String fullState = "minecraft:" + key.value() + statesString;
                location.getBlock().setBlockData(org.bukkit.Bukkit.createBlockData(fullState));
            } else {
                location.getBlock().setType(material);
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static ImmutableBlockState resolveBlockState(BlockDefinition blockDef, String statesString) {
        if (statesString == null) {
            return blockDef.defaultState();
        }

        try {
            CompoundTag properties = parseStatesToNbt(statesString);
            ImmutableBlockState state = blockDef.getBlockState(properties);
            return state != null ? state : blockDef.defaultState();
        } catch (Exception e) {
            return blockDef.defaultState();
        }
    }

    private static CompoundTag parseStatesToNbt(String statesString) {
        String inner = statesString;
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);

        CompoundTag tag = new CompoundTag();
        for (String entry : inner.split(",")) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2) {
                tag.putString(parts[0].trim(), parts[1].trim());
            }
        }
        return tag;
    }
}