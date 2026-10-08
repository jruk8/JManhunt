package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.player.PlayerStateStore;
import org.bukkit.Bukkit;

/**
 * LuckPerms soft boundary. This class never imports LuckPerms types, so
 * it loads safely without the plugin; RoleContexts is only touched
 * inside the plugin-present branch of {@link #start}.
 */
public final class LuckPermsHook {
    private final RoleContexts contexts;
    private final JManhuntLogger log;

    private LuckPermsHook(RoleContexts contexts, JManhuntLogger log) {
        this.contexts = contexts;
        this.log = log;
    }

    /** Starts the jmh-role context when LuckPerms is present; inert otherwise. Never throws. */
    public static LuckPermsHook start(PlayerStateStore states, JManhuntLogger log) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            log.info("LuckPerms is not installed; the jmh-role context stays unavailable.");
            return new LuckPermsHook(null, log);
        }
        try {
            RoleContexts contexts = new RoleContexts(states);
            contexts.start();
            log.info("Hooked into LuckPerms: the jmh-role context is available.");
            return new LuckPermsHook(contexts, log);
        } catch (Exception broken) {
            log.warning("LuckPerms is present but the context hook failed: " + broken.getMessage());
            return new LuckPermsHook(null, log);
        }
    }

    /** Unregisters the context; safe to call when never started. Never throws. */
    public void stop() {
        if (contexts == null) {
            return;
        }
        try {
            contexts.stop();
        } catch (Exception gone) {
            log.warning("LuckPerms context shutdown failed: " + gone.getMessage());
        }
    }
}
