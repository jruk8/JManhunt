package com.jruk8.jmanhunt.match.prestart;

import com.jruk8.jmanhunt.config.ConfigService;

/** One headstart side: whether held roles wait, and for how long. */
public record Headstart(boolean enabled, int delaySeconds) {

    /**
     * Parses a headstart side. No Bukkit server needed, so unit tests can
     * exercise it with a synthetic configuration.
     *
     * @param side "hunter" or "speedrunner"
     */
    public static Headstart parse(ConfigService config, String side) {
        String base = "settings.match.headstarts." + side + ".";
        return new Headstart(
                config.getBoolean(base + "enabled", false),
                config.getInt(base + "delay-seconds", 30));
    }
}