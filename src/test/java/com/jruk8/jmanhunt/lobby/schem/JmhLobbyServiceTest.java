package com.jruk8.jmanhunt.lobby.schem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.lobby.bounds.LobbyBounds;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.BoundEntry;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.Offset;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.TeleportEntry;
import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/** Bundle collection, building, and orphan handling. */
class JmhLobbyServiceTest {

    private static final LobbyBounds.Bound REGION =
            new LobbyBounds.Bound(100, 64, 100, 109, 73, 109);
    private static final Offset ORIGIN = new Offset(100, 64, 100);

    private static LobbyConfig.LobbyEntry entry(
            Integer x1, Integer y1, Integer z1, Integer x2, Integer y2, Integer z2,
            Double tpX, Double tpY, Double tpZ) {
        LobbyConfig.LobbyEntry entry = new LobbyConfig.LobbyEntry();
        if (x1 != null) {
            LobbyConfig.BoundsData bounds = new LobbyConfig.BoundsData();
            bounds.setPos1(LobbyConfig.Position.of(x1, y1, z1));
            bounds.setPos2(LobbyConfig.Position.of(x2, y2, z2));
            entry.setBounds(bounds);
        }
        if (tpX != null) {
            entry.setLobbytp(LobbyConfig.LobbyTp.of(tpX, tpY, tpZ, 90.0f, 0.0f));
        }
        return entry;
    }

    @Test
    void collectBoundsKeepsIntersectingBoxesRelative() {
        Map<String, LobbyConfig.LobbyEntry> lobbies = new LinkedHashMap<>();
        lobbies.put("0", entry(100, 64, 100, 109, 73, 109, null, null, null));
        lobbies.put("1", entry(105, 64, 105, 200, 73, 200, null, null, null));
        lobbies.put("2", entry(500, 64, 500, 509, 73, 509, null, null, null));
        lobbies.put("bogus", entry(100, 64, 100, 109, 73, 109, null, null, null));
        lobbies.put("3", new LobbyConfig.LobbyEntry());

        List<BoundEntry> collected = JmhLobbyService.collectBounds(lobbies, REGION, ORIGIN);

        assertEquals(List.of(
                new BoundEntry(0, new Offset(0, 0, 0), new Offset(9, 9, 9)),
                new BoundEntry(1, new Offset(5, 0, 5), new Offset(100, 9, 100))),
                collected);
    }

    @Test
    void round1KeepsOneDecimal() {
        assertEquals(5.5, JmhLobbyService.round1(5.47), 1e-9);
        assertEquals(5.4, JmhLobbyService.round1(5.44), 1e-9);
        assertEquals(-5.5, JmhLobbyService.round1(-5.47), 1e-9);
        assertEquals(5.0, JmhLobbyService.round1(5.04), 1e-9);
    }

    @Test
    void collectTeleportsKeepsInsidePointsAndReportsOrphans() {
        Map<String, LobbyConfig.LobbyEntry> lobbies = new LinkedHashMap<>();
        lobbies.put("0", entry(null, null, null, null, null, null, 105.47, 65.04, 105.5));
        lobbies.put("1", entry(null, null, null, null, null, null, 106.5, 65.0, 106.5));
        lobbies.put("2", entry(null, null, null, null, null, null, 500.5, 65.0, 500.5));

        JmhLobbyService.CollectedTeleports collected = JmhLobbyService.collectTeleports(
                lobbies, REGION, ORIGIN, Set.of(0));

        assertEquals(List.of(new TeleportEntry(0, 5.5, 1.0, 5.5, 90.0f, 0.0f)),
                collected.kept());
        assertEquals(1, collected.dropped().size());
        assertEquals(1, collected.dropped().get(0).lobby());
    }

    @Test
    void saveBundleWritesReadableZipAndWarnsOrphans(@TempDir Path dir) throws Exception {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        JManhuntLogger logger = mock(JManhuntLogger.class);
        when(plugin.logger()).thenReturn(logger);
        Map<String, LobbyConfig.LobbyEntry> lobbies = new LinkedHashMap<>();
        lobbies.put("0", entry(100, 64, 100, 109, 73, 109, 105.5, 65.0, 105.5));
        lobbies.put("1", entry(null, null, null, null, null, null, 106.5, 65.0, 106.5));
        File target = dir.resolve("arena.jmhlobby").toFile();

        JmhLobbyService.SavedCounts counts = new JmhLobbyService(plugin)
                .saveBundle(target, new byte[]{7, 8}, lobbies, REGION, ORIGIN);

        assertEquals(new JmhLobbyService.SavedCounts(1, 1), counts);
        assertTrue(target.isFile());
        JmhLobbyBundle reread = new JmhLobbyService(plugin).readBundle(target);
        assertEquals(1, reread.bounds().size());
        assertEquals(1, reread.teleports().size());
        verify(logger).warning(anyString());
    }

    @Test
    void buildWritesTranslatedEntriesAndSaves() {
        Map<String, LobbyConfig.LobbyEntry> stored = new LinkedHashMap<>();
        LobbyConfig.LobbyEntry existing = new LobbyConfig.LobbyEntry();
        existing.getOverrides().getSettings().put("settings.match.autostart.enabled", false);
        stored.put("0", existing);
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        LobbyConfig lobbyConfig = mock(LobbyConfig.class);
        when(lobbyConfig.getLobbies()).thenReturn(stored);
        when(plugin.lobbyConfig()).thenReturn(lobbyConfig);
        ConfigService config = mock(ConfigService.class);
        when(config.getBoolean("world-engine.enabled", false)).thenReturn(true);
        when(plugin.configService()).thenReturn(config);
        when(plugin.logger()).thenReturn(mock(JManhuntLogger.class));
        JmhLobbyBundle bundle = new JmhLobbyBundle(new byte[]{1}, new Offset(0, 0, 0),
                List.of(new BoundEntry(0, new Offset(0, 0, 0), new Offset(9, 9, 9))),
                List.of(new TeleportEntry(1, 5.5, 1.0, 5.5, 45.0f, 10.0f)));

        JmhLobbyService.BuiltCounts built = new JmhLobbyService(plugin)
                .buildIntoLobbyConfig(bundle, 200, 64, 300);

        assertEquals(new JmhLobbyService.BuiltCounts(1, 1), built);
        LobbyConfig.BoundsData bounds = stored.get("0").getBounds();
        assertEquals(200.0, bounds.getPos1().getX());
        assertEquals(64.0, bounds.getPos1().getY());
        assertEquals(300.0, bounds.getPos1().getZ());
        assertEquals(209.0, bounds.getPos2().getX());
        assertEquals(73.0, bounds.getPos2().getY());
        assertEquals(309.0, bounds.getPos2().getZ());
        assertEquals(false, stored.get("0").getOverrides().getSettings()
                .get("settings.match.autostart.enabled"));
        LobbyConfig.LobbyTp tp = stored.get("1").getLobbytp();
        assertEquals(205.5, tp.getX());
        assertEquals(65.0, tp.getY());
        assertEquals(305.5, tp.getZ());
        assertEquals(45.0f, tp.getYaw());
        assertEquals(10.0f, tp.getPitch());
        verify(lobbyConfig).save();
    }

    @Test
    void overwrittenIdsCoversStoredBoundsAndTeleports() {
        Map<String, LobbyConfig.LobbyEntry> stored = new LinkedHashMap<>();
        stored.put("0", entry(0, 64, 0, 9, 73, 9, null, null, null));
        stored.put("1", entry(null, null, null, null, null, null, 5.5, 65.0, 5.5));
        stored.put("2", new LobbyConfig.LobbyEntry());
        JmhLobbyBundle bundle = new JmhLobbyBundle(new byte[]{1}, new Offset(0, 0, 0),
                List.of(new BoundEntry(0, new Offset(0, 0, 0), new Offset(9, 9, 9)),
                        new BoundEntry(2, new Offset(0, 0, 0), new Offset(9, 9, 9)),
                        new BoundEntry(3, new Offset(0, 0, 0), new Offset(9, 9, 9))),
                List.of(new TeleportEntry(1, 5, 1, 5, 0.0f, 0.0f),
                        new TeleportEntry(2, 5, 1, 5, 0.0f, 0.0f)));

        assertEquals(Set.of(0, 1),
                JmhLobbyService.overwrittenIds(stored, bundle, true));
        assertEquals(Set.of(0),
                JmhLobbyService.overwrittenIds(stored, bundle, false));
    }

    @Test
    void overwrittenIdsSkipsEngineOffNonzero() {
        Map<String, LobbyConfig.LobbyEntry> stored = new LinkedHashMap<>();
        stored.put("2", entry(0, 64, 0, 9, 73, 9, null, null, null));
        JmhLobbyBundle bundle = new JmhLobbyBundle(new byte[]{1}, new Offset(0, 0, 0),
                List.of(new BoundEntry(2, new Offset(0, 0, 0), new Offset(9, 9, 9))),
                List.of());

        assertEquals(Set.of(2), JmhLobbyService.overwrittenIds(stored, bundle, true));
        assertEquals(Set.of(), JmhLobbyService.overwrittenIds(stored, bundle, false));
    }

    @Test
    void buildWarnsOverwrittenIds() {
        Map<String, LobbyConfig.LobbyEntry> stored = new LinkedHashMap<>();
        stored.put("0", entry(0, 64, 0, 9, 73, 9, 5.5, 65.0, 5.5));
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        LobbyConfig lobbyConfig = mock(LobbyConfig.class);
        when(lobbyConfig.getLobbies()).thenReturn(stored);
        when(plugin.lobbyConfig()).thenReturn(lobbyConfig);
        ConfigService config = mock(ConfigService.class);
        when(config.getBoolean("world-engine.enabled", false)).thenReturn(true);
        when(plugin.configService()).thenReturn(config);
        JManhuntLogger logger = mock(JManhuntLogger.class);
        when(plugin.logger()).thenReturn(logger);
        JmhLobbyBundle bundle = new JmhLobbyBundle(new byte[]{1}, new Offset(0, 0, 0),
                List.of(new BoundEntry(0, new Offset(0, 0, 0), new Offset(9, 9, 9))),
                List.of());

        new JmhLobbyService(plugin).buildIntoLobbyConfig(bundle, 0, 64, 0);

        var captor = ArgumentCaptor.forClass(String.class);
        verify(logger).warning(captor.capture());
        assertTrue(captor.getValue().contains("0"));
    }

    @Test
    void buildSkipsNonzeroLobbiesWithEngineOff() {
        Map<String, LobbyConfig.LobbyEntry> stored = new LinkedHashMap<>();
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        LobbyConfig lobbyConfig = mock(LobbyConfig.class);
        when(lobbyConfig.getLobbies()).thenReturn(stored);
        when(plugin.lobbyConfig()).thenReturn(lobbyConfig);
        ConfigService config = mock(ConfigService.class);
        when(config.getBoolean("world-engine.enabled", false)).thenReturn(false);
        when(plugin.configService()).thenReturn(config);
        JManhuntLogger logger = mock(JManhuntLogger.class);
        when(plugin.logger()).thenReturn(logger);
        JmhLobbyBundle bundle = new JmhLobbyBundle(new byte[]{1}, new Offset(0, 0, 0),
                List.of(new BoundEntry(0, new Offset(0, 0, 0), new Offset(9, 9, 9)),
                        new BoundEntry(2, new Offset(0, 0, 0), new Offset(9, 9, 9))),
                List.of(new TeleportEntry(2, 5, 1, 5, 0.0f, 0.0f)));

        JmhLobbyService.BuiltCounts built = new JmhLobbyService(plugin)
                .buildIntoLobbyConfig(bundle, 0, 64, 0);

        assertEquals(new JmhLobbyService.BuiltCounts(1, 0), built);
        assertTrue(stored.containsKey("0"));
        assertTrue(!stored.containsKey("2"));
        verify(logger, times(2)).warning(anyString());
    }
}
