package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code <placeholder>} alias over a map-backed fake resolver:
 * key wrapping, executor gating, and the bundled kill-streak shape
 * resolving through nested math.
 */
class TagPlaceholdersTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final Map<String, String> values = new HashMap<>();
        String lastPlayer;

        TagContext context(String executor) {
            ModifierTagScope scope = ModifierTagScope.match(executor, List.of(), new Random(5),
                    warnings::add);
            PlaceholderResolver resolver = (text, name) -> {
                lastPlayer = name;
                return values.getOrDefault(text, text);
            };
            return TagContext.run(scope, "get-stronger-on-kill", warnings::add, warnings::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    7L, new TagBackends(StatValues.inert(), new FlagStore(), resolver));
        }

        String replace(String command, String executor) {
            return CommandPlaceholders.replace(command, executor, 0, 0, 0, context(executor));
        }
    }

    @Test
    void aliasWrapsKeyAndRoutesThroughResolver() {
        Fixture fixture = new Fixture();
        fixture.values.put("%jmanhunt_game_kills_this_session%", "3");
        assertEquals("3", fixture.replace("<placeholder:jmanhunt_game_kills_this_session>", "Steve"));
        assertEquals("Steve", fixture.lastPlayer);
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void unknownKeysStayVerbatim() {
        Fixture fixture = new Fixture();
        assertEquals("%other_key%", fixture.replace("<placeholder:other_key>", "Steve"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void consoleExecutorWarnsAndYieldsEmpty() {
        Fixture fixture = new Fixture();
        ModifierTagScope scope = ModifierTagScope.executor(null, fixture.warnings::add);
        TagContext context = TagContext.run(scope, "gear-dice", fixture.warnings::add,
                fixture.warnings::add, (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                7L, new TagBackends(StatValues.inert(), new FlagStore(), (text, name) -> text));
        assertEquals("", CommandPlaceholders.replace("<placeholder:x>", null, 0, 0, 0, context));
        assertEquals(1, fixture.warnings.size());
    }

    @Test
    void shapesRejectBadArityAndBlankKeys() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<placeholder:a,b>", "Steve"));
        assertEquals("", fixture.replace("<placeholder:\"  \">", "Steve"));
        assertEquals(2, fixture.warnings.size());
    }

    @Test
    void killStreakAmplifierResolvesThroughNestedMath() {
        Fixture fixture = new Fixture();
        fixture.values.put("%jmanhunt_game_kills_this_session%", "3");
        TagContext context = fixture.context("Steve");
        assertEquals("",
                CommandPlaceholders.replace(
                        "<lflag:amplifier, <clamp:<placeholder:jmanhunt_game_kills_this_session>-1, 0, 9>>",
                        "Steve", 0, 0, 0, context));
        assertEquals("effect give Steve minecraft:health_boost infinite 2 true",
                CommandPlaceholders.replace(
                        "effect give <p> minecraft:health_boost infinite <lflag:amplifier> true",
                        "Steve", 0, 0, 0, context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }
}
