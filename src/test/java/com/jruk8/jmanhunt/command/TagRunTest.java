package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Run tag over a recording context: dispatches the evaluated line
 * to the command sink, yields nothing, works inside loops.
 */
class TagRunTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        final TagContext context = TagContext.run(
                new TagContext.TagIdentity(
                        ModifierTagScope.match("Steve", List.of(), new Random(11),
                                warnings::add),
                        "funcs"),
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
                        (role, reason) -> { }));

        String replace(String command) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }
    }

    @Test
    void runDispatchesLineAndYieldsNothing() {
        Fixture fixture = new Fixture();

        assertEquals("", fixture.replace("<run:give Steve apple>"));
        assertEquals(List.of("give Steve apple"), fixture.commands);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void runResolvesNestedTagsFirst() {
        Fixture fixture = new Fixture();

        assertEquals("", fixture.replace("<run:give <p> apple>"));
        assertEquals(List.of("give Steve apple"), fixture.commands);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void runStripsLeadingSlash() {
        Fixture fixture = new Fixture();

        assertEquals("", fixture.replace("<run:/say hi>"));
        assertEquals(List.of("say hi"), fixture.commands);
    }

    @Test
    void runBlankIsSilentNoop() {
        Fixture fixture = new Fixture();

        assertEquals("", fixture.replace("<run:>"));
        assertEquals("", fixture.replace("<run:   >"));
        assertTrue(fixture.commands.isEmpty());
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void runNullWarnsAndSkips() {
        Fixture fixture = new Fixture();

        assertEquals("", fixture.replace("<run:null>"));
        assertTrue(fixture.commands.isEmpty());
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("pure \"null\""), fixture.warnings.toString());
    }

    @Test
    void runInsideForLoopDispatchesPerItem() {
        Fixture fixture = new Fixture();

        assertEquals("", fixture.replace("<for:[a,b],<run:say <i>>>"));
        assertEquals(List.of("say a", "say b"), fixture.commands);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void runInsideWhileLoopDispatchesPerIteration() {
        Fixture fixture = new Fixture();

        assertEquals("", fixture.replace("<gflag:n,0>"));
        assertEquals("", fixture.replace(
                "<while:<gflag:n> lt 2,<gflag:n,<gflag:n>+1><run:say <abs:<gflag:n>>>>"));
        assertEquals(List.of("say 0", "say 1"), fixture.commands);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void runWithoutSinkWarnsAndSkips() {
        List<String> warnings = new ArrayList<>();
        TagContext context = TagContext.of(new TagContext.TagIdentity(ModifierTagScope.match("Steve", List.of(),
                new Random(11), warnings::add), "plain"),
                text -> { }, text -> { },
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { });

        assertEquals("", CommandPlaceholders.replace("<run:say hi>", "Steve", 0, 0, 0, context));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("<run>"), warnings.toString());
    }
}
