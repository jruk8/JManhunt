package com.jruk8.jmanhunt;

import org.bukkit.configuration.file.FileConfiguration;

/** Reads one side of the settings.headstarts section. */
public final class HeadstartSettings {
    /** One headstart side: whether held roles wait, and for how long. */
    public record Headstart(boolean enabled, int delaySeconds) {
    }

    private HeadstartSettings() {
    }

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
