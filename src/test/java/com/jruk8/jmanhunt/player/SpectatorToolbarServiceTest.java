package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
    void spectatorHeadNeedsTagAndOwner() {
        NamespacedKey key = new NamespacedKey("jmanhunt", "spectator_toolbar");
        SpectatorToolbarService toolbar = toolbar(key);
        UUID owner = UUID.randomUUID();

        assertFalse(toolbar.isSpectatorHead(null, owner));
        ItemStack stone = mock(ItemStack.class);
        when(stone.getType()).thenReturn(Material.STONE);
        assertFalse(toolbar.isSpectatorHead(stone, owner));
        assertFalse(toolbar.isSpectatorHead(headStack(key, owner, "c"), owner));
        assertFalse(toolbar.isSpectatorHead(headStack(key, owner, null), owner));
        assertFalse(toolbar.isSpectatorHead(headStack(key, owner, "h"), UUID.randomUUID()));
        assertFalse(toolbar.isSpectatorHead(headStack(key, owner, "h"), null));
        assertTrue(toolbar.isSpectatorHead(headStack(key, owner, "h"), owner));
    }

    private static ItemStack headStack(NamespacedKey key, UUID owner, String tag) {
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(container.get(key, PersistentDataType.STRING)).thenReturn(tag);
        SkullMeta meta = mock(SkullMeta.class);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.getUniqueId()).thenReturn(owner);
        when(meta.getOwningPlayer()).thenReturn(offline);
        ItemStack head = mock(ItemStack.class);
        when(head.getType()).thenReturn(Material.PLAYER_HEAD);
        when(head.hasItemMeta()).thenReturn(true);
        when(head.getItemMeta()).thenReturn(meta);
        return head;
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
        SpectatorToolbarService toolbar = spy(new SpectatorToolbarService(
                mock(OverrideService.class), mock(MessageService.class),
                mock(SoundService.class), players, mock(FakeSpectatorService.class),
                mock(GameManager.class), mock(LobbyService.class),
                new NamespacedKey("jmanhunt", "spectator_toolbar")));
        ItemStack head = mock(ItemStack.class);
        doReturn(head).when(toolbar).buildSpectatorHead(any());
        Player hunter = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(hunter.getInventory()).thenReturn(inventory);
        when(hunter.getUniqueId()).thenReturn(UUID.randomUUID());
        players.setRole(hunter, Role.HUNTER);

        toolbar.onModeChange(hunter, true);

        assertFalse(toolbar.isDeployed(hunter));
        verify(inventory).setHelmet(head);
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
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(hunter.getInventory()).thenReturn(inventory);
        when(hunter.getUniqueId()).thenReturn(UUID.randomUUID());
        players.setRole(hunter, Role.HUNTER);

        toolbar.onModeChange(hunter, false);

        assertFalse(toolbar.isDeployed(hunter));
        verify(inventory, never()).setHelmet(any());
    }

    private record SwapFixture(SpectatorToolbarService toolbar, GameManager game,
            LobbyService lobbies, MessageService messages, SoundService sounds, Player spectator) {
    }

    private static SwapFixture swapFixture() {
        GameManager game = mock(GameManager.class);
        LobbyService lobbies = mock(LobbyService.class);
        MessageService messages = mock(MessageService.class);
        SoundService sounds = mock(SoundService.class);
        SpectatorToolbarService toolbar = new SpectatorToolbarService(
                mock(OverrideService.class), messages, sounds, new PlayerStateStore(),
                mock(FakeSpectatorService.class), game, lobbies,
                new NamespacedKey("jmanhunt", "spectator_toolbar"));
        Player spectator = mock(Player.class);
        when(spectator.getUniqueId()).thenReturn(UUID.randomUUID());
        return new SwapFixture(toolbar, game, lobbies, messages, sounds, spectator);
    }

    private static GameInstance liveMatch(long matchId) {
        GameInstance instance = mock(GameInstance.class);
        when(instance.active()).thenReturn(true);
        when(instance.ending()).thenReturn(false);
        when(instance.matchId()).thenReturn(matchId);
        return instance;
    }

    @Test
    void swapToSameMatchMessagesInsteadOfMoving() {
        SwapFixture fixture = swapFixture();
        GameInstance current = liveMatch(7L);
        when(fixture.game().instance(7L)).thenReturn(Optional.of(current));
        when(fixture.game().instanceOf(fixture.spectator().getUniqueId()))
                .thenReturn(Optional.of(current));

        assertTrue(fixture.toolbar().swapSpectator(fixture.spectator(), 7L));

        verify(fixture.messages()).message(fixture.spectator(), "spectator.already-in-match");
        verify(fixture.sounds()).playNeutralSound(fixture.spectator());
        verify(fixture.spectator()).closeInventory();
        verify(fixture.game(), never()).leaveMatch(any(), any(), anyBoolean());
        verify(fixture.game(), never()).joinPlayers(any(), any(), any());
    }

    @Test
    void swapSuccessPlaysNeutral() {
        SwapFixture fixture = swapFixture();
        GameInstance target = liveMatch(7L);
        when(fixture.game().instance(7L)).thenReturn(Optional.of(target));
        when(fixture.game().instanceOf(fixture.spectator().getUniqueId()))
                .thenReturn(Optional.empty());
        when(fixture.lobbies().lobbyOf(fixture.spectator().getUniqueId()))
                .thenReturn(Optional.empty());
        when(fixture.game().joinPlayers(eq(target), any(), eq(Role.SPECTATOR))).thenReturn(1);
        SpectatorToolbarService toolbar = spy(fixture.toolbar());
        doNothing().when(toolbar).teleportToPriority(any(), any());

        assertTrue(toolbar.swapSpectator(fixture.spectator(), 7L));

        verify(fixture.sounds()).playNeutralSound(fixture.spectator());
        verify(fixture.spectator()).closeInventory();
    }

    @Test
    void swapToGoneMatchPlaysAngry() {
        SwapFixture fixture = swapFixture();
        when(fixture.game().instance(7L)).thenReturn(Optional.empty());

        assertFalse(fixture.toolbar().swapSpectator(fixture.spectator(), 7L));

        verify(fixture.messages()).message(fixture.spectator(), "spectator.match-gone");
        verify(fixture.sounds()).playAngrySound(fixture.spectator());
        verify(fixture.spectator()).closeInventory();
    }

    @Test
    void teleportRelockMessagesInsteadOfTeleporting() {
        OverrideService overrides = mock(OverrideService.class);
        when(overrides.getBoolean(any(), anyString(), anyBoolean())).thenReturn(true);
        MessageService messages = mock(MessageService.class);
        when(messages.roleName(any())).thenReturn("Hunter");
        SoundService sounds = mock(SoundService.class);
        PlayerStateStore players = new PlayerStateStore();
        GameManager game = mock(GameManager.class);
        when(game.instanceOf(any())).thenReturn(Optional.empty());
        SpectatorToolbarService toolbar = new SpectatorToolbarService(overrides, messages,
                sounds, players, mock(FakeSpectatorService.class), game,
                mock(LobbyService.class),
                new NamespacedKey("jmanhunt", "spectator_toolbar"));
        Player spectator = mock(Player.class);
        when(spectator.getUniqueId()).thenReturn(UUID.randomUUID());
        Player target = mock(Player.class);
        UUID targetId = UUID.randomUUID();
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Alex");
        when(target.isOnline()).thenReturn(true);
        when(target.getLocation()).thenReturn(new Location(mock(World.class), 1.0, 2.0, 3.0));
        players.setRole(target, Role.HUNTER);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(target);

            assertTrue(toolbar.teleportAndLock(spectator, targetId));
            assertTrue(toolbar.teleportAndLock(spectator, targetId));
        }

        verify(spectator, times(1)).teleport(any(Location.class));
        verify(messages, times(1)).message(eq(spectator), eq("spectator.now-spectating"),
                any());
        verify(messages).message(spectator, "spectator.already-spectating",
                Map.of("player", "Alex"));
        verify(sounds, times(2)).playNeutralSound(spectator);
        verify(spectator, times(2)).closeInventory();
    }

    @Test
    void teleportToMissingTargetPlaysAngry() {
        SwapFixture fixture = swapFixture();
        UUID targetId = UUID.randomUUID();
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(targetId)).thenReturn(null);

            assertFalse(fixture.toolbar().teleportAndLock(fixture.spectator(), targetId));
        }

        verify(fixture.sounds()).playAngrySound(fixture.spectator());
        verify(fixture.spectator()).closeInventory();
    }
}
