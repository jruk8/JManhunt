package com.jruk8.jmanhunt.placeholders;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderConfigTest {

    private static final String[] EXPECTED = {
            "time_as_speedrunner", "time_as_hunter", "formatted_time_as_speedrunner",
            "formatted_time_as_hunter", "total_kills", "total_kills_as_hunter",
            "total_kills_as_speedrunner", "total_final_kills", "total_damage_dealt", "total_wins",
            "total_wins_as_hunter", "total_wins_as_speedrunner", "total_game_sessions",
            "sessions_as_speedrunner", "sessions_as_hunter", "formatted_total_playtime",
            "total_kd_as_speedrunner", "total_kd_as_hunter", "current_win_streak", "best_win_streak",
            "game_duration", "game_speedrunners_remaining", "game_hunters_remaining",
            "game_players_remaining", "game_spectators", "lobby_lifetime_sessions",
            "lobby_current_sessions", "game_phase", "game_time_remaining",
            "game_time_remaining_formatted", "game_role", "game_kills_this_session",
            "game_deaths_this_session"};

    @Test
    void defaultsCoverEveryIdentifier() {
        PlaceholderConfig config = new PlaceholderConfig();

        assertEquals(EXPECTED.length, config.getPlaceholders().size());
        for (String key : EXPECTED) {
            assertTrue(config.getPlaceholders().containsKey(key), "missing: " + key);
            assertTrue(config.getPlaceholders().get(key).isEnabled());
            assertEquals("{value}", config.getPlaceholders().get(key).getFormat());
        }
    }

    @Test
    void phaseNamesDefaultToUppercase() {
        PhaseNames phases = new PlaceholderConfig().getPhases();

        assertEquals("LOBBY", phases.getLobby());
        assertEquals("PRESTART", phases.getPrestart());
        assertEquals("IN_PROGRESS", phases.getInprogress());
        assertEquals("ENDED", phases.getEnded());
    }
}
