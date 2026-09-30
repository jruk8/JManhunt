package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** String ops: join, literal split, case folding, and contains. */
class TagStringsTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();

        TagContext context() {
            return TagContext.run(
                    ModifierTagScope.match("Steve", List.of(), new Random(21), warnings::add),
                    "strings", warnings::add, warnings::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    (player, reason) -> { }, (role, reason) -> { },
                    7L, new TagBackends(StatValues.inert(), new FlagStore(),
                            (text, name) -> text, RosterValues.inert(),
                            PlayerSinks.inert()));
        }

        String replace(String command, TagContext context) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }
    }

    @Test
    void joinSplitRoundTrip() {
        List<String> items = List.of("a", "", "c");

        assertEquals(items, TagStrings.split(TagStrings.join(items, ","), ","));
        assertEquals(List.of("a", "b"), TagStrings.split("a,,b", ",,"));
        assertEquals("a-b", TagStrings.join(List.of("a", "b"), "-"));
    }

    @Test
    void tagsJoinSplitFoldAndFind() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("a,b", fixture.replace("<str.join:[a,b],\",\">", context));
        assertEquals("[a, b]", fixture.replace("<str.split:\"a,b\",\",\">", context));
        assertEquals("[a, , c]", fixture.replace("<str.split:\"a,,c\",\",\">", context));
        assertEquals("abc", fixture.replace("<str.lower:AbC>", context));
        assertEquals("ABC", fixture.replace("<str.upper:AbC>", context));
        assertEquals("true", fixture.replace("<str.contains:hello,ell>", context));
        assertEquals("false", fixture.replace("<str.contains:hello,ELL>", context));
        assertEquals("", fixture.replace("<gflag:csv,\"a,b\">", context));
        assertEquals("[a, b]", fixture.replace("<str.split:\"<gflag:csv>\",\",\">", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void misuseWarnsWithNull() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<str.join:plain,->", context));
        assertEquals("null", fixture.replace("<str.split:a,>", context));
        assertEquals("null", fixture.replace("<str.join:[a]>", context));
        assertEquals("null", fixture.replace("<str.contains:a>", context));
        assertEquals(4, fixture.warnings.size());
    }
}
