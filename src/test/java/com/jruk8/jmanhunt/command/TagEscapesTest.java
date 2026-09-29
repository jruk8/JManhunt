package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Escapes end to end: literal output, silent passes, restored sinks. */
class TagEscapesTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final List<String> globalMessages = new ArrayList<>();
        final List<String> namedMessages = new ArrayList<>();
        final PlayerSinks players = new PlayerSinks() {
            @Override
            public boolean message(String playerName, String text) {
                namedMessages.add(playerName + ":" + text);
                return true;
            }

            @Override
            public boolean sound(String playerName, String soundId, float pitch, float volume) {
                return true;
            }
        };
        final FlagStore flags = new FlagStore();

        TagContext context() {
            return TagContext.run(
                    ModifierTagScope.match("Steve", List.of(), new Random(9), warnings::add),
                    "escapes", globalMessages::add, globalMessages::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    (player, reason) -> { }, (role, reason) -> { },
                    7L, new TagBackends(StatValues.inert(), flags,
                            (text, name) -> text, RosterValues.inert(), players),
                    List.of(), detail -> { });
        }

        String replace(String command, TagContext context) {
            return CommandPlaceholders.replace(command, "Steve", 10, 20, 30, context);
        }
    }

    @Test
    void pmessageDeliversMiniMessageYellow() {
        Fixture fixture = new Fixture();

        assertEquals("", fixture.replace(
                "<pmessage:Steve,\\<yellow\\>hi>", fixture.context()));
        assertEquals(List.of("Steve:<yellow>hi"), fixture.namedMessages);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void escapedCommaJoinsArgs() {
        Fixture fixture = new Fixture();

        assertEquals("", fixture.replace(
                "<pmessage:Steve,hi\\,there>", fixture.context()));
        assertEquals(List.of("Steve:hi,there"), fixture.namedMessages);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void escapedPlayerTagStaysLiteral() {
        Fixture fixture = new Fixture();

        assertEquals("[<p>]", fixture.replace("[\\<p\\>]", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void backslashCollapsesInOutput() {
        Fixture fixture = new Fixture();

        assertEquals("a\\b", fixture.replace("a\\\\b", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void escapedMathStaysLiteral() {
        Fixture fixture = new Fixture();

        assertEquals("2+2", fixture.replace("2\\+2", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void escapedTagsInConditionCompareAsText() {
        Fixture fixture = new Fixture();

        assertEquals("same", fixture.replace(
                "<if:\"\\<p\\> == \\<p\\>\",\"same\",\"diff\">", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void escapedListCommaCountsOneItem() {
        Fixture fixture = new Fixture();

        assertEquals("1", fixture.replace("<len:[a\\,b]>", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void escapedSelectorStaysLiteral() {
        Fixture fixture = new Fixture();

        assertEquals("@a", fixture.replace("\\@a", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void escapedTildeStaysLiteral() {
        Fixture fixture = new Fixture();

        assertEquals("~", fixture.replace("\\~", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void doubleBackslashYellowWarnsUnknownTag() {
        assertTrue(CommandSyntax.error("say \\\\<yellow\\\\>").isEmpty());
        assertEquals(1, CommandSyntax.warnings("say \\\\<yellow\\\\>").size());
        assertTrue(CommandSyntax.warnings("say \\\\<yellow\\\\>").get(0).contains("Unknown tag"));
    }

    @Test
    void singleEscapedYellowPassesValidation() {
        assertTrue(CommandSyntax.error("say \\<yellow\\>").isEmpty());
        assertTrue(CommandSyntax.warnings("say \\<yellow\\>").isEmpty());
    }

    @Test
    void flagSetReadRoundTrip() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<lf:x,5>", context));
        assertEquals("5", fixture.replace("<lf:x>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void escapedBracesSurviveFormat() {
        Fixture fixture = new Fixture();

        assertEquals("{0}", fixture.replace("<format:\"\\{0\\}\",[a]>", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }
}
