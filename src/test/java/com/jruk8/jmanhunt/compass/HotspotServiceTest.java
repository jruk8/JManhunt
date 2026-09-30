package com.jruk8.jmanhunt.compass;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hotspot recording, trimming, reduction reads, cleanup, and the
 * per-lobby sampling tick. No Bukkit server needed.
 */
class HotspotServiceTest {

    private record Fixture(HotspotService hotspots, JManhuntConfig root, PlayerStateStore players,
            GameManager game, GameInstance instance, World world) {
    }

    private static Fixture fixture() {
        JManhuntConfig root = new JManhuntConfig();
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService configService = new ConfigService(root,
                new ModifierStore(new ModifiersConfig(), log));
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.overrides()).thenReturn(
                new OverrideService(configService, new LobbyConfig(), () -> { }));
        PlayerStateStore players = new PlayerStateStore();
        HotspotService hotspots = new HotspotService(plugin, players);
        GameManager game = mock(GameManager.class);
        GameInstance instance = mock(GameInstance.class);
        when(instance.originLobbyId()).thenReturn(0);
        when(game.liveInstances()).thenReturn(List.of(instance));
        hotspots.setGameManager(game);
        return new Fixture(hotspots, root, players, game, instance, mock(World.class));
    }

    private static Player tracked(Fixture fixture, Role role, double x, double z) {
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getLocation()).thenReturn(new Location(fixture.world(), x, 64.0, z));
        fixture.players().setRole(player, role);
        when(fixture.game().onlineActivePlayers(fixture.instance())).thenReturn(List.of(player));
        return player;
    }

    @Test
    void recordTrimsToMaxPoints() {
        Fixture fixture = fixture();
        UUID id = UUID.randomUUID();
        for (int point = 0; point < 45; point++) {
            fixture.hotspots().record(id, point, 0.0, 40);
        }
        assertEquals(40, fixture.hotspots().historySize(id));
        fixture.hotspots().record(id, 99.0, 0.0, 10);
        assertEquals(10, fixture.hotspots().historySize(id));
    }

    @Test
    void reductionNeedsHistoryAndEnabledFlag() {
        Fixture fixture = fixture();
        UUID id = UUID.randomUUID();
        assertEquals(0.0, fixture.hotspots().reductionFor(id, 0.0, 0.0, null), 0.0);
        for (int point = 0; point < 20; point++) {
            fixture.hotspots().record(id, 0.0, 0.0, 40);
        }
        assertEquals(0.0, fixture.hotspots().reductionFor(id, 0.0, 0.0, null), 0.0);
        ConfigPathMapper.set(fixture.root(),
                "settings.compass.signal.inaccuracy.accuracy-hotspot.enabled", true);
        assertEquals(0.9, fixture.hotspots().reductionFor(id, 0.0, 0.0, null), 1e-9);
        assertEquals(0.0, fixture.hotspots().reductionFor(id, 500.0, 0.0, null), 0.0);
    }

    @Test
    void clearDropsHistory() {
        Fixture fixture = fixture();
        UUID id = UUID.randomUUID();
        fixture.hotspots().record(id, 0.0, 0.0, 40);
        assertEquals(1, fixture.hotspots().historySize(id));
        fixture.hotspots().clear(id);
        assertEquals(0, fixture.hotspots().historySize(id));
    }

    @Test
    void pruneOfflineDropsGonePlayers() {
        Fixture fixture = fixture();
        UUID id = UUID.randomUUID();
        fixture.hotspots().record(id, 0.0, 0.0, 40);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
            fixture.hotspots().pruneOffline();
        }
        assertEquals(0, fixture.hotspots().historySize(id));
    }

    @Test
    void tickSamplesDueLobbiesOnly() {
        Fixture fixture = fixture();
        ConfigPathMapper.set(fixture.root(),
                "settings.compass.signal.inaccuracy.accuracy-hotspot.enabled", true);
        ConfigPathMapper.set(fixture.root(),
                "settings.compass.signal.inaccuracy.accuracy-hotspot.sample-interval", 10);
        Player player = tracked(fixture, Role.HUNTER, 100.0, 200.0);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(player);
            fixture.hotspots().tick(10_000L);
            assertEquals(1, fixture.hotspots().historySize(player.getUniqueId()));
            fixture.hotspots().tick(15_000L);
            assertEquals(1, fixture.hotspots().historySize(player.getUniqueId()));
            fixture.hotspots().tick(21_000L);
            assertEquals(2, fixture.hotspots().historySize(player.getUniqueId()));
        }
    }

    @Test
    void tickSkipsSpectatorsAndDisabled() {
        Fixture fixture = fixture();
        Player watcher = tracked(fixture, Role.SPECTATOR, 100.0, 200.0);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(watcher);
            fixture.hotspots().tick(60_000L);
            assertEquals(0, fixture.hotspots().historySize(watcher.getUniqueId()));
            ConfigPathMapper.set(fixture.root(),
                    "settings.compass.signal.inaccuracy.accuracy-hotspot.enabled", true);
            fixture.hotspots().tick(120_000L);
            assertEquals(0, fixture.hotspots().historySize(watcher.getUniqueId()));
        }
    }
}
