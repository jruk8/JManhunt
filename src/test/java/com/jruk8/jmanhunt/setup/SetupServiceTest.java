package com.jruk8.jmanhunt.setup;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.Map;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private JManhuntPlugin plugin;
    private MessageService messages;
    private SetupService setup;
    private Player clicker;

    @BeforeEach
    void setUp() throws Exception {
        game = mock(GameManager.class);
        plugin = mock(JManhuntPlugin.class);
        messages = mock(MessageService.class);
        clicker = mock(Player.class);
        ConfigService config = mock(ConfigService.class);
        setup = new SetupService(plugin, game, messages, mock(SoundService.class),
                new SettingFeedback(messages, config, null));
    }

    @Test
    void rejectedWorldEngineWriteAborts() {
        when(game.setSetting("world-engine.enabled", "true")).thenReturn(
                ConfigService.SetOutcome.fail("manhunt.setting-invalid", Map.of()));

        setup.recommendedSetup(clicker);

        verify(messages).message(clicker, "manhunt.setting-invalid", Map.of());
        verify(plugin, never()).observeWorldEngine();
        verify(game, never()).ensureLobbyWorld();
        verify(plugin, never()).markSetupDone();
    }

    @Test
    void clashingLobbyWorldAbortsBeforeGeneration() {
        when(game.setSetting(anyString(), anyString())).thenReturn(
                ConfigService.SetOutcome.ok(null, null, null));
        when(game.lobbyWorldNameClashes()).thenReturn(true);

        setup.recommendedSetup(clicker);

        verify(messages).message(clicker, "manhunt.worldengine-tpto-lobby-world-clash");
        verify(game, never()).ensureLobbyWorld();
        verify(plugin, never()).markSetupDone();
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

        verify(messages).message(eq(clicker), eq("manhunt.worldengine-tpto-failed"), any());
        verify(plugin, never()).markSetupDone();
    }

    @Test
    void realMessagesResolveSetupDoneLine() throws Exception {
        MessageService real = new MessageService();
        real.reload(new MessagesConfig());

        assertEquals("{prefix}<green>Setup complete: world engine on, lobby ready, "
                + "<white>{count}</white> players teleported. "
                + "Restart the server to fully apply.",
                real.string("manhunt.setup-oneclick-done", "missing"));
    }
}
