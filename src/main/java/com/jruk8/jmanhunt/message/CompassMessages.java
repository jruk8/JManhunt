package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** Tracking compass names, lore, and actionbars. */
@Getter
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

    @CustomKey("teammate-actionbar")
    private String teammateActionbar = "<#de7766>Tracking teammate <white>{player}<#de7766> • <white>{distance}m";

    @CustomKey("teammate-last-seen-actionbar")
    private String teammateLastSeenActionbar = "<#de7766>Tracking teammate <white>{player}<#de7766>'s Last Seen • " +
            "<white>{distance}m <gray>({reason})";

    @CustomKey("teammate-locked-actionbar")
    private String teammateLockedActionbar = "<#de7766>Tracking teammate <white>{player}<#de7766> • " +
            "<white>{distance}m <gray>[LOCKED]";

    @CustomKey("teammate-last-seen-locked-actionbar")
    private String teammateLastSeenLockedActionbar = "<#de7766>Tracking teammate " +
            "<white>{player}<#de7766>'s Last Seen • <white>{distance}m <gray>({reason}) [LOCKED]";

    @CustomKey("no-target-actionbar")
    private String noTargetActionbar = "<gray>No {role}<gray> location available.";

    @CustomKey("nearby-actionbar")
    private String nearbyActionbar = "<green>{player} is nearby! Tracking is disabled.";

    @CustomKey("too-far-actionbar")
    private String tooFarActionbar = "<yellow>{player} is out of tracking range.";

    @CustomKey("analyzing-actionbar")
    private String analyzingActionbar = "<gray>Analyzing...";

    @CustomKey("bad-signal-actionbar")
    private String badSignalActionbar = "<gray>☹ Bad signal";

    @CustomKey("bad-signal-reason-actionbar")
    private String badSignalReasonActionbar = "<gray>☹ Bad signal ({reason})";

    @CustomKey("analysis-cost-too-high")
    private String analysisCostTooHigh = "<gray>Cost too high!";

    @CustomKey("no-teammates")
    private String noTeammates = "{prefix}<yellow>You have no teammates to track.";

    @CustomKey("teammate-on-chat")
    private String teammateOnChat = "{prefix}<yellow>Now tracking <white>teammates</white>.";

    @CustomKey("teammate-off-chat")
    private String teammateOffChat = "{prefix}<yellow>Now tracking <white>enemies</white>.";

    @CustomKey("locked-target-died-chat")
    private String lockedTargetDiedChat = "{prefix}<yellow>Locked-on target died.";

    @CustomKey("signal-reason")
    private Map<String, String> signalReason = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("light-level", "low light"),
            Map.entry("underground", "underground"),
            Map.entry("underwater", "underwater"),
            Map.entry("altitude", "altitude"),
            Map.entry("weather", "weather"),
            Map.entry("biome", "wrong biome"),
            Map.entry("moved", "moved"),
            Map.entry("line-of-sight", "line of sight"),
            Map.entry("line-of-sight-hidden", "no line of sight"),
            Map.entry("invisible", "invisible"),
            Map.entry("low-health", "low health"),
            Map.entry("hungry", "hungry"),
            Map.entry("low-exp-level", "low exp level"),
            Map.entry("cancelled", "cancelled")));
}
