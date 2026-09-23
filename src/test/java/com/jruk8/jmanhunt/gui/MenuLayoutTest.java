package com.jruk8.jmanhunt.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MenuLayoutTest {

    @Test
    void parsesRowsAndFindsSlots() {
        MenuLayout layout = MenuLayout.parse("m#######p", "#xxxxxx##", "b###d###u");

        assertEquals(3, layout.rowCount());
        assertEquals(27, layout.size());
        assertEquals(List.of(0), layout.slotsFor('m'));
        assertEquals(List.of(8), layout.slotsFor('p'));
        assertEquals(List.of(10, 11, 12, 13, 14, 15), layout.contentSlots());
        assertEquals(6, layout.contentColumns());
        assertEquals(1, layout.contentRows());
    }

    @Test
    void contentIndexSkipsEmptyRows() {
        MenuLayout layout = MenuLayout.parse("#########", "#xxxxxx##", "#xxxxxx##");

        assertEquals(0, layout.contentIndex(10));
        assertEquals(5, layout.contentIndex(15));
        assertEquals(6, layout.contentIndex(19));
        assertEquals(11, layout.contentIndex(24));
        assertEquals(-1, layout.contentIndex(0));
        assertEquals(-1, layout.contentIndex(8));
        assertEquals(-1, layout.contentIndex(99));
    }

    @Test
    void allowsLayoutWithoutContent() {
        MenuLayout layout = MenuLayout.parse("m#######p", "#########", "b#######u");

        assertTrue(layout.contentSlots().isEmpty());
        assertEquals(0, layout.contentColumns());
        assertEquals(0, layout.contentRows());
        assertEquals(-1, layout.contentIndex(4));
    }

    @Test
    void rejectsMalformedRows() {
        assertThrows(IllegalArgumentException.class, () -> MenuLayout.parse());
        assertThrows(IllegalArgumentException.class, () -> MenuLayout.parse("m######p"));
        assertThrows(IllegalArgumentException.class,
                () -> MenuLayout.parse("#xxxxxx##", "#xxx#####"));
    }
}
