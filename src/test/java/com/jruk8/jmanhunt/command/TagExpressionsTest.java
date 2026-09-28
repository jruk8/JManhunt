package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Extended tags over a recording context: if branches, min/max/clamp,
 * messages, sounds, id, and exit lines.
 */
class TagExpressionsTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final List<String> globalMessages = new ArrayList<>();
        final List<String> playerMessages = new ArrayList<>();
        final List<String> globalSounds = new ArrayList<>();
        final List<String> playerSounds = new ArrayList<>();
        final TagContext context = TagContext.of(
                ModifierTagScope.match("Steve", List.of(), new Random(7), warnings::add),
                "beef",
                globalMessages::add,
                playerMessages::add,
                (id, pitch, volume) -> globalSounds.add(id + ":" + pitch + ":" + volume),
                (id, pitch, volume) -> playerSounds.add(id + ":" + pitch + ":" + volume));
    }

    private static String replace(Fixture fixture, String command) {
        return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, fixture.context);
    }

    @Test
    void ifBranchesOnTrueFalseAndMissingElse() {
        Fixture fixture = new Fixture();
        assertEquals("yes", replace(fixture, "<if:\"1 == 1\",\"yes\",\"no\">"));
        assertEquals("no", replace(fixture, "<if:\"1 == 2\",\"yes\",\"no\">"));
        assertEquals("", replace(fixture, "<if:\"1 == 2\",\"yes\">"));
        assertEquals("yes", replace(fixture, "<if:\"1 == 1\",\"yes\">"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void ifComparesOrdersStringsAndMath() {
        Fixture fixture = new Fixture();
        assertEquals("y", replace(fixture, "<if:\"7 le 7\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"8 gt 7\",\"y\",\"n\">"));
        assertEquals("n", replace(fixture, "<if:\"8 lt 7\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"8 ge 9\",\"n\",\"y\">"));
        assertEquals("y", replace(fixture, "<if:\"7 LE 7\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"apple == apple\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"apple != orange\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"8+5 == 13\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"(2+3)*4 == 20\",\"y\",\"n\">"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void wordOperatorsNeedBoundaries() {
        Fixture fixture = new Fixture();
        assertEquals("y", replace(fixture, "<if:\"elegant == elegant\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"alt == alt\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"glee != gloom\",\"y\",\"n\">"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
        assertFalse(TagExpressions.hasComparison("elegant"));
        assertFalse(TagExpressions.hasComparison("alt"));
    }

    @Test
    void ifAndBindsTighterThanOr() {
        Fixture fixture = new Fixture();
        assertEquals("n", replace(fixture, "<if:\"1 == 2 or 1 == 1 and 2 == 3\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"1 == 2 or 1 == 1 and 3 == 3\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"1 == 1 AND 2 == 2\",\"y\",\"n\">"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void ifNotNegatesWithTightestBinding() {
        Fixture fixture = new Fixture();
        assertEquals("y", replace(fixture, "<if:\"not true == false\",\"y\",\"n\">"));
        assertEquals("n", replace(fixture, "<if:\"not true == true\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"NOT 1 == 2\",\"y\",\"n\">"));
        assertEquals("n", replace(fixture, "<if:\"not not 1 == 2\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"not 8 lt 7\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"not 8 gt 7\",\"n\",\"y\">"));
        assertEquals("n", replace(fixture, "<if:\"not 1 == 1 and 2 == 2\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"not 1 == 2 and 2 == 2\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"1 == 2 or not 3 == 4\",\"y\",\"n\">"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void ifNotNeedsWordBoundariesAndAnOperand() {
        Fixture fixture = new Fixture();
        assertEquals("y", replace(fixture, "<if:\"notable == notable\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"knot == knot\",\"y\",\"n\">"));
        assertEquals("", replace(fixture, "<if:\"not\",\"y\",\"n\">"));
        assertEquals("", replace(fixture, "<if:\"1 == 1 and not\",\"y\",\"n\">"));
        assertEquals(2, fixture.warnings.size());
    }

    @Test
    void ifEvaluatesNestedTagsFirst() {
        Fixture fixture = new Fixture();
        assertEquals("yes", replace(fixture, "<if:\"<random-num:5,5> == 5\",\"yes\",\"no\">"));
        assertEquals("Steve", replace(fixture, "<if:\"1 == 1\",\"<p>\",\"nobody\">"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void ifSurvivingNestedTagsCompareAsText() {
        Fixture fixture = new Fixture();
        assertEquals("y", replace(fixture, "<if:\"<flag:a> == <flag:a>\",\"y\",\"n\">"));
        assertEquals("n", replace(fixture, "<if:\"<flag:a> == <flag:b>\",\"y\",\"n\">"));
        assertEquals("", replace(fixture, "<if:\"<flag:a> gt 1\",\"y\",\"n\">"));
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void ifNestedIfInsideConditionResolvesInsideOut() {
        Fixture fixture = new Fixture();
        assertEquals("Y", replace(fixture, "<if:\"<if:1==1,y,n> == y\",\"Y\",\"N\">"));
        assertEquals("N", replace(fixture, "<if:\"<if:1==2,y,n> == y\",\"Y\",\"N\">"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void hasComparisonSkipsNestedTags() {
        assertTrue(TagExpressions.hasComparison("<flag:a> == 1"));
        assertTrue(TagExpressions.hasComparison("7 le 5"));
        assertFalse(TagExpressions.hasComparison("<flag:a>"));
        assertFalse(TagExpressions.hasComparison("<papi:x==y>"));
        assertFalse(TagExpressions.hasComparison("\"7 le 5\""));
        assertFalse(TagExpressions.hasComparison("7 <= 5"));
        assertFalse(TagExpressions.hasComparison("a > b"));
    }

    @Test
    void wordOperatorsSurviveLaterClosingBracket() {
        assertTrue(TagExpressions.hasComparison(
                "<pstat:<p>,health> le 7 and <gstat:duration>-<pflag:x> ?? 999999 gt 300"));
        assertTrue(TagExpressions.hasComparison("7 le 5 or a gt b"));
    }

    @Test
    void ifCoalesceExampleFromGappleShape() {
        Fixture fixture = new Fixture();
        assertEquals("n", replace(fixture,
                "<if:\"7 le 7 and '100-500 ?? -1' gt 300\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture,
                "<if:\"7 le 7 and '500-100 ?? -1' gt 300\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"null ?? 5 gt 3\",\"y\",\"n\">"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void ifStructuralProblemsYieldEmptyWithWarning() {
        Fixture fixture = new Fixture();
        assertEquals("", replace(fixture, "<if:\"1 == 1\">"));
        assertEquals("", replace(fixture, "<if:\"abc\",\"y\">"));
        assertEquals("", replace(fixture, "<if:\"2.5 gt 1\",\"y\",\"n\">"));
        assertEquals("", replace(fixture, "<if:\"1 == 1\",\"a\",\"b\",\"c\">"));
        assertEquals(4, fixture.warnings.size());
    }

    @Test
    void anglesAreNotComparisons() {
        Fixture fixture = new Fixture();
        assertEquals("", replace(fixture, "<if:\"7<=7\",\"y\",\"n\">"));
        assertEquals("", replace(fixture, "<if:\"7 <=7\",\"y\",\"n\">"));
        assertEquals("", replace(fixture, "<if:\"7< 7\",\"y\",\"n\">"));
        assertEquals("", replace(fixture, "<if:\"8 > 7\",\"y\",\"n\">"));
        assertEquals("", replace(fixture, "<if:\"7 <= 7\",\"y\",\"n\">"));
        assertEquals(5, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("use lt, le, gt, ge"),
                fixture.warnings.toString());
    }

    @Test
    void minMaxClampEvaluateArgs() {
        Fixture fixture = new Fixture();
        assertEquals("3", replace(fixture, "<min:8,3>"));
        assertEquals("8", replace(fixture, "<max:8,3>"));
        assertEquals("10", replace(fixture, "<min:8+5,10>"));
        assertEquals("5", replace(fixture, "<clamp:8,1,5>"));
        assertEquals("8", replace(fixture, "<clamp:8,1,10>"));
        assertEquals("1", replace(fixture, "<clamp:-4,1,10>"));
        assertEquals("2.5", replace(fixture, "<max:2.5,2>"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void minMaxClampRejectBadShapes() {
        Fixture fixture = new Fixture();
        assertEquals("0", replace(fixture, "<min:8>"));
        assertEquals("0", replace(fixture, "<clamp:8,1>"));
        assertEquals("0", replace(fixture, "<max:abc,5>"));
        assertEquals("0", replace(fixture, "<min:8,abc>"));
        assertEquals(4, fixture.warnings.size());
    }

    @Test
    void mathUnaryRoundsAndSigns() {
        Fixture fixture = new Fixture();
        assertEquals("2", replace(fixture, "<floor:2.7>"));
        assertEquals("-3", replace(fixture, "<floor:-2.3>"));
        assertEquals("3", replace(fixture, "<ceil:2.3>"));
        assertEquals("-2", replace(fixture, "<ceil:-2.7>"));
        assertEquals("3", replace(fixture, "<round:2.5>"));
        assertEquals("2", replace(fixture, "<round:2.4>"));
        assertEquals("-2", replace(fixture, "<round:-2.5>"));
        assertEquals("4", replace(fixture, "<abs:-4>"));
        assertEquals("4.5", replace(fixture, "<abs:-4.5>"));
        assertEquals("-1", replace(fixture, "<sign:-4>"));
        assertEquals("0", replace(fixture, "<sign:0>"));
        assertEquals("1", replace(fixture, "<sign:0.5>"));
        assertEquals("3", replace(fixture, "<floor:7/2>"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void mathUnaryNullOnBadInput() {
        Fixture fixture = new Fixture();
        assertEquals("null", replace(fixture, "<floor:abc>"));
        assertEquals("null", replace(fixture, "<abs:null>"));
        assertEquals("null", replace(fixture, "<sign:1,2>"));
        assertEquals("null", replace(fixture, "<ceil:>"));
        assertEquals(4, fixture.warnings.size());
    }

    @Test
    void roleMessageAndSoundUseRoleSinks() {
        List<String> warnings = new ArrayList<>();
        List<String> roleMessages = new ArrayList<>();
        List<String> roleSounds = new ArrayList<>();
        TagContext context = TagContext.run(
                ModifierTagScope.match("Steve", List.of(), new Random(7), warnings::add),
                "beef", warnings::add, warnings::add,
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                (player, reason) -> { }, (role, reason) -> { },
                7L, TagBackends.inert(), List.of(), detail -> { },
                roleMessages::add,
                (id, pitch, volume) -> roleSounds.add(id + ":" + pitch + ":" + volume));

        assertEquals("", CommandPlaceholders.replace("<rmessage:hi>", "Steve", 0, 0, 0, context));
        assertEquals("", CommandPlaceholders.replace(
                "<rsound:block.stone.break,0.5,2>", "Steve", 0, 0, 0, context));
        assertEquals("", CommandPlaceholders.replace("<rsound:block.stone.break>", "Steve", 0, 0, 0, context));
        assertEquals(List.of("hi"), roleMessages);
        assertEquals(List.of("block.stone.break:0.5:2.0", "block.stone.break:1.0:1.0"), roleSounds);
        assertTrue(warnings.isEmpty(), warnings.toString());
    }

    @Test
    void messagesSendThroughSinksAndReturnEmpty() {
        Fixture fixture = new Fixture();
        assertEquals("say  done", replace(fixture, "say <gmessage:\"hi\"> done"));
        assertEquals(List.of("hi"), fixture.globalMessages);
        assertEquals("", replace(fixture, "<pmessage:yo>"));
        assertEquals(List.of("yo"), fixture.playerMessages);
        assertEquals("", replace(fixture, "<gmessage:\"\">"));
        assertEquals(List.of("hi", ""), fixture.globalMessages);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void soundsPlayThroughSinksWithDefaults() {
        Fixture fixture = new Fixture();
        assertEquals("", replace(fixture, "<gsound:block.stone.break>"));
        assertEquals("", replace(fixture, "<psound:block.stone.break,0.5,2>"));
        assertEquals(List.of("block.stone.break:1.0:1.0"), fixture.globalSounds);
        assertEquals(List.of("block.stone.break:0.5:2.0"), fixture.playerSounds);
        assertEquals("", replace(fixture, "<gsound:block.stone.break,loud>"));
        assertEquals(1, fixture.warnings.size());
        assertEquals(List.of("block.stone.break:1.0:1.0", "block.stone.break:1.0:1.0"),
                fixture.globalSounds);
    }

    @Test
    void idReturnsContainer() {
        Fixture fixture = new Fixture();
        assertEquals("beef", replace(fixture, "<id>"));
        assertEquals("lastuse-beef", replace(fixture, "lastuse-<id>"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void exitDetection() {
        assertTrue(TagExpressions.isExit("exit"));
        assertTrue(TagExpressions.isExit(" exit "));
        assertTrue(TagExpressions.isExit("/exit"));
        assertFalse(TagExpressions.isExit("exit give Steve apple"));
        assertFalse(TagExpressions.isExit("exiting"));
        assertFalse(TagExpressions.isExit("say exit"));
        assertTrue(TagExpressions.isExitMisuse("exit give Steve apple"));
        assertTrue(TagExpressions.isExitMisuse("/exit now"));
        assertFalse(TagExpressions.isExitMisuse("exit"));
        assertFalse(TagExpressions.isExitMisuse("exiting"));
        assertFalse(TagExpressions.isExitMisuse("say exit now"));
    }

    @Test
    void findIfSpansSeesQuotedComparisons() {
        List<TagExpressions.IfSpan> spans = TagExpressions.findIfSpans(
                "say <if:\"7 <= 5\",\"y\",\"n\"> done");
        assertEquals(1, spans.size());
        assertEquals("\"7 <= 5\",\"y\",\"n\"", spans.get(0).args());
        assertEquals("<if:\"7 <= 5\",\"y\",\"n\">",
                "say <if:\"7 <= 5\",\"y\",\"n\"> done"
                        .substring(spans.get(0).start(), spans.get(0).end()));
    }

    @Test
    void findIfSpansSkipsNonIfAndUnbalanced() {
        assertTrue(TagExpressions.findIfSpans("say <iffy> hi").isEmpty());
        assertTrue(TagExpressions.findIfSpans("say <if:\"7 < 5,hi").isEmpty());
        assertTrue(TagExpressions.findIfSpans("say <p> hi").isEmpty());
    }

    @Test
    void hasNestedIfDetectsNestedTags() {
        assertTrue(TagExpressions.hasNestedIf("\"<if:\"1==1\",\"a\">\" == \"a\",\"y\""));
        assertTrue(TagExpressions.hasNestedIf("<IF:\"1==1\",\"a\">,\"y\""));
        assertFalse(TagExpressions.hasNestedIf("\"7 <= 5\",\"y\",\"n\""));
        assertFalse(TagExpressions.hasNestedIf("\"<p> == Steve\",\"y\""));
        assertFalse(TagExpressions.hasNestedIf("\"<iffy>\""));
    }

    @Test
    void unquoteStripsOneBalancedLayer() {
        assertEquals("hi", TagMath.unquote("\"hi\""));
        assertEquals("hi", TagMath.unquote("'hi'"));
        assertEquals("\"hi", TagMath.unquote("\"hi"));
        assertEquals("a\"b", TagMath.unquote("a\"b"));
        assertEquals("\"a\"b\"", TagMath.unquote("\"a\"b\""));
    }

    @Test
    void loseplayerEliminatesByNameAndReturnsEmpty() {
        Recording recording = new Recording();

        assertEquals("", TagExpressions.loseplayer("<loseplayer:Alex,fell>", "Alex,fell",
                recording.context));
        assertEquals(List.of("Alex|fell"), recording.lost);
        assertTrue(recording.warnings.isEmpty(), recording.warnings.toString());
    }

    @Test
    void loseplayerReasonDefaultsAndKeepsCommas() {
        Recording recording = new Recording();

        assertEquals("", TagExpressions.loseplayer("<loseplayer:Alex>", "Alex", recording.context));
        assertEquals("", TagExpressions.loseplayer("<loseplayer:\"Bob Smith\",fell, hard>",
                "\"Bob Smith\",fell, hard", recording.context));
        assertEquals(List.of("Alex|unknown reason", "Bob Smith|fell, hard"), recording.lost);
        assertTrue(recording.warnings.isEmpty(), recording.warnings.toString());
    }

    @Test
    void loseplayerWithoutPlayerWarnsAndSkips() {
        Recording recording = new Recording();

        assertEquals("", TagExpressions.loseplayer("<loseplayer:>", "", recording.context));
        assertTrue(recording.lost.isEmpty());
        assertEquals(1, recording.warnings.size());
    }

    @Test
    void dispatchableLineSkipsBlankAndStripsSlash() {
        assertTrue(TagExpressions.dispatchableLine("").isEmpty());
        assertTrue(TagExpressions.dispatchableLine("   ").isEmpty());
        assertTrue(TagExpressions.dispatchableLine("/").isEmpty());
        assertTrue(TagExpressions.dispatchableLine(" / ").isEmpty());
        assertEquals("say hi", TagExpressions.dispatchableLine("say hi").orElseThrow());
        assertEquals("say hi", TagExpressions.dispatchableLine("/say hi").orElseThrow());
        assertEquals("say hi", TagExpressions.dispatchableLine("  say hi  ").orElseThrow());
    }

    @Test
    void isPureNullMatchesExactLowercaseNull() {
        assertTrue(TagExpressions.isPureNull("null"));
        assertTrue(TagExpressions.isPureNull("  null  "));
        assertFalse(TagExpressions.isPureNull("NULL"));
        assertFalse(TagExpressions.isPureNull("Null"));
        assertFalse(TagExpressions.isPureNull("null x"));
        assertFalse(TagExpressions.isPureNull(""));
        assertFalse(TagExpressions.isPureNull("say null"));
    }

    @Test
    void winEndsMatchForCanonicalRole() {
        Recording recording = new Recording();

        assertEquals("", TagExpressions.win("<win:hunter,trapped>", "hunter,trapped", recording.context));
        assertEquals(List.of("HUNTER|trapped"), recording.wins);

        assertEquals("", TagExpressions.win("<win:SPEEDRUNNER>", "SPEEDRUNNER", recording.context));
        assertEquals(List.of("HUNTER|trapped", "SPEEDRUNNER|unknown reason"), recording.wins);
    }

    @Test
    void winReasonKeepsCommas() {
        Recording recording = new Recording();

        assertEquals("", TagExpressions.win("<win:HUNTER,fell, hard>", "HUNTER,fell, hard",
                recording.context));
        assertEquals(List.of("HUNTER|fell, hard"), recording.wins);
    }

    @Test
    void winWithBadRoleWarnsAndSkips() {
        Recording recording = new Recording();

        assertEquals("", TagExpressions.win("<win:ref,out>", "ref,out", recording.context));
        assertEquals("", TagExpressions.win("<win:>", "", recording.context));
        assertTrue(recording.wins.isEmpty());
        assertEquals(2, recording.warnings.size());
    }

    private static final class Recording {
        final List<String> warnings = new ArrayList<>();
        final List<String> wins = new ArrayList<>();
        final List<String> lost = new ArrayList<>();
        final TagContext context = TagContext.run(
                ModifierTagScope.match("Steve", List.of(), new Random(7), warnings::add),
                "beef", warnings::add, warnings::add,
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                (player, reason) -> lost.add(player + "|" + reason),
                (role, reason) -> wins.add(role + "|" + reason),
                TagContext.NO_MATCH, TagBackends.inert());
    }
}
