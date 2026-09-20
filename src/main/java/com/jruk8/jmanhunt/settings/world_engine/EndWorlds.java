package com.jruk8.jmanhunt.settings.world_engine;

import com.jruk8.jmanhunt.JManhuntPlugin;
import org.bukkit.World;

/** Shared end-dimension helpers for the legacy reset and end cells. */
public final class EndWorlds {
    private EndWorlds() {
    }

    /**
     * Clears the vanilla dragon health bar explicitly. The bar is tracked
     * per player and is not always cleared when its world is unloaded, so
     * without this a stale bar stays on screen for anyone inside.
     */
    public static void clearDragonBar(JManhuntPlugin plugin, World endWorld) {
        try {
            var battle = endWorld.getEnderDragonBattle();
            if (battle == null) {
                return;
            }
            var bar = battle.getBossBar();
            if (bar == null) {
                return;
            }
            bar.removeAll();
        } catch (UnsupportedOperationException exception) {
            plugin.logger().fine("Could not clear dragon bar in " + endWorld.getName() + ": " + exception.getMessage());
        }
    }
}
