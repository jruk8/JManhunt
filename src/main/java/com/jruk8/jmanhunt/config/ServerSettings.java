package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Server settings. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ServerSettings extends OkaeriConfig {

    @CustomKey("announce-config-changes")
    @Comment({
            "When true, every in-game config change is announced to all online",
            "players except the one who made the change.",
            "Default: false"
    })
    private boolean announceConfigChanges = false;

    @CustomKey("announce-role-changes")
    @Comment({
            "When true, role changes between passive (none, afk, spectator) and",
            "active (hunter, speedrunner) roles are announced to the player's",
            "lobby mates who are not in a live match.",
            "Good for small setups.",
            "Default: false"
    })
    private boolean announceRoleChanges = false;

    @CustomKey("anti-spawn-camp")
    @Comment({
            "Rolling anti-spawn-camp guard: too many kills by one attacker on the",
            "same victim inside the window punishes the camper, broadcast to all."
    })
    private AntiSpawnCamp antiSpawnCamp = new AntiSpawnCamp();

    @Comment("Optional /manhunt status extras. Each toggles independently.")
    private Status status = new Status();

    @CustomKey("team-chat")
    @Comment("Team chat (@team ...) for match participants.")
    private TeamChat teamChat = new TeamChat();

    /** Team chat for match participants. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class TeamChat extends OkaeriConfig {

        @CustomKey("enabled")
        @Comment({
                "When true, messages starting with a team-chat prefix go",
                "to same-team match members instead of public chat.",
                "Default: true"
        })
        private boolean enabled = true;

        @CustomKey("prefixes")
        @Comment({
                "Prefix aliases that mark a line as team chat. Each must",
                "end at a word boundary, so @teammate stays public.",
                "Default: [@team, @t]"
        })
        private List<String> prefixes = new ArrayList<>(List.of("@team", "@t"));

        @CustomKey("spectators-see")
        @Comment({
                "When true, spectators in fake-spectator mode see all team",
                "chat. Lobby idlers never do.",
                "Default: true"
        })
        private boolean spectatorsSee = true;

    }

    /** Anti-spawn-camp guard. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class AntiSpawnCamp extends OkaeriConfig {

        @Comment("Default: true")
        private boolean enabled = true;

        @Comment({
                "Kills on the same victim that trigger the punishment.",
                "Default: 3"
        })
        private int kills = 3;

        @CustomKey("window-seconds")
        @Comment({
                "Rolling window in seconds.",
                "Default: 90.0"
        })
        private double windowSeconds = 90.0;

        @CustomKey("first-punishment")
        @Comment({
                "First punishment: KILL slays the camper, GEAR-WIPE clears their armor,",
                "offhand, and main hand.",
                "Default: GEAR-WIPE"
        })
        private String firstPunishment = "GEAR-WIPE";

        @CustomKey("kill-on-second-time")
        @Comment({
                "When true, a repeat offense kills the camper regardless of",
                "first-punishment.",
                "Default: true"
        })
        private boolean killOnSecondTime = true;

        @CustomKey("monitored-roles")
        @Comment({
                "Attacker roles the guard punishes: SPEEDRUNNER, HUNTER, both,",
                "or empty to switch the guard off.",
                "Default: [SPEEDRUNNER]"
        })
        private List<String> monitoredRoles = new ArrayList<>(List.of("SPEEDRUNNER"));

    }

    /** Optional status extras. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Status extends OkaeriConfig {

        @CustomKey("show-win-conditions")
        @Comment({
                "Show \"Speedrunners win on ...\" and \"Hunters win on ...\" lines.",
                "Default: false"
        })
        private boolean showWinConditions = false;

        @CustomKey("show-elapsed-time")
        @Comment({
                "Show how long the match has been running.",
                "Default: true"
        })
        private boolean showElapsedTime = true;

        @CustomKey("show-modifiers")
        @Comment({
                "Show enabled custom modifiers as rocket, a, b, and c.",
                "Hidden when no custom modifier is enabled.",
                "Default: true"
        })
        private boolean showModifiers = true;

        @CustomKey("show-ids")
        @Comment({
                "Show lobby and game ids as L{lobby}|G{game}.",
                "Useful for debugging.",
                "Default: false"
        })
        private boolean showIds = false;

        @CustomKey("show-on-start")
        @Comment({
                "Show the status roster automatically when a match starts.",
                "Default: true"
        })
        private boolean showOnStart = true;

    }
}
