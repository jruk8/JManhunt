package com.jruk8.jmanhunt.lobby;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LobbySchematicServiceTest {

    @Test
    void oddSizesCenterExactly() {
        assertEquals(-4, LobbySchematicService.cornerAxis(0, 9));
        assertEquals(60, LobbySchematicService.cornerAxis(64, 9));
    }

    @Test
    void evenSizesLeanNegative() {
        assertEquals(-5, LobbySchematicService.cornerAxis(0, 10));
        assertEquals(59, LobbySchematicService.cornerAxis(64, 10));
    }

    @Test
    void singleBlockLandsOnOrigin() {
        assertEquals(0, LobbySchematicService.cornerAxis(0, 1));
        assertEquals(64, LobbySchematicService.cornerAxis(64, 1));
    }
}
