package com.jruk8.jmanhunt.lobby;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LobbyPresetTest {

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(LobbyPreset.EMPTY, LobbyPreset.parse("empty"));
        assertEquals(LobbyPreset.DEFAULT, LobbyPreset.parse("Default"));
        assertEquals(LobbyPreset.ADVANCED, LobbyPreset.parse("ADVANCED"));
    }

    @Test
    void parseFallsBackToDefault() {
        assertEquals(LobbyPreset.DEFAULT, LobbyPreset.parse("bogus"));
        assertEquals(LobbyPreset.DEFAULT, LobbyPreset.parse(null));
        assertEquals(LobbyPreset.DEFAULT, LobbyPreset.parse(""));
    }
}
