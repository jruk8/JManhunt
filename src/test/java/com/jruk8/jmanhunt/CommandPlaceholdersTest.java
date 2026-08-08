package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the pure-logic parts of CommandPlaceholders (tilde resolution
 * and placeholder substitution that doesn't require a running Bukkit server).
 * The <random-mob> and <random-item> replacement logic depends on
 * EntityType.values() / Material.values() which require a server, so those
 * are not tested here.
 */
class CommandPlaceholdersTest {

    @Test
    void replaceSubstitutesPlayerName() {
        String result = CommandPlaceholders.replace("give <p> bread", "Steve", 0, 0, 0);
        assertEquals("give Steve bread", result);
    }

    @Test
    void resolveTildesBasic() {
        String result = CommandPlaceholders.resolveTildes("summon zombie ~ ~ ~", 10.5, 64.0, -20.2);
        assertEquals("summon zombie 10.5 64 -20.2", result);
    }

    @Test
    void resolveTildesWithPositiveOffset() {
        String result = CommandPlaceholders.resolveTildes("tp ~ ~5 ~", 10.5, 64.0, -20.2);
        assertEquals("tp 10.5 69 -20.2", result);
    }

    @Test
    void resolveTildesWithNegativeOffset() {
        String result = CommandPlaceholders.resolveTildes("tp ~ ~-3 ~", 10.5, 64.0, -20.2);
        assertEquals("tp 10.5 61 -20.2", result);
    }

    @Test
    void resolveTildesWithSixCoordinates() {
        String result = CommandPlaceholders.resolveTildes("fill ~ ~ ~ ~5 ~5 ~5 stone", 0.0, 0.0, 0.0);
        assertEquals("fill 0 0 0 5 5 5 stone", result);
    }

    @Test
    void resolveTildesWithDecimalOffset() {
        String result = CommandPlaceholders.resolveTildes("summon zombie ~ ~5.5 ~", 10.0, 64.0, 20.0);
        assertEquals("summon zombie 10 69.5 20", result);
    }

    @Test
    void resolveTildesNoTildesReturnsUnchanged() {
        String result = CommandPlaceholders.resolveTildes("give Steve bread", 10.0, 64.0, 20.0);
        assertEquals("give Steve bread", result);
    }

    @Test
    void replaceDoesNotResolveTildesForConsoleCommands() {
        // When playerName is null (console command), tildes should not be resolved
        String result = CommandPlaceholders.replace("summon zombie ~ ~ ~", null, 10.0, 64.0, 20.0);
        assertEquals("summon zombie ~ ~ ~", result);
    }

    @Test
    void replaceResolvesTildesForPlayerCommands() {
        String result = CommandPlaceholders.replace("summon zombie ~ ~ ~", "Steve", 10.5, 64.0, -20.2);
        assertEquals("summon zombie 10.5 64 -20.2", result);
    }
}
