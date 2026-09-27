package com.jruk8.jmanhunt.command;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** List foundation: detection, parsing, formatting, arg splitting. */
class TagListsTest {

    @Test
    void detectsBracketWrappedText() {
        assertTrue(TagLists.isList("[]"));
        assertTrue(TagLists.isList("[a]"));
        assertTrue(TagLists.isList("[a, b]"));
        assertTrue(TagLists.isList("  [a, b]  "));
        assertFalse(TagLists.isList(null));
        assertFalse(TagLists.isList(""));
        assertFalse(TagLists.isList("["));
        assertFalse(TagLists.isList("a, b"));
        assertFalse(TagLists.isList("[a, b"));
        assertFalse(TagLists.isList("a, b]"));
    }

    @Test
    void parsesEmptySingleAndMulti() {
        assertEquals(List.of(), TagLists.parse("[]"));
        assertEquals(List.of(), TagLists.parse("[  ]"));
        assertEquals(List.of("a"), TagLists.parse("[a]"));
        assertEquals(List.of("a", "b", "c"), TagLists.parse("[a, b,c]"));
        assertEquals(List.of(), TagLists.parse("not a list"));
        assertEquals(List.of(), TagLists.parse(null));
    }

    @Test
    void keepsNestedListsWholeAndEmptyItems() {
        assertEquals(List.of("a", "[b, c]", "d"), TagLists.parse("[a, [b, c], d]"));
        assertEquals(List.of("", ""), TagLists.parse("[,]"));
        assertEquals(List.of("a", ""), TagLists.parse("[a,]"));
    }

    @Test
    void formatsCanonicalShape() {
        assertEquals("[]", TagLists.format(List.of()));
        assertEquals("[a]", TagLists.format(List.of("a")));
        assertEquals("[a, b, c]", TagLists.format(List.of("a", "b", "c")));
    }

    @Test
    void roundTrips() {
        assertEquals("[a, [b, c], d]",
                TagLists.format(TagLists.parse("[a,[b, c] ,d]")));
        assertEquals("[, ]", TagLists.format(TagLists.parse("[,]")));
    }

    @Test
    void splitsTopLevelCommasOnly() {
        assertEquals(List.of("a", "b"), TagLists.splitTopLevel("a,b"));
        assertEquals(List.of("[a,b]", "c"), TagLists.splitTopLevel("[a,b],c"));
        assertEquals(List.of("<gflag:l>", "x"), TagLists.splitTopLevel("<gflag:l>,x"));
        assertEquals(List.of("\"a,b\"", "c"), TagLists.splitTopLevel("\"a,b\",c"));
        assertEquals(List.of("a"), TagLists.splitTopLevel("a"));
        assertEquals(List.of(""), TagLists.splitTopLevel(""));
    }
}
