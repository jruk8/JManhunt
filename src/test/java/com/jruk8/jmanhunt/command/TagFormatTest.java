package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code <format>}: plain {n} substitution, silent mismatches. */
class TagFormatTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final FlagStore flags = new FlagStore();

        TagContext context() {
            return TagContext.run(new TagContext.TagIdentity(ModifierTagScope.match("Steve", List.of(),
                    new Random(3), warnings::add), "format"),
                    TagContext.TagSinks.simple(warnings::add, warnings::add,
                            (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                            ModifierTagScope.match("Steve", List.of(), new Random(3), warnings::add)),
                    TagContext.TagRole.silent(),
                    new TagContext.TagMatch(7L, new TagBackends(StatValues.inert(), flags, (text,
                            name) -> text, RosterValues.inert(), PlayerSinks.inert()), List.of(), detail -> { },
                            detail -> { }, (player, reason) -> { }, (role, reason) -> { },
                            (player, role) -> { }));
        }

        String replace(String command, TagContext context) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }
    }

    @Test
    void substitutesByIndex() {
        Fixture fixture = new Fixture();

        assertEquals("Alex found gold", fixture.replace(
                "<format:\"{0} found {1}\",[Alex,gold]>", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void missingIndexStaysSilent() {
        Fixture fixture = new Fixture();

        assertEquals("a of {1}", fixture.replace(
                "<format:\"{0} of {1}\",[a]>", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void nonNumericBracesStayVerbatim() {
        Fixture fixture = new Fixture();

        assertEquals("{x} and a } {", fixture.replace(
                "<format:\"{x} and {0} } {\",[a]>", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void bareParamsActAsSingleItem() {
        Fixture fixture = new Fixture();

        assertEquals("hi Bob", fixture.replace("<format:\"hi {0}\",Bob>", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void multiItemListSurvivesSplitting() {
        Fixture fixture = new Fixture();

        assertEquals("a and b", fixture.replace(
                "<format:\"{0} and {1}\",[a,b]>", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void emptyStringFormatsEmpty() {
        Fixture fixture = new Fixture();

        assertEquals("", fixture.replace("<format:\"\",[a]>", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void valueHoldingPlaceholderIsNotRescanned() {
        Fixture fixture = new Fixture();

        assertEquals("{1}", fixture.replace("<format:\"{0}\",\"{1}\">", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void conversionSpecifiersStayLiteral() {
        Fixture fixture = new Fixture();

        assertEquals("{0:D}", fixture.replace("<format:\"{0:D}\",[5]>", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void nestedTagResolvesFirst() {
        Fixture fixture = new Fixture();

        assertEquals("Steve", fixture.replace("<format:\"{0}\",[<p>]>", fixture.context()));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void malformedShapeWarnsPlusNull() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<format:\"{0}\">", context));
        assertEquals("null", fixture.replace("<format:\"{0}\",[a],extra>", context));
        assertEquals(2, fixture.warnings.size());
        assertTrue(TagFormat.syntaxError("format", "\"{0} found {1}\",[Alex,gold]").isEmpty());
        assertTrue(TagFormat.syntaxError("format", "\"{0}\"").isPresent());
    }
}
