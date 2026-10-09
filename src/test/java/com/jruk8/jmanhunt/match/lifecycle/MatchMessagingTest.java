package com.jruk8.jmanhunt.match.lifecycle;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.config.ServerSettings;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Instance fan-out skips excluded ids but always reaches the console. */
class MatchMessagingTest {

    @Test
    void exceptSkipsExcludedKeepsRestAndConsole() {
        MessageService messages = mock(MessageService.class);
        Component rendered = mock(Component.class);
        when(messages.componentRaw(any(), any())).thenReturn(rendered);
        MatchStore store = mock(MatchStore.class);
        MatchMessaging messaging = new MatchMessaging(
                new MatchMessaging.MessagingTexts(messages, mock(ManhuntMessages.class),
                        mock(SoundService.class)),
                mock(ServerSettings.class), store, mock(LobbyService.class));
        GameInstance instance = mock(GameInstance.class);
        Player excluded = mock(Player.class);
        UUID excludedId = UUID.randomUUID();
        when(excluded.getUniqueId()).thenReturn(excludedId);
        Player kept = mock(Player.class);
        when(kept.getUniqueId()).thenReturn(UUID.randomUUID());
        when(store.onlineMatchAudience(instance)).thenReturn(List.of(excluded, kept));
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getConsoleSender).thenReturn(console);

            messaging.sendToInstanceExcept(instance, "tpl", Map.of("a", "b"), Set.of(excludedId));
        }

        verify(excluded, never()).sendMessage(rendered);
        verify(kept).sendMessage(rendered);
        verify(console).sendMessage(rendered);
    }

    @Test
    void blankTemplateSendsNothing() {
        MessageService messages = mock(MessageService.class);
        when(messages.blank("")).thenReturn(true);
        MatchStore store = mock(MatchStore.class);
        MatchMessaging messaging = new MatchMessaging(
                new MatchMessaging.MessagingTexts(messages, mock(ManhuntMessages.class),
                        mock(SoundService.class)),
                mock(ServerSettings.class), store, mock(LobbyService.class));

        messaging.sendToInstanceExcept(mock(GameInstance.class), "", Map.of(), Set.of());

        verify(store, never()).onlineMatchAudience(any());
    }
}
