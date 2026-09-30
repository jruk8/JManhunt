package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Event args behind {@code <args:index>}: index parsing plus the
 * per-trigger arg table, resolved through constructed contexts.
 */
class TagArgsTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();

        TagContext context(List<String> eventArgs) {
            return TagContext.run(
                    ModifierTagScope.match("Steve", List.of(), new Random(7), warnings::add),
                    "beef", warnings::add, warnings::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    (player, reason) -> { }, (role, reason) -> { },
                    TagContext.NO_MATCH, TagBackends.inert(), eventArgs);
        }

        String replace(String command, List<String> eventArgs) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context(eventArgs));
        }
    }

    @Test
    void bareArgsReadsIndexZero() {
        Fixture fixture = new Fixture();

        assertEquals("Alex", fixture.replace("<args>", List.of("Alex")));
        assertEquals("Alex", fixture.replace("<args:0>", List.of("Alex")));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void indexesReadInOrder() {
        Fixture fixture = new Fixture();
        List<String> args = List.of("world", "world_nether");

        assertEquals("world", fixture.replace("<args:0>", args));
        assertEquals("world_nether", fixture.replace("<args:1>", args));
        assertEquals("world_nether", fixture.replace("<args: 1 >", args));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void missingIndexesQuietlyResolveNull() {
        Fixture fixture = new Fixture();

        assertEquals("null", fixture.replace("<args>", List.of()));
        assertEquals("null", fixture.replace("<args:0>", List.of()));
        assertEquals("null", fixture.replace("<args:3>", List.of("a", "b")));
        assertEquals("null", fixture.replace("<args:-1>", List.of("a", "b")));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void nonNumericIndexWarnsAndResolvesNull() {
        Fixture fixture = new Fixture();

        assertEquals("null", fixture.replace("<args:x>", List.of("a")));
        assertEquals("null", fixture.replace("<args:0,1>", List.of("a", "b")));
        assertEquals(2, fixture.warnings.size());
    }

    @Test
    void killTriggersCarryTheVictimName() {
        Fixture fixture = new Fixture();
        List<String> args = List.of("Alex");

        assertEquals("Alex", fixture.replace("<args:0>", args));
        assertEquals("null", fixture.replace("<args:1>", args));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void mobKillCarriesTheEntityTypeName() {
        Fixture fixture = new Fixture();

        assertEquals("ZOMBIE", fixture.replace("<args>", List.of("ZOMBIE")));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void portalTriggersCarryBothWorldNames() {
        Fixture fixture = new Fixture();
        List<String> args = List.of("world", "world_nether");

        assertEquals("world", fixture.replace("<args:0>", args));
        assertEquals("world_nether", fixture.replace("<args:1>", args));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void advancementTriggerCarriesTheKey() {
        Fixture fixture = new Fixture();

        assertEquals("minecraft:nether/root",
                fixture.replace("<args:0>", List.of("minecraft:nether/root")));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void intervalTriggerCarriesWaitedSeconds() {
        Fixture fixture = new Fixture();

        assertEquals("60", fixture.replace("<args>", List.of("60")));
        assertEquals("57.5", fixture.replace("<args:0>", List.of("57.5")));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void respawnTriggerCarriesOneDeathLocationList() {
        Fixture fixture = new Fixture();
        List<String> args = List.of("[100, 64, -30, world, 12.5, 90]");

        assertEquals("[100, 64, -30, world, 12.5, 90]", fixture.replace("<args:0>", args));
        assertEquals("null", fixture.replace("<args:1>", args));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void startTriggerCarriesNoArgs() {
        Fixture fixture = new Fixture();

        assertEquals("null", fixture.replace("<args>", List.of()));
        assertEquals("null", fixture.replace("<args:0>", List.of()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }
}
