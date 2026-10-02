package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.LongSupplier;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cooldown tags over a fake clock: gates, countdowns, resets, misuse. */
class TagCooldownsTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        long now;
        final RosterValues roster = new RosterValues() {
            @Override
            public Optional<String> roleOf(String playerName) {
                return Optional.empty();
            }

            @Override
            public List<String> activePlayers(String role) {
                return List.of();
            }

            @Override
            public Optional<Location> locationOf(String playerName) {
                if (playerName.equalsIgnoreCase("Steve")
                        || playerName.equalsIgnoreCase("Alex")) {
                    return Optional.of(new Location(null, 0, 64, 0));
                }
                return Optional.empty();
            }
        };

        TagContext context() {
            LongSupplier clock = () -> now;
            TagContext context = TagContext.run(new TagContext.TagIdentity(ModifierTagScope.match("Steve",
                    List.of(), new Random(23), warnings::add), "cooldowns"),
                    TagContext.TagSinks.simple(warnings::add, warnings::add,
                            (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                            ModifierTagScope.match("Steve", List.of(), new Random(23), warnings::add)),
                    TagContext.TagRole.silent(),
                    TagContext.TagMatch.simple(7L, new TagBackends(StatValues.inert(),
                            new FlagStore(),
                                    (text, name) -> text, roster, PlayerSinks.inert())
                                    , (player, reason) -> { }, (role, reason) -> { }));
            context.setCooldowns(new TagCooldownStore(clock));
            return context;
        }

        String replace(String command, TagContext context) {
            return CommandPlaceholders.replace(command, "Steve", 0, 0, 0, context);
        }
    }

    @Test
    void globalGateBlocksASecondImmediateCall() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("true", fixture.replace("<gcooldown:dash,5>", context));
        assertEquals("false", fixture.replace("<gcooldown:dash,5>", context));
        fixture.now += 5000;
        assertEquals("true", fixture.replace("<gcooldown:dash,5>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void playerGatesStayIsolatedAndCaseBlind() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("true", fixture.replace("<pcooldown:Steve,dash,5>", context));
        assertEquals("true", fixture.replace("<pcooldown:Alex,dash,5>", context));
        assertEquals("false", fixture.replace("<pcooldown:Steve,dash,5>", context));
        assertEquals("false", fixture.replace("<pcooldown:steve,dash,5>", context));
        assertEquals("true", fixture.replace("<gcooldown:dash,5>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void offlinePlayersResolveNullSilently() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<pcooldown:Ghost,dash,5>", context));
        assertEquals("null", fixture.replace("<pcooldown.get:Ghost,dash,5>", context));
        assertEquals("null", fixture.replace("<pcooldown.reset:Ghost,dash>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void getCountsDownAndResetReleases() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("0", fixture.replace("<pcooldown.get:Steve,dash,5>", context));
        assertEquals("true", fixture.replace("<pcooldown:Steve,dash,5>", context));
        assertEquals("5", fixture.replace("<pcooldown.get:Steve,dash,5>", context));
        fixture.now += 2500;
        assertEquals("2.5", fixture.replace("<pcooldown.get:Steve,dash,5>", context));
        assertEquals("true", fixture.replace("<pcooldown.reset:Steve,dash>", context));
        assertEquals("0", fixture.replace("<pcooldown.get:Steve,dash,5>", context));
        assertEquals("true", fixture.replace("<pcooldown:Steve,dash,5>", context));
        assertEquals("0", fixture.replace("<gcooldown.get:dash,5>", context));
        assertEquals("true", fixture.replace("<gcooldown.reset:dash>", context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void playerGateInsideIfMatchesGappleShape() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();
        String line = "<if:\"<pcooldown:<p>,<id>,300> == true\","
                + "\"give <p> golden_apple\",\"exit\">";
        String precheck = "<if:\"<pcooldown.get:<p>,<id>,300> gt 0\",\"exit\">";

        assertEquals("", fixture.replace(precheck, context));
        assertEquals("give Steve golden_apple", fixture.replace(line, context));
        assertEquals("exit", fixture.replace(precheck, context));
        assertEquals("exit", fixture.replace(line, context));
        fixture.now += 300000;
        assertEquals("give Steve golden_apple", fixture.replace(line, context));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void misuseWarnsWithNull() {
        Fixture fixture = new Fixture();
        TagContext context = fixture.context();

        assertEquals("null", fixture.replace("<gcooldown:dash,x>", context));
        assertEquals("null", fixture.replace("<gcooldown:dash,-1>", context));
        assertEquals("null", fixture.replace("<gcooldown:>", context));
        assertEquals("null", fixture.replace("<gcooldown:dash>", context));
        assertEquals("null", fixture.replace("<gcooldown.get:dash,x>", context));
        assertEquals("null", fixture.replace("<gcooldown.reset:>", context));
        assertEquals("null", fixture.replace("<pcooldown:Steve,dash>", context));
        assertEquals("null", fixture.replace("<pcooldown:Steve,dash,x>", context));
        assertEquals("null", fixture.replace("<pcooldown:,dash,5>", context));
        assertEquals("null", fixture.replace("<pcooldown:Steve,,5>", context));
        assertEquals(10, fixture.warnings.size());
    }
}
