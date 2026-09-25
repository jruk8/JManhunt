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
        assertEquals("y", replace(fixture, "<if:\"7 <= 7\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"8 > 7\",\"y\",\"n\">"));
        assertEquals("n", replace(fixture, "<if:\"8 < 7\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"8 >= 9\",\"n\",\"y\">"));
        assertEquals("y", replace(fixture, "<if:\"apple == apple\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"apple != orange\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"8+5 == 13\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"(2+3)*4 == 20\",\"y\",\"n\">"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
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
        assertEquals("", replace(fixture, "<if:\"<flag:a> > 1\",\"y\",\"n\">"));
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
        assertTrue(TagExpressions.hasComparison("7 <= 5"));
        assertFalse(TagExpressions.hasComparison("<flag:a>"));
        assertFalse(TagExpressions.hasComparison("<papi:x==y>"));
        assertFalse(TagExpressions.hasComparison("\"7 <= 5\""));
    }

    @Test
    void ifCoalesceExampleFromGappleShape() {
        Fixture fixture = new Fixture();
        assertEquals("n", replace(fixture,
                "<if:\"7 <= 7 and '100-500 ?? -1' > 300\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture,
                "<if:\"7 <= 7 and '500-100 ?? -1' > 300\",\"y\",\"n\">"));
        assertEquals("y", replace(fixture, "<if:\"null ?? 5 > 3\",\"y\",\"n\">"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void ifStructuralProblemsYieldEmptyWithWarning() {
        Fixture fixture = new Fixture();
        assertEquals("", replace(fixture, "<if:\"1 == 1\">"));
        assertEquals("", replace(fixture, "<if:\"abc\",\"y\">"));
        assertEquals("", replace(fixture, "<if:\"2.5 > 1\",\"y\",\"n\">"));
        assertEquals("", replace(fixture, "<if:\"1 == 1\",\"a\",\"b\",\"c\">"));
        assertEquals(4, fixture.warnings.size());
    }

    @Test
    void ifSpacelessAnglesAreSyntaxErrors() {
        Fixture fixture = new Fixture();
        assertEquals("", replace(fixture, "<if:\"7<=7\",\"y\",\"n\">"));
        assertEquals("", replace(fixture, "<if:\"7 <=7\",\"y\",\"n\">"));
        assertEquals("", replace(fixture, "<if:\"7< 7\",\"y\",\"n\">"));
        assertEquals(3, fixture.warnings.size());
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
        assertEquals("hi", TagExpressions.unquote("\"hi\""));
        assertEquals("hi", TagExpressions.unquote("'hi'"));
        assertEquals("\"hi", TagExpressions.unquote("\"hi"));
        assertEquals("a\"b", TagExpressions.unquote("a\"b"));
        assertEquals("\"a\"b\"", TagExpressions.unquote("\"a\"b\""));
    }
}
