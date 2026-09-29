package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.command.TagContext;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    @Test
    void roleForPrefersListAudience() {
        ModifierTestService service = new ModifierTestService(mock(GameStateCommandManager.class),
                new PlayerStateStore(), mock(MessageService.class), mock(SoundService.class));
        Player viewer = mock(Player.class);

        assertEquals("HUNTER", service.roleFor(viewer, "hunter"));
        assertEquals("SPEEDRUNNER", service.roleFor(viewer, "speedrunner"));
    }

    @Test
    void roleForFallsBackToViewerThenHunter() {
        PlayerStateStore states = new PlayerStateStore();
        ModifierTestService service = new ModifierTestService(mock(GameStateCommandManager.class),
                states, mock(MessageService.class), mock(SoundService.class));
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
                mock(MessageService.class), mock(SoundService.class));
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
                mock(MessageService.class), mock(SoundService.class));
        Player sender = mock(Player.class);
        when(sender.getName()).thenReturn("Steve");
        service.run(sender, "HUNTER", List.of("say hi"));

        ArgumentCaptor<TagContext> contexts = ArgumentCaptor.forClass(TagContext.class);
        verify(commands).runCommandList(any(), eq(sender), contexts.capture(), any());
        contexts.getValue().runCommand("say nested", "probe");
        verify(commands).runTagCommand("say nested", "probe");
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
                messages, sounds);
        Player sender = mock(Player.class);
        ModifierTestService.TestResult result = new ModifierTestService.TestResult(12L, List.of(),
                List.of("hello"), List.of(new ModifierTestService.CapturedSound("good", 1, 1),
                        new ModifierTestService.CapturedSound("bad", 1, 1)));

        service.report(sender, result);

        verify(messages).sendText(sender, "FMT:hello");
        verify(sounds).playCustomSound(sender, "good", 1, 1);
        verify(sounds, never()).playCustomSound(eq(sender), eq("bad"), anyFloat(), anyFloat());
        verify(messages).message(sender, "modifiers.test-success", Map.of("elapsed", "12"));
    }

    @Test
    void reportJoinsWarningsIntoFailure() {
        GameStateCommandManager commands = mock(GameStateCommandManager.class);
        MessageService messages = mock(MessageService.class);
        ModifierTestService service = new ModifierTestService(commands, new PlayerStateStore(),
                messages, mock(SoundService.class));
        Player sender = mock(Player.class);
        ModifierTestService.TestResult result = new ModifierTestService.TestResult(3L,
                List.of("first", "second"), List.of(), List.of());

        service.report(sender, result);

        verify(messages).message(sender, "modifiers.test-failure",
                Map.of("error", "first; second"));
        verify(messages, never()).message(eq(sender), eq("modifiers.test-success"), any());
    }
}
