package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.match.lifecycle.MatchFinishService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class InvalidReasonTest {

    @Test
    void namesMissingSide() {
        assertEquals("no hunters remain", MatchFinishService.invalidReason(0, 2));
        assertEquals("no speedrunners remain", MatchFinishService.invalidReason(3, 0));
        assertEquals("neither side is fielded", MatchFinishService.invalidReason(0, 0));
    }
}
