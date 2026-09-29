package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Same-line evaluation order: flag writes earlier on the line are
 * visible to later {@code <if>}s and loops, while later writes stay
 * invisible to earlier readers. Guards nested-if eagerness,
 * conditional mutation, def-body verbatim storage, and def
 * rightmost-wins.
 */
class TagEvaluationOrderTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        final List<String> loopLimits = new ArrayList<>();
        final FlagStore flags = new FlagStore();

        TagContext context() {
            return TagContext.run(
                    ModifierTagScope.match("Steve", List.of(), new Random(11), warnings::add),
                    "order", messages::add, messages::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    (player, reason) -> { }, (role, reason) -> { },
                    7L, new TagBackends(StatValues.inert(), flags,
                            (text, name) -> text, RosterValues.inert(), PlayerSinks.inert()),
                    List.of(), loopLimits::add);
        }

        String replace(String command, TagContext context) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }
    }

    @Test
    void sameLineWriteVisibleToLaterIf() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("same",
                fixture.replace("<lf:x,5><if:\"<lf:x> == 5\",\"same\",\"stale\">", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void siblingIfsResolveLeftToRight() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("yes", fixture.replace(
                "<lf:x,1><if:\"1 == 1\",\"<lf:x,2>\"><if:\"<lf:x> == 2\",\"yes\",\"no\">",
                context));
    }

    @Test
    void ifInLoopBodyReadsBodyWrittenFlag() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace(
                "<for:[a],<lf:x,7><if:\"<lf:x> == 7\",\"<lf:saw,yes>\",\"<lf:saw,no>\">>",
                context));
        assertEquals("yes", fixture.replace("<lf:saw>", context));
    }

    @Test
    void nestedIfInDeadBranchStillSetsFlag() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("no", fixture.replace(
                "<if:\"1 == 2\",'<if:\"1 == 1\",\"<lf:y,9>\">',\"no\">", context));
        assertEquals("9", fixture.replace("<lf:y>", context));
    }

    @Test
    void branchMutatorWritesConditionally() {
        Fixture taken = new Fixture();
        TagContext takenContext = taken.context();

        assertEquals("", taken.replace(
                "<lf:m,[]><for:[a],<if:\"<i> == a\",\"<list.append:<lf:m>,<i>>\">>",
                takenContext));
        assertEquals("[a]", taken.replace("<lf:m>", takenContext));

        Fixture skipped = new Fixture();
        TagContext skippedContext = skipped.context();

        assertEquals("", skipped.replace(
                "<lf:m,[]><for:[a],<if:\"<i> == b\",\"<list.append:<lf:m>,<i>>\">>",
                skippedContext));
        assertEquals("[]", skipped.replace("<lf:m>", skippedContext));
    }

    @Test
    void duplicateDefRightmostWins() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("2", fixture.replace("<def:f,1><def:f,2><f:>", context));
    }

    @Test
    void defBodyWriteDoesNotFireAtDefTime() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:g,<lf:x,9>>", context));
        assertEquals("null", fixture.replace("<lf:x>", context));
    }

    @Test
    void reverseOrderWriteStaysInvisible() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("no",
                fixture.replace("<if:\"<lf:x> == 5\",\"yes\",\"no\"><lf:x,5>", context));
        assertEquals("5", fixture.replace("<lf:x>", context));
    }

    @Test
    void betweenSpanPlainLandsPositionally() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("bc", fixture.replace(
                "<if:\"1 == 2\",\"a\",\"b\"><lf:x,9><if:\"<lf:x> == 9\",\"c\",\"d\">",
                context));
    }
}
