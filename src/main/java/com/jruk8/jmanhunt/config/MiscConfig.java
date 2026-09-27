package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;

/** Power-user toggles that rarely need changing. */
@SuppressWarnings("FieldMayBeFinal")
public class MiscConfig extends OkaeriConfig {

    @Comment("Advanced server interop toggles.")
    private Interop interop = new Interop();

    @Comment({
            "Purposeful lag before a compass refresh resolves, showing",
            "\"Analyzing...\" while it runs. No second refresh starts mid-analysis,",
            "and click cooldowns restart when the analysis ends."
    })
    private Analyze analyze = new Analyze();

    @Comment("Debug display toggles.")
    private Debug debug = new Debug();

    public Interop getInterop() {
        return interop;
    }

    public void setInterop(Interop interop) {
        this.interop = interop;
    }

    public Analyze getAnalyze() {
        return analyze;
    }

    public void setAnalyze(Analyze analyze) {
        this.analyze = analyze;
    }

    public Debug getDebug() {
        return debug;
    }

    public void setDebug(Debug debug) {
        this.debug = debug;
    }

    /** Advanced server interop toggles. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Interop extends OkaeriConfig {

        @CustomKey("disable-worldedit-navwand")
        @Comment({
                "When true, compass clicks never trigger WorldEdit's navwand",
                "teleport. The navwand is a conflicting feature with the compass",
                "affecting OP'd users. This option fixes it.",
                "Purely passive, requires no dependency. If no WorldEdit is installed,",
                "feel free to leave off.",
                "Note: this also blocks breaking blocks with a compass in hand.",
                "To avoid that, configure the WorldEdit plugin navwand item to",
                "something other than the compass and turn this setting off.",
                "",
                "Performance impact: none",
                "Default: true"
        })
        private boolean disableWorldeditNavwand = true;

        public boolean isDisableWorldeditNavwand() {
            return disableWorldeditNavwand;
        }

        public void setDisableWorldeditNavwand(boolean disableWorldeditNavwand) {
            this.disableWorldeditNavwand = disableWorldeditNavwand;
        }

        @CustomKey("validate-modifier-editor-commands")
        @Comment({
                "When true, the modifier editor rejects command lines with",
                "unknown root commands or unknown give items. Placeholder",
                "checks always run either way.",
                "Default: true"
        })
        private boolean validateModifierEditorCommands = true;

        public boolean isValidateModifierEditorCommands() {
            return validateModifierEditorCommands;
        }

        public void setValidateModifierEditorCommands(boolean validateModifierEditorCommands) {
            this.validateModifierEditorCommands = validateModifierEditorCommands;
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

    /** Debug display toggles. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Debug extends OkaeriConfig {

        @CustomKey("show-ids")
        @Comment({
                "Show lobby and game ids as L{lobby}|G{game}.",
                "Useful for debugging.",
                "Default: false"
        })
        private boolean showIds = false;

        public boolean isShowIds() {
            return showIds;
        }

        public void setShowIds(boolean showIds) {
            this.showIds = showIds;
        }
    }
}
