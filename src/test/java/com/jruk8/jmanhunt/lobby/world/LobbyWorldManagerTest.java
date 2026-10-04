package com.jruk8.jmanhunt.lobby.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LobbyWorldManagerTest {

    @Test
    void defaultLobbyZeroIsBlockCentered() {
        LobbyConfig.LobbyTp tp = new LobbyConfig().getLobbies().get("0").getLobbytp();
        assertEquals(0.5, tp.getX());
        assertEquals(65.0, tp.getY());
        assertEquals(0.5, tp.getZ());
    }

    @Test
    void lowestLobbyTpKeepsDecimals() {
        LobbyConfig.LobbyEntry entry = new LobbyConfig.LobbyEntry();
        entry.setLobbytp(LobbyConfig.LobbyTp.of(0.5, 65.0, 0.5, 0.0f, 0.0f));
        Optional<Map.Entry<Integer, LobbyConfig.LobbyTp>> lowest =
                LobbyWorldManager.lowestLobbyTp(Map.of("0", entry));
        assertTrue(lowest.isPresent());
        assertEquals(0, lowest.get().getKey());
        assertEquals(0.5, lowest.get().getValue().getX());
        assertEquals(0.5, lowest.get().getValue().getZ());
    }
}
