package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class MatchDefaultsServiceTest {

    @Test
    void immediateRespawnForcedWithGateDisabledAndEmptyRules() {
        OverrideService overrides = mock(OverrideService.class);
        when(overrides.getBoolean(anyInt(), anyString(), anyBoolean())).thenReturn(false);
        when(overrides.getStringList(anyInt(), anyString())).thenReturn(List.of());
        PlayerWipeService wipes = mock(PlayerWipeService.class);
        when(wipes.endWipeEnabled(anyInt())).thenReturn(false);
        List<World> respawned = new ArrayList<>();
        MatchDefaultsService service = new MatchDefaultsService(overrides,
                mock(JManhuntLogger.class), wipes,
                new MatchDefaultsService.DefaultsStates(new PlayerStateStore(),
                        mock(FakeSpectatorService.class)),
                respawned::add);
        World world = mock(World.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));

            service.runDefault("start", List.of(), List.of(), 0, true);
            service.runDefault("end", List.of(), List.of(), 0, true);
        }

        assertEquals(List.of(world, world), respawned);
    }
}
