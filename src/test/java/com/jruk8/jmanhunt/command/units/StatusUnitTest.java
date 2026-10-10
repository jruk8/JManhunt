package com.jruk8.jmanhunt.command.units;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.StatusRosterService;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

/** Bare status: assigned watchers see their match, queuers see the lobby. */
class StatusUnitTest {

    private record Fixture(StatusUnit status, GameManager game, LobbyService lobbies,
            ConfigService config, CommandSupport support, ManhuntMessages manhunt, Player sender,
            UUID senderId) {
    }

    private static Fixture fixture() {
        GameManager game = mock(GameManager.class);
        LobbyService lobbies = mock(LobbyService.class);
        ConfigService config = mock(ConfigService.class);
        when(config.getBoolean(any(), anyBoolean())).thenReturn(false);
        ManhuntMessages manhunt = new ManhuntMessages();
        CommandSupport support = mock(CommandSupport.class);
        StatusUnit status = new StatusUnit(
                new StatusUnit.StatusDeps(game, lobbies, config, mock(StatusRosterService.class),
                        () -> null),
                new StatusUnit.StatusTexts(manhunt, mock(CommandMessages.class), support));
        Player sender = mock(Player.class);
        UUID senderId = UUID.randomUUID();
        when(sender.getUniqueId()).thenReturn(senderId);
        when(game.instanceOf(senderId)).thenReturn(Optional.empty());
        when(game.assignedInstanceOf(senderId)).thenReturn(Optional.empty());
        return new Fixture(status, game, lobbies, config, support, manhunt, sender, senderId);
    }

    @Test
    void assignedSpectatorSeesMatch() {
        Fixture fixture = fixture();
        GameInstance instance = mock(GameInstance.class);
        when(fixture.game().assignedInstanceOf(fixture.senderId()))
                .thenReturn(Optional.of(instance));

        assertTrue(fixture.status().executeParsed(fixture.sender(),
                new StatusUnit.StatusArgs(Optional.empty())));

        verify(fixture.support()).message(fixture.sender(), fixture.manhunt().getStatusHeader(),
                Map.of("status", "ACTIVE"));
        verify(fixture.lobbies(), never()).lobbyOf(any());
    }

    @Test
    void queuedSpectatorSeesLobby() {
        Fixture fixture = fixture();
        Lobby lobby = mock(Lobby.class);
        when(lobby.id()).thenReturn(3);
        when(fixture.lobbies().lobbyOf(fixture.senderId())).thenReturn(Optional.of(lobby));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());

            assertTrue(fixture.status().executeParsed(fixture.sender(),
                    new StatusUnit.StatusArgs(Optional.empty())));
        }

        verify(fixture.support()).message(fixture.sender(), fixture.manhunt().getStatusHeader(),
                Map.of("status", "INACTIVE"));
    }

    @Test
    void elapsedLandsDirectlyAfterHeader() {
        Fixture fixture = fixture();
        when(fixture.config().getBoolean("settings.server.status.show-elapsed-time", true))
                .thenReturn(true);
        GameInstance instance = mock(GameInstance.class);
        when(fixture.game().assignedInstanceOf(fixture.senderId()))
                .thenReturn(Optional.of(instance));

        assertTrue(fixture.status().executeParsed(fixture.sender(),
                new StatusUnit.StatusArgs(Optional.empty())));

        InOrder order = inOrder(fixture.support());
        order.verify(fixture.support()).message(fixture.sender(),
                fixture.manhunt().getStatusHeader(), Map.of("status", "ACTIVE"));
        order.verify(fixture.support()).message(eq(fixture.sender()),
                eq(fixture.manhunt().getStatusElapsed()), any());
    }

    @Test
    void modifiersLineWrapsEntriesWhite() {
        Fixture fixture = fixture();
        when(fixture.config().getBoolean("settings.server.status.show-modifiers", true))
                .thenReturn(true);
        when(fixture.config().modifierNames()).thenReturn(Set.of("b", "a"));
        when(fixture.config().modifierEnabled(anyString())).thenReturn(true);
        ModifierStore store = mock(ModifierStore.class);
        when(fixture.config().modifiers()).thenReturn(store);
        GameInstance instance = mock(GameInstance.class);
        when(fixture.game().assignedInstanceOf(fixture.senderId()))
                .thenReturn(Optional.of(instance));

        assertTrue(fixture.status().executeParsed(fixture.sender(),
                new StatusUnit.StatusArgs(Optional.empty())));

        verify(fixture.support()).message(fixture.sender(),
                fixture.manhunt().getStatusModifiers(), Map.of("modifiers",
                        "<white>a</white> <gray>and</gray> <white>b</white>"));
    }
}
