package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Evaluates alternate win conditions from the {@code settings.win-conditions}
 * section of config.yml. Multiple conditions can be enabled simultaneously;
 * the speedrunners win as soon as any one of them is satisfied.
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
        return config.getString("settings.win-conditions.reachAdvancement.advancement", "minecraft:story/enter_the_nether");
    }

    /**
     * Returns true if the player's inventory contains the configured item
     * for the acquireItem win condition.
     */
    public boolean hasAcquireItem(Player player) {
        if (!isAcquireItemEnabled()) return false;
        String item = acquireItem();
        NamespacedKey key = NamespacedKey.fromString(item);
        if (key == null) key = NamespacedKey.minecraft(item.replace("minecraft:", ""));
        Material material = Registry.MATERIAL.get(key);
        if (material == null) return false;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) return true;
        }
        return false;
    }

    /**
     * Returns true if the player has completed the configured advancement
     * for the reachAdvancement win condition.
     */
    public boolean hasReachAdvancement(Player player) {
        if (!isReachAdvancementEnabled()) return false;
        NamespacedKey key = NamespacedKey.fromString(reachAdvancement());
        if (key == null) return false;
        var advancement = Bukkit.getAdvancement(key);
        return advancement != null && player.getAdvancementProgress(advancement).isDone();
    }
}
