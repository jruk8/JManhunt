package com.jruk8.jmanhunt.message;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PluralsTest {

    @Test
    void picksSingularOnlyForOne() {
        assertEquals("modifier", Plurals.pick(1, "modifier", "modifiers"));
        assertEquals("modifiers", Plurals.pick(0, "modifier", "modifiers"));
        assertEquals("modifiers", Plurals.pick(2, "modifier", "modifiers"));
    }
}
