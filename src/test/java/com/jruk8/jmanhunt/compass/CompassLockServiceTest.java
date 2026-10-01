package com.jruk8.jmanhunt.compass;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.lobby.config.CompassSettingsFacade;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompassLockServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void rapidClicksRenderOncePerScrollCooldown() {
        Fixture fixture = fixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)));

        fixture.locks().handleLeftClick(fixture.player());
        fixture.locks().handleLeftClick(fixture.player());

        verify(fixture.renderer(), times(1)).accept(fixture.player());
        verify(fixture.refresher(), never()).accept(any(Player.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void singleTargetClickQuitsSilently() {
        Fixture fixture = fixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0)));

        fixture.locks().handleLeftClick(fixture.player());

        verify(fixture.renderer(), never()).accept(any(Player.class));
        verify(fixture.refresher(), never()).accept(any(Player.class));
        verify(fixture.sounds(), never()).playSound(any(Player.class), any(String.class));
        assertTrue(fixture.sharedClicks().isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void failedScrollStillThrottles() {
        Fixture fixture = fixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0)));

        fixture.locks().handleLeftClick(fixture.player());
        when(fixture.targets().collectIdentities(any(), any(), any())).thenReturn(List.of(
                new CompassIdentity(UUID.randomUUID(), "a"),
                new CompassIdentity(UUID.randomUUID(), "b")));
        fixture.locks().handleLeftClick(fixture.player());

        verify(fixture.renderer(), never()).accept(any(Player.class));
    }

    @Test
    void failedShiftToggleStillThrottles() {
        Fixture fixture = teammateFixture(List.of(), true, Role.HUNTER, 10.0);

        fixture.locks().handleShiftLeft(fixture.player());
        verify(fixture.messages(), times(1)).messageRaw(fixture.player(),
                fixture.texts().getCompass().getNoTeammates());
        when(fixture.targets().collectIdentities(any(), any(), any())).thenReturn(List.of(
                new CompassIdentity(UUID.randomUUID(), "a")));
        fixture.locks().handleShiftLeft(fixture.player());

        verify(fixture.messages(), never()).messageRaw(fixture.player(),
                fixture.texts().getCompass().getTeammateOnChat());
        verify(fixture.renderer(), never()).accept(any(Player.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void leftClickIgnoresSharedClickCooldown() {
        Fixture fixture = fixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)));
        fixture.sharedClicks().put(fixture.player().getUniqueId(), System.currentTimeMillis());

        fixture.locks().handleLeftClick(fixture.player());

        verify(fixture.renderer(), times(1)).accept(fixture.player());
    }

    @Test
    @SuppressWarnings("unchecked")
    void leftClickNeverStampsSharedClicks() {
        Fixture fixture = fixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)));

        fixture.locks().handleLeftClick(fixture.player());

        assertTrue(fixture.sharedClicks().isEmpty());
    }

    @Test
    void successfulScrollPlaysNoFailure() {
        Fixture fixture = fixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)));

        fixture.locks().handleLeftClick(fixture.player());

        verify(fixture.sounds(), never()).playSound(fixture.player(), "compass.failure");
        verify(fixture.renderer(), times(1)).accept(fixture.player());
    }

    @Test
    void vanillaSpectatorClicksDoNothing() {
        Fixture fixture = fixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)));
        when(fixture.player().getGameMode()).thenReturn(GameMode.SPECTATOR);

        fixture.locks().handleLeftClick(fixture.player());
        fixture.locks().handleShiftLeft(fixture.player());

        verify(fixture.renderer(), never()).accept(any(Player.class));
        verify(fixture.refresher(), never()).accept(any(Player.class));
        verify(fixture.sounds(), never()).playSound(any(Player.class), any(String.class));
    }

    @Test
    void jitteredDelaySamplesWithinDeviation() {
        assertEquals(3.0, AnalysisTiming.jitteredDelay(3.0, 0.0, 0.99));
        assertEquals(2.0, AnalysisTiming.jitteredDelay(3.0, 1.0, 0.0));
        assertEquals(4.0, AnalysisTiming.jitteredDelay(3.0, 1.0, 1.0), 0.000001);
        assertEquals(0.0, AnalysisTiming.jitteredDelay(3.0, 9.0, 0.0));
        assertEquals(0.0, AnalysisTiming.jitteredDelay(-2.0, 1.0, 0.5));
    }

    @Test
    void clampedSoundIntervalHonorsRegistryBounds() {
        assertEquals(0.5, CompassLockService.clampedSoundInterval(0.5));
        assertEquals(0.05, CompassLockService.clampedSoundInterval(0.01));
        assertEquals(3.0, CompassLockService.clampedSoundInterval(60.0));
    }

    @Test
    void uncachedLockSurvivesCachedNarrow() {
        UUID locked = UUID.randomUUID();
        Fixture fixture = fixture(List.of(
                new CompassCandidate(locked, "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)));
        fixture.locks().handleLeftClick(fixture.player());

        CompassLockService.LockedTargets narrowed = fixture.locks().narrowToLockCached(
                fixture.player().getUniqueId(), List.of(), List.of(), Set.of(locked));

        assertTrue(narrowed.locked());
        assertTrue(narrowed.opponents().isEmpty());
    }

    @Test
    void staleLockClearsCachedNarrow() {
        Fixture fixture = fixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)));
        fixture.locks().handleLeftClick(fixture.player());

        CompassLockService.LockedTargets narrowed = fixture.locks().narrowToLockCached(
                fixture.player().getUniqueId(), List.of(), List.of(), Set.of());

        assertFalse(narrowed.locked());
    }

    @Test
    void lockCycleChatsLockedWhenEnabled() {
        UUID locked = UUID.randomUUID();
        Fixture fixture = fixture(List.of(
                new CompassCandidate(locked, "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)));

        fixture.locks().handleLeftClick(fixture.player());

        verify(fixture.messages(), times(1)).messageRaw(fixture.player(), fixture.texts().getCompass().getLockedChat(),
                Map.of("player", "a"));
    }

    @Test
    void cycleWrapToAutomaticChatsNothing() {
        Fixture fixture = teammateFixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)), true, Role.HUNTER);

        fixture.locks().handleLeftClick(fixture.player());
        fixture.locks().handleLeftClick(fixture.player());
        fixture.locks().handleLeftClick(fixture.player());

        verify(fixture.renderer(), times(3)).accept(fixture.player());
        verify(fixture.messages(), times(2)).messageRaw(eq(fixture.player()),
                eq(fixture.texts().getCompass().getLockedChat()), any());
    }

    @Test
    void teammateToggleChatsOnAndOff() {
        Fixture fixture = teammateFixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0)), true, Role.HUNTER);

        fixture.locks().handleShiftLeft(fixture.player());
        fixture.locks().handleShiftLeft(fixture.player());

        verify(fixture.messages(), times(1)).messageRaw(fixture.player(),
                fixture.texts().getCompass().getTeammateOnChat());
        verify(fixture.messages(), times(1)).messageRaw(fixture.player(),
                fixture.texts().getCompass().getTeammateOffChat());
    }

    @Test
    void compassChatDisabledStaysSilent() {
        List<CompassCandidate> opponents = List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0));
        Fixture fixture = teammateFixture(opponents, true, Role.HUNTER, 0.0, false);

        fixture.locks().handleLeftClick(fixture.player());
        fixture.locks().handleShiftLeft(fixture.player());

        verify(fixture.messages(), never()).messageRaw(eq(fixture.player()),
                eq(fixture.texts().getCompass().getLockedChat()), any());
        verify(fixture.messages(), never())
                .messageRaw(fixture.player(), fixture.texts().getCompass().getTeammateOnChat());
        verify(fixture.messages(), never())
                .messageRaw(fixture.player(), fixture.texts().getCompass().getTeammateOffChat());
    }

    @Test
    void lockedTargetDeathClearsAndChatsOnce() {
        UUID locked = UUID.randomUUID();
        Fixture fixture = fixture(List.of(
                new CompassCandidate(locked, "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)));
        fixture.locks().handleLeftClick(fixture.player());

        fixture.locks().clearLocksOnTargetDeath(locked, id -> fixture.player());
        fixture.locks().clearLocksOnTargetDeath(locked, id -> fixture.player());

        verify(fixture.messages(), times(1))
                .messageRaw(fixture.player(), fixture.texts().getCompass().getLockedTargetDiedChat());
        assertFalse(fixture.locks().narrowToLock(fixture.player().getUniqueId(),
                List.of(new CompassCandidate(locked, "a", 10.0, 10.0)), List.of()).locked());
    }

    @Test
    void lockedTargetDeathOfflineHolderSkipsChat() {
        UUID locked = UUID.randomUUID();
        Fixture fixture = fixture(List.of(
                new CompassCandidate(locked, "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)));
        fixture.locks().handleLeftClick(fixture.player());

        fixture.locks().clearLocksOnTargetDeath(locked, id -> null);

        verify(fixture.messages(), never()).messageRaw(any(Player.class),
                eq(fixture.texts().getCompass().getLockedTargetDiedChat()));
        assertFalse(fixture.locks().narrowToLock(fixture.player().getUniqueId(),
                List.of(new CompassCandidate(locked, "a", 10.0, 10.0)), List.of()).locked());
    }

    @Test
    void shiftLeftTogglesBetweenEnemiesAndTeammates() {
        Fixture fixture = teammateFixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0)), true, Role.HUNTER);

        assertEquals(Role.SPEEDRUNNER, fixture.locks().targetRole(fixture.player()));

        fixture.locks().handleShiftLeft(fixture.player());

        assertTrue(fixture.locks().teammateMode(fixture.player().getUniqueId()));
        assertEquals(Role.HUNTER, fixture.locks().targetRole(fixture.player()));

        fixture.locks().handleShiftLeft(fixture.player());

        assertFalse(fixture.locks().teammateMode(fixture.player().getUniqueId()));
        assertEquals(Role.SPEEDRUNNER, fixture.locks().targetRole(fixture.player()));
        verify(fixture.renderer(), times(2)).accept(fixture.player());
        verify(fixture.refresher(), never()).accept(any(Player.class));
    }

    @Test
    void shiftLeftTracksOwnRoleForSpeedrunners() {
        Fixture fixture = teammateFixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0)), true,
                Role.SPEEDRUNNER);

        fixture.locks().handleShiftLeft(fixture.player());

        assertEquals(Role.SPEEDRUNNER, fixture.locks().targetRole(fixture.player()));
    }

    @Test
    void switchCooldownThrottlesSecondToggleSilently() {
        Fixture fixture = teammateFixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0)), true,
                Role.HUNTER, 10.0);

        fixture.locks().handleShiftLeft(fixture.player());
        fixture.locks().handleShiftLeft(fixture.player());

        assertTrue(fixture.locks().teammateMode(fixture.player().getUniqueId()));
        verify(fixture.renderer(), times(1)).accept(fixture.player());
        verify(fixture.sounds(), times(1)).playSound(fixture.player(), "compass.left-click");
        verify(fixture.sounds(), never()).playSound(fixture.player(), "compass.failure");
        assertTrue(fixture.sharedClicks().isEmpty());
    }

    @Test
    void shiftLeftWithDisabledSettingLocksLikeLeftClick() {
        List<CompassCandidate> opponents = List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0));
        Fixture fixture = teammateFixture(opponents, false, Role.HUNTER);

        fixture.locks().handleShiftLeft(fixture.player());

        assertFalse(fixture.locks().teammateMode(fixture.player().getUniqueId()));
        assertEquals(Role.SPEEDRUNNER, fixture.locks().targetRole(fixture.player()));
        assertTrue(fixture.locks().narrowToLock(fixture.player().getUniqueId(),
                opponents, List.of()).locked());
        verify(fixture.renderer(), times(1)).accept(fixture.player());
    }

    @Test
    void shiftLeftDropsManualLock() {
        List<CompassCandidate> opponents = List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0));
        Fixture fixture = teammateFixture(opponents, true, Role.HUNTER);
        fixture.locks().handleLeftClick(fixture.player());
        assertTrue(fixture.locks().narrowToLock(fixture.player().getUniqueId(),
                opponents, List.of()).locked());

        fixture.locks().handleShiftLeft(fixture.player());

        assertFalse(fixture.locks().narrowToLock(fixture.player().getUniqueId(),
                opponents, List.of()).locked());
    }

    @Test
    void shiftLeftOutsideMatchClearsAndSkips() {
        Fixture fixture = teammateFixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0)), true, Role.HUNTER);
        fixture.locks().handleShiftLeft(fixture.player());
        assertTrue(fixture.locks().teammateMode(fixture.player().getUniqueId()));
        when(fixture.game().instanceOf(fixture.player().getUniqueId()))
                .thenReturn(Optional.empty());

        fixture.locks().handleShiftLeft(fixture.player());

        assertFalse(fixture.locks().teammateMode(fixture.player().getUniqueId()));
        verify(fixture.renderer(), times(1)).accept(fixture.player());
    }

    @Test
    void spectatorShiftLeftRefusesToggle() {
        Fixture fixture = teammateFixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0)), true, Role.HUNTER);
        when(fixture.fakes().isFakeSpectator(fixture.player())).thenReturn(true);

        fixture.locks().handleShiftLeft(fixture.player());

        assertFalse(fixture.locks().teammateMode(fixture.player().getUniqueId()));
        verify(fixture.renderer(), never()).accept(any(Player.class));
    }

    @Test
    void shiftLeftRefusesWithoutTeammates() {
        Fixture fixture = teammateFixture(List.of(), true, Role.HUNTER);

        fixture.locks().handleShiftLeft(fixture.player());

        assertFalse(fixture.locks().teammateMode(fixture.player().getUniqueId()));
        assertEquals(Role.SPEEDRUNNER, fixture.locks().targetRole(fixture.player()));
        verify(fixture.messages(), times(1)).messageRaw(fixture.player(),
                fixture.texts().getCompass().getNoTeammates());
        verify(fixture.sounds(), times(1)).playAngrySound(fixture.player());
        verify(fixture.renderer(), never()).accept(any(Player.class));
    }

    @Test
    void shiftLeftLeavesTeammateModeWithoutTeammates() {
        Fixture fixture = teammateFixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0)), true, Role.HUNTER);
        fixture.locks().handleShiftLeft(fixture.player());
        assertTrue(fixture.locks().teammateMode(fixture.player().getUniqueId()));
        when(fixture.targets().collectIdentities(any(), any(), any())).thenReturn(List.of());

        fixture.locks().handleShiftLeft(fixture.player());

        assertFalse(fixture.locks().teammateMode(fixture.player().getUniqueId()));
        assertEquals(Role.SPEEDRUNNER, fixture.locks().targetRole(fixture.player()));
        verify(fixture.renderer(), times(2)).accept(fixture.player());
    }

    @Test
    void clearMatchStateDropsToggle() {
        Fixture fixture = teammateFixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0)), true, Role.HUNTER);
        fixture.locks().handleShiftLeft(fixture.player());

        fixture.locks().clearMatchState(fixture.player().getUniqueId());

        assertFalse(fixture.locks().teammateMode(fixture.player().getUniqueId()));
        assertEquals(Role.SPEEDRUNNER, fixture.locks().targetRole(fixture.player()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buildCycleFreezesDistancesAtRefreshTimeHolderSpot() {
        UUID holderId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        Location refreshSpot = new Location(world, 0.0, 64.0, 0.0);
        Location targetSpot = new Location(world, 3.0, 64.0, 4.0);
        CompassCache cache = new CompassCache();
        cache.replace(holderId, refreshSpot, List.of(),
                List.of(new CompassSnapshot(targetId, targetSpot)));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(holderId);
        when(player.getLocation()).thenReturn(new Location(world, 1000.0, 64.0, 0.0));
        when(player.getWorld()).thenReturn(world);
        CompassTargetService targets = mock(CompassTargetService.class);
        when(targets.collectIdentities(any(), any(), any()))
                .thenReturn(List.of(new CompassIdentity(targetId, "victim")));
        when(targets.collectSightings(any(), any(), any(), any())).thenReturn(List.of());
        MessagesConfig texts = new MessagesConfig();
        CompassLockService locks = new CompassLockService(mock(CompassSettingsFacade.class),
                new CompassLockService.LockCycle(targets, cache, mock(Consumer.class),
                        mock(Consumer.class)),
                mock(CompassAnalysisRunner.class),
                new CompassLockService.LockTexts(mock(MessageService.class), texts.getCompass(),
                        mock(SoundService.class)),
                new CompassLockService.LockPlayers(new PlayerStateStore(),
                        mock(FakeSpectatorService.class)));

        CompassLockService.CachedCycle cycle = locks.buildCycle(player, mock(GameInstance.class),
                Role.SPEEDRUNNER, 5);

        assertEquals(1, cycle.cached().size());
        assertEquals(5.0, cycle.cached().get(0).distance(), 0.001);
        ArgumentCaptor<Location> origin = ArgumentCaptor.forClass(Location.class);
        verify(targets).collectSightings(eq(player), eq(Role.SPEEDRUNNER), any(), origin.capture());
        assertEquals(refreshSpot, origin.getValue());
    }

    private record Fixture(CompassLockService locks, Player player, Consumer<Player> refresher,
            Consumer<Player> renderer, GameManager game, SoundService sounds,
            FakeSpectatorService fakes, MessageService messages, CompassTargetService targets,
            Map<UUID, Long> sharedClicks, PlayerStateStore players, MessagesConfig texts) {
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(List<CompassCandidate> opponents) {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.compass.actions.target-cycling.enabled", true);
        ConfigPathMapper.set(root, "settings.compass.actions.target-cycling.scroll-cooldown", 10.0);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService configService = new ConfigService(root,
                new ModifierStore(ModifierFiles.inMemory(), log));
        OverrideService overrides =
                new OverrideService(configService, new LobbyConfig(), () -> { });
        CompassSettingsFacade settings =
                new CompassSettingsFacade(overrides, root.getSettings().getCompass());
        FakeSpectatorService fakes = mock(FakeSpectatorService.class);
        UUID holderId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(holderId);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        PlayerStateStore playerStates = new PlayerStateStore();
        playerStates.setRole(player, Role.HUNTER);
        GameInstance instance = mock(GameInstance.class);
        GameManager game = mock(GameManager.class);
        when(game.instanceOf(holderId)).thenReturn(Optional.of(instance));
        CompassTargetService targets = mock(CompassTargetService.class);
        when(targets.collectOpponents(any(), any(), any())).thenReturn(opponents);
        when(targets.collectSightings(any(), any(), any(), any())).thenReturn(List.of());
        when(targets.collectIdentities(any(), any(), any())).thenReturn(opponents.stream()
                .map(opponent -> new CompassIdentity(opponent.id(), opponent.name())).toList());
        Consumer<Player> refresher = mock(Consumer.class);
        Consumer<Player> renderer = mock(Consumer.class);
        SoundService sounds = mock(SoundService.class);
        MessageService messages = mock(MessageService.class);
        Map<UUID, Long> sharedClicks = new HashMap<>();
        MessagesConfig texts = new MessagesConfig();
        CompassLockService locks = new CompassLockService(settings,
                new CompassLockService.LockCycle(targets, new CompassCache(), renderer,
                        refresher),
                mock(CompassAnalysisRunner.class),
                new CompassLockService.LockTexts(messages, texts.getCompass(), sounds),
                new CompassLockService.LockPlayers(playerStates, fakes));
        locks.setGameManager(game);
        return new Fixture(locks, player, refresher, renderer, game, sounds, fakes,
                messages, targets, sharedClicks, playerStates, texts);
    }

    @SuppressWarnings("unchecked")
    private static Fixture teammateFixture(List<CompassCandidate> opponents,
            boolean teammatesEnabled, Role role) {
        return teammateFixture(opponents, teammatesEnabled, role, 0.0, true);
    }

    @SuppressWarnings("unchecked")
    private static Fixture teammateFixture(List<CompassCandidate> opponents,
            boolean teammatesEnabled, Role role, double switchCooldown) {
        return teammateFixture(opponents, teammatesEnabled, role, switchCooldown, true);
    }

    @SuppressWarnings("unchecked")
    private static Fixture teammateFixture(List<CompassCandidate> opponents,
            boolean teammatesEnabled, Role role, double switchCooldown, boolean chatEnabled) {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.compass.actions.target-cycling.enabled", true);
        ConfigPathMapper.set(root, "settings.compass.actions.target-cycling.scroll-cooldown", 0.0);
        ConfigPathMapper.set(root, "settings.compass.actions.teammates.enabled", teammatesEnabled);
        ConfigPathMapper.set(root, "settings.compass.actions.teammates.switch-cooldown", switchCooldown);
        ConfigPathMapper.set(root, "settings.compass.feedback.chat-messages.enabled", chatEnabled);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService configService = new ConfigService(root,
                new ModifierStore(ModifierFiles.inMemory(), log));
        var overrides = new OverrideService(configService, new LobbyConfig(), () -> { });
        var settings = new CompassSettingsFacade(overrides, root.getSettings().getCompass());
        FakeSpectatorService fakes = mock(FakeSpectatorService.class);
        UUID holderId = UUID.randomUUID();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(holderId);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        PlayerStateStore playerStates = new PlayerStateStore();
        playerStates.setRole(player, role);
        GameInstance instance = mock(GameInstance.class);
        GameManager game = mock(GameManager.class);
        when(game.instanceOf(holderId)).thenReturn(Optional.of(instance));
        CompassTargetService targets = mock(CompassTargetService.class);
        when(targets.collectOpponents(any(), any(), any())).thenReturn(opponents);
        when(targets.collectSightings(any(), any(), any(), any())).thenReturn(List.of());
        when(targets.collectIdentities(any(), any(), any())).thenReturn(opponents.stream()
                .map(opponent -> new CompassIdentity(opponent.id(), opponent.name())).toList());
        Consumer<Player> refresher = mock(Consumer.class);
        Consumer<Player> renderer = mock(Consumer.class);
        SoundService sounds = mock(SoundService.class);
        MessageService messages = mock(MessageService.class);
        Map<UUID, Long> sharedClicks = new HashMap<>();
        MessagesConfig texts = new MessagesConfig();
        CompassLockService locks = new CompassLockService(settings,
                new CompassLockService.LockCycle(targets, new CompassCache(), renderer,
                        refresher),
                mock(CompassAnalysisRunner.class),
                new CompassLockService.LockTexts(messages, texts.getCompass(), sounds),
                new CompassLockService.LockPlayers(playerStates, fakes));
        locks.setGameManager(game);
        return new Fixture(locks, player, refresher, renderer, game, sounds, fakes,
                messages, targets, sharedClicks, playerStates, texts);
    }

    @Test
    void shortenedTicksKeepsFractionOfRemaining() {
        assertEquals(30L, AnalysisTiming.shortenedTicks(100L, 0.3));
        assertEquals(0L, AnalysisTiming.shortenedTicks(100L, 0.0));
        assertEquals(100L, AnalysisTiming.shortenedTicks(100L, 1.0));
        assertEquals(1L, AnalysisTiming.shortenedTicks(1L, 0.3));
    }

    @Test
    void shortenedTicksClampsMultiplierAndRemaining() {
        assertEquals(100L, AnalysisTiming.shortenedTicks(100L, 9.9));
        assertEquals(0L, AnalysisTiming.shortenedTicks(100L, -2.0));
        assertEquals(0L, AnalysisTiming.shortenedTicks(-5L, 0.3));
    }
}
