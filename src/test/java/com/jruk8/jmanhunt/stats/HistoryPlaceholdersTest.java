package com.jruk8.jmanhunt.stats;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Internal lifetime derivations: plain integers, one-decimal damage,
 * and compact playtime.
 */
class HistoryPlaceholdersTest {

    @Test
    void resolveFormatsAllEightLines() {
        Map<String, String> values = HistoryPlaceholders.resolve(
                new HistoryPlaceholders.Totals(7, 42, 30, 12, 5, 2, 1234.56, 3_661_000L));

        assertEquals("7", values.get("server_matches"));
        assertEquals("42", values.get("server_kills"));
        assertEquals("30", values.get("server_hunter_kills"));
        assertEquals("12", values.get("server_speedrunner_kills"));
        assertEquals("5", values.get("server_hunter_wins"));
        assertEquals("2", values.get("server_speedrunner_wins"));
        assertEquals("1234.6", values.get("server_damage"));
        assertEquals("1h 1m 1s", values.get("server_playtime"));
        assertEquals(8, values.size());
    }

    @Test
    void emptyTotalsResolveToZeros() {
        Map<String, String> values =
                HistoryPlaceholders.resolve(HistoryPlaceholders.Totals.empty());

        assertEquals("0", values.get("server_matches"));
        assertEquals("0.0", values.get("server_damage"));
        assertEquals("0s", values.get("server_playtime"));
    }
}
