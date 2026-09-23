package com.jruk8.jmanhunt.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ContentWindowTest {

    private static List<String> entries(int count) {
        return IntStream.range(0, count).mapToObj(i -> "e" + i).collect(Collectors.toList());
    }

    @Test
    void slicesVisibleEntriesByLineOffset() {
        ContentWindow<String> window = new ContentWindow<>(6, 5);
        window.setEntries(entries(31));

        assertEquals(entries(30), window.visibleEntries());
        assertTrue(window.scrollLine(1));
        assertEquals(entries(31).subList(6, 31), window.visibleEntries());
        assertFalse(window.scrollLine(1));
        assertEquals(1, window.lineOffset());
        assertEquals(6, window.lineCount());
        assertEquals(1, window.maxOffset());
    }

    @Test
    void clampsOffsetWhenEntriesShrink() {
        ContentWindow<String> window = new ContentWindow<>(6, 5);
        window.setEntries(entries(61));
        assertTrue(window.scrollLine(6));

        window.setEntries(entries(12));

        assertEquals(0, window.lineOffset());
        assertEquals(entries(12), window.visibleEntries());
    }

    @Test
    void jumpsPagesWithinRange() {
        ContentWindow<String> window = new ContentWindow<>(6, 5);
        window.setEntries(entries(61));

        assertEquals(3, window.pageCount());
        window.setPage(2);
        assertEquals(6, window.lineOffset());
        assertEquals(1, window.currentPage());

        window.setPage(99);
        assertEquals(window.maxOffset(), window.lineOffset());
        window.setPage(-1);
        assertEquals(0, window.lineOffset());
    }

    @Test
    void keepsNullPaddingInVisibleEntries() {
        ContentWindow<String> window = new ContentWindow<>(2, 2);
        window.setEntries(Arrays.asList("a", null, null, "b"));

        assertEquals(Arrays.asList("a", null, null, "b"), window.visibleEntries());
    }

    @Test
    void handlesEmptyEntries() {
        ContentWindow<String> window = new ContentWindow<>(6, 5);
        window.setEntries(null);

        assertTrue(window.visibleEntries().isEmpty());
        assertFalse(window.scrollLine(1));
        assertEquals(0, window.lineCount());
        assertEquals(0, window.maxOffset());
        assertEquals(1, window.pageCount());
        assertEquals(0, window.currentPage());
    }

    @Test
    void rejectsInvalidShape() {
        assertThrows(IllegalArgumentException.class, () -> new ContentWindow<>(0, 5));
        assertThrows(IllegalArgumentException.class, () -> new ContentWindow<>(6, 0));
    }
}
