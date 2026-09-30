package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;

/** Compass tracking settings. */
@SuppressWarnings("FieldMayBeFinal")
public class CompassSettings extends OkaeriConfig {

    @Comment("How holders get and keep their compass.")
    private Obtaining obtaining = new Obtaining();

    @CustomKey("lock-to-inventory")
    @Comment({
            "When true, the hunter compass must stay in the player's inventory and",
            "cannot be dropped or placed in chests. When false, the compass behaves",
            "like a normal item and can be dropped or stored.",
            "The compass always defaults to the last hotbar slot when given, but is",
            "not locked to that slot. The player can freely move it within their",
            "inventory.",
            "Default: true"
    })
    private boolean lockToInventory = true;

    @Comment("Automatic, manual, cycling, and teammate actions.")
    private Actions actions = new Actions();

    @CustomKey("distance-limits")
    @Comment({
            "Per-role tracking distances. The holder's role picks which block",
            "applies. Manually locked targets obey the same limits."
    })
    private DistanceLimits distanceLimits = new DistanceLimits();

    @Comment("Needle signal: interference jams, inaccuracy drifts.")
    private Signal signal = new Signal();

    @Comment("Actionbar, chat, and other compass feedback.")
    private Feedback feedback = new Feedback();

    public Obtaining getObtaining() {
        return obtaining;
    }

    public void setObtaining(Obtaining obtaining) {
        this.obtaining = obtaining;
    }

    public boolean isLockToInventory() {
        return lockToInventory;
    }

    public void setLockToInventory(boolean lockToInventory) {
        this.lockToInventory = lockToInventory;
    }

    public Actions getActions() {
        return actions;
    }

    public void setActions(Actions actions) {
        this.actions = actions;
    }

    public DistanceLimits getDistanceLimits() {
        return distanceLimits;
    }

    public void setDistanceLimits(DistanceLimits distanceLimits) {
        this.distanceLimits = distanceLimits;
    }

    public Signal getSignal() {
        return signal;
    }

    public void setSignal(Signal signal) {
        this.signal = signal;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    @SuppressWarnings("FieldMayBeFinal")
    public static class Obtaining extends OkaeriConfig {

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

        @CustomKey("given-to")
        @Comment({
                "Which roles receive a compass when a match starts (and on respawn).",
                "Hunters track speedrunners; speedrunners track hunters.",
                "Set both to false to disable the compass entirely.",
                "Default: hunters true, speedrunners false"
        })
        private GivenTo givenTo = new GivenTo();

        @CustomKey("drop-on-death")
        @Comment({
                "When true, a compass survives a speedrunner's death as a normal dropped",
                "item.",
                "If a speedrunner gets ahold of the compass, they may track the hunters.",
                "Default: true"
        })
        private Toggle dropOnDeath = new Toggle(true);

        public String getItem() {
            return item;
        }

        public void setItem(String item) {
            this.item = item;
        }

        public GivenTo getGivenTo() {
            return givenTo;
        }

        public void setGivenTo(GivenTo givenTo) {
            this.givenTo = givenTo;
        }

        public Toggle getDropOnDeath() {
            return dropOnDeath;
        }

        public void setDropOnDeath(Toggle dropOnDeath) {
            this.dropOnDeath = dropOnDeath;
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    public static class Actions extends OkaeriConfig {

        @Comment("Scheduled automatic refreshes.")
        private Auto auto = new Auto();

        @Comment("Right-click manual refreshes and their analysis.")
        private Manual manual = new Manual();

        @CustomKey("target-cycling")
        private LeftClick targetCycling = new LeftClick();

        @CustomKey("teammates")
        private Teammates teammates = new Teammates();

        public Auto getAuto() {
            return auto;
        }

        public void setAuto(Auto auto) {
            this.auto = auto;
        }

        public Manual getManual() {
            return manual;
        }

        public void setManual(Manual manual) {
            this.manual = manual;
        }

        public LeftClick getTargetCycling() {
            return targetCycling;
        }

        public void setTargetCycling(LeftClick targetCycling) {
            this.targetCycling = targetCycling;
        }

        public Teammates getTeammates() {
            return teammates;
        }

        public void setTeammates(Teammates teammates) {
            this.teammates = teammates;
        }

        @SuppressWarnings("FieldMayBeFinal")
        public static class Auto extends OkaeriConfig {

            @Comment({"When true, the compass refreshes on its own clock.", "Default: true"})
            private boolean enabled = true;

            @CustomKey("interval")
            @Comment({
                    "Seconds between automatic target/location refreshes.",
                    "Minimum: 0 (always due).",
                    "Default: 35.0"
            })
            private double interval = 35.0;

            @CustomKey("deviation")
            @Comment({
                    "Random plus-or-minus jitter applied to the interval per",
                    "refresh. Capped at the interval itself.",
                    "Minimum: 0. Maximum: the interval.",
                    "Default: 0.0"
            })
            private double deviation = 0.0;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public double getInterval() {
                return interval;
            }

            public void setInterval(double interval) {
                this.interval = interval;
            }

            public double getDeviation() {
                return deviation;
            }

            public void setDeviation(double deviation) {
                this.deviation = deviation;
            }
        }

        @SuppressWarnings("FieldMayBeFinal")
        public static class Manual extends OkaeriConfig {

            @Comment({
                    "Optional refresh-on-right-click runs alongside automatic refreshes.",
                    "Default: true"
            })
            private boolean enabled = true;

            @CustomKey("cooldown")
            @Comment({
                    "Seconds between accepted refresh clicks. Set to -1 for no",
                    "cooldown. Left-click and shift-left-click use their own",
                    "throttles and never touch this.",
                    "Default: 3.0"
            })
            private double cooldown = 3.0;

            @Comment({
                    "Purposeful lag before a compass refresh resolves, showing",
                    "\"Analyzing...\" while it runs. No second refresh starts mid-analysis,",
                    "and click cooldowns restart when the analysis ends."
            })
            private CompassAnalysisSettings analysis = new CompassAnalysisSettings();

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public double getCooldown() {
                return cooldown;
            }

            public void setCooldown(double cooldown) {
                this.cooldown = cooldown;
            }

            public CompassAnalysisSettings getAnalysis() {
                return analysis;
            }

            public void setAnalysis(CompassAnalysisSettings analysis) {
                this.analysis = analysis;
            }
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    public static class DistanceLimits extends OkaeriConfig {

        private Tracker hunter = new Tracker();

        @Comment("Same as hunter, but for the opposite role.")
        private Tracker speedrunner = new Tracker();

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
    }

    @SuppressWarnings("FieldMayBeFinal")
    public static class Signal extends OkaeriConfig {

        @CustomKey("interference")
        @Comment({
                "Signal interference: when enabled, tracking can fail with a Bad",
                "signal readout when the interference options say the signal is bad.",
                "The signal is always good unless an option below says otherwise.",
                "Works best with automatic refreshes off, analysis on, and",
                "right-click refreshes on.",
                "Default: false"
        })
        private SignalInterferenceSettings interference = new SignalInterferenceSettings();

        public SignalInterferenceSettings getInterference() {
            return interference;
        }

        public void setInterference(SignalInterferenceSettings interference) {
            this.interference = interference;
        }
    }

    @SuppressWarnings("FieldMayBeFinal")
    public static class Feedback extends OkaeriConfig {

        @Comment("Actionbar push rate and distance delta.")
        private CompassActionbarSettings actionbar = new CompassActionbarSettings();

        @CustomKey("chat-messages")
        private ChatMessages chatMessages = new ChatMessages();

        public CompassActionbarSettings getActionbar() {
            return actionbar;
        }

        public void setActionbar(CompassActionbarSettings actionbar) {
            this.actionbar = actionbar;
        }

        public ChatMessages getChatMessages() {
            return chatMessages;
        }

        public void setChatMessages(ChatMessages chatMessages) {
            this.chatMessages = chatMessages;
        }
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

    /** Left-click manual target lock. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class LeftClick extends OkaeriConfig {

        @Comment({
                "When true, left-clicking the compass cycles a manual target lock",
                "through the nearest candidates (live opponents first, then",
                "last-seen locations). While locked, the actionbar shows LOCKED and",
                "automatic refreshes keep pointing at the locked target. Cycling",
                "past the last candidate returns to automatic tracking. Cycling",
                "reads only the snapshot cache written by refreshes: it never",
                "fetches a live position and never touches the refresh cooldown.",
                "Uncached targets show a reasonless Bad Signal, and with one or",
                "fewer candidates the click quits silently. Scrolls are refused",
                "during analysis.",
                "Default: true"
        })
        private boolean enabled = true;

        @CustomKey("max-targets")
        @Comment({
                "How many nearest candidates the lock cycles through at most.",
                "Also caps the per-role snapshot cache each refresh writes.",
                "Minimum: 1, maximum: 20.",
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

    /** Shift-left-click teammate tracking toggle. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Teammates extends OkaeriConfig {

        @Comment({
                "When true, shift-left-clicking the compass toggles teammate",
                "tracking instead of cycling a manual lock: teammates mode",
                "tracks same-role players, enemy mode tracks the other role.",
                "When false, shift-left-click locks exactly like left-click.",
                "Default: true"
        })
        private boolean enabled = true;

        @CustomKey("switch-cooldown")
        @Comment({
                "Seconds between accepted teammate switches. Switches inside",
                "the window are ignored silently, which also stops a held",
                "click from toggling. Set to 0 for no throttling. This never",
                "touches the refresh cooldown and never fetches on expiry.",
                "Default: 0.5"
        })
        private double switchCooldown = 0.5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getSwitchCooldown() {
            return switchCooldown;
        }

        public void setSwitchCooldown(double switchCooldown) {
            this.switchCooldown = switchCooldown;
        }
    }

    /** Compass action chat messages. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class ChatMessages extends OkaeriConfig {

        @Comment({
                "When true, compass actions chat the holder: lock confirmations,",
                "teammate mode switches, and locked-target deaths. Refused or",
                "silent outcomes stay silent.",
                "Default: true"
        })
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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