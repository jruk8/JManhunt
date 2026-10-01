package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.lobby.config.WinConditionsSettingsFacade;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Evaluates alternate win conditions from the {@code settings.match.win-conditions}
 * section of config.yml, grouped by winning side. Multiple conditions can
 * be enabled simultaneously; each side wins as soon as any one of its
 * conditions is satisfied. One role-parameterized core serves both sides.
 */
public final class WinConditionEngine {
    private final WinConditionsSettingsFacade winConditions;

    public WinConditionEngine(WinConditionsSettingsFacade winConditions) {
        this.winConditions = winConditions;
    }

    /**
     * True when the condition is enabled for the side. Conditions owned by
     * the other side, and any condition for a non-participant role, are
     * always disabled.
     */
    public boolean enabled(Role role, WinCondition condition) {
        return enabled(null, role, condition);
    }

    /** Lobby-aware enabled flag: the lobby's override wins, else the global. */
    public boolean enabled(Integer lobby, Role role, WinCondition condition) {
        if (!role.isParticipant()) {
            return false;
        }
        switch (condition) {
            case EXIT_END, SURVIVE_TIME -> {
                if (role != Role.SPEEDRUNNER) {
                    return false;
                }
            }
            case TIME_LIMIT -> {
                if (role != Role.HUNTER) {
                    return false;
                }
            }
            case ACQUIRE_ITEM, KILL_MOB, REACH_ADVANCEMENT -> {
            }
        }
        return winConditions.conditionEnabled(lobby, role, condition);
    }

    /**
     * Configured clock in seconds: the survival time for speedrunners, the
     * expiry limit for hunters.
     */
    public double time(Role role) {
        return time(null, role);
    }

    /** Lobby-aware clock; the lobby's override wins, else the global. */
    public double time(Integer lobby, Role role) {
        return winConditions.surviveTime(lobby, role);
    }

    /** Configured item for the side's acquire-item condition. */
    public String item(Role role) {
        return item(null, role);
    }

    /** Lobby-aware item; the lobby's override wins, else the global. */
    public String item(Integer lobby, Role role) {
        return winConditions.acquireItem(lobby, role);
    }

    /** Configured mob for the side's kill-mob condition. */
    public String mob(Role role) {
        return mob(null, role);
    }

    /** Lobby-aware mob; the lobby's override wins, else the global. */
    public String mob(Integer lobby, Role role) {
        return winConditions.killMob(lobby, role);
    }

    /** Configured advancement for the side's reach-advancement condition. */
    public String advancement(Role role) {
        return advancement(null, role);
    }

    /** Lobby-aware advancement; the lobby's override wins, else the global. */
    public String advancement(Integer lobby, Role role) {
        return winConditions.reachAdvancement(lobby, role);
    }

    /** True when the cancel survived-time condition is enabled. */
    public boolean cancelSurviveEnabled() {
        return cancelSurviveEnabled(null);
    }

    /** Lobby-aware cancel flag; the lobby's override wins, else the global. */
    public boolean cancelSurviveEnabled(Integer lobby) {
        return winConditions.cancelSurviveEnabled(lobby);
    }

    /** Cancel survived-time in seconds. */
    public double cancelSurviveTime() {
        return cancelSurviveTime(null);
    }

    /** Lobby-aware cancel time; the lobby's override wins, else the global. */
    public double cancelSurviveTime(Integer lobby) {
        return winConditions.cancelSurviveTime(lobby);
    }

    /**
     * Returns true if the player's inventory contains the configured item
     * for their side's acquire-item win condition.
     */
    public boolean hasItem(Player player, Role role) {
        return hasItem(null, player, role);
    }

    /** Lobby-aware inventory check; the lobby's override wins, else the global. */
    public boolean hasItem(Integer lobby, Player player, Role role) {
        if (!enabled(lobby, role, WinCondition.ACQUIRE_ITEM)) {
            return false;
        }
        return hasMaterial(player, item(lobby, role));
    }

    /**
     * True when picking up the given material wins at once for the side:
     * the acquire-item condition is enabled and the material equals the
     * configured one. The disabled and null gates are pure for tests; the
     * material comparison needs the server registry.
     */
    public boolean materialWins(Material material, Role role) {
        return materialWins(null, material, role);
    }

    /** Lobby-aware pickup check; the lobby's override wins, else the global. */
    public boolean materialWins(Integer lobby, Material material, Role role) {
        if (!enabled(lobby, role, WinCondition.ACQUIRE_ITEM)) {
            return false;
        }
        return material != null && material == materialFor(item(lobby, role));
    }

    /**
     * Returns true if the player has completed the configured advancement
     * for their side's reach-advancement win condition.
     */
    public boolean hasReachAdvancement(Player player, Role role) {
        return hasReachAdvancement(null, player, role);
    }

    /** Lobby-aware advancement check; the lobby's override wins, else the global. */
    public boolean hasReachAdvancement(Integer lobby, Player player, Role role) {
        if (!enabled(lobby, role, WinCondition.REACH_ADVANCEMENT)) {
            return false;
        }
        NamespacedKey key = NamespacedKey.fromString(advancement(lobby, role));
        if (key == null) {
            return false;
        }
        var advancement = Bukkit.getAdvancement(key);
        return advancement != null && player.getAdvancementProgress(advancement).isDone();
    }

    /** True when the killed mob satisfies the side's kill-mob condition. */
    public boolean mobMatches(EntityType type, Role role) {
        return mobMatches(null, type, role);
    }

    /** Lobby-aware mob check; the lobby's override wins, else the global. */
    public boolean mobMatches(Integer lobby, EntityType type, Role role) {
        return enabled(lobby, role, WinCondition.KILL_MOB) && matchesMob(mob(lobby, role), type);
    }

    private boolean hasMaterial(Player player, String item) {
        Material material = materialFor(item);
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

    private static Material materialFor(String item) {
        NamespacedKey key = NamespacedKey.fromString(item);
        if (key == null) {
            key = NamespacedKey.minecraft(item.replace("minecraft:", ""));
        }
        return Registry.MATERIAL.get(key);
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
