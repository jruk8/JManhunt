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
    void chunkSplitsLinesAtLimit() {
        assertEquals(List.of("a, b", "c"),
                ListFormatter.chunk(List.of("a", "b", "c"), 2));
        assertEquals(List.of("a, b, c"), ListFormatter.chunk(List.of("a", "b", "c"), 0));
    }
}
