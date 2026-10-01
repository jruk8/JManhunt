package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Refresh analysis lag and debuffs. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class CompassAnalysisSettings extends OkaeriConfig {

    @Comment({
            "Analysis runs on right-click refreshes only.",
            "Default: true"
    })
    private boolean enabled = true;

    @CustomKey("delay-seconds")
    @Comment({
            "Seconds the analysis takes before the compass updates.",
            "Default: 5.0"
    })
    private double delaySeconds = 5.0;

    @CustomKey("delay-deviation-seconds")
    @Comment({
            "Random plus-or-minus jitter applied to delay-seconds per",
            "analysis. Capped at the delay itself.",
            "Default: 3.0"
    })
    private double delayDeviationSeconds = 3.0;

    @CustomKey("sound-interval-seconds")
    @Comment({
            "Seconds between analysis tick sounds, rounded to whole ticks.",
            "Default: 0.5"
    })
    private double soundIntervalSeconds = 0.5;

    @Comment({
            "Commands run as console when an analysis starts, in modifier",
            "style: <p> is the holder, ~ resolves against their location, and",
            "<duration> is the analysis delay in whole seconds (floored). The",
            "player list runs for every analyzing holder plus their own role",
            "list. Only participants are affected.",
            "Default: false"
    })
    private Debuffs debuffs = new Debuffs();

    @CustomKey("cost")
    @Comment({
            "Health, hunger, and experience charges for running an",
            "analysis. Each payment is toggled separately below.",
            "Default: false"
    })
    private Cost cost = new Cost();

    @CustomKey("cancel-early")
    @Comment({
            "Shortens analyses that are already doomed to fail, so a bad",
            "signal lands sooner instead of after the full delay.",
            "Default: true"
    })
    private CancelEarly cancelEarly = new CancelEarly();

    /** Analysis console commands. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Debuffs extends OkaeriConfig {
        private boolean enabled = true;
        private DebuffCommands commands = new DebuffCommands();

        /** Command lists per audience. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class DebuffCommands extends OkaeriConfig {
            private List<String> player = new ArrayList<>(List.of(
                    "effect give <p> minecraft:slowness <duration> 1 true"));
            private List<String> speedrunner = new ArrayList<>();
            private List<String> hunter = new ArrayList<>();

        }
    }

    /** Analysis charges. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Cost extends OkaeriConfig {
        @Comment({
                "When true, analyses charge the holder.",
                "Default: false"
        })
        private boolean enabled = false;

        @CustomKey("cost-on")
        @Comment({
                "When to charge: INITIATE at press, SUCCESS at resolution,",
                "BOTH at each.",
                "Default: INITIATE"
        })
        private String costOn = "INITIATE";

        @Comment("One container per charge; each toggles separately.")
        private Payment payment = new Payment();

        @CustomKey("poverty-behavior")
        private PovertyBehavior povertyBehavior = new PovertyBehavior();

        /** One container per charge. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class Payment extends OkaeriConfig {
            private Saturation saturation = new Saturation();
            private Health health = new Health();

            @CustomKey("exp-level")
            private ExpLevel expLevel = new ExpLevel();

            @CustomKey("failure-cooldown")
            @Comment({
                    "Seconds a holder who cannot pay must wait before",
                    "refreshing again. Both this and the manual cooldown",
                    "must pass. At or below 0 disables the wait.",
                    "Default: 1.0"
            })
            private double failureCooldown = 1.0;

            /** Saturation charge. */
            @Getter
            @Setter
            @SuppressWarnings("FieldMayBeFinal")
            public static class Saturation extends OkaeriConfig {
                private boolean enabled = false;

                @CustomKey("value")
                @Comment({
                        "Points drained from the 0-40 hunger pool: hidden",
                        "saturation first, then the visible hunger bar.",
                        "Minimum: 1. Maximum: 40.",
                        "Default: 3"
                })
                private int value = 3;

            }

            /** Health charge. */
            @Getter
            @Setter
            @SuppressWarnings("FieldMayBeFinal")
            public static class Health extends OkaeriConfig {
                private boolean enabled = false;

                @CustomKey("value")
                @Comment({
                        "Health points drained, 1 to 100. Vanilla full",
                        "health is 20.",
                        "Default: 4"
                })
                private int value = 4;

                @CustomKey("can-kill")
                @Comment({
                        "When true, the charge can kill. When false,",
                        "health never drops below half a heart.",
                        "Default: true"
                })
                private boolean canKill = true;

            }

            /** Experience charge. */
            @Getter
            @Setter
            @SuppressWarnings("FieldMayBeFinal")
            public static class ExpLevel extends OkaeriConfig {
                private boolean enabled = true;

                @CustomKey("value")
                @Comment({
                        "Experience levels drained, 1 to 100.",
                        "Default: 1"
                })
                private int value = 1;

            }
        }

        /** What happens when the holder cannot pay. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class PovertyBehavior extends OkaeriConfig {

            @CustomKey("cancel-when-poor")
            @Comment({
                    "When true, a holder who cannot pay never starts",
                    "(or resolves) the analysis.",
                    "Default: true"
            })
            private boolean cancelWhenPoor = true;

            @CustomKey("show-reason")
            @Comment({
                    "When true, a cancelled analysis names each lacking",
                    "charge in the actionbar.",
                    "Default: true"
            })
            private boolean showReason = true;

        }
    }

    /** Shorter waits for doomed analyses. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class CancelEarly extends OkaeriConfig {

        @Comment({
                "When true, an analysis that is already doomed finishes",
                "early instead of running the full delay.",
                "Default: true"
        })
        private boolean enabled = true;

        @CustomKey("time-multiplier")
        @Comment({
                "Fraction of the remaining time a doomed analysis keeps,",
                "0 to 1. At 0 it resolves at once; at 1 the duration is",
                "untouched.",
                "Default: 0.3"
        })
        private double timeMultiplier = 0.3;

    }
}
