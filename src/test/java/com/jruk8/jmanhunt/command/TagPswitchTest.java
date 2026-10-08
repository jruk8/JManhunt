package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pswitch tag over a recording context: arg parsing, role
 * validation, sink capture, and dispatch registration.
 */
class TagPswitchTest {

    private static final class Recording {
        final List<String> warnings = new ArrayList<>();
        final List<String> switched = new ArrayList<>();
        final TagContext context = TagContext.run(new TagContext.TagIdentity(ModifierTagScope.match("Steve",
                List.of(), new Random(7), warnings::add), "beef"),
                TagContext.TagSinks.simple(warnings::add, warnings::add, (id, pitch, volume) -> { },
                        (id, pitch, volume) -> { },
                        ModifierTagScope.match("Steve", List.of(), new Random(7), warnings::add)),
                TagContext.TagRole.silent(),
                TagContext.TagMatch.simple(TagContext.NO_MATCH, TagBackends.inert(),
                        (player, reason) -> { }, (role, reason) -> { },
                        (player, role) -> switched.add(player + "|" + role)));
    }

    @Test
    void pswitchPassesPlayerAndCanonicalRole() {
        Recording recording = new Recording();

        assertEquals("", TagExpressions.pswitch("<pswitch:Alex,hunter>", "Alex,hunter",
                recording.context));
        assertEquals(List.of("Alex|HUNTER"), recording.switched);
        assertTrue(recording.warnings.isEmpty(), recording.warnings.toString());
    }

    @Test
    void pswitchAcceptsQuotedNames() {
        Recording recording = new Recording();

        assertEquals("", TagExpressions.pswitch("<pswitch:\"Bob Smith\",SPEEDRUNNER>",
                "\"Bob Smith\",SPEEDRUNNER", recording.context));
        assertEquals(List.of("Bob Smith|SPEEDRUNNER"), recording.switched);
        assertTrue(recording.warnings.isEmpty(), recording.warnings.toString());
    }

    @Test
    void pswitchRejectsBadRole() {
        Recording recording = new Recording();

        assertEquals("", TagExpressions.pswitch("<pswitch:Alex,runner>", "Alex,runner",
                recording.context));
        assertTrue(recording.switched.isEmpty());
        assertEquals(1, recording.warnings.size());
    }

    @Test
    void pswitchNeedsBothArgs() {
        Recording missing = new Recording();
        assertEquals("", TagExpressions.pswitch("<pswitch:Alex>", "Alex", missing.context));
        assertTrue(missing.switched.isEmpty());
        assertEquals(1, missing.warnings.size());

        Recording extra = new Recording();
        assertEquals("", TagExpressions.pswitch("<pswitch:Alex,HUNTER,x>", "Alex,HUNTER,x",
                extra.context));
        assertTrue(extra.switched.isEmpty());
        assertEquals(1, extra.warnings.size());

        Recording blank = new Recording();
        assertEquals("", TagExpressions.pswitch("<pswitch:>", "", blank.context));
        assertTrue(blank.switched.isEmpty());
        assertEquals(1, blank.warnings.size());
    }

    @Test
    void pswitchDispatchesThroughReplace() {
        Recording recording = new Recording();

        assertEquals("go !", CommandPlaceholders.replace("go <pswitch:Alex,HUNTER>!", "Steve",
                0, 0, 0, recording.context));
        assertEquals(List.of("Alex|HUNTER"), recording.switched);
        assertTrue(recording.warnings.isEmpty(), recording.warnings.toString());
    }
}
