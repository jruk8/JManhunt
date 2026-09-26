package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class FakeSpectatorServiceTest {

    private record Fixture(FakeSpectatorService fakes, Plugin plugin, PlayerStateStore players,
            Player watched, Player viewer) {
    }

    private static Fixture fixture() {
        Plugin plugin = mock(Plugin.class);
        PlayerStateStore players = new PlayerStateStore();
        Player watched = mock(Player.class);
        when(watched.getUniqueId()).thenReturn(UUID.randomUUID());
        Player viewer = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        FakeSpectatorService fakes =
                new FakeSpectatorService(plugin, players, () -> List.of(watched, viewer));
        return new Fixture(fakes, plugin, players, watched, viewer);
    }

    @Test
    void enableAppliesFlightAndHidesFromOthers() {
        Fixture fixture = fixture();

        fixture.fakes().enable(fixture.watched());

        assertTrue(fixture.fakes().isFakeSpectator(fixture.watched()));
        assertFalse(fixture.fakes().isFakeSpectator(fixture.viewer()));
        verify(fixture.watched()).setGameMode(GameMode.ADVENTURE);
        verify(fixture.watched()).setAllowFlight(true);
        verify(fixture.watched()).setFlying(true);
        verify(fixture.watched()).setCollidable(false);
        verify(fixture.viewer()).hidePlayer(fixture.plugin(), fixture.watched());
        verify(fixture.watched(), never()).hidePlayer(
                fixture.plugin(), fixture.watched());
    }

    @Test
    void enableIsIdempotent() {
        Fixture fixture = fixture();

        fixture.fakes().enable(fixture.watched());
        fixture.fakes().enable(fixture.watched());

        assertTrue(fixture.fakes().isFakeSpectator(fixture.watched()));
    }

    @Test
    void disableRestoresAndShows() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.watched());

        fixture.fakes().disable(fixture.watched());

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.watched()).setFlying(false);
        verify(fixture.watched()).setAllowFlight(false);
        verify(fixture.watched()).setCollidable(true);
        verify(fixture.watched()).setGameMode(GameMode.SURVIVAL);
        verify(fixture.viewer()).showPlayer(fixture.plugin(), fixture.watched());
    }

    @Test
    void disableOfNonFakeStillRestores() {
        Fixture fixture = fixture();

        fixture.fakes().disable(fixture.watched());

        verify(fixture.watched()).setGameMode(GameMode.SURVIVAL);
        verify(fixture.watched()).setAllowFlight(false);
    }

    @Test
    void quitDisables() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.watched());

        fixture.fakes().handleQuit(fixture.watched());

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.viewer()).showPlayer(fixture.plugin(), fixture.watched());
    }

    @Test
    void quitOfNonFakeTouchesNothing() {
        Fixture fixture = fixture();

        fixture.fakes().handleQuit(fixture.watched());

        verify(fixture.watched(), never()).setGameMode(GameMode.SURVIVAL);
        verify(fixture.watched(), never()).setAllowFlight(false);
    }

    @Test
    void hideFromHidesOnlyActives() {
        Fixture fixture = fixture();
        Player joiner = mock(Player.class);
        when(joiner.getUniqueId()).thenReturn(UUID.randomUUID());
        fixture.fakes().enable(fixture.watched());

        fixture.fakes().hideFrom(joiner);

        verify(joiner).hidePlayer(fixture.plugin(), fixture.watched());
        verify(joiner, never()).hidePlayer(fixture.plugin(), fixture.viewer());
    }

    @Test
    void clearDanglingResetsFlightAndVisibility() {
        Fixture fixture = fixture();

        fixture.fakes().clearDanglingState(fixture.watched());

        verify(fixture.watched()).setFlying(false);
        verify(fixture.watched()).setAllowFlight(false);
        verify(fixture.viewer()).showPlayer(fixture.plugin(), fixture.watched());
    }

    @Test
    void spectatorRoleEnablesOnlinePlayer() {
        Fixture fixture = fixture();

        fixture.players().setRole(fixture.watched(), Role.SPECTATOR);

        assertTrue(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.watched()).setGameMode(GameMode.ADVENTURE);
    }

    @Test
    void leavingSpectatorRoleDisables() {
        Fixture fixture = fixture();
        fixture.players().setRole(fixture.watched(), Role.SPECTATOR);

        fixture.players().setRole(fixture.watched(), Role.HUNTER);

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.watched()).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void otherRoleChangesLeaveModeAlone() {
        Fixture fixture = fixture();

        fixture.players().setRole(fixture.watched(), Role.HUNTER);
        fixture.players().setRole(fixture.watched(), Role.SPEEDRUNNER);

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.watched(), never()).setGameMode(GameMode.ADVENTURE);
    }

    @Test
    void offlineRoleChangesOnlyTrack() {
        Fixture fixture = fixture();
        UUID offlineId = UUID.randomUUID();

        fixture.players().setRole(offlineId, Role.SPECTATOR);

        assertFalse(fixture.fakes().isFakeSpectator(offlineId));
        verify(fixture.viewer(), never()).hidePlayer(
                fixture.plugin(), fixture.watched());
    }

    @Test
    void offlineRoleResetDropsTrackingWithoutBukkitCalls() {
        Plugin plugin = mock(Plugin.class);
        PlayerStateStore players = new PlayerStateStore();
        Player watched = mock(Player.class);
        UUID watchedId = UUID.randomUUID();
        when(watched.getUniqueId()).thenReturn(watchedId);
        List<Player> online = new ArrayList<>(List.of(watched));
        FakeSpectatorService fakes = new FakeSpectatorService(plugin, players, () -> online);
        players.setRole(watched, Role.SPECTATOR);
        assertTrue(fakes.isFakeSpectator(watchedId));
        online.clear();

        players.setRole(watchedId, Role.NONE);

        assertFalse(fakes.isFakeSpectator(watchedId));
    }

    @Test
    void fakeCheckIsNullSafe() {
        Fixture fixture = fixture();

        assertFalse(fixture.fakes().isFakeSpectator((Player) null));
        assertFalse(fixture.fakes().isFakeSpectator((UUID) null));
    }
}
