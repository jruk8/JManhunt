package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;

/** Server settings. */
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

    public boolean isAnnounceConfigChanges() {
        return announceConfigChanges;
    }

    public void setAnnounceConfigChanges(boolean announceConfigChanges) {
        this.announceConfigChanges = announceConfigChanges;
    }

    public boolean isAnnounceRoleChanges() {
        return announceRoleChanges;
    }

    public void setAnnounceRoleChanges(boolean announceRoleChanges) {
        this.announceRoleChanges = announceRoleChanges;
    }

    public AntiSpawnCamp getAntiSpawnCamp() {
        return antiSpawnCamp;
    }

    public void setAntiSpawnCamp(AntiSpawnCamp antiSpawnCamp) {
        this.antiSpawnCamp = antiSpawnCamp;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public TeamChat getTeamChat() {
        return teamChat;
    }

    public void setTeamChat(TeamChat teamChat) {
        this.teamChat = teamChat;
    }

    /** Team chat for match participants. */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getPrefixes() {
            return prefixes;
        }

        public void setPrefixes(List<String> prefixes) {
            this.prefixes = prefixes;
        }

        public boolean isSpectatorsSee() {
            return spectatorsSee;
        }

        public void setSpectatorsSee(boolean spectatorsSee) {
            this.spectatorsSee = spectatorsSee;
        }
    }

    /** Anti-spawn-camp guard. */
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

        @Comment({
                "Punishment: KILL slays the camper, GEAR-WIPE clears their armor,",
                "offhand, and main hand.",
                "Default: KILL"
        })
        private String punishment = "KILL";

        @CustomKey("monitored-roles")
        @Comment({
                "Attacker roles the guard punishes: SPEEDRUNNER, HUNTER, both,",
                "or empty to switch the guard off.",
                "Default: [SPEEDRUNNER]"
        })
        private List<String> monitoredRoles = new ArrayList<>(List.of("SPEEDRUNNER"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getKills() {
            return kills;
        }

        public void setKills(int kills) {
            this.kills = kills;
        }

        public double getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(double windowSeconds) {
            this.windowSeconds = windowSeconds;
        }

        public String getPunishment() {
            return punishment;
        }

        public List<String> getMonitoredRoles() {
            return monitoredRoles;
        }

        public void setMonitoredRoles(List<String> monitoredRoles) {
            this.monitoredRoles = monitoredRoles;
        }

        public void setPunishment(String punishment) {
            this.punishment = punishment;
        }
    }

    /** Optional status extras. */
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
                "Default: false"
        })
        private boolean showElapsedTime = false;

        @CustomKey("show-modifiers")
        @Comment({
                "Show enabled custom modifiers as \"Modifiers: a, b, and c\".",
                "Hidden when no custom modifier is enabled.",
                "Default: false"
        })
        private boolean showModifiers = false;

        public boolean isShowWinConditions() {
            return showWinConditions;
        }

        public void setShowWinConditions(boolean showWinConditions) {
            this.showWinConditions = showWinConditions;
        }

        public boolean isShowElapsedTime() {
            return showElapsedTime;
        }

        public void setShowElapsedTime(boolean showElapsedTime) {
            this.showElapsedTime = showElapsedTime;
        }

        public boolean isShowModifiers() {
            return showModifiers;
        }

        public void setShowModifiers(boolean showModifiers) {
            this.showModifiers = showModifiers;
        }

        @CustomKey("show-ids")
        @Comment({
                "Show lobby and game ids as L{lobby}|G{game}.",
                "Useful for debugging.",
                "Default: true"
        })
        private boolean showIds = true;

        public boolean isShowIds() {
            return showIds;
        }

        public void setShowIds(boolean showIds) {
            this.showIds = showIds;
        }
    }
}