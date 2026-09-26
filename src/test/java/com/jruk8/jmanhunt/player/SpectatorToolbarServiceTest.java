package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/** Spectator toolbar layout, targeting, lock breaks, and item identity. */
class SpectatorToolbarServiceTest {

    private SpectatorToolbarService toolbar(NamespacedKey key) {
        return new SpectatorToolbarService(mock(OverrideService.class),
                mock(MessageService.class), new PlayerStateStore(),
                mock(FakeSpectatorService.class), mock(GameManager.class),
                mock(LobbyService.class), key);
    }

    @Test
    void defaultLayoutParses() {
        SpectatorToolbarService.ToolbarButton[] buttons =
                SpectatorToolbarService.parseLayout("cp######b");

        assertEquals(SpectatorToolbarService.ToolbarButton.LOBBIES, buttons[0]);
        assertEquals(SpectatorToolbarService.ToolbarButton.PLAYERS, buttons[1]);
        assertEquals(SpectatorToolbarService.ToolbarButton.BACK, buttons[8]);
        for (int slot = 2; slot < 8; slot++) {
            assertEquals(SpectatorToolbarService.ToolbarButton.EMPTY, buttons[slot]);
        }
    }

    @Test
    void customLayoutParses() {
        SpectatorToolbarService.ToolbarButton[] buttons =
                SpectatorToolbarService.parseLayout("b##p##c##");

        assertEquals(SpectatorToolbarService.ToolbarButton.BACK, buttons[0]);
        assertEquals(SpectatorToolbarService.ToolbarButton.PLAYERS, buttons[3]);
        assertEquals(SpectatorToolbarService.ToolbarButton.LOBBIES, buttons[6]);
    }

    @Test
    void unknownCharsAreEmpty() {
        SpectatorToolbarService.ToolbarButton[] buttons =
                SpectatorToolbarService.parseLayout("CxPxxxxxB");

        for (SpectatorToolbarService.ToolbarButton button : buttons) {
            assertEquals(SpectatorToolbarService.ToolbarButton.EMPTY, button);
        }
    }

    @Test
    void invalidLayoutFallsBackToDefault() {
        assertEquals(SpectatorToolbarService.ToolbarButton.LOBBIES,
                SpectatorToolbarService.parseLayout("short")[0]);
        assertEquals(SpectatorToolbarService.ToolbarButton.LOBBIES,
                SpectatorToolbarService.parseLayout(null)[0]);
        assertEquals(SpectatorToolbarService.ToolbarButton.PLAYERS,
                SpectatorToolbarService.parseLayout("way-too-long")[1]);
    }

    private SpectatorToolbarService.SpectateCandidate candidate(UUID id, Role role) {
        return new SpectatorToolbarService.SpectateCandidate(id, role, true, true, false, true);
    }

    @Test
    void targetValidMatrix() {
        UUID id = UUID.randomUUID();
        assertTrue(SpectatorToolbarService.targetValid(candidate(id, Role.SPEEDRUNNER)));
        assertTrue(SpectatorToolbarService.targetValid(candidate(id, Role.HUNTER)));
        assertFalse(SpectatorToolbarService.targetValid(null));
        assertFalse(SpectatorToolbarService.targetValid(candidate(id, Role.SPECTATOR)));
        assertFalse(SpectatorToolbarService.targetValid(candidate(id, Role.NONE)));
        assertFalse(SpectatorToolbarService.targetValid(
                new SpectatorToolbarService.SpectateCandidate(
                        id, Role.SPEEDRUNNER, true, true, true, true)));
        assertFalse(SpectatorToolbarService.targetValid(
                new SpectatorToolbarService.SpectateCandidate(
                        id, Role.SPEEDRUNNER, true, true, false, false)));
        assertFalse(SpectatorToolbarService.targetValid(
                new SpectatorToolbarService.SpectateCandidate(
                        id, Role.SPEEDRUNNER, false, true, false, true)));
        assertFalse(SpectatorToolbarService.targetValid(
                new SpectatorToolbarService.SpectateCandidate(
                        id, Role.SPEEDRUNNER, true, false, false, true)));
        assertTrue(SpectatorToolbarService.targetValid(
                new SpectatorToolbarService.SpectateCandidate(
                        id, Role.HUNTER, true, false, false, true)));
    }

    @Test
    void pickPrefersSpeedrunnersThenHunters() {
        UUID hunter = UUID.randomUUID();
        UUID runner = UUID.randomUUID();
        List<SpectatorToolbarService.SpectateCandidate> candidates = List.of(
                candidate(hunter, Role.HUNTER), candidate(runner, Role.SPEEDRUNNER));

        assertEquals(Optional.of(runner),
                SpectatorToolbarService.pickTeleportTarget(candidates));
        assertEquals(Optional.of(hunter), SpectatorToolbarService.pickTeleportTarget(
                List.of(candidate(hunter, Role.HUNTER))));
        assertEquals(Optional.empty(), SpectatorToolbarService.pickTeleportTarget(List.of(
                candidate(UUID.randomUUID(), Role.SPECTATOR))));
    }

    @Test
    void pickSkipsInvalidCandidates() {
        UUID id = UUID.randomUUID();
        List<SpectatorToolbarService.SpectateCandidate> candidates = List.of(
                new SpectatorToolbarService.SpectateCandidate(
                        UUID.randomUUID(), Role.SPEEDRUNNER, true, true, true, true),
                candidate(id, Role.HUNTER));

        assertEquals(Optional.of(id),
                SpectatorToolbarService.pickTeleportTarget(candidates));
    }

    @Test
    void changedBlockDetectsBlockMovesOnly() {
        World world = mock(World.class);
        Location from = new Location(world, 1.2, 64.0, 3.9);

        assertFalse(SpectatorToolbarService.changedBlock(from,
                new Location(world, 1.8, 64.9, 3.1)));
        assertTrue(SpectatorToolbarService.changedBlock(from,
                new Location(world, 2.0, 64.0, 3.9)));
        assertTrue(SpectatorToolbarService.changedBlock(from,
                new Location(world, 1.2, 65.0, 3.9)));
        assertTrue(SpectatorToolbarService.changedBlock(from,
                new Location(mock(World.class), 1.2, 64.0, 3.9)));
        assertFalse(SpectatorToolbarService.changedBlock(from, null));
        assertFalse(SpectatorToolbarService.changedBlock(null, from));
    }

    @Test
    void toolbarButtonReadsMarker() {
        NamespacedKey key = new NamespacedKey("jmanhunt", "spectator_toolbar");
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(container.get(key, PersistentDataType.STRING)).thenReturn("p");
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(item.getItemMeta()).thenReturn(meta);

        assertEquals(Optional.of('p'), toolbar(key).toolbarButton(item));
        assertTrue(toolbar(key).isToolbarItem(item));
    }

    @Test
    void toolbarButtonIgnoresUnmarked() {
        NamespacedKey key = new NamespacedKey("jmanhunt", "spectator_toolbar");

        assertEquals(Optional.empty(), toolbar(key).toolbarButton(null));
        ItemStack plain = mock(ItemStack.class);
        when(plain.hasItemMeta()).thenReturn(false);
        assertEquals(Optional.empty(), toolbar(key).toolbarButton(plain));
        assertFalse(toolbar(key).isToolbarItem(plain));
    }

    @Test
    void locksDefaultToNoneAndClearIsSafe() {
        SpectatorToolbarService toolbar = toolbar(
                new NamespacedKey("jmanhunt", "spectator_toolbar"));
        UUID spectator = UUID.randomUUID();

        assertEquals(null, toolbar.lockedTarget(spectator));
        toolbar.clearLock(spectator);

        assertEquals(null, toolbar.lockedTarget(spectator));
    }
}
