package com.jruk8.jmanhunt.command;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrdinalTest {

    @Test
    void formatsSuffixesWithTeenOverride() {
        assertEquals("1st", Ordinal.format(1));
        assertEquals("2nd", Ordinal.format(2));
        assertEquals("3rd", Ordinal.format(3));
        assertEquals("4th", Ordinal.format(4));
        assertEquals("11th", Ordinal.format(11));
        assertEquals("12th", Ordinal.format(12));
        assertEquals("13th", Ordinal.format(13));
        assertEquals("21st", Ordinal.format(21));
        assertEquals("22nd", Ordinal.format(22));
        assertEquals("23rd", Ordinal.format(23));
        assertEquals("111th", Ordinal.format(111));
        assertEquals("112th", Ordinal.format(112));
        assertEquals("113th", Ordinal.format(113));
    }

    @Test
    void listNamesMapToThemselves() {
        for (String list : List.of("player", "speedrunner", "hunter",
                "console", "player-cleanup", "console-cleanup")) {
            assertEquals(list, Ordinal.listName(list));
        }
    }
}
