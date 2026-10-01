package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;

/** Alternate win conditions per side, plus cancel conditions. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class WinConditionsSettings extends OkaeriConfig {

    @Comment("Conditions the speedrunners can win with.")
    private SpeedrunnerWin speedrunner = new SpeedrunnerWin();

    @Comment("Conditions the hunters can win with.")
    private HunterWin hunter = new HunterWin();

    @Comment("Conditions that cancel the match instead of crowning a winner.")
    private CancelWin cancel = new CancelWin();

    /** Speedrunner win conditions. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class SpeedrunnerWin extends OkaeriConfig {

        @CustomKey("exit-end")
        @Comment("Speedrunners win by entering the End and returning to the Overworld.")
        private Toggle exitEnd = new Toggle(true);

        @CustomKey("survive-time")
        @Comment("Speedrunners win by surviving for the configured time.")
        private SurviveTime surviveTime = new SurviveTime();

        @CustomKey("acquire-item")
        @Comment("Speedrunners win by acquiring the configured item.")
        private AcquireItem acquireItem = new AcquireItem();

        @CustomKey("reach-advancement")
        @Comment("Speedrunners win by completing the configured advancement.")
        private ReachAdvancement reachAdvancement = new ReachAdvancement();

        @CustomKey("kill-mob")
        @Comment("Speedrunners win when a speedrunner kills the configured mob.")
        private KillMob killMob = new KillMob();

        /** Survive-for-time condition. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class SurviveTime extends OkaeriConfig {
            private boolean enabled = false;

            @Comment({
                    "Time in seconds the speedrunner(s) must survive.",
                    "Default: 3600.0"
            })
            private double time = 3600.0;

        }

        /** Acquire-item condition. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class AcquireItem extends OkaeriConfig {
            private boolean enabled = false;

            @Comment({
                    "The item to acquire (namespaced key, e.g. minecraft:netherite_ingot).",
                    "Default: minecraft:netherite_ingot"
            })
            private String item = "minecraft:netherite_ingot";

        }

        /** Reach-advancement condition. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class ReachAdvancement extends OkaeriConfig {
            private boolean enabled = false;

            @Comment({
                    "The advancement to reach (namespaced key, e.g. minecraft:story/enter_the_nether).",
                    "Default: minecraft:story/enter_the_nether"
            })
            private String advancement = "minecraft:story/enter_the_nether";

        }

        /** Kill-mob condition. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class KillMob extends OkaeriConfig {
            private boolean enabled = false;

            @Comment({
                    "The mob to kill (namespaced key, e.g. minecraft:wither).",
                    "Default: minecraft:wither"
            })
            private String mob = "minecraft:wither";

        }
    }

    /** Hunter win conditions. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class HunterWin extends OkaeriConfig {

        @CustomKey("survive-time")
        @Comment("Hunters win when the time expires before the speedrunners win.")
        private SurviveTime surviveTime = new SurviveTime();

        @CustomKey("acquire-item")
        @Comment("Hunters win when a hunter acquires the configured item.")
        private AcquireItem acquireItem = new AcquireItem();

        @CustomKey("reach-advancement")
        @Comment("Hunters win by completing the configured advancement.")
        private ReachAdvancement reachAdvancement = new ReachAdvancement();

        @CustomKey("kill-mob")
        @Comment("Hunters win when a hunter kills the configured mob.")
        private KillMob killMob = new KillMob();

        /** Expiry clock in seconds. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class SurviveTime extends OkaeriConfig {
            private boolean enabled = false;
            private double time = 3600.0;

        }

        /** Acquire-item condition. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class AcquireItem extends OkaeriConfig {
            private boolean enabled = false;
            private String item = "minecraft:netherite_ingot";

        }

        /** Reach-advancement condition. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class ReachAdvancement extends OkaeriConfig {
            private boolean enabled = false;
            private String advancement = "minecraft:story/enter_the_nether";

        }

        /** Kill-mob condition. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class KillMob extends OkaeriConfig {
            private boolean enabled = false;
            private String mob = "minecraft:ender_dragon";

        }
    }

    /** Cancel conditions. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class CancelWin extends OkaeriConfig {

        @CustomKey("survived-time")
        @Comment("The match is cancelled once the configured time expires.")
        private SurvivedTime survivedTime = new SurvivedTime();

        /** Cancel clock in seconds. */
        @Getter
        @Setter
        @SuppressWarnings("FieldMayBeFinal")
        public static class SurvivedTime extends OkaeriConfig {
            private boolean enabled = true;

            @Comment({
                    "Time in seconds before the match is cancelled.",
                    "Default: 28800.0 (8 hours)"
            })
            private double time = 28800.0;

        }
    }
}
