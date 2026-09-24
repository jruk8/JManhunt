package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.compass.SignalInterference;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.List;

/** Signal interference options. */
@SuppressWarnings("FieldMayBeFinal")
public class SignalInterferenceSettings extends OkaeriConfig {
    private boolean enabled = false;

    @CustomKey("required-to-fail")
    @Comment({
            "How many enabled options must interfere before tracking fails.",
            "Clamped to the enabled option count, minimum 1.",
            "Default: 1"
    })
    private int requiredToFail = 1;

    @CustomKey("two-way")
    @Comment({
            "When true, the target's location must also have a good signal.",
            "Default: false"
    })
    private boolean twoWay = false;

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
            "Default: true"
    })
    private Underground underground = new Underground();

    @Comment({
            "Water or lava above the holder's head. Counts fluid blocks",
            "strictly above the feet block; interferes when the count passes",
            "the maximum.",
            "Default: true"
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

    @CustomKey("line-of-sight")
    @Comment({
            "Line of sight between holder and target. Raycasts one eye-to-eye",
            "ray; glass, leaves, and other non-whole blocks never block it.",
            "Only live targets in the same world are evaluated.",
            "Default: false"
    })
    private LineOfSight lineOfSight = new LineOfSight();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRequiredToFail() {
        return requiredToFail;
    }

    public void setRequiredToFail(int requiredToFail) {
        this.requiredToFail = requiredToFail;
    }

    public boolean isTwoWay() {
        return twoWay;
    }

    public void setTwoWay(boolean twoWay) {
        this.twoWay = twoWay;
    }

    public double getChanceToBypass() {
        return chanceToBypass;
    }

    public void setChanceToBypass(double chanceToBypass) {
        this.chanceToBypass = chanceToBypass;
    }

    public boolean isShowReasonInActionbar() {
        return showReasonInActionbar;
    }

    public void setShowReasonInActionbar(boolean showReasonInActionbar) {
        this.showReasonInActionbar = showReasonInActionbar;
    }

    public LightLevel getLightLevel() {
        return lightLevel;
    }

    public void setLightLevel(LightLevel lightLevel) {
        this.lightLevel = lightLevel;
    }

    public Underground getUnderground() {
        return underground;
    }

    public void setUnderground(Underground underground) {
        this.underground = underground;
    }

    public Underwater getUnderwater() {
        return underwater;
    }

    public void setUnderwater(Underwater underwater) {
        this.underwater = underwater;
    }

    public Altitude getAltitude() {
        return altitude;
    }

    public void setAltitude(Altitude altitude) {
        this.altitude = altitude;
    }

    public Weather getWeather() {
        return weather;
    }

    public void setWeather(Weather weather) {
        this.weather = weather;
    }

    public Biome getBiome() {
        return biome;
    }

    public void setBiome(Biome biome) {
        this.biome = biome;
    }

    public LineOfSight getLineOfSight() {
        return lineOfSight;
    }

    public void setLineOfSight(LineOfSight lineOfSight) {
        this.lineOfSight = lineOfSight;
    }

    /** Light-level interference. */
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
                "Default: ONE_UNMET"
        })
        private SignalInterference.InterfereWhen interfereWhen =
                SignalInterference.InterfereWhen.ONE_UNMET;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinSkyLight() {
            return minSkyLight;
        }

        public void setMinSkyLight(int minSkyLight) {
            this.minSkyLight = minSkyLight;
        }

        public int getMinBlockLight() {
            return minBlockLight;
        }

        public void setMinBlockLight(int minBlockLight) {
            this.minBlockLight = minBlockLight;
        }

        public SignalInterference.InterfereWhen getInterfereWhen() {
            return interfereWhen;
        }

        public void setInterfereWhen(SignalInterference.InterfereWhen interfereWhen) {
            this.interfereWhen = interfereWhen;
        }
    }

    /** Underground cover interference. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Underground extends OkaeriConfig {
        private boolean enabled = true;

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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxBlocksAbove() {
            return maxBlocksAbove;
        }

        public void setMaxBlocksAbove(int maxBlocksAbove) {
            this.maxBlocksAbove = maxBlocksAbove;
        }

        public boolean isIgnoreTransparent() {
            return ignoreTransparent;
        }

        public void setIgnoreTransparent(boolean ignoreTransparent) {
            this.ignoreTransparent = ignoreTransparent;
        }
    }

    /** Underwater fluid interference. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Underwater extends OkaeriConfig {
        private boolean enabled = true;

        @CustomKey("max-blocks-above")
        @Comment({
                "Maximum fluid blocks above before interference, 1 to 380.",
                "Default: 2"
        })
        private int maxBlocksAbove = 2;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxBlocksAbove() {
            return maxBlocksAbove;
        }

        public void setMaxBlocksAbove(int maxBlocksAbove) {
            this.maxBlocksAbove = maxBlocksAbove;
        }
    }

    /** Altitude band interference. */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMinY() {
            return minY;
        }

        public void setMinY(int minY) {
            this.minY = minY;
        }

        public int getMaxY() {
            return maxY;
        }

        public void setMaxY(int maxY) {
            this.maxY = maxY;
        }
    }

    /** Weather interference. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Weather extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("interfere-during")
        @Comment("Default: STORM, RAIN")
        private List<String> interfereDuring = new ArrayList<>(List.of("STORM", "RAIN"));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getInterfereDuring() {
            return interfereDuring;
        }

        public void setInterfereDuring(List<String> interfereDuring) {
            this.interfereDuring = interfereDuring;
        }
    }

    /** Biome interference. */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getInterfereIn() {
            return interfereIn;
        }

        public void setInterfereIn(List<String> interfereIn) {
            this.interfereIn = interfereIn;
        }
    }

    /** Line-of-sight interference. */
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public SignalInterference.InterfereWhenVisible getInterfereWhen() {
            return interfereWhen;
        }

        public void setInterfereWhen(SignalInterference.InterfereWhenVisible interfereWhen) {
            this.interfereWhen = interfereWhen;
        }

        public int getMaxRayDistance() {
            return maxRayDistance;
        }

        public void setMaxRayDistance(int maxRayDistance) {
            this.maxRayDistance = maxRayDistance;
        }
    }
}