package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.command.DrillResolve;
import com.jruk8.jmanhunt.command.ManhuntCommand;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the /manhunt config drill-down resolution against the real
 * setting registry. List sizes come from fixture lookups; everything
 * else is static registry data, so no server is needed.
 */
class ConfigDrillTest {

    private static Function<String, List<String>> lists(Map<String, List<String>> fixtures) {
        return fixtures::get;
    }

    private static Function<String, List<String>> noLists() {
        return path -> null;
    }

    @Test
    void fullLeafPathResolvesWithEmptyRemainder() {
        DrillResolve resolved = ManhuntCommand.resolveDrill(
                List.of("settings", "compass", "given-to", "hunters"), noLists());

        assertEquals("settings.compass.given-to.hunters", resolved.path());
        assertTrue(resolved.leaf());
        assertTrue(resolved.remainder().isEmpty());
    }

    @Test
    void trailingValueStaysAsRemainder() {
        DrillResolve resolved = ManhuntCommand.resolveDrill(
                List.of("settings", "compass", "given-to", "hunters", "true"), noLists());

        assertEquals("settings.compass.given-to.hunters", resolved.path());
        assertTrue(resolved.leaf());
        assertEquals(List.of("true"), resolved.remainder());
    }

    @Test
    void sectionResolvesAsSection() {
        DrillResolve resolved = ManhuntCommand.resolveDrill(
                List.of("settings", "compass"), noLists());

        assertEquals("settings.compass", resolved.path());
        assertFalse(resolved.leaf());
        assertTrue(resolved.section());
    }

    @Test
    void unknownFirstSegmentResolvesToNull() {
        assertNull(ManhuntCommand.resolveDrill(List.of("bogus"), noLists()));
    }

    @Test
    void unknownDeeperSegmentLeavesSectionRemainder() {
        DrillResolve resolved = ManhuntCommand.resolveDrill(
                List.of("settings", "bogus"), noLists());

        assertEquals("settings", resolved.path());
        assertFalse(resolved.leaf());
        assertTrue(resolved.section());
        assertEquals(List.of("bogus"), resolved.remainder());
    }

    @Test
    void matchingIsCaseInsensitiveButCanonical() {
        DrillResolve resolved = ManhuntCommand.resolveDrill(
                List.of("SETTINGS", "Compass", "Given-To", "HUNTERS"), noLists());

        assertEquals("settings.compass.given-to.hunters", resolved.path());
        assertTrue(resolved.leaf());
    }

    @Test
    void topLevelChildrenAreRegistryCategories() {
        assertEquals(List.of("debug", "lobbies", "match", "settings", "statistics",
                "update-checker", "world-engine"), ManhuntCommand.drillChildren(List.of(), noLists()));
    }

    @Test
    void childrenOfferSectionsAndLeaves() {
        assertEquals(List.of("compass", "match", "players", "server"),
                ManhuntCommand.drillChildren(List.of("settings"), noLists()));
        assertEquals(List.of("hunters", "speedrunners"), ManhuntCommand.drillChildren(
                List.of("settings", "compass", "given-to"), noLists()));
    }

    @Test
    void childrenOfUnknownPrefixAreEmpty() {
        assertTrue(ManhuntCommand.drillChildren(List.of("bogus"), noLists()).isEmpty());
        assertTrue(ManhuntCommand.drillChildren(
                List.of("settings", "compass", "given-to", "hunters"), noLists()).isEmpty());
    }

    @Test
    void listChildrenOfferIndicesAndVerbs() {
        Function<String, List<String>> fixtures =
                lists(Map.of("match.end-statistics", List.of("a", "b")));

        assertEquals(List.of("0", "1", "add", "remove", "reset"),
                ManhuntCommand.drillChildren(List.of("match", "end-statistics"), fixtures));
    }

    @Test
    void indexPathResolvesAsLeaf() {
        Function<String, List<String>> fixtures =
                lists(Map.of("match.end-statistics", List.of("a", "b")));

        DrillResolve resolved = ManhuntCommand.resolveDrill(
                List.of("match", "end-statistics", "1"), fixtures);

        assertEquals("match.end-statistics.1", resolved.path());
        assertTrue(resolved.leaf());
        assertTrue(resolved.remainder().isEmpty());
    }

    @Test
    void indexOutOfRangeStopsAtList() {
        Function<String, List<String>> fixtures =
                lists(Map.of("match.end-statistics", List.of("a", "b")));

        DrillResolve resolved = ManhuntCommand.resolveDrill(
                List.of("match", "end-statistics", "7"), fixtures);

        assertEquals("match.end-statistics", resolved.path());
        assertFalse(resolved.leaf());
        assertTrue(resolved.section());
        assertEquals(List.of("7"), resolved.remainder());
    }

    @Test
    void entriesRenderKeyWhiteAndValueGray() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("settings.match.autostart.enabled", ": true");
        entries.put("settings", "");
        assertEquals("\n<green>» <white>settings.match.autostart.enabled</white><gray>: true</gray></white>"
                        + "\n<green>» <white>settings</white><gray></gray></white>",
                ManhuntCommand.renderEntries(
                        entries, "\n<green>» <white>{key}</white><gray>{suffix}</gray></white>"));
    }

    @Test
    void emptyEntriesRenderEmpty() {
        assertEquals("", ManhuntCommand.renderEntries(
                Map.of(), "\n<green>» <white>{key}</white><gray>{suffix}</gray></white>"));
    }

    @Test
    void registryChildrenSplitSectionsFromLeaves() {
        var settings = SettingRegistry.children("settings");
        assertEquals(List.of("compass", "match", "players", "server"), settings.sections());
        assertEquals(List.of(), settings.leaves());

        var givenTo = SettingRegistry.children("settings.compass.given-to");
        assertEquals(List.of(), givenTo.sections());
        assertEquals(List.of("hunters", "speedrunners"), givenTo.leaves());

        var missing = SettingRegistry.children("bogus");
        assertEquals(List.of(), missing.sections());
        assertEquals(List.of(), missing.leaves());

        var scalar = SettingRegistry.children("match.end-delay");
        assertEquals(List.of(), scalar.sections());
        assertEquals(List.of(), scalar.leaves());
    }
}
