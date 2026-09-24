package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tracking compass names, lore, and actionbars. */
@SuppressWarnings("FieldMayBeFinal")
public class CompassMessages extends OkaeriConfig {

    @CustomKey("hunter-name")
    private String hunterName = "{role-color-hunter}Hunter's Compass";

    @CustomKey("hunter-lore")
    private List<String> hunterLore = new ArrayList<>(List.of(
            "<gray>Tracks the nearest speedrunner.",
            "<gray><white>Right-click</white> to refresh when enabled.",
            "<gray><white>Left-click</white> to cycle targets when enabled."));

    @CustomKey("speedrunner-name")
    private String speedrunnerName = "{role-color-speedrunner}Speedrunner's Compass";

    @CustomKey("speedrunner-lore")
    private List<String> speedrunnerLore = new ArrayList<>(List.of(
            "<gray>Tracks the nearest hunter.",
            "<gray><white>Right-click</white> to refresh when enabled.",
            "<gray><white>Left-click</white> to cycle targets when enabled."));

    @CustomKey("compass-actionbar")
    private String compassActionbar = "<#de7766>Tracking <white>{player}<#de7766> • <white>{distance}m";

    @CustomKey("compass-last-seen-actionbar")
    private String compassLastSeenActionbar = "<#de7766>Tracking <white>{player}<#de7766>'s Last Seen • " +
            "<white>{distance}m <gray>({reason})";

    @CustomKey("compass-locked-actionbar")
    private String compassLockedActionbar = "<#de7766>Tracking <white>{player}<#de7766> • <white>{distance}m " +
            "<gray>[LOCKED]";

    @CustomKey("compass-last-seen-locked-actionbar")
    private String compassLastSeenLockedActionbar = "<#de7766>Tracking <white>{player}<#de7766>'s Last Seen • " +
            "<white>{distance}m <gray>({reason}) [LOCKED]";

    @CustomKey("no-target-actionbar")
    private String noTargetActionbar = "<gray>No {role}<gray> location available.";

    @CustomKey("nearby-actionbar")
    private String nearbyActionbar = "<green>{player} is nearby! The compass is disabled.";

    @CustomKey("too-far-actionbar")
    private String tooFarActionbar = "<yellow>{player} is out of tracking range.";

    @CustomKey("analyzing-actionbar")
    private String analyzingActionbar = "<gray>Analyzing...";

    @CustomKey("bad-signal-actionbar")
    private String badSignalActionbar = "<gray>☹ Bad signal";

    @CustomKey("bad-signal-reason-actionbar")
    private String badSignalReasonActionbar = "<gray>:( Bad signal (<white>{reason}</white>)";

    @CustomKey("signal-reason")
    private Map<String, String> signalReason = new LinkedHashMap<>(Map.of(
            "light-level", "low light",
            "underground", "underground",
            "underwater", "underwater",
            "altitude", "altitude",
            "weather", "weather",
            "biome", "biome",
            "line-of-sight", "line of sight"));
}
