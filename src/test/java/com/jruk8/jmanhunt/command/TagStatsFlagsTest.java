package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stat and flag tags over a run context with a fake stat backend:
 * key tables, offline and match-less misses, flag scoping, and the
 * set-returns-empty plus unset-is-null shapes.
 */
class TagStatsFlagsTest {

    private static final class Fixture {
        final List<String> warnings = new ArrayList<>();
        final FlagStore flags = new FlagStore();
        final Map<String, String> playerValues = new HashMap<>();
        final Map<String, String> globalValues = new HashMap<>();
        final StatValues backend = new StatValues() {
            @Override
            public Optional<String> player(String playerName, String key) {
                return Optional.ofNullable(playerValues.get(playerName + "|" + key));
            }

            @Override
            public Optional<String> global(String key) {
                return Optional.ofNullable(globalValues.get(key));
            }
        };

        TagContext context(String executor, long matchId) {
            ModifierTagScope scope = ModifierTagScope.match(executor, List.of(), new Random(3),
                    warnings::add);
            return TagContext.run(scope, "gear-dice", warnings::add, warnings::add,
                    (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                    matchId, new TagBackends(backend, flags, (text, name) -> text));
        }

        String replace(String command, String executor, long matchId) {
            return CommandPlaceholders.replace(command, executor, 0, 0, 0,
                    context(executor, matchId));
        }

        String replace(String command) {
            return replace(command, "Steve", 7L);
        }
    }

    @Test
    void pstatResolvesKeysCaseBlindly() {
        Fixture fixture = new Fixture();
        fixture.playerValues.put("Steve|health", "19.5");
        fixture.playerValues.put("Steve|hunger", "14");
        fixture.playerValues.put("Steve|mobs-killed", "3");
        fixture.playerValues.put("Steve|achievements-gained", "0");
        assertEquals("19.5", fixture.replace("<pstat:Steve,health>"));
        assertEquals("14", fixture.replace("<pstat:\"Steve\",\"HUNGER\">"));
        assertEquals("3", fixture.replace("<pstat:<p>,Mobs-Killed>"));
        assertEquals("0", fixture.replace("<pstat:Steve,achievements-gained>"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void pstatOfflineWarnsAndYieldsEmpty() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<pstat:Alex,health>"));
        assertEquals(1, fixture.warnings.size());
        assertTrue(fixture.warnings.get(0).contains("Alex"), fixture.warnings.toString());
    }

    @Test
    void statShapesRejectBadArityAndUnknownKeys() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<pstat:Steve>"));
        assertEquals("", fixture.replace("<pstat:Steve,heath>"));
        assertEquals("", fixture.replace("<gstat:duration,daytime>"));
        assertEquals("", fixture.replace("<gstat:uptime>"));
        assertEquals(4, fixture.warnings.size());
        assertTrue(fixture.warnings.get(1).contains("health, hunger, mobs-killed"),
                fixture.warnings.toString());
        assertTrue(fixture.warnings.get(3).contains("duration, daytime"),
                fixture.warnings.toString());
    }

    @Test
    void gstatResolvesAndMissesTotalZero() {
        Fixture fixture = new Fixture();
        fixture.globalValues.put("duration", "732");
        fixture.globalValues.put("daytime", "6000");
        assertEquals("732", fixture.replace("<gstat:duration>"));
        assertEquals("6000", fixture.replace("<gstat:\"DAYTIME\">"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());

        Fixture missing = new Fixture();
        assertEquals("0", missing.replace("<gstat:duration>"));
        assertEquals("0", missing.replace("<gstat:daytime>"));
        assertEquals(2, missing.warnings.size());
    }

    @Test
    void gflagSetsThenGetsWithUnsetNull() {
        Fixture fixture = new Fixture();
        assertEquals("null", fixture.replace("<gflag:phase>"));
        assertEquals("", fixture.replace("<gflag:phase,one>"));
        assertEquals("one", fixture.replace("<gflag:phase>"));
        assertEquals("", fixture.replace("<gflag:\"spaced name\",hi>"));
        assertEquals("hi", fixture.replace("<gflag:spaced name>"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void gflagNamesMatchExactly() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<gflag:Phase,one>"));
        assertEquals("null", fixture.replace("<gflag:phase>"));
        assertEquals("one", fixture.replace("<gflag:Phase>"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void pflagScopesToExecutorAndConsole() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<pflag:cooldown,732>", "Steve", 7L));
        assertEquals("732", fixture.replace("<pflag:cooldown>", "Steve", 7L));
        assertEquals("null", fixture.replace("<pflag:cooldown>", "Alex", 7L));
        assertEquals("", fixture.replace("<pflag:cooldown,9>", null, 7L));
        assertEquals("9", fixture.replace("<pflag:cooldown>", null, 7L));
        assertEquals("732", fixture.replace("<pflag:cooldown>", "Steve", 7L));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void pflagAcceptsNestedIdInName() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<pflag:lastuse-<id>,732>"));
        assertEquals("732", fixture.replace("<pflag:lastuse-gear-dice>"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void lflagLivesAndDiesWithTheRun() {
        Fixture fixture = new Fixture();
        TagContext first = fixture.context("Steve", 7L);
        TagContext second = fixture.context("Steve", 7L);
        assertEquals("", CommandPlaceholders.replace("<lflag:x,1>", "Steve", 0, 0, 0, first));
        assertEquals("1", CommandPlaceholders.replace("<lflag:x>", "Steve", 0, 0, 0, first));
        assertEquals("null", CommandPlaceholders.replace("<lflag:x>", "Steve", 0, 0, 0, second));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void matchScopedFlagsGateOutsideMatches() {
        Fixture fixture = new Fixture();
        assertEquals("null", fixture.replace("<gflag:x>", "Steve", TagContext.NO_MATCH));
        assertEquals("null", fixture.replace("<pflag:x>", "Steve", TagContext.NO_MATCH));
        assertEquals("", fixture.replace("<gflag:x,1>", "Steve", TagContext.NO_MATCH));
        assertEquals("", fixture.replace("<pflag:x,1>", "Steve", TagContext.NO_MATCH));
        assertEquals(2, fixture.warnings.size());
    }

    @Test
    void flagValuesResolveNestedTags() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<gflag:x,<min:8,3>>"));
        assertEquals("3", fixture.replace("<gflag:x>"));
        assertEquals("null", fixture.replace("<gflag:y>"));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void gappleCooldownShapeGivesOnlyAfterFiveMinutes() {
        Fixture fixture = new Fixture();
        fixture.playerValues.put("Steve|health", "5");
        fixture.globalValues.put("duration", "732");
        String check = "<if:\"<pstat:<p>,health> <= 7"
                + " and <gstat:duration>-<pflag:lastuse-<id>> ?? 999999 > 300\","
                + "\"give <p> golden_apple\",\"exit\">";
        assertEquals("give Steve golden_apple", fixture.replace(check));

        assertEquals("", fixture.replace("<pflag:lastuse-<id>,<gstat:duration>>"));
        fixture.globalValues.put("duration", "735");
        assertEquals("exit", fixture.replace(check));

        fixture.globalValues.put("duration", "1033");
        assertEquals("give Steve golden_apple", fixture.replace(check));

        fixture.playerValues.put("Steve|health", "8");
        assertEquals("exit", fixture.replace(check));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void showcaseCountdownAnnouncesRemaining() {
        Fixture fixture = new Fixture();
        String line = "say <if:\"3600-<gstat:duration> > 0\","
                + "\"Border shrinks in <clamp:3600-<gstat:duration>, 0, 3600>s\","
                + "\"The border is shrinking!\">";
        fixture.globalValues.put("duration", "500");
        assertEquals("say Border shrinks in 3100s", fixture.replace(line));
        fixture.globalValues.put("duration", "3700");
        assertEquals("say The border is shrinking!", fixture.replace(line));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void showcaseDiceRaceScoresToAHundred() {
        Fixture fixture = new Fixture();
        String roll = "<pflag:score,<clamp:(<pflag:score> ?? 0)+<random-num:1,6>, 0, 100>>";
        String milestone = "<if:\"<pflag:score> >= 100\",\"say <p> hit 100!\",\"exit\">";
        assertEquals("", fixture.replace(roll));
        int first = Integer.parseInt(fixture.replace("<pflag:score>"));
        assertTrue(first >= 1 && first <= 6, "first roll out of range: " + first);
        assertEquals("exit", fixture.replace(milestone));
        assertEquals("", fixture.replace("<pflag:score,99>"));
        assertEquals("", fixture.replace(roll));
        assertEquals("100", fixture.replace("<pflag:score>"));
        assertEquals("say Steve hit 100!", fixture.replace(milestone));
        assertTrue(fixture.warnings.isEmpty(), fixture.warnings.toString());
    }

    @Test
    void flagShapesRejectBadArityAndBlankNames() {
        Fixture fixture = new Fixture();
        assertEquals("", fixture.replace("<gflag:>"));
        assertEquals("", fixture.replace("<gflag:a,b,c>"));
        assertEquals("", fixture.replace("<pflag:\"  \",1>"));
        assertEquals("", fixture.replace("<lflag:>"));
        assertEquals(4, fixture.warnings.size());
    }
}
