package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.config.DurationFormat;
import com.jruk8.jmanhunt.config.PlayerSettings;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.WinconMessages;
import com.jruk8.jmanhunt.player.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Status text for the win conditions: base plus enabled alternates.
 */
public final class WinConditionTextService {
    private final MessageService messages;
    private final WinconMessages wincon;
    private final PlayerSettings.Respawn respawn;
    private final WinConditionEngine winConditionEngine;

    public WinConditionTextService(MessageService messages, WinconMessages wincon,
            PlayerSettings.Respawn respawn, WinConditionEngine winConditionEngine) {
        this.messages = messages;
        this.wincon = wincon;
        this.respawn = respawn;
        this.winConditionEngine = winConditionEngine;
    }

    /** Status text for the speedrunner win conditions: base plus enabled alternates. */
    public String speedrunnerWinConditions() {
        // Hunters with infinite lives can never be eliminated, so the
        // elimination line hides instead of promising an un-winnable goal.
        List<String> conditions = new ArrayList<>();
        int hunterLives = respawn.getHunter().getLives();
        if (hunterLives != -1) {
            conditions.add(winconFragment(wincon.getEliminateHunters(), Map.of()));
        }
        if (winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.EXIT_END)) {
            conditions.add(winconFragment(wincon.getCredits(), Map.of()));
        }
        if (winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.SURVIVE_TIME)) {
            conditions.add(winconFragment(wincon.getSurvive(), Map.of("time",
                    DurationFormat.format((long) winConditionEngine.time(Role.SPEEDRUNNER)))));
        }
        if (winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.ACQUIRE_ITEM)) {
            conditions.add(winconFragment(wincon.getAcquired(), Map.of("item",
                    WinConditionEngine.prettyKey(winConditionEngine.item(Role.SPEEDRUNNER)))));
        }
        if (winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.REACH_ADVANCEMENT)) {
            conditions.add(winconFragment(wincon.getAdvancement(),
                    Map.of("advancement", winConditionEngine.advancement(Role.SPEEDRUNNER))));
        }
        if (winConditionEngine.enabled(Role.SPEEDRUNNER, WinCondition.KILL_MOB)) {
            conditions.add(winconFragment(wincon.getKilled(), Map.of("mob",
                    WinConditionEngine.prettyKey(winConditionEngine.mob(Role.SPEEDRUNNER)))));
        }
        return ListFormatter.joinOxford(conditions);
    }

    /** Status text for the hunter win conditions: base plus enabled alternates. */
    public String hunterWinConditions() {
        List<String> conditions = new ArrayList<>(List.of(winconFragment(wincon.getEliminateSpeedrunners(), Map.of())));
        if (winConditionEngine.enabled(Role.HUNTER, WinCondition.TIME_LIMIT)) {
            conditions.add(winconFragment(wincon.getTimeLimit(), Map.of("time",
                    DurationFormat.format((long) winConditionEngine.time(Role.HUNTER)))));
        }
        if (winConditionEngine.enabled(Role.HUNTER, WinCondition.ACQUIRE_ITEM)) {
            conditions.add(winconFragment(wincon.getAcquired(), Map.of("item",
                    WinConditionEngine.prettyKey(winConditionEngine.item(Role.HUNTER)))));
        }
        if (winConditionEngine.enabled(Role.HUNTER, WinCondition.REACH_ADVANCEMENT)) {
            conditions.add(winconFragment(wincon.getAdvancement(),
                    Map.of("advancement", winConditionEngine.advancement(Role.HUNTER))));
        }
        if (winConditionEngine.enabled(Role.HUNTER, WinCondition.KILL_MOB)) {
            conditions.add(winconFragment(wincon.getKilled(), Map.of("mob",
                    WinConditionEngine.prettyKey(winConditionEngine.mob(Role.HUNTER)))));
        }
        return ListFormatter.joinOxford(conditions);
    }

    /**
     * Renders one wincon fragment template with its named values. Fragments
     * stay unparsed here; they are substituted into the status-win lines and
     * parsed once with them.
     */
    private String winconFragment(String template, Map<String, String> values) {
        String raw = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return raw;
    }
}
