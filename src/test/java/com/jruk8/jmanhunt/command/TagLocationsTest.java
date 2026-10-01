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

/** Canonical location lists: x, y, z, world alias, pitch, yaw. */
class TagLocationsTest {

    @Test
    void formatsWholeCoordsBare() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        Location location = new Location(world, 100, 64, -30, 90f, 12f);

        assertEquals("[100, 64, -30, world, 12, 90]",
                TagLocations.formatLocation(location));
    }

    @Test
    void keepsFractionalCoords() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world_nether");
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        Location location = new Location(world, 100.5, 64.25, -30.75, 90.5f, 12.5f);

        assertEquals("[100.5, 64.25, -30.75, nether, 12.5, 90.5]",
                TagLocations.formatLocation(location));
    }

    @Test
    void worldAliasMapsEnvironments() {
        assertEquals("nether", TagLocations.worldAlias("NETHER", "world_nether"));
        assertEquals("nether", TagLocations.worldAlias("nether", "anything"));
        assertEquals("end", TagLocations.worldAlias("THE_END", "jmh_end_3"));
        assertEquals("end", TagLocations.worldAlias("the_end", "anything"));
        assertEquals("world", TagLocations.worldAlias("NORMAL", "world"));
        assertEquals("lobby", TagLocations.worldAlias("NORMAL", "lobby"));
        assertEquals("custom", TagLocations.worldAlias("CUSTOM", "custom"));
        assertEquals("custom", TagLocations.worldAlias(null, "custom"));
    }

    @Test
    void plocationFormatsOnlinePlayers() {
        List<String> warnings = new ArrayList<>();
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
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
        TagContext context = TagContext.run(new TagContext.TagIdentity(ModifierTagScope.match("Steve",
                List.of(), new Random(5), warnings::add), "locs"),
                TagContext.TagSinks.simple(warnings::add, warnings::add, (id, pitch, volume) -> { },
                        (id, pitch, volume) -> { },
                                ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add)),
                TagContext.TagRole.silent(),
                TagContext.TagMatch.simple(7L, new TagBackends(StatValues.inert(), new FlagStore(),
                        (text, name) -> text, roster, PlayerSinks.inert()),
                                (player, reason) -> { }, (role, reason) -> { }));

        assertEquals("[100, 64, -30, world, 12, 90]",
                CommandPlaceholders.replace("<plocation:Steve>", "Steve", 0, 0, 0, context));
        assertEquals("null",
                CommandPlaceholders.replace("<plocation:Ghost>", "Steve", 0, 0, 0, context));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void distanceMeasuresXyzOnly() {
        List<String> warnings = new ArrayList<>();
        TagContext context = TagContext.run(new TagContext.TagIdentity(ModifierTagScope.match("Steve",
                List.of(), new Random(5), warnings::add), "locs"),
                TagContext.TagSinks.simple(warnings::add, warnings::add, (id, pitch, volume) -> { },
                        (id, pitch, volume) -> { },
                                ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add)),
                TagContext.TagRole.silent(),
                TagContext.TagMatch.simple(7L, TagBackends.inert(), (player, reason) -> { }, (role, reason) -> { }));

        assertEquals("5", CommandPlaceholders.replace(
                "<distance:[0,0,0],[3,4,0]>", "Steve", 0, 0, 0, context));
        assertEquals("0", CommandPlaceholders.replace(
                "<distance:[1,2,3],[1,2,3]>", "Steve", 0, 0, 0, context));
        assertEquals("10", CommandPlaceholders.replace(
                "<distance:[0,0,0],[0,10,0]>", "Steve", 0, 0, 0, context));
        assertEquals("5", CommandPlaceholders.replace(
                "<distance:[0, 0, 0, world, 0, 0],[3, 4, 0, world, 90, 12]>",
                "Steve", 0, 0, 0, context));
        assertEquals("5", CommandPlaceholders.replace(
                "<distance:[0, 0, 0, world, 0, 0],[3, 4, 0]>",
                "Steve", 0, 0, 0, context));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void distanceYieldsNullSilentlyCrossDimension() {
        List<String> warnings = new ArrayList<>();
        TagContext context = TagContext.run(new TagContext.TagIdentity(ModifierTagScope.match("Steve",
                List.of(), new Random(5), warnings::add), "locs"),
                TagContext.TagSinks.simple(warnings::add, warnings::add, (id, pitch, volume) -> { },
                        (id, pitch, volume) -> { },
                                ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add)),
                TagContext.TagRole.silent(),
                TagContext.TagMatch.simple(7L, TagBackends.inert(), (player, reason) -> { }, (role, reason) -> { }));

        assertEquals("null", CommandPlaceholders.replace(
                "<distance:[0, 0, 0, world, 0, 0],[3, 4, 0, world_nether, 90, 12]>",
                "Steve", 0, 0, 0, context));
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void distanceYieldsNullSilentlyForNullSides() {
        List<String> warnings = new ArrayList<>();
        TagContext context = TagContext.run(new TagContext.TagIdentity(ModifierTagScope.match("Steve",
                List.of(), new Random(5), warnings::add), "locs"),
                TagContext.TagSinks.simple(warnings::add, warnings::add, (id, pitch, volume) -> { },
                        (id, pitch, volume) -> { },
                                ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add)),
                TagContext.TagRole.silent(),
                TagContext.TagMatch.simple(7L, TagBackends.inert(), (player, reason) -> { }, (role, reason) -> { }));

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
    void overlapFiltersRolesSortsNearestFirstAndCaps() {
        ProximityFixture fixture = new ProximityFixture();

        assertEquals("[Steve, Ned, Endy, Amy, Zoe]", fixture.replace(
                "<overlap-players:[0,64,0],ALL,200,5>", "Steve"));
        assertEquals("[Steve, Endy, Far]", fixture.replace(
                "<overlap-players:[0,64,0],HUNTER,200,5>", "Steve"));
        assertEquals("[Ned]", fixture.replace(
                "<overlap-players:[0,64,0],SPEEDRUNNER,4,5>", "Steve"));
        assertEquals("[Ned, Amy, Zoe]", fixture.replace(
                "<overlap-players:[0,64,0],SPEEDRUNNER,5,5>", "Steve"));
        assertEquals("[Steve, Ned]", fixture.replace(
                "<overlap-players:[0,64,0],ALL,200,2>", "Steve"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void overlapScopesToOriginWorld() {
        ProximityFixture fixture = new ProximityFixture();

        assertEquals("[Steve, Amy, Zoe, Far]", fixture.replace(
                "<overlap-players:[0,64,0,world,0,0],ALL,200,5>", "Steve"));
        assertEquals("[Ned]", fixture.replace(
                "<overlap-players:[0,64,0,nether,0,0],ALL,200,5>", "Steve"));
        assertEquals("[Endy]", fixture.replace(
                "<overlap-players:[0,64,0,end,0,0],ALL,200,5>", "Steve"));
        assertEquals("[]", fixture.replace(
                "<overlap-players:[0,64,0,void,0,0],ALL,200,5>", "Steve"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void overlapRejectsBadShapes() {
        ProximityFixture fixture = new ProximityFixture();

        assertEquals("null", fixture.replace(
                "<overlap-players:[0,64,0],REF,10,5>", "Steve"));
        assertEquals("null", fixture.replace(
                "<overlap-players:[0,64,0],HUNTER,-1,5>", "Steve"));
        assertEquals("null", fixture.replace(
                "<overlap-players:[0,64,0],HUNTER,10,0>", "Steve"));
        assertEquals("null", fixture.replace(
                "<overlap-players:plain,HUNTER,10,5>", "Steve"));
        assertEquals("null", fixture.replace(
                "<overlap-players:[0,64,0],HUNTER,10>", "Steve"));
        assertEquals(5, fixture.warnings.size());
    }

    @Test
    void nearbyExcludesSenderAndScopesWorld() {
        ProximityFixture fixture = new ProximityFixture();

        assertEquals("[Amy, Zoe, Far]", fixture.replace(
                "<nearby-players:Steve,ALL,200,5>", "Steve"));
        assertEquals("[Steve, Far]", fixture.replace(
                "<nearby-players:Steve,HUNTER,200,5>", null));
        assertEquals("null", fixture.replace(
                "<nearby-players:Ghost,ALL,200,5>", "Steve"));
        assertEquals("null", fixture.replace(
                "<nearby-players:,ALL,200,5>", "Steve"));
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void pworldReportsAliases() {
        ProximityFixture fixture = new ProximityFixture();

        assertEquals("world", fixture.replace("<pworld:Steve>", "Steve"));
        assertEquals("nether", fixture.replace("<pworld:Ned>", "Steve"));
        assertEquals("end", fixture.replace("<pworld:Endy>", "Steve"));
        assertEquals("nether", fixture.replace("<world:Ned>", "Steve"));
        assertEquals("null", fixture.replace("<pworld:Ghost>", "Steve"));
        assertEquals("null", fixture.replace("<pworld:>", "Steve"));
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void coordsReadLiveValues() {
        ProximityFixture fixture = new ProximityFixture();

        assertEquals("3", fixture.replace("<px:Amy>", "Steve"));
        assertEquals("64", fixture.replace("<py:Amy>", "Steve"));
        assertEquals("4", fixture.replace("<pz:Amy>", "Steve"));
        assertEquals("45.5", fixture.replace("<pyaw:Amy>", "Steve"));
        assertEquals("-7.25", fixture.replace("<ppitch:Amy>", "Steve"));
        assertEquals("null", fixture.replace("<px:Ghost>", "Steve"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void distanceRejectsMalformedLists() {
        List<String> warnings = new ArrayList<>();
        TagContext context = TagContext.run(new TagContext.TagIdentity(ModifierTagScope.match("Steve",
                List.of(), new Random(5), warnings::add), "locs"),
                TagContext.TagSinks.simple(warnings::add, warnings::add, (id, pitch, volume) -> { },
                        (id, pitch, volume) -> { },
                                ModifierTagScope.match("Steve", List.of(), new Random(5), warnings::add)),
                TagContext.TagRole.silent(),
                TagContext.TagMatch.simple(7L, TagBackends.inert(), (player, reason) -> { }, (role, reason) -> { }));

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

    private static final class ProximityFixture {
        private final List<String> warnings = new ArrayList<>();
        private final java.util.Map<String, Location> locations = new java.util.HashMap<>();
        private final RosterValues roster;

        private ProximityFixture() {
            World overworld = world("world", World.Environment.NORMAL);
            World nether = world("world_nether", World.Environment.NETHER);
            World end = world("jmh_end_3", World.Environment.THE_END);
            locations.put("Steve", new Location(overworld, 0, 64, 0, 90f, 12f));
            locations.put("Amy", new Location(overworld, 3, 64, 4, 45.5f, -7.25f));
            locations.put("Zoe", new Location(overworld, -3, 64, -4, 0f, 0f));
            locations.put("Ned", new Location(nether, 1, 64, 1, 0f, 0f));
            locations.put("Far", new Location(overworld, 100, 64, 0, 0f, 0f));
            locations.put("Endy", new Location(end, 2, 64, 0, 0f, 0f));
            roster = new RosterValues() {
                @Override
                public Optional<String> roleOf(String playerName) {
                    if (locations.containsKey(playerName)) {
                        return Optional.of("SPEEDRUNNER");
                    }
                    return Optional.empty();
                }

                @Override
                public List<String> activePlayers(String role) {
                    return List.of();
                }

                @Override
                public Optional<Location> locationOf(String playerName) {
                    return Optional.ofNullable(locations.get(playerName));
                }

                @Override
                public List<RosterValues.NearbyParticipant> nearbyParticipants() {
                    return List.of(
                            new RosterValues.NearbyParticipant(
                                    "Steve", "HUNTER", 0, 64, 0, "NORMAL", "world"),
                            new RosterValues.NearbyParticipant(
                                    "Amy", "SPEEDRUNNER", 3, 64, 4, "NORMAL", "world"),
                            new RosterValues.NearbyParticipant(
                                    "Zoe", "SPEEDRUNNER", -3, 64, -4, "NORMAL", "world"),
                            new RosterValues.NearbyParticipant(
                                    "Ned", "SPEEDRUNNER", 1, 64, 1, "NETHER", "world_nether"),
                            new RosterValues.NearbyParticipant(
                                    "Far", "HUNTER", 100, 64, 0, "NORMAL", "world"),
                            new RosterValues.NearbyParticipant(
                                    "Endy", "HUNTER", 2, 64, 0, "THE_END", "jmh_end_3"));
                }
            };
        }

        private static World world(String name, World.Environment environment) {
            World world = mock(World.class);
            when(world.getName()).thenReturn(name);
            when(world.getEnvironment()).thenReturn(environment);
            return world;
        }

        private String replace(String command, String sender) {
            TagContext context = TagContext.run(new TagContext.TagIdentity(ModifierTagScope.match(sender,
                    List.of(), new Random(5), warnings::add), "locs"),
                    TagContext.TagSinks.simple(warnings::add, warnings::add,
                            (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                            ModifierTagScope.match(sender, List.of(), new Random(5), warnings::add)),
                    TagContext.TagRole.silent(),
                    TagContext.TagMatch.simple(7L, new TagBackends(StatValues.inert(),
                            new FlagStore(),
                                    (text, name) -> text, roster, PlayerSinks.inert())
                                    , (player, reason) -> { }, (role, reason) -> { }));
            return CommandPlaceholders.replace(command, sender, 0, 0, 0, context);
        }
    }
}
