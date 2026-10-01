package com.jruk8.jmanhunt.setup;

import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.Map;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One-click setup failure paths over mocks: a rejected
 * world-engine write and a clashing lobby world name both abort
 * before touching worlds or teleports. The success path needs
 * live worlds, so it stays an in-game check.
 */
class SetupServiceTest {

    private GameManager game;
    private MessageService messages;
    private SetupService setup;
    private Player clicker;
    private Runnable observeWorldEngine;
    private Runnable markSetupDone;

    @BeforeEach
    void setUp() throws Exception {
        game = mock(GameManager.class);
        messages = mock(MessageService.class);
        observeWorldEngine = mock(Runnable.class);
        markSetupDone = mock(Runnable.class);
        clicker = mock(Player.class);
        ConfigService config = mock(ConfigService.class);
        MessagesConfig texts = new MessagesConfig();
        ConfigPathMapper.set(texts, "manhunt.setting-invalid", "invalid tpl");
        ConfigPathMapper.set(texts, "manhunt.worldengine-tpto-lobby-world-clash", "clash tpl");
        ConfigPathMapper.set(texts, "manhunt.worldengine-tpto-failed", "failed tpl");
        ManhuntMessages manhunt = texts.getManhunt();
        setup = new SetupService(game,
                new SetupService.Announcer(messages, manhunt, mock(SoundService.class)),
                new SettingFeedback(messages, manhunt, config, null), observeWorldEngine,
                markSetupDone);
    }

    @Test
    void rejectedWorldEngineWriteAborts() {
        when(game.setSetting("world-engine.enabled", "true")).thenReturn(
                ConfigService.SetOutcome.fail("manhunt.setting-invalid", Map.of()));

        setup.recommendedSetup(clicker);

        verify(messages).messageRaw(clicker, "invalid tpl", Map.of());
        verify(observeWorldEngine, never()).run();
        verify(game, never()).ensureLobbyWorld();
        verify(markSetupDone, never()).run();
    }

    @Test
    void clashingLobbyWorldAbortsBeforeGeneration() {
        when(game.setSetting(anyString(), anyString())).thenReturn(
                ConfigService.SetOutcome.ok(null, null, null));
        when(game.lobbyWorldNameClashes()).thenReturn(true);

        setup.recommendedSetup(clicker);

        verify(messages).messageRaw(clicker, "clash tpl");
        verify(game, never()).ensureLobbyWorld();
        verify(markSetupDone, never()).run();
    }

    @Test
    void failedGenerationAbortsBeforeTeleports() {
        when(game.setSetting(anyString(), anyString())).thenReturn(
                ConfigService.SetOutcome.ok(null, null, null));
        when(game.lobbyWorldNameClashes()).thenReturn(false);
        when(game.lobbyWorldName()).thenReturn("jmh_lobby");
        when(game.lobbyWorldExists()).thenReturn(false);
        when(game.ensureLobbyWorld()).thenReturn(Optional.empty());

        setup.recommendedSetup(clicker);

        verify(messages).messageRaw(eq(clicker), eq("failed tpl"), any());
        verify(markSetupDone, never()).run();
    }
}
