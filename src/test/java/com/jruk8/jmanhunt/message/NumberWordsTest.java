package com.jruk8.jmanhunt.message;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberWordsTest {
    @Test
    void wordsBelowTenDigitsAbove() {
        assertEquals("zero", NumberWords.word(0));
        assertEquals("one", NumberWords.word(1));
        assertEquals("nine", NumberWords.word(9));
        assertEquals("10", NumberWords.word(10));
        assertEquals("11", NumberWords.word(11));
        assertEquals("137", NumberWords.word(137));
    }
}
