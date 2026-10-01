package com.jruk8.jmanhunt.match.prestart;

import com.jruk8.jmanhunt.config.MatchSettings;

/** One headstart side: whether held roles wait, and for how long. */
public record Headstart(boolean enabled, int delaySeconds) {

    /**
     * Resolves a headstart side. No Bukkit server needed, so unit tests can
     * exercise it with a synthetic section.
     *
     * @param side "hunter" or "speedrunner"
     */
    public static Headstart parse(MatchSettings.Headstarts headstarts, String side) {
        MatchSettings.Headstarts.Headstart section = "hunter".equalsIgnoreCase(side)
                ? headstarts.getHunter()
                : headstarts.getSpeedrunner();
        return new Headstart(section.isEnabled(), section.getDelaySeconds());
    }
}