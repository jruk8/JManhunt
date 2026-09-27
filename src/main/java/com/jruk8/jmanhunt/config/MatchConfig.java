package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;

/** Match lifecycle settings. */
@SuppressWarnings("FieldMayBeFinal")
public class MatchConfig extends OkaeriConfig {

    @CustomKey("game-rules")
    @Comment("Built-in game-state actions applied at match start and match end.")
    private GameRules gameRules = new GameRules();

    @CustomKey("end-delay")
    @Comment({
            "Seconds between the win announcement and final inventory cleanup. Statistics",
            "are displayed halfway through this delay.",
            "Set to -1 to skip the delay (cleanup is immediate); other negative values are",
            "also treated as zero."
    })
    private double endDelay = 10.0;

    @CustomKey("start-reminder-interval")
    @Comment({
            "Seconds between reminders while waiting for the first speedrunner hit.",
            "For finite delays, exactly three reminders are shown at the delay and two",
            "equally-sized slices (e.g. 30s -> 30, 20, 10). This value is only used as",
            "the repeat interval when delay-seconds is -1 (wait indefinitely).",
            "Set to -1 to disable the reminders entirely while still waiting for damage."
    })
    private double startReminderInterval = 30.0;

    @CustomKey("disconnect-handling")
    @Comment("Disconnect rules for active participants.")
    private DisconnectHandling disconnectHandling = new DisconnectHandling();

    @CustomKey("end-statistics")
    @Comment({
            "End-screen sections, in display order. Available values are DAMAGE_DEALT,",
            "HUNTER_FINAL_KILLS, SPEEDRUNNER_KILLS, and PROGRESSION. Progression is based",
            "on reliable vanilla advancements and is calculated only at match end."
    })
    private List<String> endStatistics = new ArrayList<>(List.of(
            "DAMAGE_DEALT", "HUNTER_FINAL_KILLS", "SPEEDRUNNER_KILLS", "PROGRESSION"));

    public GameRules getGameRules() {
        return gameRules;
    }

    public void setGameRules(GameRules gameRules) {
        this.gameRules = gameRules;
    }

    public double getEndDelay() {
        return endDelay;
    }

    public void setEndDelay(double endDelay) {
        this.endDelay = endDelay;
    }

    public double getStartReminderInterval() {
        return startReminderInterval;
    }

    public void setStartReminderInterval(double startReminderInterval) {
        this.startReminderInterval = startReminderInterval;
    }

    public DisconnectHandling getDisconnectHandling() {
        return disconnectHandling;
    }

    public void setDisconnectHandling(DisconnectHandling disconnectHandling) {
        this.disconnectHandling = disconnectHandling;
    }

    public List<String> getEndStatistics() {
        return endStatistics;
    }

    public void setEndStatistics(List<String> endStatistics) {
        this.endStatistics = endStatistics;
    }

    /** Built-in game-state actions. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class GameRules extends OkaeriConfig {

        /** Config path of the rules enum array. */
        public static final String RULES_PATH =
                "advanced.advanced-match-controls.game-rules.rules";

        /** Known rule keys, in display order. */
        public static final List<String> KNOWN = List.of(
                "AUTO_SET_GAMEMODE",
                "RESET_PLAYERS_STATS",
                "DISABLE_LOCATOR_BAR",
                "SET_RESPAWN_IMMEDIATE",
                "SET_DAYTIME",
                "DISABLE_PHANTOMS",
                "DISABLE_COMMAND_FEEDBACK",
                "DISABLE_PILLAGER_PATROLS",
                "DISABLE_WANDERING_TRADER");

        /** Rules enabled by default: all but DISABLE_COMMAND_FEEDBACK. */
        public static final List<String> DEFAULT_RULES = List.of(
                "AUTO_SET_GAMEMODE",
                "RESET_PLAYERS_STATS",
                "DISABLE_LOCATOR_BAR",
                "SET_RESPAWN_IMMEDIATE",
                "SET_DAYTIME",
                "DISABLE_PHANTOMS",
                "DISABLE_PILLAGER_PATROLS",
                "DISABLE_WANDERING_TRADER");

        @Comment("if false, nothing runs.")
        private boolean enabled = true;

        @Comment({
                "Enabled game-state rules. Unknown entries are ignored.",
                "Known: AUTO_SET_GAMEMODE, RESET_PLAYERS_STATS, DISABLE_LOCATOR_BAR,",
                "SET_RESPAWN_IMMEDIATE, SET_DAYTIME, DISABLE_PHANTOMS,",
                "DISABLE_COMMAND_FEEDBACK, DISABLE_PILLAGER_PATROLS, DISABLE_WANDERING_TRADER."
        })
        private List<String> rules = new ArrayList<>(DEFAULT_RULES);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getRules() {
            return rules;
        }

        public void setRules(List<String> rules) {
            this.rules = rules;
        }

        /** Case-insensitive membership for the rules array. Pure for tests. */
        public static boolean isRuleEnabled(List<String> rules, String key) {
            if (rules == null || key == null) {
                return false;
            }
            for (String entry : rules) {
                if (key.equalsIgnoreCase(entry)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Per-role disconnect rules. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class DisconnectHandling extends OkaeriConfig {
        private DisconnectRules speedrunner = new DisconnectRules();
        private DisconnectRules hunter = new DisconnectRules();

        public DisconnectRules getSpeedrunner() {
            return speedrunner;
        }

        public void setSpeedrunner(DisconnectRules speedrunner) {
            this.speedrunner = speedrunner;
        }

        public DisconnectRules getHunter() {
            return hunter;
        }

        public void setHunter(DisconnectRules hunter) {
            this.hunter = hunter;
        }

        /** Reconnect grace and strike limit for one role. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class DisconnectRules extends OkaeriConfig {
            @CustomKey("reconnect-grace-seconds")
            @Comment({
                    "Seconds a disconnected player has to rejoin before they are treated",
                    "as dead and removed from the match."
            })
            private int reconnectGraceSeconds = 60;

            @CustomKey("max-strikes")
            @Comment({
                    "Disconnect strike limit before the player instantly loses.",
                    "Set to 1 to disable retries entirely."
            })
            private int maxStrikes = 3;

            public int getReconnectGraceSeconds() {
                return reconnectGraceSeconds;
            }

            public void setReconnectGraceSeconds(int reconnectGraceSeconds) {
                this.reconnectGraceSeconds = reconnectGraceSeconds;
            }

            public int getMaxStrikes() {
                return maxStrikes;
            }

            public void setMaxStrikes(int maxStrikes) {
                this.maxStrikes = maxStrikes;
            }
        }
    }
}