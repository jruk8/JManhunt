package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.lobby.bounds.LobbyBoundsPalette;
import org.bukkit.Color;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LobbyBoundsPaletteTest {

    private static int[] rgb(Color color) {
        return new int[]{color.getRed(), color.getGreen(), color.getBlue()};
    }

    @Test
    void firstLevelUsesFullPrimariesThenSecondaries() {
        assertArrayEquals(new int[]{255, 0, 0}, rgb(LobbyBoundsPalette.colorForIndex(0)));
        assertArrayEquals(new int[]{0, 255, 0}, rgb(LobbyBoundsPalette.colorForIndex(1)));
        assertArrayEquals(new int[]{0, 0, 255}, rgb(LobbyBoundsPalette.colorForIndex(2)));
        assertArrayEquals(new int[]{255, 255, 0}, rgb(LobbyBoundsPalette.colorForIndex(3)));
        assertArrayEquals(new int[]{0, 255, 255}, rgb(LobbyBoundsPalette.colorForIndex(4)));
        assertArrayEquals(new int[]{255, 0, 255}, rgb(LobbyBoundsPalette.colorForIndex(5)));
    }

    @Test
    void laterLevelsHalveEachChannel() {
        assertArrayEquals(new int[]{127, 0, 0}, rgb(LobbyBoundsPalette.colorForIndex(6)));
        assertArrayEquals(new int[]{63, 63, 0}, rgb(LobbyBoundsPalette.colorForIndex(15)));
        assertArrayEquals(new int[]{0, 0, 31}, rgb(LobbyBoundsPalette.colorForIndex(20)));
    }

    @Test
    void paletteWrapsAfterFourLevels() {
        assertArrayEquals(new int[]{255, 0, 0}, rgb(LobbyBoundsPalette.colorForIndex(24)));
        assertArrayEquals(new int[]{0, 255, 0}, rgb(LobbyBoundsPalette.colorForIndex(25)));
    }

    @Test
    void refreshIntervalIsHalfASecond() {
        assertEquals(10L, LobbyBoundsPalette.refreshTicks());
        assertEquals(25.0, LobbyBoundsPalette.CHECK_RADIUS_BLOCKS);
    }
}
