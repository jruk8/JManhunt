package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Scope participant filtering: teams narrow, blank and ALL widen. */
class ModifierTagScopeTest {

    @Test
    void participantNamesTreatsAllLikeBlank() {
        ModifierTagScope scope = ModifierTagScope.match("Steve", List.of(
                new ModifierTagScope.Participant("Alice", "HUNTER"),
                new ModifierTagScope.Participant("Bob", "SPEEDRUNNER")),
                new Random(1), warning -> { });

        assertEquals(List.of("Alice", "Bob"), scope.participantNames("ALL"));
        assertEquals(List.of("Alice", "Bob"), scope.participantNames("all"));
        assertEquals(List.of("Alice", "Bob"), scope.participantNames(null));
        assertEquals(List.of("Alice", "Bob"), scope.participantNames("  "));
        assertEquals(List.of("Alice"), scope.participantNames("hunter"));
        assertEquals(List.of(), scope.participantNames("ref"));
    }
}
