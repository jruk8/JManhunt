package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Run-local functions: verbatim def storage, positional calls,
 * optional params, builtin refusal, substitution edges, and
 * recursion (direct, mutual, loop-mixed) under the shared budget.
 */
class TagFunctionsTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        final List<String> loopLimits = new ArrayList<>();
        final FlagStore flags = new FlagStore();

        TagContext context() {
            return TagContext.run(
                    ModifierTagScope.match("Steve", List.of(), new Random(11), warnings::add),
                    "funcs", messages::add, messages::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    (player, reason) -> { }, (role, reason) -> { },
                    7L, new TagBackends(StatValues.inert(), flags,
                            (text, name) -> text, RosterValues.inert()),
                    List.of(), loopLimits::add);
        }

        String replace(String command, TagContext context) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }
    }

    @Test
    void defStoresWithoutEvaluating() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:greet,<gmessage:hi>>", context));
        assertTrue(fixture.messages.isEmpty());
        assertEquals("", fixture.replace("<greet>", context));
        assertEquals(List.of("hi"), fixture.messages);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void defSkipsLoopsUntilCalled() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<gflag:n,0>", context));
        assertEquals("", fixture.replace("<def:bump,<while:<gflag:n> lt 3,<gflag:n,<gflag:n>+1>>>",
                context));
        assertEquals("0", fixture.replace("<gflag:n>", context));
        assertEquals("", fixture.replace("<bump>", context));
        assertEquals("3", fixture.replace("<gflag:n>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void callSubstitutesParams() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:double,x+x,x>", context));
        assertEquals("4", fixture.replace("<double:2>", context));
        assertEquals("go 4 done", fixture.replace("go <double:2> done", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void missingParamsBindNullSilently() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:pair,a and b,a,b>", context));
        assertEquals("1 and null", fixture.replace("<pair:1>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void extraArgsWarnAndIgnore() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:one,x,x>", context));
        assertEquals("1", fixture.replace("<one:1,2>", context));
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("extras ignored"), fixture.warnings.toString());
    }

    @Test
    void paramsMatchWholeTokensOutsideQuotes() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:echo,x says \"x\" loudly,x>", context));
        assertEquals("hi says \"x\" loudly", fixture.replace("<echo:hi>", context));
        assertEquals("", fixture.replace("<def:ids,x0 and x,x>", context));
        assertEquals("x0 and 9", fixture.replace("<ids:9>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void valuesNeverResubstitute() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:swap,a b,a,b>", context));
        assertEquals("b 1", fixture.replace("<swap:b,1>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void defRefusesBuiltins() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:abs,0>", context));
        assertEquals("", fixture.replace("<def:while,0>", context));
        assertEquals(2, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("builtin"), fixture.warnings.toString());
        assertEquals("4", fixture.replace("<abs:-4>", context));
    }

    @Test
    void defRejectsBadShapes() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:>", context));
        assertEquals("", fixture.replace("<def:9bad,x>", context));
        assertEquals("", fixture.replace("<def:ok,x,9bad>", context));
        assertEquals(3, fixture.warnings.size());
        assertEquals("<9bad:1>", fixture.replace("<9bad:1>", context));
        assertEquals("x", fixture.replace("<ok>", context));
    }

    @Test
    void unknownCallsSurviveUntouched() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("say <nope:1>!", fixture.replace("say <nope:1>!", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void callsAreCaseInsensitive() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:Shout,hey>", context));
        assertEquals("hey", fixture.replace("<SHOUT>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void defsPersistAcrossLinesButNotContexts() {
        Fixture fixture = new Fixture();
        TagContext first = fixture.context();

        assertEquals("", fixture.replace("<def:yo,hi>", first));
        assertEquals("hi", fixture.replace("<yo>", first));

        TagContext second = fixture.context();
        assertEquals("<yo>", fixture.replace("<yo>", second));
    }

    @Test
    void directRecursionCountsDown() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:countdown,<if:n le 0,done,<countdown:n-1>>,n>",
                context));
        assertEquals("done", fixture.replace("<countdown:3>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
        assertTrue(fixture.loopLimits.isEmpty());
    }

    @Test
    void mutualRecursionPingPongs() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<def:is_even,<if:n==0,true,<is_odd:n-1>>,n>", context));
        assertEquals("", fixture.replace("<def:is_odd,<if:n==0,false,<is_even:n-1>>,n>", context));
        assertEquals("true", fixture.replace("<is_even:4>", context));
        assertEquals("false", fixture.replace("<is_odd:4>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
        assertTrue(fixture.loopLimits.isEmpty());
    }

    @Test
    void infiniteRecursionHitsBudget() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();
        context.setProvenance(TagContext.Provenance.of("boom", 0, "console").withLine(1));

        assertEquals("", fixture.replace("<def:boom,<boom>>", context));
        assertEquals("null", fixture.replace("<boom>", context));
        assertEquals(1, fixture.loopLimits.size());
        String detail = fixture.loopLimits.get(0);
        assertTrue(detail.contains("boom"), detail);
        assertTrue(detail.contains("1000"), detail);
    }

    @Test
    void infiniteRecursionOnTinyStackReportsLoopLimit() throws Exception {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();
        context.setProvenance(TagContext.Provenance.of("boom", 0, "console").withLine(1));
        assertEquals("", fixture.replace("<def:boom,<boom>>", context));

        String[] result = new String[1];
        // A thread with an explicit tiny stack overflows before the
        // step budget trips; JVMs that ignore the size still pass
        // through the budget path with identical assertions.
        Thread eval = new Thread(null, () -> result[0] = fixture.replace("<boom>", context),
                "tiny-stack-eval", 256 * 1024L);
        eval.start();
        eval.join(30_000);

        assertEquals("null", result[0]);
        assertEquals(1, fixture.loopLimits.size());
        String detail = fixture.loopLimits.get(0);
        assertTrue(detail.contains("boom"), detail);
        assertTrue(detail.contains("1000"), detail);
    }

    @Test
    void loopPlusRecursionSharesOneBudget() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<gflag:n,0>", context));
        assertEquals("", fixture.replace(
                "<def:spin,<while:<gflag:n> lt 600,<gflag:n,<abs:<gflag:n>+1>>><gflag:n,0><spin>>",
                context));
        assertEquals("0", fixture.replace("<gflag:n>", context));

        // 600 loop steps plus a self-call fit; the second lap exhausts
        // the same 1000 budget instead of starting a fresh one. Both the
        // loop and the trailing call fail, but the line fires once.
        assertEquals("nullnull", fixture.replace("<spin>", context));
        assertEquals(1, fixture.loopLimits.size());
    }

    @Test
    void planePointDistance() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace(
                "<def:plane_point_dist,<abs:A*x0+B*y0+C*z0+D>/<sqrt:(A**2+B**2+C**2)>,"
                        + "A,B,C,D,x0,y0,z0>",
                context));
        // Substitution is textual like every other tag: -3**2 parses as
        // -(3**2) per engine math rules, so this call divides by sqrt(11).
        String result = fixture.replace("<plane_point_dist:2,-3,4,-6,1,2,3>", context);
        assertEquals(TagMath.formatNumber(2 / Math.sqrt(11)), result);
        assertTrue(result.startsWith("0.6030"), result);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void planePointDistanceParenthesized() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        // Math bodies parenthesize params so negative args bind as one value.
        assertEquals("", fixture.replace(
                "<def:ppd,<abs:(A)*x0+(B)*y0+(C)*z0+(D)>/<sqrt:((A)**2+(B)**2+(C)**2)>,"
                        + "A,B,C,D,x0,y0,z0>",
                context));
        String result = fixture.replace("<ppd:2,-3,4,-6,1,2,3>", context);
        assertEquals(TagMath.formatNumber(2 / Math.sqrt(29)), result);
        assertTrue(result.startsWith("0.3713"), result);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }
}
