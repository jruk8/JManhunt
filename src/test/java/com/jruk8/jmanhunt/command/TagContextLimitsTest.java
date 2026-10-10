package com.jruk8.jmanhunt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Loop-limit and stack-exhausted channels share one once-per-line guard. */
class TagContextLimitsTest {

    private record Channels(TagContext context, List<String> loop, List<String> stack) {
    }

    private static Channels channels() {
        ModifierTagScope scope = ModifierTagScope.executor(null, text -> { });
        List<String> loop = new ArrayList<>();
        List<String> stack = new ArrayList<>();
        TagContext context = TagContext.run(
                new TagContext.TagIdentity(scope, "test"),
                TagContext.TagSinks.simple(text -> { }, text -> { },
                        (id, pitch, volume) -> { }, (id, pitch, volume) -> { }, scope),
                TagContext.TagRole.silent(),
                new TagContext.TagMatch(7L, TagBackends.inert(), List.of(), loop::add,
                        stack::add, (player, reason) -> { }, (role, reason) -> { },
                        (player, role) -> { }));
        return new Channels(context, loop, stack);
    }

    @Test
    void stackExhaustedFiresOnce() {
        Channels channels = channels();

        channels.context().stackExhausted("boom");
        channels.context().stackExhausted("boom");

        assertEquals(List.of("boom"), channels.stack());
        assertEquals(List.of(), channels.loop());
    }

    @Test
    void loopLimitExceededBlocksStackAfter() {
        Channels channels = channels();

        channels.context().loopLimitExceeded("steps");
        channels.context().stackExhausted("boom");

        assertEquals(List.of("steps"), channels.loop());
        assertEquals(List.of(), channels.stack());
    }

    @Test
    void stackExhaustedBlocksLoopAfter() {
        Channels channels = channels();

        channels.context().stackExhausted("boom");
        channels.context().loopLimitExceeded("steps");

        assertEquals(List.of("boom"), channels.stack());
        assertEquals(List.of(), channels.loop());
    }

    @Test
    void resetStepBudgetRearmsBothChannels() {
        Channels channels = channels();

        channels.context().loopLimitExceeded("steps");
        channels.context().resetStepBudget();
        channels.context().stackExhausted("boom");

        assertEquals(List.of("steps"), channels.loop());
        assertEquals(List.of("boom"), channels.stack());
    }
}
