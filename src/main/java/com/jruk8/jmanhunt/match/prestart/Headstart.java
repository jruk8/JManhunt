package com.jruk8.jmanhunt.match.prestart;

import org.bukkit.configuration.file.FileConfiguration;

/** One headstart side: whether held roles wait, and for how long. */
public record Headstart(boolean enabled, int delaySeconds) {

    /**
     * Parses a headstart side. No Bukkit server needed, so unit tests can
     * exercise it with a synthetic configuration.
     *
     * @param side "hunter" or "speedrunner"
     */
    public static Headstart parse(FileConfiguration config, String side) {
        String base = "settings.headstarts." + side + ".";
        return new Headstart(
                config.getBoolean(base + "enabled", false),
                config.getInt(base + "delay-seconds", 30));
    }
}