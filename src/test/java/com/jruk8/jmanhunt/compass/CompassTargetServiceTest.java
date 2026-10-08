package com.jruk8.jmanhunt.compass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class CompassTargetServiceTest {

    private record Fixture(Player holder, Player target, GameInstance instance,
            PlayerStateStore states) {
    }

    private static Fixture fixture(World world, Location holderSpot, Location targetSpot) {
        Player holder = mock(Player.class);
        UUID holderId = UUID.randomUUID();
        when(holder.getUniqueId()).thenReturn(holderId);
        when(holder.getLocation()).thenReturn(holderSpot);
        when(holder.getWorld()).thenReturn(world);
        Player target = mock(Player.class);
        UUID targetId = UUID.randomUUID();
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("Prey");
        when(target.getLocation()).thenReturn(targetSpot);
        when(target.getWorld()).thenReturn(world);
        PlayerStateStore states = new PlayerStateStore();
        states.setRole(holderId, Role.HUNTER);
        states.setRole(targetId, Role.SPEEDRUNNER);
        states.setSpeedrunnerAlive(targetId, true);
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 1_000L);
        instance.activate(targetId);
        return new Fixture(holder, target, instance, states);
    }

    @Test
    void opponentsMeasureFromGivenOrigin() {
        World world = mock(World.class);
        // Holder teleported 100 out, but the press snapshot stayed put.
        Fixture fixture = fixture(world, new Location(world, 100.0, 64.0, 0.0),
                new Location(world, 2.0, 64.0, 0.0));
        Location origin = new Location(world, 0.0, 64.0, 0.0);
        CompassTargetService targets = new CompassTargetService(fixture.states(),
                mock(FakeSpectatorService.class));
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers)
                    .thenReturn(List.of(fixture.holder(), fixture.target()));

            List<CompassCandidate> cached = targets.collectOpponents(fixture.holder(),
                    Role.SPEEDRUNNER, fixture.instance(), origin);

            assertEquals(1, cached.size());
            assertEquals(2.0, cached.get(0).distance(), 1e-9);

            List<CompassCandidate> live = targets.collectOpponents(fixture.holder(),
                    Role.SPEEDRUNNER, fixture.instance());
            assertEquals(1, live.size());
            assertEquals(98.0, live.get(0).distance(), 1e-9);
        }
    }
}
