package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;

/** Refresh analysis lag and debuffs. */
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
            "Default: 6.0"
    })
    private double delaySeconds = 6.0;

    @CustomKey("delay-deviation-seconds")
    @Comment({
            "Random plus-or-minus jitter applied to delay-seconds per",
            "analysis. Capped at the delay itself.",
            "Default: 4.0"
    })
    private double delayDeviationSeconds = 4.0;

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

    @CustomKey("cancel-immediate")
    @Comment({
            "Shortens analyses that are already doomed to fail, so a bad",
            "signal lands sooner instead of after the full delay.",
            "Default: true"
    })
    private CancelImmediate cancelImmediate = new CancelImmediate();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(double delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public double getDelayDeviationSeconds() {
        return delayDeviationSeconds;
    }

    public void setDelayDeviationSeconds(double delayDeviationSeconds) {
        this.delayDeviationSeconds = delayDeviationSeconds;
    }

    public double getSoundIntervalSeconds() {
        return soundIntervalSeconds;
    }

    public void setSoundIntervalSeconds(double soundIntervalSeconds) {
        this.soundIntervalSeconds = soundIntervalSeconds;
    }

    public Debuffs getDebuffs() {
        return debuffs;
    }

    public void setDebuffs(Debuffs debuffs) {
        this.debuffs = debuffs;
    }

    public Cost getCost() {
        return cost;
    }

    public void setCost(Cost cost) {
        this.cost = cost;
    }

    public CancelImmediate getCancelImmediate() {
        return cancelImmediate;
    }

    public void setCancelImmediate(CancelImmediate cancelImmediate) {
        this.cancelImmediate = cancelImmediate;
    }

    /** Analysis console commands. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Debuffs extends OkaeriConfig {
        private boolean enabled = true;
        private DebuffCommands commands = new DebuffCommands();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public DebuffCommands getCommands() {
            return commands;
        }

        public void setCommands(DebuffCommands commands) {
            this.commands = commands;
        }

        /** Command lists per audience. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class DebuffCommands extends OkaeriConfig {
            private List<String> player = new ArrayList<>(List.of(
                    "effect give <p> minecraft:slowness <duration> 1 true"));
            private List<String> speedrunner = new ArrayList<>();
            private List<String> hunter = new ArrayList<>();

            public List<String> getPlayer() {
                return player;
            }

            public void setPlayer(List<String> player) {
                this.player = player;
            }

            public List<String> getSpeedrunner() {
                return speedrunner;
            }

            public void setSpeedrunner(List<String> speedrunner) {
                this.speedrunner = speedrunner;
            }

            public List<String> getHunter() {
                return hunter;
            }

            public void setHunter(List<String> hunter) {
                this.hunter = hunter;
            }
        }
    }

    /** Analysis charges. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Cost extends OkaeriConfig {
        @Comment({
                "When true, analyses charge the holder.",
                "Default: false"
        })
        private boolean enabled = false;

        @CustomKey("cost-on")
        @Comment({
                "When to charge: INITIATE at press, SUCCESS at resolution.",
                "List either or both.",
                "Default: INITIATE"
        })
        private List<String> costOn = new ArrayList<>(List.of("INITIATE"));

        @Comment("One container per charge; each toggles separately.")
        private Payment payment = new Payment();

        @CustomKey("poverty-behavior")
        private PovertyBehavior povertyBehavior = new PovertyBehavior();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getCostOn() {
            return costOn;
        }

        public void setCostOn(List<String> costOn) {
            this.costOn = costOn;
        }

        public Payment getPayment() {
            return payment;
        }

        public void setPayment(Payment payment) {
            this.payment = payment;
        }

        public PovertyBehavior getPovertyBehavior() {
            return povertyBehavior;
        }

        public void setPovertyBehavior(PovertyBehavior povertyBehavior) {
            this.povertyBehavior = povertyBehavior;
        }

        /** One container per charge. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class Payment extends OkaeriConfig {
            private Saturation saturation = new Saturation();
            private Health health = new Health();

            @CustomKey("exp-level")
            private ExpLevel expLevel = new ExpLevel();

            public Saturation getSaturation() {
                return saturation;
            }

            public void setSaturation(Saturation saturation) {
                this.saturation = saturation;
            }

            public Health getHealth() {
                return health;
            }

            public void setHealth(Health health) {
                this.health = health;
            }

            public ExpLevel getExpLevel() {
                return expLevel;
            }

            public void setExpLevel(ExpLevel expLevel) {
                this.expLevel = expLevel;
            }

            /** Saturation charge. */
            @SuppressWarnings("FieldMayBeFinal")
            public static class Saturation extends OkaeriConfig {
                private boolean enabled = true;

                @CustomKey("value")
                @Comment({
                        "Points drained from the 0-40 hunger pool: hidden",
                        "saturation first, then the visible hunger bar.",
                        "Minimum: 1. Maximum: 40.",
                        "Default: 3"
                })
                private int value = 3;

                public boolean isEnabled() {
                    return enabled;
                }

                public void setEnabled(boolean enabled) {
                    this.enabled = enabled;
                }

                public int getValue() {
                    return value;
                }

                public void setValue(int value) {
                    this.value = value;
                }
            }

            /** Health charge. */
            @SuppressWarnings("FieldMayBeFinal")
            public static class Health extends OkaeriConfig {
                private boolean enabled = true;

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

                public boolean isEnabled() {
                    return enabled;
                }

                public void setEnabled(boolean enabled) {
                    this.enabled = enabled;
                }

                public int getValue() {
                    return value;
                }

                public void setValue(int value) {
                    this.value = value;
                }

                public boolean isCanKill() {
                    return canKill;
                }

                public void setCanKill(boolean canKill) {
                    this.canKill = canKill;
                }
            }

            /** Experience charge. */
            @SuppressWarnings("FieldMayBeFinal")
            public static class ExpLevel extends OkaeriConfig {
                private boolean enabled = true;

                @CustomKey("value")
                @Comment({
                        "Experience levels drained, 1 to 100.",
                        "Default: 1"
                })
                private int value = 1;

                public boolean isEnabled() {
                    return enabled;
                }

                public void setEnabled(boolean enabled) {
                    this.enabled = enabled;
                }

                public int getValue() {
                    return value;
                }

                public void setValue(int value) {
                    this.value = value;
                }
            }
        }

        /** What happens when the holder cannot pay. */
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

            public boolean isCancelWhenPoor() {
                return cancelWhenPoor;
            }

            public void setCancelWhenPoor(boolean cancelWhenPoor) {
                this.cancelWhenPoor = cancelWhenPoor;
            }

            public boolean isShowReason() {
                return showReason;
            }

            public void setShowReason(boolean showReason) {
                this.showReason = showReason;
            }
        }
    }

    /** Shorter waits for doomed analyses. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class CancelImmediate extends OkaeriConfig {

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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getTimeMultiplier() {
            return timeMultiplier;
        }

        public void setTimeMultiplier(double timeMultiplier) {
            this.timeMultiplier = timeMultiplier;
        }
    }
}
