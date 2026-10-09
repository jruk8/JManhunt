package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.command.TagContext;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Dry-run pipeline: line parsing, role rule, mock env, reporting. */
class ModifierTestServiceTest {

    @Test
    void parseCommandLinesRunsSingleLine() {
        assertEquals(List.of("give <p> bread"), ModifierTestService.parseCommandLines("give <p> bread"));
        assertEquals(List.of("say hi"), ModifierTestService.parseCommandLines("  say hi  "));
        assertEquals(List.of(), ModifierTestService.parseCommandLines("   "));
    }

    @Test
    void parseCommandLinesSplitsArrays() {
        assertEquals(List.of("say one", "say two"),
                ModifierTestService.parseCommandLines("[\"say one\", \"say two\"]"));
        assertEquals(List.of("say one", "say two"),
                ModifierTestService.parseCommandLines("[say one, say two]"));
        assertEquals(List.of(), ModifierTestService.parseCommandLines("[]"));
        assertEquals(List.of("say one"),
                ModifierTestService.parseCommandLines("[say one, \"\"]"));
    }

    private static ModifiersMessages texts() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "modifiers.test-success", "success tpl");
        ConfigPathMapper.set(config, "modifiers.test-failure", "failure tpl");
        ConfigPathMapper.set(config, "modifiers.test-error-line", "error line tpl");
        ConfigPathMapper.set(config, "modifiers.test-error", "error tpl");
        return config.getModifiers();
    }

    @Test
    void roleForPrefersListAudience() {
        ModifierTestService service = new ModifierTestService(mock(GameStateCommandManager.class),
                new PlayerStateStore(), mock(MessageService.class), new ModifiersMessages(),
                mock(SoundService.class));
        Player viewer = mock(Player.class);

        assertEquals("HUNTER", service.roleFor(viewer, "hunter"));
        assertEquals("SPEEDRUNNER", service.roleFor(viewer, "speedrunner"));
    }

    @Test
    void roleForFallsBackToViewerThenHunter() {
        PlayerStateStore states = new PlayerStateStore();
        ModifierTestService service = new ModifierTestService(mock(GameStateCommandManager.class),
                states, mock(MessageService.class), new ModifiersMessages(), mock(SoundService.class));
        Player viewer = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(id);

        assertEquals("HUNTER", service.roleFor(viewer, "console"));
        states.setRole(id, Role.SPEEDRUNNER);
        assertEquals("SPEEDRUNNER", service.roleFor(viewer, "console"));
        states.setRole(id, Role.SPECTATOR);
        assertEquals("HUNTER", service.roleFor(viewer, "player"));
    }

    @Test
    void runUsesMockStatsAndRoster() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                mock(MessageService.class), new ModifiersMessages(), mock(SoundService.class));
        Player sender = mock(Player.class);
        when(sender.getName()).thenReturn("Steve");
        List<String> lines = List.of("say hi");

        ModifierTestService.TestResult result = service.run(sender, "HUNTER", lines);

        ArgumentCaptor<TagContext> contexts = ArgumentCaptor.forClass(TagContext.class);
        verify(commands).runCommandList(eq(lines), eq(sender), contexts.capture(), any());
        TagContext context = contexts.getValue();
        assertEquals(Optional.of("20.0"), context.statValues().player("Steve", "health"));
        assertEquals(Optional.of("20"), context.statValues().player("Steve", "hunger"));
        assertEquals(Optional.of("0"), context.statValues().global("duration"));
        assertEquals(Optional.of("HUNTER"), context.roster().roleOf("Steve"));
        assertEquals(List.of("Steve"), context.roster().activePlayers("HUNTER"));
        assertEquals(List.of("Steve"), context.roster().activePlayers("hunter"));
        assertTrue(result.elapsedMs() >= 0);
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void runDelegatesNestedCommandsToManager() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                mock(MessageService.class), new ModifiersMessages(), mock(SoundService.class));
        Player sender = mock(Player.class);
        when(sender.getName()).thenReturn("Steve");
        service.run(sender, "HUNTER", List.of("say hi"));

        ArgumentCaptor<TagContext> contexts = ArgumentCaptor.forClass(TagContext.class);
        verify(commands).runCommandList(any(), eq(sender), contexts.capture(), any());
        contexts.getValue().runCommand("say nested", "probe");
        verify(commands).runTagCommand("say nested", "probe");
    }

    @Test
    void nullDistanceSidePrintsNullAndPasses() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        java.util.List<String> outputs = new java.util.ArrayList<>();
        doAnswer(call -> {
            TagContext context = call.getArgument(2);
            @SuppressWarnings("unchecked")
            java.util.List<String> lines = (java.util.List<String>) call.getArgument(0);
            for (String line : lines) {
                outputs.add(com.jruk8.jmanhunt.command.CommandPlaceholders.replace(
                        line, "Steve", 0, 64, 0, context));
            }
            return null;
        }).when(commands).runCommandList(any(), any(), any(), any());
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                mock(MessageService.class), new ModifiersMessages(), mock(SoundService.class));
        Player sender = mock(Player.class);
        when(sender.getName()).thenReturn("Steve");
        org.bukkit.World world = mock(org.bukkit.World.class);
        when(world.getName()).thenReturn("world");
        when(world.getEnvironment()).thenReturn(org.bukkit.World.Environment.NORMAL);
        when(sender.getLocation()).thenReturn(new org.bukkit.Location(world, 0, 64, 0));

        ModifierTestService.TestResult result = service.run(sender, "HUNTER",
                List.of("say <distance:<plocation:<p>>,<plocation:FakePlayer>>"));

        assertEquals(List.of("say null"), outputs);
        assertTrue(result.warnings().isEmpty(), result.warnings().toString());
    }

    @Test
    void reportReplaysOutputThenSuccess() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        when(commands.formatEngineMessage(anyString()))
                .thenAnswer(call -> "FMT:" + call.getArgument(0));
        MessageService messages = mock(MessageService.class);
        SoundService sounds = mock(SoundService.class);
        when(sounds.isValidSound("good")).thenReturn(true);
        when(sounds.isValidSound("bad")).thenReturn(false);
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                messages, texts(), sounds);
        Player sender = mock(Player.class);
        ModifierTestService.TestResult result = new ModifierTestService.TestResult(12L, List.of(),
                List.of("hello"), List.of(new ModifierTestService.CapturedSound("good", 1, 1),
                        new ModifierTestService.CapturedSound("bad", 1, 1)),
                null, null);

        service.report(sender, result);

        verify(messages).sendText(sender, "FMT:hello");
        verify(sounds).playCustomSound(sender, "good", 1, 1);
        verify(sounds, never()).playCustomSound(eq(sender), eq("bad"), anyFloat(), anyFloat());
        verify(messages).messageRaw(sender, "success tpl", Map.of("elapsed", "12"));
    }

    @Test
    void runCapturesThrowingLineAndStops() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        doAnswer(call -> {
            TagContext context = call.getArgument(2);
            context.setProvenance(new TagContext.Provenance("modifiers-test", -1, "test", 1));
            throw new IllegalStateException("boom");
        }).when(commands).runCommandList(any(), any(), any(), any());
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                mock(MessageService.class), new ModifiersMessages(), mock(SoundService.class));
        Player sender = mock(Player.class);
        when(sender.getName()).thenReturn("Steve");

        ModifierTestService.TestResult result =
                service.run(sender, "HUNTER", List.of("say one", "say two"));

        assertEquals(2, result.errorLine());
        assertEquals("java.lang.IllegalStateException: boom", result.errorText());
        assertTrue(result.elapsedMs() >= 0);
        assertTrue(result.messages().isEmpty());
        verify(commands).runCommandList(any(), any(), any(), any());
    }

    @Test
    void runFloorsUnknownLinesAndCatchesErrors() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        doAnswer(call -> {
            throw new AssertionError("fatal");
        }).when(commands).runCommandList(any(), any(), any(), any());
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                mock(MessageService.class), new ModifiersMessages(), mock(SoundService.class));
        Player sender = mock(Player.class);
        when(sender.getName()).thenReturn("Steve");

        ModifierTestService.TestResult result = service.run(sender, "HUNTER", List.of("say hi"));

        assertEquals(1, result.errorLine());
        assertEquals("java.lang.AssertionError: fatal", result.errorText());
    }

    @Test
    void runLeavesErrorInfoNullWhenClean() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                mock(MessageService.class), new ModifiersMessages(), mock(SoundService.class));
        Player sender = mock(Player.class);
        when(sender.getName()).thenReturn("Steve");

        ModifierTestService.TestResult result = service.run(sender, "HUNTER", List.of("say hi"));

        assertNull(result.errorLine());
        assertNull(result.errorText());
    }

    @Test
    void reportPrintsErrorLineThenFailure() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        MessageService messages = mock(MessageService.class);
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                messages, texts(), mock(SoundService.class));
        Player sender = mock(Player.class);
        ModifierTestService.TestResult result = new ModifierTestService.TestResult(7L,
                List.of(), List.of("partial"), List.of(), 2, "java.lang.Boom: bang");

        service.report(sender, result);

        InOrder order = inOrder(messages);
        order.verify(messages).messageRaw(sender, "error line tpl",
                Map.of("line", "2", "exception", "java.lang.Boom: bang"));
        order.verify(messages).messageRaw(sender, "error tpl", Map.of("time", "7"));
        verify(messages, never()).messageRaw(eq(sender), eq("success tpl"), any());
        verify(messages, never()).messageRaw(eq(sender), eq("failure tpl"), any());
        verify(messages, never()).sendText(eq(sender), anyString());
    }

    @Test
    void runResolvesSenderForNamedPlayerSinks() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        doAnswer(call -> {
            TagContext context = call.getArgument(2);
            @SuppressWarnings("unchecked")
            java.util.List<String> lines = (java.util.List<String>) call.getArgument(0);
            for (String line : lines) {
                com.jruk8.jmanhunt.command.CommandPlaceholders.replace(
                        line, "Steve", 0, 64, 0, context);
            }
            return null;
        }).when(commands).runCommandList(any(), any(), any(), any());
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                mock(MessageService.class), new ModifiersMessages(), mock(SoundService.class));
        Player sender = mock(Player.class);
        when(sender.getName()).thenReturn("Steve");

        ModifierTestService.TestResult result = service.run(sender, "HUNTER",
                List.of("<psound:<p>,good,1,1>", "<pmessage:<p>,hello>"));

        assertTrue(result.warnings().isEmpty());
        assertEquals(List.of(new ModifierTestService.CapturedSound("good", 1, 1)),
                result.sounds());
        assertEquals(List.of("[Steve] hello"), result.messages());
    }

    @Test
    void runKeepsOfflineWarningForUnknownTestNames() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        doAnswer(call -> {
            TagContext context = call.getArgument(2);
            @SuppressWarnings("unchecked")
            java.util.List<String> lines = (java.util.List<String>) call.getArgument(0);
            for (String line : lines) {
                com.jruk8.jmanhunt.command.CommandPlaceholders.replace(
                        line, "Steve", 0, 64, 0, context);
            }
            return null;
        }).when(commands).runCommandList(any(), any(), any(), any());
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                mock(MessageService.class), new ModifiersMessages(), mock(SoundService.class));
        Player sender = mock(Player.class);
        when(sender.getName()).thenReturn("Steve");

        ModifierTestService.TestResult result = service.run(sender, "HUNTER",
                List.of("<psound:Nobody,good,1,1>"));

        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("is offline"));
        assertTrue(result.sounds().isEmpty());
    }

    @Test
    void reportJoinsWarningsIntoFailure() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        MessageService messages = mock(MessageService.class);
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                messages, texts(), mock(SoundService.class));
        Player sender = mock(Player.class);
        ModifierTestService.TestResult result = new ModifierTestService.TestResult(3L,
                List.of("first", "second"), List.of(), List.of(), null, null);

        service.report(sender, result);

        verify(messages).messageRaw(sender, "failure tpl",
                Map.of("error", "first; second"));
        verify(messages, never()).messageRaw(eq(sender), eq("success tpl"), any());
    }
}
