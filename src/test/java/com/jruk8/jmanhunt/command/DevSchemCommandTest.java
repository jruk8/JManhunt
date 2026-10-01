package com.jruk8.jmanhunt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.jruk8.jmanhunt.config.DevConfig;
import com.jruk8.jmanhunt.config.WorldEngineConfig;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyService;
import com.jruk8.jmanhunt.lobby.world.LobbySchematicService;
import com.jruk8.jmanhunt.message.MessageService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DevSchemCommandTest {
    @Test
    void normalizedOrdersCornersPerAxis() {
        List<BlockVector> corners = DevSchemCommand.normalized(
                new BlockVector(5, 70, -3), new BlockVector(-2, 64, 9));

        assertEquals(new BlockVector(-2, 64, -3), corners.get(0));
        assertEquals(new BlockVector(5, 70, 9), corners.get(1));
    }

    @Test
    void blockVectorFloorsFeetToBlock() {
        assertEquals(new BlockVector(10, 64, -21),
                DevSchemCommand.blockVector(new Location(null, 10.5, 64.0, -20.2)));
    }

    @Test
    void lobbySummaryPluralizes() {
        assertEquals("no bounds or teleports",
                DevSchemCommand.lobbySummary(new JmhLobbyService.SavedCounts(0, 0)));
        assertEquals("1 bound, 1 teleport",
                DevSchemCommand.lobbySummary(new JmhLobbyService.SavedCounts(1, 1)));
        assertEquals("2 bounds, 3 teleports",
                DevSchemCommand.lobbySummary(new JmhLobbyService.SavedCounts(2, 3)));
    }

    @Test
    void schematicNamesUnionsBundlesAndLegacy(@TempDir Path dir) throws Exception {
        Path schematics = dir.resolve("settings/world-engine/lobby-schematics");
        Files.createDirectories(schematics);
        Files.write(schematics.resolve("arena.jmhlobby"), new byte[]{1});
        Files.write(schematics.resolve("old.nbt"), new byte[]{1});
        Files.write(schematics.resolve("both.jmhlobby"), new byte[]{1});
        Files.write(schematics.resolve("both.nbt"), new byte[]{1});
        Files.write(schematics.resolve("notes.txt"), new byte[]{1});
        LobbySchematicService service = new LobbySchematicService(mock(JmhLobbyService.class),
                mock(DevConfig.class), dir, mock(JManhuntLogger.class), name -> null);

        List<String> names = new DevSchemCommand(service, mock(LobbyConfig.class),
                mock(WorldEngineConfig.class), mock(JManhuntLogger.class),
                new DevSchemCommand.Texts(mock(MessageService.class), null, null)).schematicNames();

        assertEquals(List.of("arena", "both", "old"), names);
    }

    @Test
    void pendingLoadExpiresAfterTenSeconds() {
        DevSchemCommand.PendingLoad pending = new DevSchemCommand.PendingLoad("arena", 1_000L);

        assertFalse(pending.expired(1_000L + DevSchemCommand.CONFIRM_WINDOW_MILLIS));
        assertTrue(pending.expired(1_001L + DevSchemCommand.CONFIRM_WINDOW_MILLIS));
    }

    @Test
    void validNameRejectsSeparatorsAndParentRefs() {
        assertTrue(DevSchemCommand.validName("arena"));
        assertTrue(DevSchemCommand.validName("my-arena_2"));
        assertFalse(DevSchemCommand.validName("a/b"));
        assertFalse(DevSchemCommand.validName("a\\b"));
        assertFalse(DevSchemCommand.validName("../arena"));
        assertFalse(DevSchemCommand.validName("  "));
        assertFalse(DevSchemCommand.validName(null));
    }
}
