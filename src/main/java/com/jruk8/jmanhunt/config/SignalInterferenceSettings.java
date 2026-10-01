package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.compass.SignalInterference;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** Signal interference options. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class SignalInterferenceSettings extends OkaeriConfig {
    private boolean enabled = true;

    @CustomKey("required-to-fail")
    @Comment({
            "How many enabled options must interfere before tracking fails.",
            "Clamped to the enabled option count, minimum 1.",
            "Default: 1"
    })
    private int requiredToFail = 1;

    @CustomKey("chance-to-bypass")
    @Comment({
            "Chance from 0.0 to 1.0 that a bad signal still tracks anyway.",
            "Default: 0.0"
    })
    private double chanceToBypass = 0.0;

    @CustomKey("show-reason-in-actionbar")
    @Comment({
            "When true, a bad signal names its cause in the actionbar.",
            "Default: true"
    })
    private boolean showReasonInActionbar = true;

    @CustomKey("light-level")
    @Comment({
            "Light at the holder's feet. Only considered in the overworld;",
            "skipped in the nether and the end.",
            "Default: false"
    })
    private LightLevel lightLevel = new LightLevel();

    @Comment({
            "Cover above the holder's head. Counts solid blocks strictly above",
            "the feet block; interferes when the count passes the maximum.",
            "Default: false"
    })
    private Underground underground = new Underground();

    @Comment({
            "Water above the holder. Only applies with water feet; then",
            "counts fluid blocks strictly above the feet block and interferes",
            "when the count passes the maximum.",
            "Default: false"
    })
    private Underwater underwater = new Underwater();

    @Comment({
            "Height band with good signal. Interferes outside min-y to max-y.",
            "Default: false"
    })
    private Altitude altitude = new Altitude();

    @Comment({
            "Weather that interferes: STORM, RAIN, CLEAR.",
            "Default: false"
    })
    private Weather weather = new Weather();

    @Comment({
            "Biomes with bad signal, as full keys like minecraft:desert.",
            "Default: false"
    })
    private Biome biome = new Biome();

    @Comment({
            "Movement since the refresh started. Fails when the refresher",
            "moved more than the threshold from their press spot; only",
            "fires on the analysis path, since instant refreshes have no",
            "gap to move in.",
            "Default: false"
    })
    private Movement movement = new Movement();

    @CustomKey("line-of-sight")
    @Comment({
            "Line of sight between holder and target. Raycasts one eye-to-eye",
            "ray; glass, leaves, and other non-whole blocks never block it.",
            "Only live targets in the same world are evaluated.",
            "Default: false"
    })
    private LineOfSight lineOfSight = new LineOfSight();

    @Comment({
            "Invisibility of either side. The check-on key picks whose",
            "invisibility interferes.",
            "Default: true"
    })
    private Invisible invisible = new Invisible();

    @CustomKey("player-stats")
    @Comment({
            "Health, hunger, and experience of either side.",
            "Default: false"
    })
    private PlayerStatsSettings playerStats = new PlayerStatsSettings();

    /** Light-level interference. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class LightLevel extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("min-sky-light")
        @Comment({
                "Minimum sky light, 0 to 15.",
                "Default: 10"
        })
        private int minSkyLight = 10;

        @CustomKey("min-block-light")
        @Comment({
                "Minimum block light, 0 to 15.",
                "Default: 5"
        })
        private int minBlockLight = 5;

        @CustomKey("interfere-when")
        @Comment({
                "ONE_UNMET interferes when either reading is below its minimum;",
                "BOTH_UNMET only when both are.",
                "Default: BOTH_UNMET"
        })
        private SignalInterference.InterfereWhen interfereWhen =
                SignalInterference.InterfereWhen.BOTH_UNMET;

        @CustomKey("check-on")
        @Comment({
                "Which sides' light must pass: SELF checks the holder,",
                "TARGET checks the tracked target, BOTH checks each.",
                "Default: SELF"
        })
        private SignalInterference.CheckOn checkOn = SignalInterference.CheckOn.SELF;

    }

    /** Underground cover interference. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Underground extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("max-blocks-above")
        @Comment({
                "Maximum solid blocks above before interference, 1 to 380.",
                "Default: 3"
        })
        private int maxBlocksAbove = 3;

        @CustomKey("ignore-transparent")
        @Comment({
                "When true, glass, leaves, and other non-whole blocks are not",
                "counted. Set to false to count every solid block.",
                "Default: true"
        })
        private boolean ignoreTransparent = true;

        @CustomKey("check-on")
        @Comment({
                "Which sides' cover must pass: SELF checks the holder,",
                "TARGET checks the tracked target, BOTH checks each.",
                "Default: BOTH"
        })
        private SignalInterference.CheckOn checkOn = SignalInterference.CheckOn.BOTH;

    }

    /** Underwater fluid interference. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Underwater extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("max-blocks-above")
        @Comment({
                "Maximum fluid blocks above before interference, 1 to 380.",
                "Only checked with water feet.",
                "Default: 2"
        })
        private int maxBlocksAbove = 2;

        @CustomKey("check-on")
        @Comment({
                "Which sides' water must pass: SELF checks the holder,",
                "TARGET checks the tracked target, BOTH checks each.",
                "Default: BOTH"
        })
        private SignalInterference.CheckOn checkOn = SignalInterference.CheckOn.BOTH;

    }

    /** Altitude band interference. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Altitude extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("min-y")
        @Comment({
                "Both from -64 to 319. Order does not matter.",
                "Default: -20"
        })
        private int minY = -20;

        @CustomKey("max-y")
        @Comment("Default: 120")
        private int maxY = 120;

        @CustomKey("check-on")
        @Comment({
                "Which sides' altitude must pass: SELF checks the holder,",
                "TARGET checks the tracked target, BOTH checks each.",
                "Default: BOTH"
        })
        private SignalInterference.CheckOn checkOn = SignalInterference.CheckOn.BOTH;

    }

    /** Weather interference, always evaluated at the holder's spot. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Weather extends OkaeriConfig {

        /** Config path of the weather enum array. */
        public static final transient String INTERFERE_DURING_PATH =
                "settings.compass.signal.interference.weather.interfere-during";

        private boolean enabled = false;

        @CustomKey("interfere-during")
        @Comment({
                "Weather at the holder's spot; weather has no check-on key",
                "and never evaluates the target side.",
                "Default: STORM, RAIN"
        })
        private List<String> interfereDuring = new ArrayList<>(List.of("STORM", "RAIN"));

    }

    /** Biome interference. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Biome extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("interfere-in")
        private List<String> interfereIn = new ArrayList<>(List.of(
                "minecraft:desert",
                "minecraft:badlands",
                "minecraft:mushroom_fields",
                "minecraft:deep_ocean",
                "minecraft:deep_cold_ocean",
                "minecraft:deep_lukewarm_ocean",
                "minecraft:deep_frozen_ocean",
                "minecraft:deep_dark",
                "minecraft:soul_sand_valley",
                "minecraft:basalt_deltas",
                "minecraft:the_end"));

        @CustomKey("check-on")
        @Comment({
                "Which sides' biome must pass: SELF checks the holder,",
                "TARGET checks the tracked target, BOTH checks each.",
                "Default: BOTH"
        })
        private SignalInterference.CheckOn checkOn = SignalInterference.CheckOn.BOTH;

    }

    /** Movement interference. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Movement extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("threshold-blocks")
        @Comment({
                "Blocks moved from the press spot before interference, 0 and up.",
                "Default: 0.2"
        })
        private double thresholdBlocks = 0.2;

        @CustomKey("check-on")
        @Comment({
                "Which sides' movement must pass: SELF checks the holder,",
                "TARGET checks the tracked target, BOTH checks each.",
                "Default: SELF"
        })
        private SignalInterference.CheckOn checkOn = SignalInterference.CheckOn.SELF;

    }

    /** Line-of-sight interference. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class LineOfSight extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("interfere-when")
        @Comment({
                "VISIBLE interferes when the target is visible; NOT_VISIBLE",
                "interferes when hidden.",
                "Default: VISIBLE"
        })
        private SignalInterference.InterfereWhenVisible interfereWhen =
                SignalInterference.InterfereWhenVisible.VISIBLE;

        @CustomKey("max-ray-distance")
        @Comment({
                "Hard cap on the ray length. Past this distance there is no",
                "line of sight. From 1 to 1000.",
                "Default: 300"
        })
        private int maxRayDistance = 300;

    }

    /** Invisibility interference. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Invisible extends OkaeriConfig {
        private boolean enabled = true;

        @CustomKey("check-on")
        @Comment({
                "Which sides' invisibility interferes: SELF checks the holder,",
                "TARGET checks the tracked target, BOTH checks each.",
                "Default: BOTH"
        })
        private SignalInterference.CheckOn checkOn = SignalInterference.CheckOn.BOTH;

    }

}
