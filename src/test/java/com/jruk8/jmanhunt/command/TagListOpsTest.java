package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * List manipulation: pure ops over literals, flag write-back through
 * the pre-pass, and misuse shapes.
 */
class TagListOpsTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final FlagStore flags = new FlagStore();

        TagContext context() {
            return TagContext.run(
                    ModifierTagScope.match("Steve", List.of(), new Random(11), warnings::add),
                    "lists", warnings::add, warnings::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    (player, reason) -> { }, (role, reason) -> { },
                    7L, new TagBackends(StatValues.inert(), flags, (text, name) -> text,
                            RosterValues.inert(), PlayerSinks.inert()));
        }

        String replace(String command, TagContext context) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }
    }

    @Test
    void pureOpsOverLiterals() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("b", fixture.replace("<list.get:[a,b,c],1>", context));
        assertEquals("null", fixture.replace("<list.get:[a,b],5>", context));
        assertEquals("null", fixture.replace("<list.get:[a,b],-1>", context));
        assertEquals("null", fixture.replace("<list.get:plain,0>", context));
        assertEquals("true", fixture.replace("<list.contains:[a,b],b>", context));
        assertEquals("false", fixture.replace("<list.contains:[a,b],z>", context));
        assertEquals("false", fixture.replace("<list.contains:plain,z>", context));
        assertEquals("3", fixture.replace("<len:[a,b,c]>", context));
        assertEquals("0", fixture.replace("<len:[]>", context));
        assertEquals("0", fixture.replace("<len:plain>", context));
        assertEquals("", fixture.replace("<list.append:[a],b>", context));
        assertEquals("", fixture.replace("<list.set:[a,b],0,z>", context));
        assertEquals("true", fixture.replace("<list.remove:[a,b],a>", context));
        assertEquals("false", fixture.replace("<list.remove:[a,b],z>", context));
        assertEquals("", fixture.replace("<list.clear:[a,b]>", context));
        assertEquals("a", fixture.replace("<list.pop:[a,b]>", context));
        assertEquals("null", fixture.replace("<list.pop:[]>", context));
        assertEquals("", fixture.replace("<list.shuffle:[a,b,c]>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void nestedListItemsStayWhole() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[x, y]", fixture.replace("<list.get:[a,[x, y],c],1>", context));
        assertEquals("3", fixture.replace("<len:[a,[x, y],c]>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void rangeBuildsPythonStyleLists() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[0, 1, 2, 3, 4]", fixture.replace("<range:5>", context));
        assertEquals("[1, 2, 3, 4]", fixture.replace("<range:1,5>", context));
        assertEquals("[]", fixture.replace("<range:5,5>", context));
        assertEquals("[0, 2, 4]", fixture.replace("<range:0,5,2>", context));
        assertEquals("[5, 4, 3, 2, 1]", fixture.replace("<range:5,0,-1>", context));
        assertEquals("[-2, -1, 0, 1]", fixture.replace("<range:-2,2>", context));
        assertEquals("[2, 3, 4, 5]", fixture.replace("<range:1+1,2*3>", context));
        assertEquals("[]", fixture.replace("<range:5,1>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void rangeWarnsAndNullsOnBadBounds() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<range:1,5,0>", context));
        assertEquals("null", fixture.replace("<range:a,5>", context));
        assertEquals("null", fixture.replace("<range:1,2.5>", context));
        assertEquals("null", fixture.replace("<range:null,5>", context));
        assertEquals("null", fixture.replace("<range:1,2,3,4>", context));
        assertEquals(5, fixture.warnings.size());
    }

    @Test
    void rangeCapsLongRunsAtOneThousand() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        String result = fixture.replace("<range:0,1000000>", context);
        assertEquals(1000, TagLists.parse(result).size());
        assertEquals("0", TagLists.parse(result).get(0));
        assertEquals("999", TagLists.parse(result).get(999));
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("capped"),
                fixture.warnings.toString());
    }

    @Test
    void mutatorsWriteBackThroughShortAliases() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<gf:nums,[]>", context));
        assertEquals("", fixture.replace("<list.append:<gf:nums>,a>", context));
        assertEquals("[a]", fixture.replace("<gflag:nums>", context));
        assertEquals("", fixture.replace("<list.append:<gflag:nums>,b>", context));
        assertEquals("[a, b]", fixture.replace("<gf:nums>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void mutatorsWriteBackToFlags() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<gflag:nums,[]>", context));
        assertEquals("", fixture.replace("<list.append:<gflag:nums>,a>", context));
        assertEquals("", fixture.replace("<list.append:<gflag:nums>,b>", context));
        assertEquals("[a, b]", fixture.replace("<gflag:nums>", context));
        assertEquals("", fixture.replace("<list.set:<gflag:nums>,0,z>", context));
        assertEquals("[z, b]", fixture.replace("<gflag:nums>", context));
        assertEquals("true", fixture.replace("<list.remove:<gflag:nums>,z>", context));
        assertEquals("[b]", fixture.replace("<gflag:nums>", context));
        assertEquals("b", fixture.replace("<list.pop:<gflag:nums>>", context));
        assertEquals("[]", fixture.replace("<gflag:nums>", context));
        assertEquals("", fixture.replace("<list.append:<gflag:nums>,q>", context));
        assertEquals("", fixture.replace("<list.clear:<gflag:nums>>", context));
        assertEquals("[]", fixture.replace("<gflag:nums>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void writeBackTreatsNonListsAsEmpty() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<gflag:raw,plain>", context));
        assertEquals("", fixture.replace("<list.append:<gflag:raw>,a>", context));
        assertEquals("[a]", fixture.replace("<gflag:raw>", context));
        assertEquals("", fixture.replace("<gflag:empty,[]>", context));
        assertEquals("", fixture.replace("<list.set:<gflag:empty>,0,a>", context));
        assertEquals("[a]", fixture.replace("<gflag:empty>", context));
        assertEquals("null", fixture.replace("<list.set:<gflag:empty>,5,z>", context));
        assertEquals("[a]", fixture.replace("<gflag:empty>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void writeBackArgsResolveNestedTags() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<gflag:who,[]>", context));
        assertEquals("", fixture.replace("<list.append:<gflag:who>,<p>>", context));
        assertEquals("[Steve]", fixture.replace("<gflag:who>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void misuseWarnsWithFailureReturns() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<list.get:[a]>", context));
        assertEquals("null", fixture.replace("<list.get:[a,b],x>", context));
        assertEquals("null", fixture.replace("<list.set:[a],x,y>", context));
        assertEquals("null", fixture.replace("<list.append:[a]>", context));
        assertEquals("0", fixture.replace("<len:[a],extra>", context));
        assertEquals("null", fixture.replace("<list.pop:[a],extra>", context));
        assertEquals(6, fixture.warnings.size());
    }

    @Test
    void removeDropsFirstMatchOnly() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("", fixture.replace("<gflag:dupes,[a,b,a]>", context));
        assertEquals("true", fixture.replace("<list.remove:<gflag:dupes>,a>", context));
        assertEquals("[b, a]", fixture.replace("<gflag:dupes>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void filterBindsLoopItemPerEntry() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[bb, abc]", fixture.replace(
                "<list.filter:[a,bb,abc],<str.contains:<i>,b>>", context));
        assertEquals("[2, 3]", fixture.replace(
                "<list.filter:[1,2,3],<if:\"<i> gt 1\",\"true\",\"false\">>", context));
        assertEquals("[a, b]", fixture.replace("<list.filter:[a,b],TRUE>", context));
        assertEquals("[]", fixture.replace("<list.filter:[a,b],false>", context));
        assertEquals("[]", fixture.replace("<list.filter:[a,b],1>", context));
        assertEquals("null", fixture.replace("<list.filter:plain,<i>>", context));
        assertEquals("null", fixture.replace("<list.filter:[a]>", context));
        assertEquals(2, fixture.warnings.size());
    }

    @Test
    void sliceClampsAndCountsFromTheEnd() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[b, c]", fixture.replace("<list.slice:[a,b,c],1,3>", context));
        assertEquals("[a, b]", fixture.replace("<list.slice:[a,b,c],0,-1>", context));
        assertEquals("[b, c]", fixture.replace("<list.slice:[a,b,c],-2,99>", context));
        assertEquals("[a, b, c]", fixture.replace("<list.slice:[a,b,c],-99,99>", context));
        assertEquals("[]", fixture.replace("<list.slice:[a,b,c],2,1>", context));
        assertEquals("[]", fixture.replace("<list.slice:[a,b,c],9,9>", context));
        assertEquals("[]", fixture.replace("<list.slice:plain,0,1>", context));
        assertEquals("null", fixture.replace("<list.slice:[a,b],0,x>", context));
        assertEquals("null", fixture.replace("<list.slice:[a,b],0>", context));
        assertEquals(2, fixture.warnings.size());
    }

    @Test
    void readOnlyOps() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[c, b, a]", fixture.replace("<list.reverse:[a,b,c]>", context));
        assertEquals("a-b", fixture.replace("<list.join:[a,b],->", context));
        assertEquals("ab", fixture.replace("<list.join:[a,b],>", context));
        assertEquals("a", fixture.replace("<list.first:[a,b]>", context));
        assertEquals("b", fixture.replace("<list.last:[a,b]>", context));
        assertEquals("null", fixture.replace("<list.first:[]>", context));
        assertEquals("null", fixture.replace("<list.last:plain>", context));
        assertEquals("null", fixture.replace("<list.join:plain,->", context));
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void filterSliceJoinCompose() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("[c]", fixture.replace(
                "<list.filter:<list.slice:[a,b,c],1,3>,<str.contains:<i>,c>>", context));
        assertEquals("c-b", fixture.replace(
                "<list.join:<list.reverse:<list.slice:[a,b,c,d],1,3>>,->", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }
}
