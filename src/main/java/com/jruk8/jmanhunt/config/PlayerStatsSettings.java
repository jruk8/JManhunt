package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.compass.SignalInterference;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;

/** Health, hunger, and experience interference. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class PlayerStatsSettings extends OkaeriConfig {
    private Health health = new Health();
    private Hunger hunger = new Hunger();
    private Experience experience = new Experience();

    /** Health interference. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Health extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("min-health")
        @Comment({
                "Minimum health in health points, 1 to 100. Vanilla full",
                "health is 20; higher values cover boosted maxima.",
                "Default: 8"
        })
        private int minHealth = 8;

        @CustomKey("check-on")
        @Comment({
                "Which sides' health must pass: SELF checks the holder,",
                "TARGET checks the tracked target, BOTH checks each.",
                "Default: SELF"
        })
        private SignalInterference.CheckOn checkOn = SignalInterference.CheckOn.SELF;

    }

    /** Hunger interference. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Hunger extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("min-hunger")
        @Comment({
                "Minimum hunger bar level, 1 to 20.",
                "Default: 10"
        })
        private int minHunger = 10;

        @CustomKey("check-on")
        @Comment({
                "Which sides' hunger must pass: SELF checks the holder,",
                "TARGET checks the tracked target, BOTH checks each.",
                "Default: SELF"
        })
        private SignalInterference.CheckOn checkOn = SignalInterference.CheckOn.SELF;

    }

    /** Experience interference. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Experience extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("min-exp-level")
        @Comment({
                "Minimum experience level, 1 to 100.",
                "Default: 5"
        })
        private int minExpLevel = 5;

        @CustomKey("check-on")
        @Comment({
                "Which sides' level must pass: SELF checks the holder,",
                "TARGET checks the tracked target, BOTH checks each.",
                "Default: SELF"
        })
        private SignalInterference.CheckOn checkOn = SignalInterference.CheckOn.SELF;

    }
}
