package com.jruk8.jmanhunt.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.ScalingLayout;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.SpectatorToolbarService;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Spectator browsers: shared list shape, scaling players, empty states. */
class SpectatorMenusTest {

    private record Fixture(SpectatorMenus menus, GuiService gui, MessageService messages,
            SpectatorToolbarService toolbar, Player spectator) {
    }

    private static Fixture fixture() {
        MessageService messages = mock(MessageService.class);
        when(messages.string(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(messages.parse(anyString()))
                .thenAnswer(invocation ->
                        Component.text(invocation.getArgument(0, String.class)));
        when(messages.nonItalic(any(Component.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(messages.roleName(any(Role.class))).thenReturn("Hunter");
        SpectatorToolbarService toolbar = mock(SpectatorToolbarService.class);
        GuiService gui = mock(GuiService.class);
        Player spectator = mock(Player.class);
        when(spectator.getUniqueId()).thenReturn(UUID.randomUUID());
        return new Fixture(new SpectatorMenus(messages, gui, toolbar), gui, messages,
                toolbar, spectator);
    }

    @Test
    void lobbiesMenuUsesSharedListShape() {
        Fixture fixture = fixture();
        when(fixture.toolbar().matchEntries(fixture.spectator())).thenReturn(List.of(
                new SpectatorToolbarService.MatchEntry(7L, "Lobby 1", 2, 3, false, false)));

        Menu menu = fixture.menus().lobbiesMenu(fixture.spectator());

        assertEquals(45, menu.layout().size());
        assertEquals(Material.ARROW, menu.buttonAt(9).material());
        assertEquals(Material.PAPER, menu.buttonAt(18).material());
        assertEquals(Material.ARROW, menu.buttonAt(27).material());
        assertNull(menu.buttonAt(8));
        assertNull(menu.buttonAt(26));
        assertNull(menu.buttonAt(44));
        assertEquals(Material.ENDER_EYE, menu.buttonAt(2).material());
        assertNotNull(menu.buttonAt(2).action());
        assertNull(menu.parent());
    }

    @Test
    void playersMenuScalesWithBackButton() {
        Fixture fixture = fixture();
        when(fixture.toolbar().playerEntries(fixture.spectator())).thenReturn(List.of(
                new SpectatorToolbarService.PlayerEntry(UUID.randomUUID(), "a",
                        Role.HUNTER, false),
                new SpectatorToolbarService.PlayerEntry(UUID.randomUUID(), "b",
                        Role.SPEEDRUNNER, true)));

        Menu menu = fixture.menus().playersMenu(fixture.spectator());

        assertEquals(9, menu.layout().size());
        assertEquals(Material.PLAYER_HEAD, menu.buttonAt(0).material());
        assertEquals(Material.PLAYER_HEAD, menu.buttonAt(1).material());
        assertEquals(Material.PAPER,
                menu.buttonAt(ScalingLayout.backSlot(menu.layout().rowCount())).material());
        assertNull(menu.parent());
    }

    @Test
    void emptyLobbiesMessageAndSkipOpen() {
        Fixture fixture = fixture();
        when(fixture.toolbar().matchEntries(fixture.spectator())).thenReturn(List.of());

        fixture.menus().openLobbiesMenu(fixture.spectator());

        verify(fixture.messages()).message(fixture.spectator(), "spectator.no-matches");
        verify(fixture.gui(), never()).open(any(), any());
    }

    @Test
    void emptyPlayersMessageAndSkipOpen() {
        Fixture fixture = fixture();
        when(fixture.toolbar().playerEntries(fixture.spectator())).thenReturn(List.of());

        fixture.menus().openPlayersMenu(fixture.spectator());

        verify(fixture.messages()).message(fixture.spectator(), "spectator.no-players");
        verify(fixture.gui(), never()).open(any(), any());
    }
}
