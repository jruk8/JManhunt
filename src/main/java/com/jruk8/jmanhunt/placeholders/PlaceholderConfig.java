package com.jruk8.jmanhunt.placeholders;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PlaceholderAPI definitions: every built-in identifier with its toggle
 * and format, plus the game_phase names. Lives in placeholders.yml.
 * Identifiers take the jmanhunt_ prefix at use time; lobby-scoped ones
 * append the lobby id (game_duration_0). Field names stay single
 * lowercase words so the yaml keys never depend on a naming strategy.
 */
@SuppressWarnings("FieldMayBeFinal")
@Header({
        "JManhunt's built-in PlaceholderAPI identifiers.",
        "All placeholders must be used with the prefix %jmanhunt_<placeholder>%",
        "(e.g. %jmanhunt_total_kills%, %jmanhunt_game_phase_0%)."
})
public class PlaceholderConfig extends OkaeriConfig {

    @Comment("Placeholder definitions by identifier (without the jmanhunt_ prefix).")
    private Map<String, PlaceholderEntry> placeholders = defaultPlaceholders();

    @Comment("Phase names used by game_phase.")
    private PhaseNames phases = new PhaseNames();

    public Map<String, PlaceholderEntry> getPlaceholders() {
        return placeholders;
    }

    public void setPlaceholders(Map<String, PlaceholderEntry> placeholders) {
        this.placeholders = placeholders;
    }

    public PhaseNames getPhases() {
        return phases;
    }

    public void setPhases(PhaseNames phases) {
        this.phases = phases;
    }

    private static Map<String, PlaceholderEntry> defaultPlaceholders() {
        Map<String, PlaceholderEntry> defaults = new LinkedHashMap<>();
        careerPlaceholders(defaults);
        lobbyPlaceholders(defaults);
        runtimePlaceholders(defaults);
        return defaults;
    }

    private static void careerPlaceholders(Map<String, PlaceholderEntry> defaults) {
        defaults.put("time_as_speedrunner",
                new PlaceholderEntry("LONG", "Career time played as a speedrunner, in milliseconds."));
        defaults.put("time_as_hunter",
                new PlaceholderEntry("LONG", "Career time played as a hunter, in milliseconds."));
        defaults.put("formatted_time_as_speedrunner",
                new PlaceholderEntry("TEXT", "Career speedrunner time formatted as days, hours, minutes, seconds."));
        defaults.put("formatted_time_as_hunter",
                new PlaceholderEntry("TEXT", "Career hunter time formatted as days, hours, minutes, seconds."));
        defaults.put("total_kills", new PlaceholderEntry("INTEGER", "Career total kills."));
        defaults.put("total_kills_as_hunter",
                new PlaceholderEntry("INTEGER", "Career kills while playing as a hunter."));
        defaults.put("total_kills_as_speedrunner",
                new PlaceholderEntry("INTEGER", "Career kills while playing as a speedrunner."));
        defaults.put("total_final_kills", new PlaceholderEntry("INTEGER", "Career final kills."));
        defaults.put("total_damage_dealt",
                new PlaceholderEntry("DECIMAL", "Career damage dealt in hearts."));
        defaults.put("total_wins", new PlaceholderEntry("INTEGER", "Career total wins."));
        defaults.put("total_wins_as_hunter", new PlaceholderEntry("INTEGER", "Career wins as a hunter."));
        defaults.put("total_wins_as_speedrunner",
                new PlaceholderEntry("INTEGER", "Career wins as a speedrunner."));
        defaults.put("total_game_sessions",
                new PlaceholderEntry("INTEGER", "Career total matches played as a participant."));
        defaults.put("sessions_as_speedrunner",
                new PlaceholderEntry("INTEGER", "Career matches played as a speedrunner."));
        defaults.put("sessions_as_hunter",
                new PlaceholderEntry("INTEGER", "Career matches played as a hunter."));
        defaults.put("formatted_total_playtime",
                new PlaceholderEntry("TEXT",
                        "Career total playtime (speedrunner + hunter) formatted as days, hours, minutes, seconds."));
        defaults.put("total_kd_as_speedrunner",
                new PlaceholderEntry("DECIMAL", "Career K/D ratio as a speedrunner (kills per session)."));
        defaults.put("total_kd_as_hunter",
                new PlaceholderEntry("DECIMAL", "Career K/D ratio as a hunter (kills per session)."));
        defaults.put("current_win_streak", new PlaceholderEntry("INTEGER", "Career current win streak."));
        defaults.put("best_win_streak", new PlaceholderEntry("INTEGER", "Career best win streak."));
    }

    private static void lobbyPlaceholders(Map<String, PlaceholderEntry> defaults) {
        defaults.put("lobby_lifetime_sessions",
                new PlaceholderEntry("INTEGER", "Total started sessions for the lobby, all time."));
        defaults.put("lobby_current_sessions",
                new PlaceholderEntry("INTEGER", "Running sessions for the lobby right now."));
    }

    private static void runtimePlaceholders(Map<String, PlaceholderEntry> defaults) {
        defaults.put("game_duration",
                new PlaceholderEntry("LONG", "Running match duration in milliseconds, -1 when idle."));
        defaults.put("game_speedrunners_remaining",
                new PlaceholderEntry("INTEGER", "Live speedrunners in the lobby's match."));
        defaults.put("game_hunters_remaining",
                new PlaceholderEntry("INTEGER", "Live hunters in the lobby's match."));
        defaults.put("game_players_remaining",
                new PlaceholderEntry("INTEGER", "Live participants in the lobby's match."));
        defaults.put("game_spectators",
                new PlaceholderEntry("INTEGER", "Online spectators assigned to the lobby's match."));
        defaults.put("game_phase",
                new PlaceholderEntry("TEXT", "Lobby match phase: LOBBY, PRESTART, IN_PROGRESS, or ENDED."));
        defaults.put("game_time_remaining",
                new PlaceholderEntry("LONG", "Survive clock left in milliseconds, -1 when none runs."));
        defaults.put("game_time_remaining_formatted",
                new PlaceholderEntry("TEXT", "Survive clock left, formatted; -1 when none runs."));
        defaults.put("game_role",
                new PlaceholderEntry("TEXT", "Player's current role with its configured color."));
        defaults.put("game_kills_this_session",
                new PlaceholderEntry("INTEGER", "Player's kills this match, -1 outside a match."));
        defaults.put("game_deaths_this_session",
                new PlaceholderEntry("INTEGER", "Player's deaths this match, -1 outside a match."));
    }
}
