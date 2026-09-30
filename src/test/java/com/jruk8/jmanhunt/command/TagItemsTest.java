package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Inventory tag over a fake roster: counts, defaults, warnings. */
class TagItemsTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final Map<String, Integer> counts = new HashMap<>();
        final Map<String, String> held = new HashMap<>();
        final RosterValues roster = new RosterValues() {
            @Override
            public Optional<String> roleOf(String playerName) {
                return Optional.empty();
            }

            @Override
            public List<String> activePlayers(String role) {
                return List.of();
            }

            @Override
            public Optional<Location> locationOf(String playerName) {
                return Optional.empty();
            }

            @Override
            public Optional<Integer> countItem(String playerName, String materialKey) {
                return Optional.ofNullable(counts.get(playerName + "|" + materialKey));
            }

            @Override
            public Optional<String> heldItem(String playerName) {
                return Optional.ofNullable(held.get(playerName));
            }
        };

        TagContext context() {
            return TagContext.run(
                    ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add),
                    "items", warnings::add, warnings::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    (player, reason) -> { }, (role, reason) -> { },
                    7L, new TagBackends(StatValues.inert(), new FlagStore(),
                            (text, name) -> text, roster, PlayerSinks.inert()));
        }

        String replace(String command) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context());
        }
    }

    @Test
    void hasItemComparesAgainstQty() {
        Fixture fixture = new Fixture();
        fixture.counts.put("Steve|golden_apple", 3);

        assertEquals("true", fixture.replace("<phasitem:Steve,golden_apple,2>"));
        assertEquals("true", fixture.replace("<phasitem:Steve,golden_apple,3>"));
        assertEquals("false", fixture.replace("<phasitem:Steve,golden_apple,4>"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void hasItemDefaultsQtyToOne() {
        Fixture fixture = new Fixture();
        fixture.counts.put("Steve|golden_apple", 1);

        assertEquals("true", fixture.replace("<phasitem:Steve,golden_apple>"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void hasItemResolvesNestedPlayer() {
        Fixture fixture = new Fixture();
        fixture.counts.put("Steve|golden_apple", 1);

        assertEquals("true", fixture.replace("<phasitem:<p>,golden_apple>"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void hasItemWarnsAndYieldsFalse() {
        Fixture fixture = new Fixture();
        fixture.counts.put("Steve|golden_apple", 1);

        assertEquals("false", fixture.replace("<phasitem:Alex,golden_apple>"));
        assertEquals("false", fixture.replace("<phasitem:Steve>"));
        assertEquals("false", fixture.replace("<phasitem:Steve,golden_apple,0>"));
        assertEquals("false", fixture.replace("<phasitem:Steve,golden_apple,many>"));
        assertEquals(4, fixture.warnings.size());
    }

    @Test
    void heldReportsMainHandMaterial() {
        Fixture fixture = new Fixture();
        fixture.held.put("Steve", "DIAMOND_SWORD");

        assertEquals("DIAMOND_SWORD", fixture.replace("<pheld:Steve>"));
        assertEquals("null", fixture.replace("<pheld:Ghost>"));
        assertEquals("null", fixture.replace("<pheld:>"));
        assertEquals("null", fixture.replace("<pheld:Steve,extra>"));
        assertEquals(2, fixture.warnings.size());
    }

    @Test
    void normalizeMaterialKeyStripsPrefixAndCases() {
        assertEquals("GOLDEN_APPLE", TagItems.normalizeMaterialKey("golden_apple"));
        assertEquals("GOLDEN_APPLE", TagItems.normalizeMaterialKey("minecraft:golden_apple"));
        assertEquals("GOLDEN_APPLE", TagItems.normalizeMaterialKey("Minecraft:GOLDEN_APPLE"));
        assertEquals("GOLDEN_APPLE", TagItems.normalizeMaterialKey("  GOLDEN_APPLE  "));
    }
}
