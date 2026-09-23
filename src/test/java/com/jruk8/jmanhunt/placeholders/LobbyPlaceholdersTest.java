package com.jruk8.jmanhunt.placeholders;

import com.jruk8.jmanhunt.match.GameInstance;
import org.junit.jupiter.api.Test;
import java.util.OptionalLong;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LobbyPlaceholdersTest {

    @Test
    void parseLobbyKeySplitsBaseAndId() {
        var key = LobbyPlaceholders.parseLobbyKey("game_phase_0").orElseThrow();

        assertEquals("game_phase", key.base());
        assertEquals(0, key.lobbyId());
    }

    @Test
    void parseLobbyKeyHandlesLongBases() {
        var formatted = LobbyPlaceholders.parseLobbyKey("game_time_remaining_formatted_12").orElseThrow();

        assertEquals("game_time_remaining_formatted", formatted.base());
        assertEquals(12, formatted.lobbyId());
    }

    @Test
    void parseLobbyKeyRejectsOthers() {
        assertTrue(LobbyPlaceholders.parseLobbyKey("game_role").isEmpty());
        assertTrue(LobbyPlaceholders.parseLobbyKey("game_phase").isEmpty());
        assertTrue(LobbyPlaceholders.parseLobbyKey("game_phase_x").isEmpty());
        assertTrue(LobbyPlaceholders.parseLobbyKey("total_kills_0").isEmpty());
        assertTrue(LobbyPlaceholders.parseLobbyKey("game_phase_99999999999999999999").isEmpty());
    }

    private GameInstance instance(boolean begun, boolean ending) {
        GameInstance match = new GameInstance(1L, 0, OptionalLong.empty(), System.currentTimeMillis());
        match.setBegun(begun);
        match.setEnding(ending);
        return match;
    }

    @Test
    void phaseForMapsMatchStates() {
        PhaseNames phases = new PhaseNames();

        assertEquals("LOBBY", LobbyPlaceholders.phaseFor(null, true, phases));
        assertEquals("ENDED", LobbyPlaceholders.phaseFor(instance(true, true), true, phases));
        assertEquals("PRESTART", LobbyPlaceholders.phaseFor(instance(false, false), true, phases));
        assertEquals("IN_PROGRESS", LobbyPlaceholders.phaseFor(instance(true, false), true, phases));
    }

    @Test
    void phaseForSkipsPrestartWhenUnconfigured() {
        assertEquals("IN_PROGRESS",
                LobbyPlaceholders.phaseFor(instance(false, false), false, new PhaseNames()));
    }

    @Test
    void phaseForUsesConfiguredNames() {
        PhaseNames phases = new PhaseNames();
        phases.setLobby("IDLE");
        phases.setEnded("DONE");

        assertEquals("IDLE", LobbyPlaceholders.phaseFor(null, true, phases));
        assertEquals("DONE", LobbyPlaceholders.phaseFor(instance(true, true), true, phases));
    }
}
