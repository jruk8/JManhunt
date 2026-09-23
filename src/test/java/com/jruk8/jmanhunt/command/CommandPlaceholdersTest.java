package com.jruk8.jmanhunt.command;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the pure-logic parts of CommandPlaceholders (tilde resolution
 * and placeholder substitution that doesn't require a running Bukkit server).
 * The <random-mob> and <random-item> replacement logic depends on
 * EntityType.values() / Material.values() which require a server, so those
 * are not tested here.
 */
class CommandPlaceholdersTest {

    @Test
    void replaceSubstitutesPlayerName() {
        String result = CommandPlaceholders.replace("give <p> bread", "Steve", 0, 0, 0);
        assertEquals("give Steve bread", result);
    }

    @Test
    void resolveTildesBasic() {
        String result = CommandPlaceholders.resolveTildes("summon zombie ~ ~ ~", 10.5, 64.0, -20.2);
        assertEquals("summon zombie 10.5 64 -20.2", result);
    }

    @Test
    void resolveTildesWithPositiveOffset() {
        String result = CommandPlaceholders.resolveTildes("tp ~ ~5 ~", 10.5, 64.0, -20.2);
        assertEquals("tp 10.5 69 -20.2", result);
    }

    @Test
    void resolveTildesWithNegativeOffset() {
        String result = CommandPlaceholders.resolveTildes("tp ~ ~-3 ~", 10.5, 64.0, -20.2);
        assertEquals("tp 10.5 61 -20.2", result);
    }

    @Test
    void resolveTildesWithSixCoordinates() {
        String result = CommandPlaceholders.resolveTildes("fill ~ ~ ~ ~5 ~5 ~5 stone", 0.0, 0.0, 0.0);
        assertEquals("fill 0 0 0 5 5 5 stone", result);
    }

    @Test
    void resolveTildesWithDecimalOffset() {
        String result = CommandPlaceholders.resolveTildes("summon zombie ~ ~5.5 ~", 10.0, 64.0, 20.0);
        assertEquals("summon zombie 10 69.5 20", result);
    }

    @Test
    void resolveTildesNoTildesReturnsUnchanged() {
        String result = CommandPlaceholders.resolveTildes("give Steve bread", 10.0, 64.0, 20.0);
        assertEquals("give Steve bread", result);
    }

    @Test
    void replaceDoesNotResolveTildesForConsoleCommands() {
        // When playerName is null (console command), tildes should not be resolved
        String result = CommandPlaceholders.replace("summon zombie ~ ~ ~", null, 10.0, 64.0, 20.0);
        assertEquals("summon zombie ~ ~ ~", result);
    }

    @Test
    void replaceResolvesTildesForPlayerCommands() {
        String result = CommandPlaceholders.replace("summon zombie ~ ~ ~", "Steve", 10.5, 64.0, -20.2);
        assertEquals("summon zombie 10.5 64 -20.2", result);
    }

    @Test
    void withDurationFloorsDecimals() {
        assertEquals("effect give Steve minecraft:slowness 2 1 true",
                CommandPlaceholders.withDuration("effect give Steve minecraft:slowness <duration> 1 true", 2.9));
    }

    @Test
    void withDurationKeepsWholeSeconds() {
        assertEquals("effect give Steve minecraft:slowness 1 1 true",
                CommandPlaceholders.withDuration("effect give Steve minecraft:slowness <duration> 1 true", 1.0));
    }

    @Test
    void withDurationLeavesOtherCommandsAlone() {
        assertEquals("summon lightning_bolt ~ ~ ~",
                CommandPlaceholders.withDuration("summon lightning_bolt ~ ~ ~", 3.7));
    }
    private ModifierTagScope matchScope(List<String> warnings) {
        return ModifierTagScope.match("Steve", List.of(
                new ModifierTagScope.Participant("Alice", "HUNTER"),
                new ModifierTagScope.Participant("Bob", "SPEEDRUNNER")),
                new Random(42), warnings::add);
    }

    @Test
    void convertSelectorsBareAt() {
        assertEquals("tp <all-players> Steve", CommandPlaceholders.convertSelectors("tp @a Steve"));
        assertEquals("give <random-player> apple", CommandPlaceholders.convertSelectors("give @r apple"));
    }

    @Test
    void convertSelectorsKeepsTeamFilter() {
        assertEquals("give <all-players:HUNTER> apple",
                CommandPlaceholders.convertSelectors("give @a[team=HUNTER] apple"));
        assertEquals("give <all-players:SPEEDRUNNER> apple",
                CommandPlaceholders.convertSelectors("give @a[distance=..15,team=speedrunner] apple"));
    }

    @Test
    void convertSelectorsLeavesOtherTextAlone() {
        assertEquals("tell @admin hi", CommandPlaceholders.convertSelectors("tell @admin hi"));
        assertEquals("kill @e[type=zombie]", CommandPlaceholders.convertSelectors("kill @e[type=zombie]"));
    }

    @Test
    void randomNumberFixedRange() {
        List<String> warnings = new ArrayList<>();
        String result = CommandPlaceholders.replace("give <p> coal <random-num:5,5>",
                "Steve", 0, 0, 0, matchScope(warnings));
        assertEquals("give Steve coal 5", result);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void randomNumberIgnoresOrder() {
        List<String> warnings = new ArrayList<>();
        for (int attempt = 0; attempt < 25; attempt++) {
            String result = CommandPlaceholders.replace("give <p> coal <random-num:12,4>",
                    "Steve", 0, 0, 0, matchScope(warnings));
            int rolled = Integer.parseInt(result.replace("give Steve coal ", ""));
            assertTrue(rolled >= 4 && rolled <= 12, "out of range: " + result);
        }
        assertTrue(warnings.isEmpty());
    }

    @Test
    void randomNumberInvalidWarnsAndYieldsZero() {
        List<String> warnings = new ArrayList<>();
        assertEquals("give Steve coal 0", CommandPlaceholders.replace("give <p> coal <random-num:4>",
                "Steve", 0, 0, 0, matchScope(warnings)));
        assertEquals("give Steve coal 0", CommandPlaceholders.replace("give <p> coal <random-num:a,b>",
                "Steve", 0, 0, 0, matchScope(warnings)));
        assertEquals(2, warnings.size());
    }

    @Test
    void splitPickArgsRespectsQuotes() {
        assertEquals(List.of("gravel", " \"dirt\"", "   sand"),
                CommandPlaceholders.splitPickArgs("gravel, \"dirt\",   sand"));
        assertEquals(List.of("'a,b'", "c"),
                CommandPlaceholders.splitPickArgs("'a,b',c"));
    }

    @Test
    void parsePickItemAcceptsBareAndQuoted() {
        assertEquals("gravel", CommandPlaceholders.parsePickItem("  gravel ").orElseThrow());
        assertEquals("dirt", CommandPlaceholders.parsePickItem("\"dirt\"").orElseThrow());
        assertEquals("this is \"a\" sentence",
                CommandPlaceholders.parsePickItem("'this is \"a\" sentence'").orElseThrow());
    }

    @Test
    void parsePickItemRejectsIllegal() {
        assertTrue(CommandPlaceholders.parsePickItem("'this is 'illegal''").isEmpty());
        assertTrue(CommandPlaceholders.parsePickItem("'only one string per' 'this fails'").isEmpty());
        assertTrue(CommandPlaceholders.parsePickItem("un'balanced").isEmpty());
        assertTrue(CommandPlaceholders.parsePickItem("   ").isEmpty());
    }

    @Test
    void randomPickSingleItem() {
        List<String> warnings = new ArrayList<>();
        assertEquals("give Steve only", CommandPlaceholders.replace("give <p> <random-pick:only>",
                "Steve", 0, 0, 0, matchScope(warnings)));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void randomPickRetriesPastIllegalItems() {
        List<String> warnings = new ArrayList<>();
        String result = CommandPlaceholders.replace("give <p> <random-pick:'bad 'item'', ok>",
                "Steve", 0, 0, 0, matchScope(warnings));
        assertEquals("give Steve ok", result);
        assertTrue(warnings.size() <= 1);
        for (String warning : warnings) {
            assertTrue(warning.contains("('bad 'item'')"), warning);
            assertTrue(warning.contains("index 0"), warning);
        }
    }

    @Test
    void randomPickAllIllegalYieldsEmpty() {
        List<String> warnings = new ArrayList<>();
        String result = CommandPlaceholders.replace("give <p> <random-pick:'bad 'one'', 'bad 'two''>",
                "Steve", 0, 0, 0, matchScope(warnings));
        assertEquals("give Steve ", result);
        assertEquals(3, warnings.size());
    }

    @Test
    void nestedTagsEvaluateInsideOut() {
        List<String> warnings = new ArrayList<>();
        assertEquals("give Steve coal 1", CommandPlaceholders.replace(
                "give <p> <random-pick:coal <random-num:1,1>>", "Steve", 0, 0, 0, matchScope(warnings)));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void unknownTagsSurviveUntouched() {
        List<String> warnings = new ArrayList<>();
        assertEquals("say <hello> Steve", CommandPlaceholders.replace(
                "say <hello> <p>", "Steve", 0, 0, 0, matchScope(warnings)));
        assertEquals("effect give Steve slow <duration> 1",
                CommandPlaceholders.replace("effect give <p> slow <duration> 1",
                        "Steve", 0, 0, 0, matchScope(warnings)));
    }

    @Test
    void expandAllPlayersFansOut() {
        List<String> warnings = new ArrayList<>();
        assertEquals(List.of("tp Alice Steve", "tp Bob Steve"),
                CommandPlaceholders.expandAllPlayers("tp @a Steve", matchScope(warnings)));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void expandAllPlayersHonorsTeamFilter() {
        List<String> warnings = new ArrayList<>();
        assertEquals(List.of("give Bob apple"),
                CommandPlaceholders.expandAllPlayers("give @a[team=SPEEDRUNNER] apple", matchScope(warnings)));
        assertEquals(List.of("give Alice apple"),
                CommandPlaceholders.expandAllPlayers("give <all-players:HUNTER> apple", matchScope(warnings)));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void expandAllPlayersEmptyScopeSkipsWithWarning() {
        List<String> warnings = new ArrayList<>();
        ModifierTagScope scope = ModifierTagScope.executor("Steve", warnings::add);
        assertEquals(List.of(), CommandPlaceholders.expandAllPlayers("tp @a Steve", scope));
        assertEquals(1, warnings.size());
    }

    @Test
    void expandAllPlayersLeavesPlainCommandsAlone() {
        List<String> warnings = new ArrayList<>();
        assertEquals(List.of("say hi"),
                CommandPlaceholders.expandAllPlayers("say hi", matchScope(warnings)));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void randomPlayerDrawsFromScope() {
        List<String> warnings = new ArrayList<>();
        String result = CommandPlaceholders.replace("give <random-player> apple",
                "Steve", 0, 0, 0, matchScope(warnings));
        assertTrue(result.equals("give Alice apple") || result.equals("give Bob apple"), result);
        assertTrue(warnings.isEmpty());
    }

    @Test
    void randomPlayerFallsBackToExecutor() {
        assertEquals("give Steve apple",
                CommandPlaceholders.replace("give <random-player> apple", "Steve", 0, 0, 0));
    }
}
