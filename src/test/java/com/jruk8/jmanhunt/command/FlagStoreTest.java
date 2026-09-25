package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flag store scoping: matches isolate, players flush, unset is null. */
class FlagStoreTest {

    @Test
    void unsetFlagsReadNull() {
        FlagStore flags = new FlagStore();
        assertEquals("null", flags.global(7L, "x"));
        assertEquals("null", flags.player(7L, "x-Steve"));
        assertEquals(FlagStore.UNSET, "null");
    }

    @Test
    void globalAndPlayerMapsStaySeparate() {
        FlagStore flags = new FlagStore();
        flags.setGlobal(7L, "x", "g");
        flags.setPlayer(7L, "x", "p");
        assertEquals("g", flags.global(7L, "x"));
        assertEquals("p", flags.player(7L, "x"));
    }

    @Test
    void matchesAreIsolated() {
        FlagStore flags = new FlagStore();
        flags.setGlobal(7L, "x", "seven");
        flags.setPlayer(7L, "x-Steve", "seven");
        assertEquals("null", flags.global(8L, "x"));
        assertEquals("null", flags.player(8L, "x-Steve"));
    }

    @Test
    void removePlayerDropsOnlySuffixedKeys() {
        FlagStore flags = new FlagStore();
        flags.setPlayer(7L, "a-Steve", "1");
        flags.setPlayer(7L, "b-Steve", "2");
        flags.setPlayer(7L, "a-Alex", "3");
        flags.setPlayer(7L, "Steve", "bare");
        flags.removePlayer(7L, "Steve");
        assertEquals("null", flags.player(7L, "a-Steve"));
        assertEquals("null", flags.player(7L, "b-Steve"));
        assertEquals("3", flags.player(7L, "a-Alex"));
        assertEquals("bare", flags.player(7L, "Steve"));
    }

    @Test
    void removePlayerMatchesExactly() {
        FlagStore flags = new FlagStore();
        flags.setPlayer(7L, "a-Steven", "1");
        flags.removePlayer(7L, "Steve");
        assertEquals("1", flags.player(7L, "a-Steven"));
    }

    @Test
    void clearMatchDropsGlobalsAndPlayers() {
        FlagStore flags = new FlagStore();
        flags.setGlobal(7L, "x", "1");
        flags.setPlayer(7L, "x-Steve", "2");
        flags.setGlobal(8L, "x", "3");
        flags.clearMatch(7L);
        assertEquals("null", flags.global(7L, "x"));
        assertEquals("null", flags.player(7L, "x-Steve"));
        assertEquals("3", flags.global(8L, "x"));
    }

    @Test
    void playerKeyAndSuffixHelpers() {
        assertEquals("x-Steve", FlagStore.playerKey("x", "Steve"));
        assertEquals("CONSOLE", FlagStore.consoleSuffix());
        ModifierTagScope player = ModifierTagScope.match("Steve", List.of(), new Random(1),
                message -> { });
        ModifierTagScope console = ModifierTagScope.executor(null, message -> { });
        assertEquals("Steve", FlagStore.suffixFor(player));
        assertEquals("CONSOLE", FlagStore.suffixFor(console));
    }

    @Test
    void parseNameTrimsAndRejectsBlank() {
        assertEquals("cooldown", FlagStore.parseName("\"cooldown\"").orElseThrow());
        assertEquals("a b", FlagStore.parseName("\"a b\"").orElseThrow());
        assertTrue(FlagStore.parseName("\"\"").isEmpty());
        assertTrue(FlagStore.parseName("   ").isEmpty());
        assertTrue(FlagStore.parseName("\"oops").isEmpty());
    }
}
