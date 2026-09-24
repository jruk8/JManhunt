package com.jruk8.jmanhunt.compass;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.GameMode;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompassLockServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void rapidClicksRefreshOncePerCooldown() {
        Fixture fixture = fixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0),
                new CompassCandidate(UUID.randomUUID(), "b", 20.0, 20.0)));

        fixture.locks().handleLeftClick(fixture.player());
        fixture.locks().handleLeftClick(fixture.player());

        verify(fixture.refresher(), times(1)).accept(fixture.player());
    }

    @Test
    @SuppressWarnings("unchecked")
    void singleTargetClickSkipsRefresh() {
        Fixture fixture = fixture(List.of(
                new CompassCandidate(UUID.randomUUID(), "a", 10.0, 10.0)));

        fixture.locks().handleLeftClick(fixture.player());

        verify(fixture.refresher(), never()).accept(any(Player.class));
    }

    @Test
    void jitteredDelaySamplesWithinDeviation() {
        assertEquals(3.0, CompassLockService.jitteredDelay(3.0, 0.0, 0.99));
        assertEquals(2.0, CompassLockService.jitteredDelay(3.0, 1.0, 0.0));
        assertEquals(4.0, CompassLockService.jitteredDelay(3.0, 1.0, 1.0), 0.000001);
        assertEquals(0.0, CompassLockService.jitteredDelay(3.0, 9.0, 0.0));
        assertEquals(0.0, CompassLockService.jitteredDelay(-2.0, 1.0, 0.5));
    }

    @Test
    void clampedSoundIntervalHonorsRegistryBounds() {
        assertEquals(0.5, CompassLockService.clampedSoundInterval(0.5));
        assertEquals(0.05, CompassLockService.clampedSoundInterval(0.01));
        assertEquals(3.0, CompassLockService.clampedSoundInterval(60.0));
    }

    private record Fixture(CompassLockService locks, Player player, Consumer<Player> refresher) {
    }

    @SuppressWarnings("unchecked")
    private static Fixture fixture(List<CompassCandidate> opponents) {
        JManhuntConfig root = new JManhuntConfig();
        ConfigPathMapper.set(root, "settings.compass.left-click.enabled", true);
        ConfigPathMapper.set(root, "settings.compass.left-click.scroll-cooldown", 10.0);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService configService = new ConfigService(root,
                new ModifierStore(new ModifiersConfig(), log));
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.configService()).thenReturn(configService);
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
        when(targets.collectSightings(any(), any(), any())).thenReturn(List.of());
        Consumer<Player> refresher = mock(Consumer.class);
        Map<UUID, Long> sharedClicks = new HashMap<>();
        CompassLockService locks = new CompassLockService(plugin, playerStates,
                mock(SoundService.class), null, targets, mock(CompassSignalService.class),
                new HashMap<>(), refresher, sharedClicks);
        locks.setGameManager(game);
        return new Fixture(locks, player, refresher);
    }
}
