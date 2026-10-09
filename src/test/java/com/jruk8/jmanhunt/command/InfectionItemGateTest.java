package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Infection item gate: the nested single-quoted if-in-for with the gt word
 * operator grants per-candidate items gated by give_fail_chance.
 */
class InfectionItemGateTest {

    private static final String ITEM_LINE = "<if:\"<lflag:items> == true\","
            + "\"<for:<lflag:item_list>,<if:'<random-num:1,100> gt <lflag:give_fail_chance>',"
            + "'<run:give <p> <list.get:<i>,0> "
            + "<random-num:<list.get:<i>,1>,<list.get:<i>,2>>>'>>\">";

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        final TagContext context = TagContext.run(
                new TagContext.TagIdentity(
                        ModifierTagScope.match("Steve", List.of(), new Random(11),
                                warnings::add),
                        "infection"),
                new TagContext.TagSinks(text -> { }, text -> { },
                        (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                        (line, provenance) -> commands.add(line)),
                new TagContext.TagRole((role, text) -> { },
                        (role, id, pitch, volume) -> { }),
                new TagContext.TagMatch(7L,
                        new TagBackends(StatValues.inert(), new FlagStore(),
                                (text, name) -> text, RosterValues.inert(),
                                PlayerSinks.inert()),
                        List.of(), detail -> { }, (player, reason) -> { },
                        (role, reason) -> { }, (player, role) -> { }));

        String replace(String command) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }

        void setupItemFlags(int failChance) {
            assertEquals("", replace("<lflag:items,true>"));
            assertEquals("", replace("<lflag:item_list,[[rotten_flesh, 3, 7], [bone, 2, 4]]>"));
            assertEquals("", replace("<lflag:give_fail_chance," + failChance + ">"));
        }
    }

    @Test
    void zeroFailChanceGivesOnePerCandidate() {
        Fixture fixture = new Fixture();
        fixture.setupItemFlags(0);

        assertEquals("", fixture.replace(ITEM_LINE));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
        assertEquals(2, fixture.commands.size(), fixture.commands.toString());
        assertGive(fixture.commands.get(0), "rotten_flesh", 3, 7);
        assertGive(fixture.commands.get(1), "bone", 2, 4);
    }

    @Test
    void fullFailChanceGivesNothing() {
        Fixture fixture = new Fixture();
        fixture.setupItemFlags(100);

        assertEquals("", fixture.replace(ITEM_LINE));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
        assertTrue(fixture.commands.isEmpty(), fixture.commands.toString());
    }

    @Test
    void fullHunterListFiresForInfectedHunter() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<list.append:<gflag:infected>,Steve>"));
        fixture.setupItemFlags(0);
        assertEquals("", fixture.replace("<lflag:effects,true>"));
        assertEquals("", fixture.replace("<lflag:effect_list,[[blindness, 0, 5]]>"));

        assertEquals("", fixture.replace(
                "<if:\"<list.contains:<default:<gflag:infected>,[]>,<p>> != true\",\"exit\">"));
        assertEquals("", fixture.replace(
                "<if:\"<lflag:effects> == true and <random-num:1,3> == 1\","
                        + "\"<for:<lflag:effect_list>,<run:effect give <p> <list.get:<i>,0> "
                        + "<list.get:<i>,2> <list.get:<i>,1> true>>\">"));
        int afterEffects = fixture.commands.size();
        assertEquals("", fixture.replace(ITEM_LINE));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
        assertEquals(afterEffects + 2, fixture.commands.size(), fixture.commands.toString());
    }

    private static void assertGive(String command, String item, int min, int max) {
        String[] parts = command.split(" ");
        assertEquals(4, parts.length, command);
        assertEquals("give", parts[0]);
        assertEquals("Steve", parts[1]);
        assertEquals(item, parts[2]);
        int count = Integer.parseInt(parts[3]);
        assertTrue(count >= min && count <= max, command);
    }
}
