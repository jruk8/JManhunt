package com.jruk8.jmanhunt.match;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InvalidReasonTest {

    @Test
    void namesMissingSide() {
        assertEquals("no hunters remain", GameManager.invalidReason(0, 2));
        assertEquals("no speedrunners remain", GameManager.invalidReason(3, 0));
        assertEquals("neither side is fielded", GameManager.invalidReason(0, 0));
    }
}
