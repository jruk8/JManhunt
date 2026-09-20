package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Evaluates alternate win conditions from the {@code settings.win-conditions}
 * section of config.yml. Multiple conditions can be enabled simultaneously;
 * each side wins as soon as any one of its conditions is satisfied.
 */
public final class WinConditionEngine {
    private FileConfiguration config;

    public WinConditionEngine(FileConfiguration config) {
        this.config = config;
    }

    /** Updates the config reference after a reload. */
    public void reload(FileConfiguration config) {
        this.config = config;
    }

    public boolean isExitEndEnabled() {
        return config.getBoolean("settings.win-conditions.exitEnd.enabled", true);
    }

    public boolean isSurviveTimeEnabled() {
        return config.getBoolean("settings.win-conditions.surviveTime.enabled", false);
    }

    public double surviveTimeSeconds() {
        return config.getDouble("settings.win-conditions.surviveTime.time", 3600.0);
    }

    public boolean isAcquireItemEnabled() {
        return config.getBoolean("settings.win-conditions.acquireItem.enabled", false);
    }

    public String acquireItem() {
        return config.getString("settings.win-conditions.acquireItem.item", "minecraft:netherite_ingot");
    }

    public boolean isReachAdvancementEnabled() {
        return config.getBoolean("settings.win-conditions.reachAdvancement.enabled", false);
    }

    public String reachAdvancement() {
        return config.getString(
                "settings.win-conditions.reachAdvancement.advancement",
                "minecraft:story/enter_the_nether");
    }

    public boolean isKillMobEnabled() {
        return config.getBoolean("settings.win-conditions.killMob.enabled", false);
    }

    public String killMob() {
        return config.getString("settings.win-conditions.killMob.mob", "minecraft:ender_dragon");
    }

    public boolean isHunterTimeLimitEnabled() {
        return config.getBoolean("settings.win-conditions.hunterTimeLimit.enabled", false);
    }

    public double hunterTimeLimitSeconds() {
        return config.getDouble("settings.win-conditions.hunterTimeLimit.time", 3600.0);
    }

    public boolean isHunterAcquireItemEnabled() {
        return config.getBoolean("settings.win-conditions.hunterAcquireItem.enabled", false);
    }

    public String hunterAcquireItem() {
        return config.getString(
                "settings.win-conditions.hunterAcquireItem.item", "minecraft:netherite_ingot");
    }

    public boolean isHunterKillMobEnabled() {
        return config.getBoolean("settings.win-conditions.hunterKillMob.enabled", false);
    }

    public String hunterKillMob() {
        return config.getString("settings.win-conditions.hunterKillMob.mob", "minecraft:ender_dragon");
    }

    /**
     * Returns true if the player's inventory contains the configured item
     * for the acquireItem win condition.
     */
    public boolean hasAcquireItem(Player player) {
        if (!isAcquireItemEnabled()) {
            return false;
        }
        return hasMaterial(player, acquireItem());
    }

    /**
     * Returns true if the player has completed the configured advancement
     * for the reachAdvancement win condition.
     */
    public boolean hasReachAdvancement(Player player) {
        if (!isReachAdvancementEnabled()) {
            return false;
        }
        NamespacedKey key = NamespacedKey.fromString(reachAdvancement());
        if (key == null) {
            return false;
        }
        var advancement = Bukkit.getAdvancement(key);
        return advancement != null && player.getAdvancementProgress(advancement).isDone();
    }

    /**
     * Returns true if the player's inventory contains the configured item
     * for the hunterAcquireItem win condition.
     */
    public boolean hasHunterAcquireItem(Player player) {
        if (!isHunterAcquireItemEnabled()) {
            return false;
        }
        return hasMaterial(player, hunterAcquireItem());
    }

    /** True when the killed mob satisfies the speedrunner killMob condition. */
    public boolean isKillMob(EntityType type) {
        return isKillMobEnabled() && matchesMob(killMob(), type);
    }

    /** True when the killed mob satisfies the hunter hunterKillMob condition. */
    public boolean isHunterKillMob(EntityType type) {
        return isHunterKillMobEnabled() && matchesMob(hunterKillMob(), type);
    }

    private boolean hasMaterial(Player player, String item) {
        NamespacedKey key = NamespacedKey.fromString(item);
        if (key == null) {
            key = NamespacedKey.minecraft(item.replace("minecraft:", ""));
        }
        Material material = Registry.MATERIAL.get(key);
        if (material == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesMob(String configured, EntityType type) {
        if (type == null) {
            return false;
        }
        NamespacedKey key = NamespacedKey.fromString(configured);
        if (key == null) {
            key = NamespacedKey.minecraft(configured.replace("minecraft:", ""));
        }
        EntityType wanted = Registry.ENTITY_TYPE.get(key);
        return wanted != null && wanted == type;
    }

    /**
     * Human-readable name for a namespaced key: "minecraft:ender_dragon"
     * becomes "ender dragon". Pure for tests.
     */
    public static String prettyKey(String namespacedKey) {
        if (namespacedKey == null) {
            return "?";
        }
        String raw = namespacedKey.trim();
        int colon = raw.indexOf(':');
        if (colon >= 0 && colon + 1 < raw.length()) {
            raw = raw.substring(colon + 1);
        }
        String spaced = raw.replace('_', ' ').replace('/', ' ').trim().toLowerCase(java.util.Locale.ROOT);
        if (spaced.isEmpty()) {
            return "?";
        }
        return spaced;
    }
}
