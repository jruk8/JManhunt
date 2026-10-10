package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * While and for loops: iteration with {@code <i>}, source-flag
 * cancel, nesting, the 1000-step limit, and bad shapes.
 */
class TagLoopsTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
        final List<String> loopLimits = new ArrayList<>();
        final FlagStore flags = new FlagStore();

        TagContext context() {
            return TagContext.run(new TagContext.TagIdentity(ModifierTagScope.match("Steve", List.of(),
                    new Random(11), warnings::add), "loops"),
                    TagContext.TagSinks.simple(messages::add, messages::add,
                            (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                            ModifierTagScope.match("Steve", List.of(), new Random(11), warnings::add)),
                    TagContext.TagRole.silent(),
                    new TagContext.TagMatch(7L, new TagBackends(StatValues.inert(), flags, (text,
                            name) -> text, RosterValues.inert(), PlayerSinks.inert()), List.of(), loopLimits::add,
                            detail -> { }, (player, reason) -> { }, (role, reason) -> { },
                            (player, role) -> { }));
        }

        String replace(String command, TagContext context) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }
    }

    @Test
    void forWalksListWithI() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<for:[a,b],<gmessage:got-<i>>>", context));
        assertEquals(List.of("got-a", "got-b"), fixture.messages);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void forOverFlagListAndRange() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<gflag:nums,[x,y]>", context));
        assertEquals("", fixture.replace("<for:<gflag:nums>,<gmessage:<i>>>", context));
        assertEquals("", fixture.replace("<for:<range:3>,<gmessage:n<i>>>", context));
        assertEquals(List.of("x", "y", "n0", "n1", "n2"), fixture.messages);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void forCancelsWhenSourceFlagMoves() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<gflag:nums,[a,b,c]>", context));
        assertEquals("null", fixture.replace(
                "<for:<gflag:nums>,<list.append:<gflag:nums>,<i>>>", context));
        assertEquals("[a, b, c, a]", fixture.replace("<gflag:nums>", context));
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("changed during the loop"),
                fixture.warnings.toString());
    }

    @Test
    void forBuildsSecondFlagFromRange() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<gflag:out,[]>", context));
        assertEquals("", fixture.replace(
                "<for:<range:3>,<list.append:<gflag:out>,item<i>>>", context));
        assertEquals("[item0, item1, item2]", fixture.replace("<gflag:out>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void nestedForSeesInnermost() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<for:[a,b],<for:[1,2],<gmessage:<i>>>>", context));
        assertEquals(List.of("1", "2", "1", "2"), fixture.messages);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void iOutsideForIsSilentNull() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("say null!", fixture.replace("say <i>!", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void whileRepeatsUntilFalse() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<gflag:n,0>", context));
        assertEquals("", fixture.replace(
                "<while:<gflag:n> lt 3,<gmessage:tick><gflag:n,<gflag:n>+1>>", context));
        assertEquals(List.of("tick", "tick", "tick"), fixture.messages);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void whileFalseWordSkipsBody() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<while:false,<gmessage:x>>", context));
        assertTrue(fixture.messages.isEmpty());
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void whileGarbageConditionWarnsAndNulls() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<while:maybe,<gmessage:x>>", context));
        assertTrue(fixture.messages.isEmpty());
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("needs a comparison"),
                fixture.warnings.toString());
    }

    @Test
    void whileLimitFiresSinkWithProvenance() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();
        context.setProvenance(TagContext.Provenance.of("spin", 2, "console").withLine(4));

        assertEquals("null", fixture.replace("<while:true,<gmessage:x>>", context));
        assertEquals(20_000, fixture.messages.size());
        assertEquals(1, fixture.loopLimits.size());
        String detail = fixture.loopLimits.get(0);
        assertTrue(detail.contains("<while>"), detail);
        assertTrue(detail.contains(
                "modifier 'spin', behavior 2, list 'console', line 4 (0-based)"), detail);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void forLimitFiresOnHugeList() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();
        List<String> items = new ArrayList<>();
        for (int index = 0; index < 20_005; index++) {
            items.add(String.valueOf(index));
        }
        fixture.flags.setGlobal(7L, "big", TagLists.format(items));

        assertEquals("null", fixture.replace("<for:<gflag:big>,<gmessage:x>>", context));
        assertEquals(20_000, fixture.messages.size());
        assertEquals(1, fixture.loopLimits.size());
        assertTrue(fixture.loopLimits.get(0).contains("<for>"), fixture.loopLimits.toString());
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void loopsNeedTwoParts() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<for:[a]>", context));
        assertEquals("null", fixture.replace("<while:true>", context));
        assertEquals("null", fixture.replace("<for:plain,<gmessage:x>>", context));
        assertTrue(fixture.messages.isEmpty());
        assertEquals(3, fixture.warnings.size());
    }

    @Test
    void provenanceDescribesModifierAndDebuffRuns() {
        assertEquals("modifier 'spin', behavior 2, list 'console', line 4 (0-based)",
                TagContext.Provenance.of("spin", 2, "console").withLine(4).describe());
        assertEquals("modifier 'debuffs', list 'debuffs', line 1 (0-based)",
                TagContext.Provenance.of("debuffs", -1, "debuffs").withLine(1).describe());
    }
}
