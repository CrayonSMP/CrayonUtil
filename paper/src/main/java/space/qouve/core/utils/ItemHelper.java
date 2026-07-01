package space.qouve.core.utils;

import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.item.BukkitItemDefinition;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import space.qouve.core.CrayonUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ItemHelper {

    private static final String MINECRAFT_NAMESPACE = "minecraft";
    private static final Map<String, Attribute> ATTRIBUTE_MAP = new HashMap<>();

    static {
        ATTRIBUTE_MAP.put("generic_attack_damage", Attribute.ATTACK_DAMAGE);
        ATTRIBUTE_MAP.put("attack_damage", Attribute.ATTACK_DAMAGE);
        ATTRIBUTE_MAP.put("generic_attack_speed", Attribute.ATTACK_SPEED);
        ATTRIBUTE_MAP.put("attack_speed", Attribute.ATTACK_SPEED);
        ATTRIBUTE_MAP.put("generic_max_health", Attribute.MAX_HEALTH);
        ATTRIBUTE_MAP.put("max_health", Attribute.MAX_HEALTH);
        ATTRIBUTE_MAP.put("generic_armor", Attribute.ARMOR);
        ATTRIBUTE_MAP.put("armor", Attribute.ARMOR);
        ATTRIBUTE_MAP.put("generic_armor_toughness", Attribute.ARMOR_TOUGHNESS);
        ATTRIBUTE_MAP.put("armor_toughness", Attribute.ARMOR_TOUGHNESS);
    }

    public static String getKey(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR) return null;
        Key key = CraftEngineItems.getCustomItemId(itemStack);
        return (key != null) ? key.toString() : itemStack.getType().getKey().toString();
    }

    public static ItemStack create(Key key, int amount) {
        if (MINECRAFT_NAMESPACE.equals(key.namespace())) {
            try {
                return new ItemStack(Material.valueOf(key.value().toUpperCase()), amount);
            } catch (IllegalArgumentException e) { return null; }
        }
        BukkitItemDefinition def = CraftEngineItems.byId(key);
        if (def == null) return null;
        ItemStack item = def.buildBukkitItem();
        item.setAmount(amount);
        return item;
    }

    public static ItemStack createWithSelectedMeta(String targetKey, ItemStack source, ConfigurationSection metaSec, boolean isRevert) {
        ItemStack newItem = create(Key.of(targetKey), source.getAmount());
        if (newItem == null) return null;

        if (metaSec != null && !metaSec.getBoolean("enabled", true)) return newItem;

        org.bukkit.inventory.meta.ItemMeta newMeta = newItem.getItemMeta();
        org.bukkit.inventory.meta.ItemMeta sourceMeta = source.getItemMeta();

        if (sourceMeta != null) {
            List<String> blacklist = (metaSec != null) ? metaSec.getStringList("blacklist") : List.of();
            if (!blacklist.contains("ENCHANTMENTS")) sourceMeta.getEnchants().forEach((e, l) -> newMeta.addEnchant(e, l, true));
            if (!blacklist.contains("DISPLAY_NAME") && sourceMeta.hasDisplayName()) newMeta.setDisplayName(sourceMeta.getDisplayName());
            if (!blacklist.contains("LORE") && sourceMeta.hasLore()) newMeta.setLore(sourceMeta.getLore());

            if (!blacklist.contains("DAMAGE") && sourceMeta instanceof org.bukkit.inventory.meta.Damageable srcDmg
                    && newMeta instanceof org.bukkit.inventory.meta.Damageable newDmg) {
                newDmg.setDamage(srcDmg.getDamage());
            }
            if (!blacklist.contains("COLOR") && sourceMeta instanceof org.bukkit.inventory.meta.LeatherArmorMeta srcLeather
                    && newMeta instanceof org.bukkit.inventory.meta.LeatherArmorMeta newLeather) {
                newLeather.setColor(srcLeather.getColor());
            }
            if (!blacklist.contains("POTION") && sourceMeta instanceof org.bukkit.inventory.meta.PotionMeta srcPotion
                    && newMeta instanceof org.bukkit.inventory.meta.PotionMeta newPotion) {
                newPotion.setBasePotionType(srcPotion.getBasePotionType());
                srcPotion.getCustomEffects().forEach(effect -> newPotion.addCustomEffect(effect, true));
                newPotion.setColor(srcPotion.getColor());
            }
            if (!blacklist.contains("MODEL_DATA") && sourceMeta.hasCustomModelData()) {
                newMeta.setCustomModelData(sourceMeta.getCustomModelData());
            }
        }

        if (!isRevert && metaSec != null && metaSec.contains("attributes")) {
            applyAttributes(newMeta, metaSec.getConfigurationSection("attributes"));
        }

        newItem.setItemMeta(newMeta);
        return newItem;
    }

    private static void applyAttributes(org.bukkit.inventory.meta.ItemMeta newMeta, ConfigurationSection attrSec) {
        for (String attrName : attrSec.getKeys(false)) {
            Attribute attribute = ATTRIBUTE_MAP.get(attrName.toLowerCase());
            if (attribute == null) continue;

            double value = attrSec.getDouble(attrName);
            newMeta.removeAttributeModifier(attribute);

            AttributeModifier modifier = new AttributeModifier(
                    new NamespacedKey(CrayonUtil.getInstance(), "cu_" + attrName.toLowerCase()),
                    value,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY
            );
            newMeta.addAttributeModifier(attribute, modifier);
        }
    }
}