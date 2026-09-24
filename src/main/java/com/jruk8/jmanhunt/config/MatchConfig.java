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
    private double startReminderInterval = 10.0;

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

        @Comment("if false, nothing runs.")
        private boolean enabled = true;

        private Rules rules = new Rules();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Rules getRules() {
            return rules;
        }

        public void setRules(Rules rules) {
            this.rules = rules;
        }

        /** Individual game-state rules. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class Rules extends OkaeriConfig {
            @CustomKey("auto-set-gamemode")
            private boolean autoSetGamemode = true;
            @CustomKey("reset-players-stats")
            private boolean resetPlayersStats = true;
            @CustomKey("disable-locator-bar")
            private boolean disableLocatorBar = true;
            @CustomKey("set-respawn-immediate")
            private boolean setRespawnImmediate = true;
            @CustomKey("set-daytime")
            private boolean setDaytime = true;
            @CustomKey("disable-phantoms")
            private boolean disablePhantoms = true;
            @CustomKey("disable-command-feedback")
            private boolean disableCommandFeedback = false;
            @CustomKey("disable-pillager-patrols")
            private boolean disablePillagerPatrols = true;

            public boolean isAutoSetGamemode() {
                return autoSetGamemode;
            }

            public void setAutoSetGamemode(boolean autoSetGamemode) {
                this.autoSetGamemode = autoSetGamemode;
            }

            public boolean isResetPlayersStats() {
                return resetPlayersStats;
            }

            public void setResetPlayersStats(boolean resetPlayersStats) {
                this.resetPlayersStats = resetPlayersStats;
            }

            public boolean isDisableLocatorBar() {
                return disableLocatorBar;
            }

            public void setDisableLocatorBar(boolean disableLocatorBar) {
                this.disableLocatorBar = disableLocatorBar;
            }

            public boolean isSetRespawnImmediate() {
                return setRespawnImmediate;
            }

            public void setSetRespawnImmediate(boolean setRespawnImmediate) {
                this.setRespawnImmediate = setRespawnImmediate;
            }

            public boolean isSetDaytime() {
                return setDaytime;
            }

            public void setDaytime(boolean setDaytime) {
                this.setDaytime = setDaytime;
            }

            public boolean isDisablePhantoms() {
                return disablePhantoms;
            }

            public void setDisablePhantoms(boolean disablePhantoms) {
                this.disablePhantoms = disablePhantoms;
            }

            public boolean isDisableCommandFeedback() {
                return disableCommandFeedback;
            }

            public void setDisableCommandFeedback(boolean disableCommandFeedback) {
                this.disableCommandFeedback = disableCommandFeedback;
            }

            public boolean isDisablePillagerPatrols() {
                return disablePillagerPatrols;
            }

            public void setDisablePillagerPatrols(boolean disablePillagerPatrols) {
                this.disablePillagerPatrols = disablePillagerPatrols;
            }
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