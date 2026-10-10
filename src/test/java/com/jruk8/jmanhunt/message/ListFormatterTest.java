package com.jruk8.jmanhunt.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ListFormatterTest {
    @Test
    void joinOxfordHandlesZeroOneTwoAndMany() {
        assertEquals("", ListFormatter.joinOxford(List.of()));
        assertEquals("a", ListFormatter.joinOxford(List.of("a")));
        assertEquals("a <gray>and</gray> b", ListFormatter.joinOxford(List.of("a", "b")));
        assertEquals("a<gray>,</gray> b<gray>,</gray> <gray>and</gray> c",
                ListFormatter.joinOxford(List.of("a", "b", "c")));
        assertEquals("a<gray>,</gray> b<gray>,</gray> c<gray>,</gray> <gray>and</gray> d",
                ListFormatter.joinOxford(List.of("a", "b", "c", "d")));
    }

    @Test
    void truncatedWithoutOverflowMatchesOxford() {
        assertEquals("", ListFormatter.joinOxfordTruncated(List.of(), 3, ",", "and", "more", "dead"));
        assertEquals("a", ListFormatter.joinOxfordTruncated(List.of("a"), 3, ",", "and", "more", "dead"));
        assertEquals("a and b",
                ListFormatter.joinOxfordTruncated(List.of("a", "b"), 3, ",", "and", "more", "dead"));
        assertEquals("a, b, and c", ListFormatter.joinOxfordTruncated(List.of("a", "b", "c"), 3, ",",
                "and", "more", "dead"));
    }

    @Test
    void truncatedOverflowAppendsAndNMore() {
        assertEquals("a, b, c, and 2 more", ListFormatter.joinOxfordTruncated(
                List.of("a", "b", "c", "d", "e"), 3, ",", "and", "more", "dead"));
        assertEquals(
                "a<gray>,</gray> b<gray>,</gray> c<gray>,</gray> <gray>and</gray> 2 more",
                ListFormatter.joinOxfordTruncated(List.of("a", "b", "c", "d", "e"), 3, "<gray>,</gray>",
                        "<gray>and</gray>", "more", "dead"));
    }

    @Test
    void truncatedGhostLineShapes() {
        assertEquals("💀a and 5 more", ListFormatter.joinOxfordTruncated(
                List.of("💀a", "b", "c", "d", "e", "f"), 1, ",", "and", "more", "dead"));
        assertEquals("💀a, 💀b, 💀c, and 2 more", ListFormatter.joinOxfordTruncated(
                List.of("💀a", "💀b", "💀c", "d", "e"), 3, ",", "and", "more", "dead"));
        assertEquals("6 dead", ListFormatter.joinOxfordTruncated(
                List.of("a", "b", "c", "d", "e", "f"), 0, ",", "and", "more", "dead"));
    }

    @Test
    void joinOxfordColoredWrapsEntriesWhite() {
        assertEquals("", ListFormatter.joinOxfordColored(List.of(), "<white>", "</white>"));
        assertEquals("<white>a</white>",
                ListFormatter.joinOxfordColored(List.of("a"), "<white>", "</white>"));
        assertEquals("<white>a</white> <gray>and</gray> <white>b</white>",
                ListFormatter.joinOxfordColored(List.of("a", "b"), "<white>", "</white>"));
        assertEquals("<white>a</white><gray>,</gray> <white>b</white><gray>,</gray> "
                        + "<gray>and</gray> <white>c</white>",
                ListFormatter.joinOxfordColored(List.of("a", "b", "c"), "<white>", "</white>"));
    }

    @Test
    void chunkSplitsLinesAtLimit() {
        assertEquals(List.of("a, b", "c"),
                ListFormatter.chunk(List.of("a", "b", "c"), 2));
        assertEquals(List.of("a, b, c"), ListFormatter.chunk(List.of("a", "b", "c"), 0));
    }
}
