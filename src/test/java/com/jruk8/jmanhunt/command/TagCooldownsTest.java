package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cooldown tags over a fake clock: gate, countdown, reset, misuse. */
class TagCooldownsTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        long now;

        TagContext context() {
            LongSupplier clock = () -> now;
            TagContext context = TagContext.run(
                    ModifierTagScope.match("Steve", List.of(), new Random(23), warnings::add),
                    "cooldowns", warnings::add, warnings::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    (player, reason) -> { }, (role, reason) -> { },
                    7L, new TagBackends(StatValues.inert(), new FlagStore(),
                            (text, name) -> text, RosterValues.inert(),
                            PlayerSinks.inert()));
            context.setCooldowns(new TagCooldownStore(clock));
            return context;
        }

        String replace(String command, TagContext context) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }
    }

    @Test
    void gateBlocksASecondImmediateCall() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("true", fixture.replace("<cooldown:dash,5>", context));
        assertEquals("false", fixture.replace("<cooldown:dash,5>", context));
        fixture.now += 5000;
        assertEquals("true", fixture.replace("<cooldown:dash,5>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void getCountsDownAndResetReleases() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("0", fixture.replace("<cooldown.get:dash,5>", context));
        assertEquals("true", fixture.replace("<cooldown:dash,5>", context));
        assertEquals("5", fixture.replace("<cooldown.get:dash,5>", context));
        fixture.now += 2500;
        assertEquals("2.5", fixture.replace("<cooldown.get:dash,5>", context));
        assertEquals("true", fixture.replace("<cooldown.reset:dash>", context));
        assertEquals("0", fixture.replace("<cooldown.get:dash,5>", context));
        assertEquals("true", fixture.replace("<cooldown:dash,5>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void misuseWarnsWithNull() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<cooldown:dash,x>", context));
        assertEquals("null", fixture.replace("<cooldown:dash,-1>", context));
        assertEquals("null", fixture.replace("<cooldown:>", context));
        assertEquals("null", fixture.replace("<cooldown:dash>", context));
        assertEquals("null", fixture.replace("<cooldown.get:dash,x>", context));
        assertEquals("null", fixture.replace("<cooldown.reset:>", context));
        assertEquals(6, fixture.warnings.size());
    }
}
