package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

/** Spectator toolbar layout, targeting, lock breaks, and item identity. */
class SpectatorToolbarServiceTest {

    private SpectatorToolbarService toolbar(NamespacedKey key) {
        return new SpectatorToolbarService(mock(OverrideService.class),
                mock(MessageService.class), mock(SoundService.class), new PlayerStateStore(),
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
    void snowballParsesToMiddleSlot() {
        SpectatorToolbarService.ToolbarButton[] buttons =
                SpectatorToolbarService.parseLayout("cp##s###b");

        assertEquals(SpectatorToolbarService.ToolbarButton.SNOWBALL, buttons[4]);
        assertEquals(4, SpectatorToolbarService.snowballSlot(buttons));
        assertEquals(-1, SpectatorToolbarService.snowballSlot(
                SpectatorToolbarService.parseLayout("cp######b")));
    }

    @Test
    void snowballSettingsDefaultOnWithEightSeconds() {
        // Real item builds need a running server; the item-native
        // cooldown component itself is covered by manual QA on live.
        SnowballFixture fixture = snowballFixture(0, null, null);

        assertTrue(fixture.toolbar().snowballEnabled(fixture.player()));
        assertEquals(8, fixture.toolbar().snowballCooldownSeconds(fixture.player()));
    }

    @Test
    void snowballSettingsReadOverridesWithFloor() {
        SnowballFixture fixture = snowballFixture(2, false, -5);

        assertFalse(fixture.toolbar().snowballEnabled(fixture.player()));
        assertEquals(0, fixture.toolbar().snowballCooldownSeconds(fixture.player()));
    }

    private record SnowballFixture(SpectatorToolbarService toolbar, Player player) {
    }

    private SnowballFixture snowballFixture(int lobby, Boolean enabled, Integer seconds) {
        OverrideService overrides = mock(OverrideService.class);
        GameManager game = mock(GameManager.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(game.lobbyOfPlayer(id)).thenReturn(lobby);
        if (enabled == null || seconds == null) {
            when(overrides.getBoolean(eq(lobby), anyString(), anyBoolean()))
                    .thenAnswer(invocation -> invocation.getArgument(2));
            when(overrides.getInt(eq(lobby), anyString(), anyInt()))
                    .thenAnswer(invocation -> invocation.getArgument(2));
        } else {
            when(overrides.getBoolean(eq(lobby),
                    eq(SpectatorToolbarService.SNOWBALL_ENABLED_PATH), eq(true)))
                    .thenReturn(enabled);
            when(overrides.getInt(eq(lobby),
                    eq(SpectatorToolbarService.SNOWBALL_COOLDOWN_PATH), eq(8)))
                    .thenReturn(seconds);
        }
        SpectatorToolbarService toolbar = new SpectatorToolbarService(overrides,
                mock(MessageService.class), mock(SoundService.class), new PlayerStateStore(),
                mock(FakeSpectatorService.class), game,
                mock(LobbyService.class),
                new NamespacedKey("jmanhunt", "spectator_toolbar"));
        return new SnowballFixture(toolbar, player);
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

    @Test
    void sneakPairCompletesWithinWindow() {
        SpectatorToolbarService toolbar = toolbar(
                new NamespacedKey("jmanhunt", "spectator_toolbar"));
        UUID spectator = UUID.randomUUID();

        assertFalse(toolbar.registerSneak(spectator, 1000L));
        assertTrue(toolbar.registerSneak(spectator, 1500L));
    }

    @Test
    void sneakPairIsSingleUse() {
        SpectatorToolbarService toolbar = toolbar(
                new NamespacedKey("jmanhunt", "spectator_toolbar"));
        UUID spectator = UUID.randomUUID();
        toolbar.registerSneak(spectator, 1000L);
        assertTrue(toolbar.registerSneak(spectator, 1200L));

        assertFalse(toolbar.registerSneak(spectator, 1300L));
    }

    @Test
    void expiredSneakStartsNewPair() {
        SpectatorToolbarService toolbar = toolbar(
                new NamespacedKey("jmanhunt", "spectator_toolbar"));
        UUID spectator = UUID.randomUUID();
        toolbar.registerSneak(spectator, 1000L);

        assertFalse(toolbar.registerSneak(spectator, 1501L));
        assertTrue(toolbar.registerSneak(spectator, 1600L));
    }

    @Test
    void backwardsClockSneakStaysFirstTap() {
        SpectatorToolbarService toolbar = toolbar(
                new NamespacedKey("jmanhunt", "spectator_toolbar"));
        UUID spectator = UUID.randomUUID();
        toolbar.registerSneak(spectator, 1000L);

        assertFalse(toolbar.registerSneak(spectator, 900L));
    }

    @Test
    void sneakPairsArePerSpectator() {
        SpectatorToolbarService toolbar = toolbar(
                new NamespacedKey("jmanhunt", "spectator_toolbar"));
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        toolbar.registerSneak(first, 1000L);

        assertFalse(toolbar.registerSneak(second, 1100L));
        assertTrue(toolbar.registerSneak(first, 1200L));
    }

    @Test
    void modeChangeSkipsDeployForParticipants() {
        PlayerStateStore players = new PlayerStateStore();
        SpectatorToolbarService toolbar = new SpectatorToolbarService(
                mock(OverrideService.class), mock(MessageService.class),
                mock(SoundService.class), players, mock(FakeSpectatorService.class),
                mock(GameManager.class), mock(LobbyService.class),
                new NamespacedKey("jmanhunt", "spectator_toolbar"));
        Player hunter = mock(Player.class);
        when(hunter.getUniqueId()).thenReturn(UUID.randomUUID());
        players.setRole(hunter, Role.HUNTER);

        toolbar.onModeChange(hunter, true);

        assertFalse(toolbar.isDeployed(hunter));
    }

    @Test
    void modeDisableWithoutDeployIsNoop() {
        PlayerStateStore players = new PlayerStateStore();
        SpectatorToolbarService toolbar = new SpectatorToolbarService(
                mock(OverrideService.class), mock(MessageService.class),
                mock(SoundService.class), players, mock(FakeSpectatorService.class),
                mock(GameManager.class), mock(LobbyService.class),
                new NamespacedKey("jmanhunt", "spectator_toolbar"));
        Player hunter = mock(Player.class);
        when(hunter.getUniqueId()).thenReturn(UUID.randomUUID());
        players.setRole(hunter, Role.HUNTER);

        toolbar.onModeChange(hunter, false);

        assertFalse(toolbar.isDeployed(hunter));
    }
}
