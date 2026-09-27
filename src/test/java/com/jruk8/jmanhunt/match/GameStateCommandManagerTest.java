package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.command.ModifierTagScope;
import com.jruk8.jmanhunt.command.StatValues;
import com.jruk8.jmanhunt.command.TagBackends;
import com.jruk8.jmanhunt.command.TagContext;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameStateCommandManagerTest {

    @Test
    void removedLegacyKeysPassThroughUnmatched() {
        assertEquals("ON_FIRST_ENTER_NETHER",
                ModifierTriggers.normalizeTrigger("ON_FIRST_ENTER_NETHER"));
        assertEquals("ON_FIRST_ENTER_END",
                ModifierTriggers.normalizeTrigger("ON_FIRST_ENTER_END"));
        assertEquals("ON_EVERY_KILL",
                ModifierTriggers.normalizeTrigger("ON_EVERY_KILL"));
        assertFalse(ModifierTriggers.KNOWN.contains("ON_EVERY_KILL"));
    }

    @Test
    void canonicalAndUnknownKeysPassThrough() {
        assertEquals("ON_NETHER_ENTER",
                ModifierTriggers.normalizeTrigger("ON_NETHER_ENTER"));
        assertEquals("ON_FIRST_NETHER_ENTER",
                ModifierTriggers.normalizeTrigger("ON_FIRST_NETHER_ENTER"));
        assertEquals("ON_FIRST_END_ENTER",
                ModifierTriggers.normalizeTrigger("ON_FIRST_END_ENTER"));
        assertEquals("ON_MOB_KILL",
                ModifierTriggers.normalizeTrigger("ON_MOB_KILL"));
        assertEquals("ON_PLAYER_KILL",
                ModifierTriggers.normalizeTrigger("ON_PLAYER_KILL"));
    }

    @Test
    void killTriggersSplitMobAndPlayer() {
        assertTrue(ModifierTriggers.KNOWN.contains("ON_MOB_KILL"));
        assertTrue(ModifierTriggers.KNOWN.contains("ON_PLAYER_KILL"));
        assertTrue(ModifierTriggers.KNOWN.indexOf("ON_MOB_KILL")
                < ModifierTriggers.KNOWN.indexOf("ON_PLAYER_KILL"));
    }

    @Test
    void matchingIgnoresCaseAndSurroundingWhitespace() {
        assertEquals("on_first_enter_nether",
                ModifierTriggers.normalizeTrigger("  on_first_enter_nether "));
        assertEquals("on_start",
                ModifierTriggers.normalizeTrigger("  on_start "));
    }

    @Test
    void nullStaysNull() {
        assertNull(ModifierTriggers.normalizeTrigger(null));
    }

    @Test
    void chanceClampsToUnitRange() {
        assertEquals(1.0, ModifierTriggers.clampChance(1.0));
        assertEquals(0.0, ModifierTriggers.clampChance(0.0));
        assertEquals(1.0, ModifierTriggers.clampChance(1.5));
        assertEquals(0.0, ModifierTriggers.clampChance(-0.25));
        assertEquals(1.0, ModifierTriggers.clampChance(Double.NaN));
    }

    @Test
    void gameruleRestoresOnLastMatchEnd() {
        assertFalse(GameStateCommandManager.gameruleRestored("start", true, true));
        assertFalse(GameStateCommandManager.gameruleRestored("start", false, true));
        assertFalse(GameStateCommandManager.gameruleRestored("end", false, true));
        assertTrue(GameStateCommandManager.gameruleRestored("end", true, true));
        assertTrue(GameStateCommandManager.gameruleRestored("start", true, false));
        assertTrue(GameStateCommandManager.gameruleRestored("end", false, false));
    }

    @Test
    void chanceRollUsesStrictLessThan() {
        assertTrue(ModifierTriggers.rollChance(1.0, 0.999));
        assertFalse(ModifierTriggers.rollChance(0.0, 0.0));
        assertTrue(ModifierTriggers.rollChance(0.5, 0.499));
        assertFalse(ModifierTriggers.rollChance(0.5, 0.5));
    }

    @Test
    void scopeDefaultsToPerInvoke() {
        assertEquals(ModifierTriggers.TriggerScope.PER_INVOKE,
                ModifierTriggers.parseScope(null));
        assertEquals(ModifierTriggers.TriggerScope.PER_INVOKE,
                ModifierTriggers.parseScope("PER_INVOKE"));
        assertEquals(ModifierTriggers.TriggerScope.PER_EXECUTOR,
                ModifierTriggers.parseScope("  per_executor "));
        assertEquals(ModifierTriggers.TriggerScope.PER_INVOKE,
                ModifierTriggers.parseScope("banana"));
    }

    @Test
    void selectionDefaultsToInOrder() {
        assertEquals(ModifierTriggers.Selection.IN_ORDER,
                ModifierTriggers.parseSelection(null));
        assertEquals(ModifierTriggers.Selection.PICK_RANDOM,
                ModifierTriggers.parseSelection("pick_random"));
        assertEquals(ModifierTriggers.Selection.IN_ORDER,
                ModifierTriggers.parseSelection("IN_ORDER"));
    }

    @Test
    void picksDrawDistinctLinesUpToCount() {
        List<String> commands = List.of("a", "b", "c", "d");
        List<String> picked = ModifierTriggers.pickCommands(commands, 2, new Random(7));
        assertEquals(2, picked.size());
        assertEquals(2, picked.stream().distinct().count());
        assertTrue(commands.containsAll(picked));
        assertEquals(4, ModifierTriggers.pickCommands(commands, 99, new Random(7)).size());
        assertTrue(ModifierTriggers.pickCommands(commands, 0, new Random(7)).isEmpty());
        assertTrue(ModifierTriggers.pickCommands(List.of(), 1, new Random(7)).isEmpty());
    }

    @Test
    void deviationClampsToIntervalBounds() {
        assertEquals(0.0, ModifierTriggers.clampDeviation(0.0, 60.0));
        assertEquals(0.0, ModifierTriggers.clampDeviation(-5.0, 60.0));
        assertEquals(15.0, ModifierTriggers.clampDeviation(15.0, 60.0));
        assertEquals(60.0, ModifierTriggers.clampDeviation(90.0, 60.0));
    }

    @Test
    void jitteredIntervalStaysWithinBoundsAndTicks() {
        assertEquals(1200L, ModifierTriggers.jitteredIntervalTicks(60.0, 0.0, 0.5));
        assertEquals(900L, ModifierTriggers.jitteredIntervalTicks(60.0, 15.0, 0.0));
        assertEquals(1500L, ModifierTriggers.jitteredIntervalTicks(60.0, 15.0, 0.999999));
        // deviation equal to the interval can roll down to zero seconds: still one tick, never zero.
        assertEquals(1L, ModifierTriggers.jitteredIntervalTicks(60.0, 60.0, 0.0));
        assertEquals(1L, ModifierTriggers.secondsToTicks(0.0));
        assertEquals(1L, ModifierTriggers.secondsToTicks(-3.0));
    }

    @Test
    void preStartOrderDefaultsToBefore() {
        assertFalse(ModifierTriggers.runsAfterPrestart(null));
        assertFalse(ModifierTriggers.runsAfterPrestart("BEFORE"));
        assertFalse(ModifierTriggers.runsAfterPrestart("banana"));
        assertTrue(ModifierTriggers.runsAfterPrestart("AFTER"));
        assertTrue(ModifierTriggers.runsAfterPrestart("  after  "));
    }

    @Test
    void tagOnlyLinesSkipDispatchQuietly() {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        JManhuntLogger logger = mock(JManhuntLogger.class);
        when(plugin.logger()).thenReturn(logger);
        GameStateCommandManager manager = new GameStateCommandManager(plugin,
                new PlayerStateStore(), mock(ConfigService.class), mock(MessageService.class),
                mock(SoundService.class), mock(GameManager.class));
        List<String> warnings = new ArrayList<>();
        TagContext context = TagContext.run(
                ModifierTagScope.executor("Steve", warnings::add), "gapple-on-low-hp",
                text -> { }, text -> { },
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                (player, reason) -> { }, (role, reason) -> { },
                7L, TagBackends.inert());

        // A bare set evaluates to blank text; dispatching it crashes the
        // server dispatcher, so the line must be skipped with no error.
        manager.runCommandList(List.of("<pflag:lastuse-<id>,5>"), null, context,
                TagContext.Provenance.of("gapple-on-low-hp", 0, "console"));

        assertTrue(warnings.isEmpty(), warnings.toString());
        verify(logger, never()).severe(anyString());
    }

    @Test
    void runawayLoopCancelsMatchWithProvenance() {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        JManhuntLogger logger = mock(JManhuntLogger.class);
        when(plugin.logger()).thenReturn(logger);
        OverrideService overrides = mock(OverrideService.class);
        when(plugin.overrides()).thenReturn(overrides);
        when(overrides.modifierEnabled(any(), eq("spin"))).thenReturn(true);
        ConfigService config = mock(ConfigService.class);
        when(config.modifierNames()).thenReturn(java.util.Set.of("spin"));
        when(config.behaviorIndexes("spin")).thenReturn(List.of(0));
        when(config.commandList("spin", 0, "console-cleanup"))
                .thenReturn(List.of("<gmessage:warm>", "<while:true,<gmessage:x>>"));
        GameManager game = mock(GameManager.class);
        GameInstance instance = mock(GameInstance.class);
        Player player = mock(Player.class);
        when(game.instance(7L)).thenReturn(Optional.of(instance));
        when(game.onlineParticipants(7L)).thenReturn(List.of(player));
        when(game.matchStatValues(7L)).thenReturn(StatValues.inert());
        when(game.flagStore()).thenReturn(new FlagStore());
        MessageService messages = mock(MessageService.class);
        when(messages.string(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        GameStateCommandManager manager = new GameStateCommandManager(plugin,
                new PlayerStateStore(), config, messages,
                mock(SoundService.class), game);

        manager.runConsoleCleanup(7L);

        verify(logger).severe(argThat(line -> line != null && line.contains("<while>")
                && line.contains(
                        "modifier 'spin', behavior 0, list 'console-cleanup', line 1 (0-based)")));
        verify(messages).sendText(eq(player), argThat(text -> text != null
                && text.contains("step limit") && text.contains("administrator")));
        verify(game).cancel(instance);
    }

    @Test
    void runsOnMatchesTriggersAndDefaultsEmptyToStart() {
        assertTrue(ModifierTriggers.runsOn(List.of("INTERVAL"), "INTERVAL"));
        assertTrue(ModifierTriggers.runsOn(List.of("  on_start "), "ON_START"));
        assertTrue(ModifierTriggers.runsOn(List.of(), "ON_START"));
        assertFalse(ModifierTriggers.runsOn(List.of(), "INTERVAL"));
        assertFalse(ModifierTriggers.runsOn(List.of("ON_START"), "INTERVAL"));
    }

    @Test
    void staleDispatchVoidsRestartedOrTornDownEngines() {
        assertFalse(ModifierTriggers.isStaleDispatch(3L, 3L, true));
        assertTrue(ModifierTriggers.isStaleDispatch(3L, 4L, true));
        assertTrue(ModifierTriggers.isStaleDispatch(3L, 3L, false));
        assertTrue(ModifierTriggers.isStaleDispatch(3L, 4L, false));
    }

    @Test
    void bundledConfigRulesListDefaults() throws Exception {
        try (InputStream stream = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("config.yml"),
                "missing test resource: config.yml")) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
            List<String> rules = config.getStringList(
                    "advanced.advanced-match-controls.game-rules.rules");
            assertEquals(8, rules.size());
            assertTrue(rules.contains("DISABLE_PILLAGER_PATROLS"));
            assertFalse(rules.contains("DISABLE_COMMAND_FEEDBACK"));
        }
    }
}
