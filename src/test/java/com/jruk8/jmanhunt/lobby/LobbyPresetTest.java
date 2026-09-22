package com.jruk8.jmanhunt.lobby;

import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jruk8.jmanhunt.lobby.config.LobbyPreset;

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

    @Test
    void tryParseIsStrict() {
        assertEquals(Optional.of(LobbyPreset.DEFAULT), LobbyPreset.tryParse("default"));
        assertEquals(Optional.of(LobbyPreset.ADVANCED), LobbyPreset.tryParse("ADVANCED"));
        assertEquals(Optional.empty(), LobbyPreset.tryParse("bogus"));
        assertEquals(Optional.empty(), LobbyPreset.tryParse(null));
        assertEquals(Optional.empty(), LobbyPreset.tryParse(""));
    }
}
