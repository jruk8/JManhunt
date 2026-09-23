package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.command.DrillResolve;
import com.jruk8.jmanhunt.command.ManhuntCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the /manhunt config drill-down resolution. YamlConfiguration
 * is a pure YAML wrapper, so these tests run without a Bukkit server.
 */
class ConfigDrillTest {

    private static YamlConfiguration fixture() {
        YamlConfiguration root = new YamlConfiguration();
        root.set("settings.compass.given-to.hunters", true);
        root.set("settings.compass.given-to.speedrunners", false);
        root.set("settings.compass.hunter.max-distance.distance", -1.0);
        root.set("settings.autostart.enabled", false);
        root.set("match.end-delay", 10.0);
        root.set("config-version", 3);
        return root;
    }

    private static Set<String> editable() {
        return Set.of(
                "settings.compass.given-to.hunters",
                "settings.compass.given-to.speedrunners",
                "settings.autostart.enabled",
                "match.end-delay");
    }

    @Test
    void fullLeafPathResolvesWithEmptyRemainder() {
        DrillResolve resolved = ManhuntCommand.resolveDrill(
                fixture(), editable(), List.of("settings", "compass", "given-to", "hunters"));

        assertEquals("settings.compass.given-to.hunters", resolved.path());
        assertTrue(resolved.leaf());
        assertTrue(resolved.remainder().isEmpty());
    }

    @Test
    void trailingValueStaysAsRemainder() {
        DrillResolve resolved = ManhuntCommand.resolveDrill(
                fixture(), editable(), List.of("settings", "compass", "given-to", "hunters", "true"));

        assertEquals("settings.compass.given-to.hunters", resolved.path());
        assertTrue(resolved.leaf());
        assertEquals(List.of("true"), resolved.remainder());
    }

    @Test
    void sectionResolvesAsSection() {
        DrillResolve resolved = ManhuntCommand.resolveDrill(
                fixture(), editable(), List.of("settings", "compass"));

        assertEquals("settings.compass", resolved.path());
        assertFalse(resolved.leaf());
        assertTrue(resolved.section());
    }

    @Test
    void unknownFirstSegmentResolvesToNull() {
        assertNull(ManhuntCommand.resolveDrill(fixture(), editable(), List.of("bogus")));
    }

    @Test
    void unknownDeeperSegmentLeavesSectionRemainder() {
        DrillResolve resolved = ManhuntCommand.resolveDrill(
                fixture(), editable(), List.of("settings", "bogus"));

        assertEquals("settings", resolved.path());
        assertFalse(resolved.leaf());
        assertEquals(List.of("bogus"), resolved.remainder());
    }

    @Test
    void matchingIsCaseInsensitiveButCanonical() {
        DrillResolve resolved = ManhuntCommand.resolveDrill(
                fixture(), editable(), List.of("SETTINGS", "Compass", "Given-To", "HUNTERS"));

        assertEquals("settings.compass.given-to.hunters", resolved.path());
        assertTrue(resolved.leaf());
    }

    @Test
    void categoriesExcludeTheVersionKey() {
        List<String> categories = ManhuntCommand.drillCategories(fixture());

        assertEquals(List.of("match", "settings"), categories);
    }

    @Test
    void childrenOnlyOfferEditablePaths() {
        YamlConfiguration root = fixture();

        assertEquals(List.of("autostart", "compass"),
                ManhuntCommand.drillChildren(root, editable(), List.of("settings")));
        assertEquals(List.of("hunters", "speedrunners"),
                ManhuntCommand.drillChildren(root, editable(),
                        List.of("settings", "compass", "given-to")));
    }

    @Test
    void childrenHideScalarLeavesOutsideTheEditableSet() {
        YamlConfiguration root = fixture();

        // hunter.max-distance exists in config but is not editable, so the
        // compass level only offers given-to.
        assertEquals(List.of("given-to"),
                ManhuntCommand.drillChildren(root, editable(), List.of("settings", "compass")));
    }

    @Test
    void childrenOfUnknownPrefixAreEmpty() {
        assertTrue(ManhuntCommand.drillChildren(fixture(), editable(), List.of("bogus")).isEmpty());
    }

    @Test
    void entriesRenderKeyWhiteAndValueGray() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("settings.autostart.enabled", ": true");
        entries.put("settings", "");
        assertEquals("\n<green>» <white>settings.autostart.enabled</white><gray>: true</gray></white>"
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
    void nextSegmentsSplitSectionsFromLeaves() {
        var listing = ManhuntCommand.nextSegments(editable(), "settings");

        assertEquals(List.of("autostart", "compass"), listing.sections());
        assertEquals(List.of(), listing.leaves());
    }

    @Test
    void nextSegmentsHideNonEditableSubtrees() {
        // hunter.max-distance exists in config but is not editable, so the
        // compass level only lists given-to as a section.
        var listing = ManhuntCommand.nextSegments(editable(), "settings.compass");

        assertEquals(List.of("given-to"), listing.sections());
        assertEquals(List.of(), listing.leaves());
    }

    @Test
    void nextSegmentsListEditableLeaves() {
        var autostart = ManhuntCommand.nextSegments(editable(), "settings.autostart");
        assertEquals(List.of(), autostart.sections());
        assertEquals(List.of("enabled"), autostart.leaves());

        var givenTo = ManhuntCommand.nextSegments(editable(), "settings.compass.given-to");
        assertEquals(List.of(), givenTo.sections());
        assertEquals(List.of("hunters", "speedrunners"), givenTo.leaves());
    }

    @Test
    void nextSegmentsOfUnknownOrScalarPathAreEmpty() {
        var missing = ManhuntCommand.nextSegments(editable(), "bogus");
        assertEquals(List.of(), missing.sections());
        assertEquals(List.of(), missing.leaves());

        var scalar = ManhuntCommand.nextSegments(editable(), "match.end-delay");
        assertEquals(List.of(), scalar.sections());
        assertEquals(List.of(), scalar.leaves());
    }
}
