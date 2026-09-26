package com.jruk8.jmanhunt.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/** GuiTexts.truncate: the shared button-text clamp. */
class GuiTextsTest {

    @Test
    void truncateKeepsShortTextAndClipsLongText() {
        assertEquals("abc", GuiTexts.truncate("abc", 60));
        assertEquals(60, GuiTexts.truncate("x".repeat(100), 60).length());
        assertEquals("...", GuiTexts.truncate("x".repeat(100), 3));
    }

    @Test
    void truncateClampsCommandLinesToThirtyTwo() {
        assertEquals("x".repeat(32), GuiTexts.truncate("x".repeat(32), 32));
        assertEquals("x".repeat(29) + "...", GuiTexts.truncate("x".repeat(33), 32));
    }

    @Test
    void truncateTreatsNullAsEmpty() {
        assertEquals("", GuiTexts.truncate(null, 32));
    }
}
