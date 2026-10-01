package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;

/** Actionbar push rate and distance delta. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class CompassActionbarSettings extends OkaeriConfig {

    @CustomKey("refresh-ticks")
    @Comment({
            "Ticks between actionbar pushes. Frontend only: the tracking",
            "refresh interval is untouched.",
            "Default: 1"
    })
    private int refreshTicks = 1;

    @CustomKey("show-distance")
    @Comment({
            "Show the distance in meters on tracking actionbars.",
            "Off hides the distance along with its delta triangle.",
            "Default: true"
    })
    private boolean showDistance = true;

    @CustomKey("show-distance-delta")
    @Comment("Distance delta triangles.")
    private ShowDistanceDelta showDistanceDelta = new ShowDistanceDelta();

    @CustomKey("show-accuracy")
    @Comment("Stepped accuracy percent after the tracked name.")
    private ShowAccuracy showAccuracy = new ShowAccuracy();

    /** Stepped accuracy percent. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class ShowAccuracy extends OkaeriConfig {

        @Comment({
                "Show a stepped accuracy percent after the tracked name,",
                "like Tracking Alex (80%). Steps run in tens with a matching",
                "stepped color, so sub-step precision never leaks. Reads 100%",
                "whenever inaccuracy is off.",
                "Default: false"
        })
        private boolean enabled = false;

        @CustomKey("accurate-color")
        @Comment({
                "Hex color at full accuracy, as #rrggbb.",
                "Default: #63d42a"
        })
        private String accurateColor = "#63d42a";

        @CustomKey("inaccurate-color")
        @Comment({
                "Hex color at zero accuracy, as #rrggbb.",
                "Default: #cc472d"
        })
        private String inaccurateColor = "#cc472d";

    }

    /** Distance delta triangles. */
    @Getter
    @Setter
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
        @Comment("Format when the rounded distance grew. Default: <green>▲{distance}")
        private String furtherFormat = "<green>▲{distance}";

        @CustomKey("closer-format")
        @Comment("Format when the rounded distance shrank. Default: <red>▼{distance}")
        private String closerFormat = "<red>▼{distance}";

        @CustomKey("max-distance")
        @Comment({
                "Delta colors stop past this distance in meters; small",
                "changes far away only clutter the screen.",
                "Default: 500.0"
        })
        private double maxDistance = 500.0;

        @CustomKey("min-delta-to-show")
        @Comment({
                "Deltas below this many meters render the plain white",
                "distance instead.",
                "Default: 5.0"
        })
        private double minDeltaToShow = 5.0;

        @Comment({
                "How long the delta shows: HOLD keeps the triangle until",
                "the next refresh, BLINK shows it briefly, then reverts",
                "to the plain white distance.",
                "Default: BLINK"
        })
        private String mode = "BLINK";

        @CustomKey("blink-duration-seconds")
        @Comment({
                "Seconds a BLINK delta stays visible before reverting to",
                "the plain white distance. 0 shows plain immediately.",
                "Default: 0.6"
        })
        private double blinkDurationSeconds = 0.6;

        @CustomKey("reverse-on-hunter")
        @Comment({
                "Swap the delta formats for hunters: green on closer,",
                "red on further.",
                "Default: true"
        })
        private boolean reverseOnHunter = true;

    }
}
