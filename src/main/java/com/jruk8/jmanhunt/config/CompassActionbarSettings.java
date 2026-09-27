package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;

/** Actionbar push rate and distance delta. */
@SuppressWarnings("FieldMayBeFinal")
public class CompassActionbarSettings extends OkaeriConfig {

    @CustomKey("refresh-ticks")
    @Comment({
            "Ticks between actionbar pushes. Frontend only: the tracking",
            "refresh interval is untouched.",
            "Default: 1"
    })
    private int refreshTicks = 1;

    @CustomKey("show-distance-delta")
    @Comment("Distance delta triangles.")
    private ShowDistanceDelta showDistanceDelta = new ShowDistanceDelta();

    public int getRefreshTicks() {
        return refreshTicks;
    }

    public void setRefreshTicks(int refreshTicks) {
        this.refreshTicks = refreshTicks;
    }

    public ShowDistanceDelta getShowDistanceDelta() {
        return showDistanceDelta;
    }

    public void setShowDistanceDelta(ShowDistanceDelta showDistanceDelta) {
        this.showDistanceDelta = showDistanceDelta;
    }

    /** Distance delta triangles. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class ShowDistanceDelta extends OkaeriConfig {

        @Comment({
                "Show a green up triangle when the target got further",
                "since the last refresh, or a red down triangle when it",
                "got closer. Off renders the plain white distance.",
                "Default: true"
        })
        private boolean enabled = true;

        @CustomKey("further-format")
        @Comment("Format when the rounded distance grew. Default: <green>▲{distance}m")
        private String furtherFormat = "<green>▲{distance}m";

        @CustomKey("closer-format")
        @Comment("Format when the rounded distance shrank. Default: <red>▼{distance}m")
        private String closerFormat = "<red>▼{distance}m";

        @CustomKey("max-distance")
        @Comment({
                "Delta colors stop past this distance in meters; small",
                "changes far away only clutter the screen.",
                "Default: 200.0"
        })
        private double maxDistance = 200.0;

        @CustomKey("min-delta-to-show")
        @Comment({
                "Deltas below this many meters render the plain white",
                "distance instead.",
                "Default: 5.0"
        })
        private double minDeltaToShow = 5.0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getFurtherFormat() {
            return furtherFormat;
        }

        public void setFurtherFormat(String furtherFormat) {
            this.furtherFormat = furtherFormat;
        }

        public String getCloserFormat() {
            return closerFormat;
        }

        public void setCloserFormat(String closerFormat) {
            this.closerFormat = closerFormat;
        }

        public double getMaxDistance() {
            return maxDistance;
        }

        public void setMaxDistance(double maxDistance) {
            this.maxDistance = maxDistance;
        }

        public double getMinDeltaToShow() {
            return minDeltaToShow;
        }

        public void setMinDeltaToShow(double minDeltaToShow) {
            this.minDeltaToShow = minDeltaToShow;
        }
    }
}
