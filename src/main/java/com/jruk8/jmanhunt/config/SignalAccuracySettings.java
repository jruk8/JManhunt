package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;

/** Signal accuracy options. */
@SuppressWarnings("FieldMayBeFinal")
public class SignalAccuracySettings extends OkaeriConfig {

    @Comment({
            "Hotspot accuracy: records where targets linger and shrinks the",
            "inaccuracy donut for idlers, like averaging noisy fixes to find",
            "the truth. Only matters while signal inaccuracy is enabled."
    })
    private Hotspot hotspot = new Hotspot();

    public Hotspot getHotspot() {
        return hotspot;
    }

    public void setHotspot(Hotspot hotspot) {
        this.hotspot = hotspot;
    }

    /** Hotspot accuracy. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Hotspot extends OkaeriConfig {
        private boolean enabled = false;

        @CustomKey("hotspot-radius")
        @Comment({
                "How close recorded points must be to the target to count as",
                "the same hotspot, in meters. Must stay above 0.",
                "Default: 50.0"
        })
        private double hotspotRadius = 50.0;

        @CustomKey("sample-interval")
        @Comment({
                "Seconds between recorded positions, from 1 to 100.",
                "Default: 10"
        })
        private int sampleInterval = 10;

        @CustomKey("max-points")
        @Comment({
                "How many past positions are remembered per target.",
                "Default: 40"
        })
        private int maxPoints = 40;

        @CustomKey("full-accuracy-fraction")
        @Comment({
                "Share of max-points inside the hotspot needed for the full",
                "effect: 40 points at 0.5 means 20 idling samples max out",
                "the bonus.",
                "Default: 0.5"
        })
        private double fullAccuracyFraction = 0.5;

        @CustomKey("max-reduction")
        @Comment({
                "Fraction of the error removed at full effect, above 0 up to",
                "1. At 0.9 a fully idling target reads with a tenth of the",
                "normal error.",
                "Default: 0.9"
        })
        private double maxReduction = 0.9;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public double getHotspotRadius() {
            return hotspotRadius;
        }

        public void setHotspotRadius(double hotspotRadius) {
            this.hotspotRadius = hotspotRadius;
        }

        public int getSampleInterval() {
            return sampleInterval;
        }

        public void setSampleInterval(int sampleInterval) {
            this.sampleInterval = sampleInterval;
        }

        public int getMaxPoints() {
            return maxPoints;
        }

        public void setMaxPoints(int maxPoints) {
            this.maxPoints = maxPoints;
        }

        public double getFullAccuracyFraction() {
            return fullAccuracyFraction;
        }

        public void setFullAccuracyFraction(double fullAccuracyFraction) {
            this.fullAccuracyFraction = fullAccuracyFraction;
        }

        public double getMaxReduction() {
            return maxReduction;
        }

        public void setMaxReduction(double maxReduction) {
            this.maxReduction = maxReduction;
        }
    }
}
