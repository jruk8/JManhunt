package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;

/** Compass tracking settings. */
@SuppressWarnings("FieldMayBeFinal")
public class CompassSettings extends OkaeriConfig {

    @CustomKey("given-to")
    @Comment({
            "Which roles receive a compass when a match starts (and on respawn).",
            "Hunters track speedrunners; speedrunners track hunters.",
            "Set both to false to disable the compass entirely.",
            "Default: hunters true, speedrunners false"
    })
    private GivenTo givenTo = new GivenTo();

    @CustomKey("item")
    @Comment({
            "The item handed out as the tracking compass, in modern",
            "minecraft:material_name format (the minecraft: namespace may be",
            "omitted). Try \"clock\" or \"recovery_compass\" for a different look.",
            "Anything unknown, or anything with placement functionality such as",
            "dirt, signs, or redstone, falls back to \"compass\".",
            "Default: compass"
    })
    private String item = "compass";

    @CustomKey("must-be-inventory")
    @Comment({
            "When true, the hunter compass must stay in the player's inventory and",
            "cannot be dropped or placed in chests. When false, the compass behaves",
            "like a normal item and can be dropped or stored.",
            "The compass always defaults to the last hotbar slot when given, but is",
            "not locked to that slot. The player can freely move it within their",
            "inventory.",
            "Default: true"
    })
    private Toggle mustBeInventory = new Toggle(true);

    @CustomKey("drop-on-death")
    @Comment({
            "When true, a compass survives a speedrunner's death as a normal dropped",
            "item.",
            "If a speedrunner gets ahold of the compass, they may track the hunters.",
            "Default: true"
    })
    private Toggle dropOnDeath = new Toggle(true);

    @CustomKey("refresh-interval")
    @Comment({
            "Seconds between compass target/location refreshes. Set to -1 to disable",
            "scheduled refreshes (in that case use right-click refresh or the compass",
            "NEVER refreshes); right-click refreshes can still be used separately."
    })
    private double refreshInterval = 10.0;

    @CustomKey("right-click")
    private RightClick rightClick = new RightClick();

    @Comment("Cooldown shared by left-click and right-click compass actions.")
    private Click click = new Click();

    @CustomKey("left-click")
    private LeftClick leftClick = new LeftClick();

    @Comment({
            "Purposeful lag before a compass refresh resolves, showing",
            "\"Analyzing...\" while it runs. No second refresh starts mid-analysis,",
            "and click cooldowns restart when the analysis ends."
    })
    private Analyze analyze = new Analyze();

    @Comment({
            "Per-role tracking distances. The holder's role picks which block",
            "applies. Manually locked targets obey the same limits."
    })
    private Tracker hunter = new Tracker();

    @Comment("Same as hunter, but for the opposite role.")
    private Tracker speedrunner = new Tracker();

    @CustomKey("signal-interference")
    @Comment({
            "Signal interference: when enabled, tracking can fail with a Bad",
            "signal readout when the interference options say the signal is bad.",
            "The signal is always good unless an option below says otherwise.",
            "Default: false"
    })
    private SignalInterferenceSettings signalInterference = new SignalInterferenceSettings();

    public GivenTo getGivenTo() {
        return givenTo;
    }

    public void setGivenTo(GivenTo givenTo) {
        this.givenTo = givenTo;
    }

    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    public Toggle getMustBeInventory() {
        return mustBeInventory;
    }

    public void setMustBeInventory(Toggle mustBeInventory) {
        this.mustBeInventory = mustBeInventory;
    }

    public Toggle getDropOnDeath() {
        return dropOnDeath;
    }

    public void setDropOnDeath(Toggle dropOnDeath) {
        this.dropOnDeath = dropOnDeath;
    }

    public double getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(double refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public RightClick getRightClick() {
        return rightClick;
    }

    public void setRightClick(RightClick rightClick) {
        this.rightClick = rightClick;
    }

    public Click getClick() {
        return click;
    }

    public void setClick(Click click) {
        this.click = click;
    }

    public LeftClick getLeftClick() {
        return leftClick;
    }

    public void setLeftClick(LeftClick leftClick) {
        this.leftClick = leftClick;
    }

    public Analyze getAnalyze() {
        return analyze;
    }

    public void setAnalyze(Analyze analyze) {
        this.analyze = analyze;
    }

    public Tracker getHunter() {
        return hunter;
    }

    public void setHunter(Tracker hunter) {
        this.hunter = hunter;
    }

    public Tracker getSpeedrunner() {
        return speedrunner;
    }

    public void setSpeedrunner(Tracker speedrunner) {
        this.speedrunner = speedrunner;
    }

    public SignalInterferenceSettings getSignalInterference() {
        return signalInterference;
    }

    public void setSignalInterference(SignalInterferenceSettings signalInterference) {
        this.signalInterference = signalInterference;
    }

    /** Which roles receive a compass. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class GivenTo extends OkaeriConfig {
        private boolean hunters = true;
        private boolean speedrunners = false;

        public boolean isHunters() {
            return hunters;
        }

        public void setHunters(boolean hunters) {
            this.hunters = hunters;
        }

        public boolean isSpeedrunners() {
            return speedrunners;
        }

        public void setSpeedrunners(boolean speedrunners) {
            this.speedrunners = speedrunners;
        }
    }

    /** Right-click refresh behavior. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class RightClick extends OkaeriConfig {

        @CustomKey("refresh-on-right-click")
        @Comment("Optional refresh-on-right-click runs alongside interval refreshes.")
        private boolean refreshOnRightClick = true;

        public boolean isRefreshOnRightClick() {
            return refreshOnRightClick;
        }

        public void setRefreshOnRightClick(boolean refreshOnRightClick) {
            this.refreshOnRightClick = refreshOnRightClick;
        }
    }

    /** Cooldown shared by left-click and right-click compass actions. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Click extends OkaeriConfig {

        @CustomKey("click-cooldown")
        @Comment("Seconds between accepted compass clicks. Set to -1 for no cooldown.")
        private double clickCooldown = 3.0;

        public double getClickCooldown() {
            return clickCooldown;
        }

        public void setClickCooldown(double clickCooldown) {
            this.clickCooldown = clickCooldown;
        }
    }

    /** Left-click manual target lock. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class LeftClick extends OkaeriConfig {

        @Comment({
                "When true, left-clicking the compass cycles a manual target lock",
                "through the nearest candidates (live opponents first, then",
                "last-seen locations). While locked, the actionbar shows LOCKED and",
                "automatic refreshes keep pointing at the locked target. Cycling",
                "past the last candidate returns to automatic tracking. Locking",
                "needs at least two candidates, and is refused during bad signal",
                "and during analysis.",
                "Default: true"
        })
        private boolean enabled = true;

        @CustomKey("max-targets")
        @Comment({
                "How many nearest candidates the lock cycles through at most.",
                "Minimum: 1.",
                "Default: 5"
        })
        private int maxTargets = 5;

        @CustomKey("scroll-cooldown")
        @Comment({
                "Seconds between manual target scrolls. Clicks inside the window",
                "are ignored, which also stops a held click from scrolling.",
                "Set to 0 for no throttling.",
                "Default: 0.5"
        })
        private double scrollCooldown = 0.5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxTargets() {
            return maxTargets;
        }

        public void setMaxTargets(int maxTargets) {
            this.maxTargets = maxTargets;
        }

        public double getScrollCooldown() {
            return scrollCooldown;
        }

        public void setScrollCooldown(double scrollCooldown) {
            this.scrollCooldown = scrollCooldown;
        }
    }

    /** Refresh analysis lag and debuffs. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Analyze extends OkaeriConfig {

        @CustomKey("right-click")
        @Comment({
                "Applies to right-click refreshes.",
                "Default: false"
        })
        private boolean rightClick = false;

        @Comment({
                "Applies to automatic (interval) refreshes.",
                "Default: false"
        })
        private boolean auto = false;

        @CustomKey("delay-seconds")
        @Comment({
                "Seconds the analysis takes before the compass updates.",
                "Default: 1.0"
        })
        private double delaySeconds = 1.0;

        @CustomKey("delay-deviation-seconds")
        @Comment({
                "Random plus-or-minus jitter applied to delay-seconds per",
                "analysis. Capped at the delay itself.",
                "Default: 0.0"
        })
        private double delayDeviationSeconds = 0.0;

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

        public boolean isRightClick() {
            return rightClick;
        }

        public void setRightClick(boolean rightClick) {
            this.rightClick = rightClick;
        }

        public boolean isAuto() {
            return auto;
        }

        public void setAuto(boolean auto) {
            this.auto = auto;
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
    }

    /** Per-role tracking distances. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Tracker extends OkaeriConfig {

        @CustomKey("min-distance")
        @Comment({
                "When true, the compass stops tracking when the target is within",
                "the configured flat distance. Only X/Z coordinates are compared,",
                "so the target may still be above or below the holder.",
                "Default: true"
        })
        private Limit minDistance = new Limit(true, 25.0);

        @CustomKey("max-distance")
        @Comment({
                "When true, the compass stops tracking when the target is beyond",
                "the configured distance and shows an out-of-range actionbar.",
                "Set distance to -1 for unlimited distance.",
                "Default: true"
        })
        private MaxLimit maxDistance = new MaxLimit(true, -1.0);

        public Limit getMinDistance() {
            return minDistance;
        }

        public void setMinDistance(Limit minDistance) {
            this.minDistance = minDistance;
        }

        public MaxLimit getMaxDistance() {
            return maxDistance;
        }

        public void setMaxDistance(MaxLimit maxDistance) {
            this.maxDistance = maxDistance;
        }

        /** Minimum tracking distance. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class Limit extends OkaeriConfig {
            private boolean enabled;

            @Comment({
                    "The flat distance in blocks at which the compass stops tracking.",
                    "Default: 25"
            })
            private double distance;

            public Limit() {
                this(true, 25.0);
            }

            public Limit(boolean enabled, double distance) {
                this.enabled = enabled;
                this.distance = distance;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public double getDistance() {
                return distance;
            }

            public void setDistance(double distance) {
                this.distance = distance;
            }
        }

        /** Maximum tracking distance. */
        @SuppressWarnings("FieldMayBeFinal")
        public static class MaxLimit extends OkaeriConfig {
            private boolean enabled;

            @Comment({
                    "Maximum distance in blocks at which the compass can track.",
                    "Default: -1"
            })
            private double distance;

            public MaxLimit() {
                this(true, -1.0);
            }

            public MaxLimit(boolean enabled, double distance) {
                this.enabled = enabled;
                this.distance = distance;
            }

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public double getDistance() {
                return distance;
            }

            public void setDistance(double distance) {
                this.distance = distance;
            }
        }
    }

}