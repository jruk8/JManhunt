package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Vector math over a fake roster: goldens, primitives, shift, pdir, ploc. */
class TagVectorsTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final RosterValues roster;

        Fixture() {
            World world = mock(World.class);
            when(world.getName()).thenReturn("world");
            when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
            Location steve = new Location(world, 0, 64, 0, 90f, 12f);
            Location alex = new Location(world, 3, 68, 0, 0f, 0f);
            roster = new RosterValues() {
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
                    if (playerName.equalsIgnoreCase("Steve")) {
                        return Optional.of(steve);
                    }
                    if (playerName.equalsIgnoreCase("Alex")) {
                        return Optional.of(alex);
                    }
                    return Optional.empty();
                }

                @Override
                public Optional<Vector> lookDirection(String playerName) {
                    if (playerName.equalsIgnoreCase("Steve")) {
                        return Optional.of(new Vector(0, 2, 0));
                    }
                    if (playerName.equalsIgnoreCase("Alex")) {
                        return Optional.of(new Vector(0, 0, 0));
                    }
                    return Optional.empty();
                }
            };
        }

        TagContext context() {
            return TagContext.run(
                    ModifierTagScope.match("Steve", List.of(), new Random(29), warnings::add),
                    "vectors", warnings::add, warnings::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    (player, reason) -> { }, (role, reason) -> { },
                    7L, new TagBackends(StatValues.inert(), new FlagStore(),
                            (text, name) -> text, roster, PlayerSinks.inert()));
        }

        String replace(String command, TagContext context) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }
    }

    @Test
    void goldenVectorsPerOp() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[5, 7, 9]",
                fixture.replace("<vec.add:[1,2,3],[4,5,6]>", context));
        assertEquals("[3, 3, 3]",
                fixture.replace("<vec.sub:[4,5,6],[1,2,3]>", context));
        assertEquals("[0, 5, 0]",
                fixture.replace("<vec.mult:[0,1,0],5>", context));
        assertEquals("[0.6, 0, 0.8]",
                fixture.replace("<vec.normalize:[3,0,4]>", context));
        assertEquals("25",
                fixture.replace("<vec.sqrdist:[0,0,0],[3,4,0]>", context));
        assertEquals("5",
                fixture.replace("<vec.dist:[0,0,0],[3,4,0]>", context));
        assertEquals("32",
                fixture.replace("<vec.dot:[1,2,3],[4,5,6]>", context));
        assertEquals("[0, 0, 1]",
                fixture.replace("<vec.cross:[1,0,0],[0,1,0]>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void primitivesMatchTriples() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[5, 7, 9]",
                fixture.replace("<vec.add:[1,2,3,world,0,0],[4,5,6]>", context));
        assertEquals("32", fixture.replace(
                "<vec.dot:[1,2,3,world,0,0],[4,5,6,nether,0,0]>", context));
        assertEquals("[0.6, 0, 0.8]",
                fixture.replace("<vec.normalize:[3,0,4,end,0,0]>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void zeroVectorsStaySilent() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[0, 0, 0]",
                fixture.replace("<vec.normalize:[0,0,0]>", context));
        assertEquals("[0, 0, 0]", fixture.replace("<pdir:Alex>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void malformedInputsWarnWithNull() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<vec.add:[1,2],[3,4,5]>", context));
        assertEquals("null", fixture.replace("<vec.add:[1,2,3,4],[5,6,7]>", context));
        assertEquals("null", fixture.replace("<vec.add:plain,[1,2,3]>", context));
        assertEquals("null", fixture.replace("<vec.sub:[a,b,c],[1,2,3]>", context));
        assertEquals("null", fixture.replace("<vec.mult:[1,2,3],x>", context));
        assertEquals("null", fixture.replace("<vec.mult:[1,2,3]>", context));
        assertEquals("null", fixture.replace("<vec.normalize:[1,2,3,4,5]>", context));
        assertEquals(7, fixture.warnings.size());
    }

    @Test
    void shiftCarriesWorldAndAngles() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[0, 69, 0, world, 12, 90]", fixture.replace(
                "<loc.shift:[0,64,0,world,12,90],[0,1,0],5>", context));
        assertEquals("[0, 74, 0, world, 12, 90]", fixture.replace(
                "<loc.shift:[0,64,0,world,12,90],[0,2,0],5>", context));
        assertEquals("[1, 64, 0, nether, 0, 0]", fixture.replace(
                "<loc.shift:[0,64,0,nether,0,0],[1,0,0,end,0,0],1>", context));
        assertEquals("null", fixture.replace(
                "<loc.shift:[0,64,0],[0,1,0],5>", context));
        assertEquals("null", fixture.replace(
                "<loc.shift:[0,64,0,world,12,90],[0,1,0],far>", context));
        assertEquals("null", fixture.replace(
                "<loc.shift:[0,64,0,world,12,90],[0,1,0]>", context));
        assertEquals(3, fixture.warnings.size());
    }

    @Test
    void pdirNormalizesAndMissesSilently() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[0, 1, 0]", fixture.replace("<pdir:Steve>", context));
        assertEquals("null", fixture.replace("<pdir:Ghost>", context));
        assertEquals("null", fixture.replace("<pdir:>", context));
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void plocMatchesPlocation() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals(fixture.replace("<plocation:Steve>", context),
                fixture.replace("<ploc:Steve>", context));
        assertEquals("[0, 64, 0, world, 12, 90]",
                fixture.replace("<ploc:Steve>", context));
        assertEquals("null", fixture.replace("<ploc:Ghost>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void aimAndSpawnRecipesCompose() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[0.6, 0.8, 0]", fixture.replace(
                "<vec.normalize:<vec.sub:<ploc:Alex>,<ploc:Steve>>>", context));
        assertEquals("[0, 69, 0, world, 12, 90]", fixture.replace(
                "<loc.shift:<plocation:Steve>,<vec.normalize:<vec.mult:<pdir:Steve>,2>>,5>",
                context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }
}
