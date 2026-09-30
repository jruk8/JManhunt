package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Canonical location lists: pitch before yaw, world as dimension. */
class TagLocationsTest {

    @Test
    void formatsWholeCoordsBare() {
        Location location = new Location(null, 100, 64, -30, 90f, 12f);

        assertEquals("[100, 64, -30, 12, 90, world]",
                TagLocations.formatLocation(location, "world"));
    }

    @Test
    void keepsFractionalCoords() {
        Location location = new Location(null, 100.5, 64.25, -30.75, 90.5f, 12.5f);

        assertEquals("[100.5, 64.25, -30.75, 12.5, 90.5, world_nether]",
                TagLocations.formatLocation(location, "world_nether"));
    }

    @Test
    void plocationFormatsOnlinePlayers() {
        List<String> warnings = new ArrayList<>();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        Location spot = new Location(world, 100, 64, -30, 90f, 12f);
        RosterValues roster = new RosterValues() {
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
                return "Steve".equals(playerName) ? Optional.of(spot) : Optional.empty();
            }
        };
        TagContext context = TagContext.run(
                ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add),
                "locs", warnings::add, warnings::add,
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                (player, reason) -> { }, (role, reason) -> { },
                7L, new TagBackends(StatValues.inert(), new FlagStore(),
                        (text, name) -> text, roster, PlayerSinks.inert()));

        assertEquals("[100, 64, -30, 12, 90, world]",
                CommandPlaceholders.replace("<plocation:Steve>", "Steve", 0, 0, 0, context));
        assertEquals("null",
                CommandPlaceholders.replace("<plocation:Ghost>", "Steve", 0, 0, 0, context));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void distanceMeasuresXyzOnly() {
        List<String> warnings = new ArrayList<>();
        TagContext context = TagContext.run(
                ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add),
                "locs", warnings::add, warnings::add,
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                (player, reason) -> { }, (role, reason) -> { },
                7L, TagBackends.inert());

        assertEquals("5", CommandPlaceholders.replace(
                "<distance:[0,0,0],[3,4,0]>", "Steve", 0, 0, 0, context));
        assertEquals("0", CommandPlaceholders.replace(
                "<distance:[1,2,3],[1,2,3]>", "Steve", 0, 0, 0, context));
        assertEquals("10", CommandPlaceholders.replace(
                "<distance:[0,0,0],[0,10,0]>", "Steve", 0, 0, 0, context));
        assertEquals("5", CommandPlaceholders.replace(
                "<distance:[0, 0, 0, 0, 0, world],[3, 4, 0, 90, 12, world]>",
                "Steve", 0, 0, 0, context));
        assertEquals("5", CommandPlaceholders.replace(
                "<distance:[0, 0, 0, 0, 0, world],[3, 4, 0]>",
                "Steve", 0, 0, 0, context));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void distanceYieldsNullSilentlyCrossDimension() {
        List<String> warnings = new ArrayList<>();
        TagContext context = TagContext.run(
                ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add),
                "locs", warnings::add, warnings::add,
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                (player, reason) -> { }, (role, reason) -> { },
                7L, TagBackends.inert());

        assertEquals("null", CommandPlaceholders.replace(
                "<distance:[0, 0, 0, 0, 0, world],[3, 4, 0, 90, 12, world_nether]>",
                "Steve", 0, 0, 0, context));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void distanceYieldsNullSilentlyForNullSides() {
        List<String> warnings = new ArrayList<>();
        TagContext context = TagContext.run(
                ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add),
                "locs", warnings::add, warnings::add,
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                (player, reason) -> { }, (role, reason) -> { },
                7L, TagBackends.inert());

        assertEquals("null", CommandPlaceholders.replace(
                "<distance:null,[3,4,0]>", "Steve", 0, 0, 0, context));
        assertEquals("null", CommandPlaceholders.replace(
                "<distance:[3,4,0],NULL>", "Steve", 0, 0, 0, context));
        assertEquals("null", CommandPlaceholders.replace(
                "<distance:\"\",[3,4,0]>", "Steve", 0, 0, 0, context));
        assertEquals("null", CommandPlaceholders.replace(
                "<distance:,[3,4,0]>", "Steve", 0, 0, 0, context));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void distanceRejectsMalformedLists() {
        List<String> warnings = new ArrayList<>();
        TagContext context = TagContext.run(
                ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add),
                "locs", warnings::add, warnings::add,
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                (player, reason) -> { }, (role, reason) -> { },
                7L, TagBackends.inert());

        assertEquals("null", CommandPlaceholders.replace(
                "<distance:plain,[3,4,0]>", "Steve", 0, 0, 0, context));
        assertEquals("null", CommandPlaceholders.replace(
                "<distance:[0,x,0],[3,4,0]>", "Steve", 0, 0, 0, context));
        assertEquals("null", CommandPlaceholders.replace(
                "<distance:[0,0],[3,4,0]>", "Steve", 0, 0, 0, context));
        assertEquals("null", CommandPlaceholders.replace(
                "<distance:[0,0,0]>", "Steve", 0, 0, 0, context));
        assertEquals(4, warnings.size());
    }
}
