package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;

/**
 * The settings block of config.yml, grouped into four categories: match
 * flow, compass tracking, players, and server. Every key keeps its relative
 * path below its category; only the category parents are new.
 */
@SuppressWarnings("FieldMayBeFinal")
public class SettingsConfig extends OkaeriConfig {

    @Comment("Match flow: autostart, pre-start wait, head starts, leaving, win rules, and world boosts.")
    private MatchSettings match = new MatchSettings();

    @Comment({
            "Compass tracking settings.",
            "The compass selects its target in this order:",
            "",
            "i. The nearest live player of the opposite role",
            "ii. The nearest last seen location of the opposite role",
            "",
            "Last seen is cached on portal exits to support cross-dimensional",
            "tracking. However, active players are always prioritized over last seen.",
            "The compass shows an actionbar with meters to target where applicable."
    })
    private CompassSettings compass = new CompassSettings();

    @Comment("Players: roles, respawn and lives, friendly fire, invulnerability, and role announcements.")
    private PlayerSettings players = new PlayerSettings();

    @Comment("Server: config and role announcements, spawn-camp guard, and status extras.")
    private ServerSettings server = new ServerSettings();

    public MatchSettings getMatch() {
        return match;
    }

    public void setMatch(MatchSettings match) {
        this.match = match;
    }

    public CompassSettings getCompass() {
        return compass;
    }

    public void setCompass(CompassSettings compass) {
        this.compass = compass;
    }

    public PlayerSettings getPlayers() {
        return players;
    }

    public void setPlayers(PlayerSettings players) {
        this.players = players;
    }

    public ServerSettings getServer() {
        return server;
    }

    public void setServer(ServerSettings server) {
        this.server = server;
    }

}
