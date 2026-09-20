package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.player.Role;
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
 * section of config.yml, grouped by winning side. Multiple conditions can
 * be enabled simultaneously; each side wins as soon as any one of its
 * conditions is satisfied. One role-parameterized core serves both sides.
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

    /**
     * True when the condition is enabled for the side. Conditions owned by
     * the other side, and any condition for a non-participant role, are
     * always disabled.
     */
    public boolean enabled(Role role, WinCondition condition) {
        if (!role.isParticipant()) {
            return false;
        }
        switch (condition) {
            case EXIT_END, SURVIVE_TIME, REACH_ADVANCEMENT -> {
                if (role != Role.SPEEDRUNNER) {
                    return false;
                }
            }
            case TIME_LIMIT -> {
                if (role != Role.HUNTER) {
                    return false;
                }
            }
            case ACQUIRE_ITEM, KILL_MOB -> {
            }
        }
        return config.getBoolean(base(role, condition) + "enabled", condition == WinCondition.EXIT_END);
    }

    /**
     * Configured clock in seconds: the survival time for speedrunners, the
     * expiry limit for hunters.
     */
    public double time(Role role) {
        if (role == Role.SPEEDRUNNER) {
            return config.getDouble(base(role, WinCondition.SURVIVE_TIME) + "time", 3600.0);
        }
        if (role == Role.HUNTER) {
            return config.getDouble(base(role, WinCondition.TIME_LIMIT) + "time", 3600.0);
        }
        return 3600.0;
    }

    /** Configured item for the side's acquire-item condition. */
    public String item(Role role) {
        return config.getString(base(role, WinCondition.ACQUIRE_ITEM) + "item", "minecraft:netherite_ingot");
    }

    /** Configured mob for the side's kill-mob condition. */
    public String mob(Role role) {
        return config.getString(base(role, WinCondition.KILL_MOB) + "mob", "minecraft:ender_dragon");
    }

    /** Configured advancement for the speedrunner reach-advancement condition. */
    public String advancement() {
        return config.getString("settings.win-conditions.speedrunner.reach-advancement.advancement",
                "minecraft:story/enter_the_nether");
    }

    /**
     * Returns true if the player's inventory contains the configured item
     * for their side's acquire-item win condition.
     */
    public boolean hasItem(Player player, Role role) {
        if (!enabled(role, WinCondition.ACQUIRE_ITEM)) {
            return false;
        }
        return hasMaterial(player, item(role));
    }

    /**
     * Returns true if the player has completed the configured advancement
     * for the reach-advancement win condition.
     */
    public boolean hasReachAdvancement(Player player) {
        if (!enabled(Role.SPEEDRUNNER, WinCondition.REACH_ADVANCEMENT)) {
            return false;
        }
        NamespacedKey key = NamespacedKey.fromString(advancement());
        if (key == null) {
            return false;
        }
        var advancement = Bukkit.getAdvancement(key);
        return advancement != null && player.getAdvancementProgress(advancement).isDone();
    }

    /** True when the killed mob satisfies the side's kill-mob condition. */
    public boolean mobMatches(EntityType type, Role role) {
        return enabled(role, WinCondition.KILL_MOB) && matchesMob(mob(role), type);
    }

    private String base(Role role, WinCondition condition) {
        return "settings.win-conditions." + side(role) + "." + leaf(condition) + ".";
    }

    private static String side(Role role) {
        return role == Role.SPEEDRUNNER ? "speedrunner" : "hunter";
    }

    private static String leaf(WinCondition condition) {
        return switch (condition) {
            case EXIT_END -> "exit-end";
            case SURVIVE_TIME -> "survive-time";
            case ACQUIRE_ITEM -> "acquire-item";
            case REACH_ADVANCEMENT -> "reach-advancement";
            case KILL_MOB -> "kill-mob";
            case TIME_LIMIT -> "time-limit";
        };
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
