package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.compass.SignalInaccuracy;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;

/** Signal inaccuracy options. */
@SuppressWarnings("FieldMayBeFinal")
public class SignalInaccuracySettings extends OkaeriConfig {
    private boolean enabled = false;

    @CustomKey("inner-deadzone")
    @Comment({
            "Hole in the middle of the error donut, from 0 up to (but not",
            "including) 1. 0.5 means no error ever lands inside half of the",
            "outer radius; 0 lets the error land right on the true spot.",
            "Default: 0.4"
    })
    private double innerDeadzone = 0.4;

    @CustomKey("drift-radius")
    @Comment({
            "How far the readout can wander, as a share of the true",
            "distance. 1 wanders up to the full distance away; values near",
            "0 stay near the truth. Clamped to 0.01 at the bottom so the",
            "error can never fully vanish while enabled.",
            "Default: 0.6"
    })
    private double driftRadius = 0.6;

    @Comment("Range gate for the error: when it starts and stops growing.")
    private Thresholds thresholds = new Thresholds();

    @CustomKey("inaccurate-on")
    @Comment({
            "Which readout drifts: NEEDLE moves only the compass needle,",
            "DISTANCE_FEEDBACK moves only the shown distance, BOTH moves",
            "both. The needle only drifts on a plain compass item, and the",
            "distance only drifts while a distance readout is visible.",
            "Default: BOTH"
    })
    private SignalInaccuracy.InaccurateOn inaccurateOn =
            SignalInaccuracy.InaccurateOn.BOTH;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getInnerDeadzone() {
        return innerDeadzone;
    }

    public void setInnerDeadzone(double innerDeadzone) {
        this.innerDeadzone = innerDeadzone;
    }

    public double getDriftRadius() {
        return driftRadius;
    }

    public void setDriftRadius(double driftRadius) {
        this.driftRadius = driftRadius;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    public void setThresholds(Thresholds thresholds) {
        this.thresholds = thresholds;
    }

    public SignalInaccuracy.InaccurateOn getInaccurateOn() {
        return inaccurateOn;
    }

    public void setInaccurateOn(SignalInaccuracy.InaccurateOn inaccurateOn) {
        this.inaccurateOn = inaccurateOn;
    }

    /** Range gate for the error. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Thresholds extends OkaeriConfig {

        @CustomKey("min-distance")
        @Comment({
                "Meters past which the error kicks in. Set to -1 to always",
                "apply the error, or to a positive value for close-range",
                "truth. Must stay below max-distance while max is set.",
                "Default: 100.0"
        })
        private double minDistance = 100.0;

        @CustomKey("max-distance")
        @Comment({
                "Meters past which the error stops growing: longer true",
                "distances reuse this range for the donut. Set to -1 for",
                "unbounded growth.",
                "Default: 1000.0"
        })
        private double maxDistance = 1000.0;

        public double getMinDistance() {
            return minDistance;
        }

        public void setMinDistance(double minDistance) {
            this.minDistance = minDistance;
        }

        public double getMaxDistance() {
            return maxDistance;
        }

        public void setMaxDistance(double maxDistance) {
            this.maxDistance = maxDistance;
        }
    }
}
