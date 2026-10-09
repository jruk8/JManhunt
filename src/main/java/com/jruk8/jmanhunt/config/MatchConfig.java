package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Match lifecycle settings. */
@SuppressWarnings("FieldMayBeFinal")
public class MatchConfig extends OkaeriConfig {

    @CustomKey("game-rules")
    @Comment("Built-in game-state actions applied at match start and match end.")
    @Getter
    @Setter
    private GameRules gameRules = new GameRules();

    @CustomKey("end-delay")
    @Comment({
            "Seconds between the win announcement and final inventory cleanup. Statistics",
            "are displayed halfway through this delay.",
            "Set to -1 to skip the delay (cleanup is immediate); other negative values are",
            "also treated as zero."
    })
    @Getter
    @Setter
    private double endDelay = 10.0;

    @CustomKey("disconnect-handling")
    @Comment("Disconnect rules for active participants.")
    @Getter
    @Setter
    private DisconnectHandling disconnectHandling = new DisconnectHandling();

    @CustomKey("end-statistics")
    @Comment({
            "End-screen sections, in display order. Available values are DAMAGE_DEALT,",
            "HUNTER_FINAL_KILLS, SPEEDRUNNER_KILLS, and PROGRESSION. Progression is based",
            "on reliable vanilla advancements and is calculated only at match end."
    })
    @Getter
    @Setter
    private List<String> endStatistics = new ArrayList<>(List.of(
            "DAMAGE_DEALT", "HUNTER_FINAL_KILLS", "SPEEDRUNNER_KILLS", "PROGRESSION"));

    /** Built-in game-state actions. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class GameRules extends OkaeriConfig {

        /** Config path of the rules enum array. */
        public static final transient String RULES_PATH =
                "advanced.advanced-match-controls.game-rules.rules";

        /** Known rule keys, in display order. */
        public static final transient List<String> KNOWN = List.of(
                "DISABLE_LOCATOR_BAR",
                "SET_RESPAWN_IMMEDIATE",
                "SET_DAYTIME",
                "DISABLE_PHANTOMS",
                "DISABLE_PILLAGER_PATROLS",
                "DISABLE_WANDERING_TRADER");

        /** Rules enabled by default. */
        public static final transient List<String> DEFAULT_RULES = List.of(
                "DISABLE_LOCATOR_BAR",
                "SET_RESPAWN_IMMEDIATE",
                "SET_DAYTIME",
                "DISABLE_PHANTOMS",
                "DISABLE_PILLAGER_PATROLS",
                "DISABLE_WANDERING_TRADER");

        @Comment("if false, nothing runs.")
        @Getter
        @Setter
        private boolean enabled = true;

        @Comment({
                "Enabled game-state rules. Unknown entries are ignored.",
                "Known: DISABLE_LOCATOR_BAR, SET_RESPAWN_IMMEDIATE, SET_DAYTIME,",
                "DISABLE_PHANTOMS, DISABLE_PILLAGER_PATROLS, DISABLE_WANDERING_TRADER."
        })
        @Getter
        @Setter
        private List<String> rules = new ArrayList<>(DEFAULT_RULES);

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
        @Getter
        @Setter
        private DisconnectRules speedrunner = new DisconnectRules();
        @Getter
        @Setter
        private DisconnectRules hunter = new DisconnectRules();

        /** Reconnect grace and strike limit for one role. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class DisconnectRules extends OkaeriConfig {
            @CustomKey("reconnect-grace-seconds")
            @Comment({
                    "Seconds a disconnected player has to rejoin before they are treated",
                    "as dead and removed from the match."
            })
            @Getter
            @Setter
            private int reconnectGraceSeconds = 60;

            @CustomKey("max-strikes")
            @Comment({
                    "Disconnect strike limit before the player instantly loses.",
                    "Set to 1 to disable retries entirely."
            })
            @Getter
            @Setter
            private int maxStrikes = 3;

        }
    }
}
