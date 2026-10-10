package com.jruk8.jmanhunt.match.lifecycle;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpawnCampService;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mid-match elimination: role-gated lose plus any-role engine deaths. */
class MatchEliminationServiceTest {

    private record Fixture(MatchEliminationService elimination, MatchStore store,
            GameInstance instance, PlayerStateStore states, Player player, UUID id) {
    }

    private static Fixture fixture(Role role, boolean begun, boolean ending) {
        MatchStore store = mock(MatchStore.class);
        GameInstance instance = mock(GameInstance.class);
        when(store.instance(7L)).thenReturn(Optional.of(instance));
        when(instance.matchId()).thenReturn(7L);
        when(instance.begun()).thenReturn(begun);
        when(instance.ending()).thenReturn(ending);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("Alex");
        when(instance.assignedPlayerIds()).thenReturn(Set.of(id));
        PlayerStateStore states = mock(PlayerStateStore.class);
        when(states.role(player)).thenReturn(role);
        @SuppressWarnings("unchecked")
        Consumer<GameInstance> onEliminated = mock(Consumer.class);
        MatchEliminationService elimination = new MatchEliminationService(
                new MatchEliminationService.ElimPlayers(states,
                        mock(FakeSpectatorService.class)),
                new MatchEliminationService.ElimEdge(mock(TaskScheduler.class),
                        mock(SpawnCampService.class), mock(RoleTeamService.class)),
                mock(CompassManager.class), mock(MatchMessaging.class),
                new MatchEliminationService.ElimMatch(store, mock(FlagStore.class),
                        onEliminated));
        return new Fixture(elimination, store, instance, states, player, id);
    }

    @Test
    void losePlayerKeepsParticipantGate() {
        Fixture hunter = fixture(Role.HUNTER, true, false);
        Fixture spectator = fixture(Role.SPECTATOR, true, false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(hunter.id())).thenReturn(hunter.player());
            bukkit.when(() -> Bukkit.getPlayer(spectator.id()))
                    .thenReturn(spectator.player());

            assertTrue(hunter.elimination().losePlayer(7L, "Alex", "fell"));
            assertFalse(spectator.elimination().losePlayer(7L, "Alex", "fell"));
        }
    }

    @Test
    void losePlayerRejectsUnknownOfflineAndNotLive() {
        Fixture fixture = fixture(Role.HUNTER, true, false);
        Fixture idle = fixture(Role.HUNTER, false, false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.id())).thenReturn(fixture.player());
            bukkit.when(() -> Bukkit.getPlayer(idle.id())).thenReturn(idle.player());

            assertFalse(fixture.elimination().losePlayer(7L, "Ghost", "fell"));
            assertFalse(fixture.elimination().losePlayer(9L, "Alex", "fell"));
            assertFalse(idle.elimination().losePlayer(7L, "Alex", "fell"));
        }
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.id())).thenReturn(null);

            assertFalse(fixture.elimination().losePlayer(7L, "Alex", "fell"));
        }
    }

    @Test
    void eliminateAnyRoleAcceptsSpectators() {
        Fixture fixture = fixture(Role.SPECTATOR, true, false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.id())).thenReturn(fixture.player());

            assertTrue(fixture.elimination().eliminateAnyRole(7L, "Alex", "zero hp"));
        }
    }

    @Test
    void eliminateAnyRoleRunsSharedCore() {
        Fixture fixture = fixture(Role.HUNTER, true, false);
        SpawnCampService spawnCamp = mock(SpawnCampService.class);
        doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(spawnCamp).suppressDeathTrigger(any(Player.class), any(Runnable.class));
        MatchMessaging messaging = mock(MatchMessaging.class);
        @SuppressWarnings("unchecked")
        Consumer<GameInstance> onEliminated = mock(Consumer.class);
        MatchEliminationService elimination = new MatchEliminationService(
                new MatchEliminationService.ElimPlayers(fixture.states(),
                        mock(FakeSpectatorService.class)),
                new MatchEliminationService.ElimEdge(mock(TaskScheduler.class), spawnCamp,
                        mock(RoleTeamService.class)),
                mock(CompassManager.class), messaging,
                new MatchEliminationService.ElimMatch(fixture.store(), mock(FlagStore.class),
                        onEliminated));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.id())).thenReturn(fixture.player());

            assertTrue(elimination.eliminateAnyRole(7L, "Alex", "zero hp"));
        }

        verify(spawnCamp).suppressDeathTrigger(eq(fixture.player()), any(Runnable.class));
        verify(spawnCamp).quietKill(fixture.player());
        verify(fixture.instance()).recordDeath(fixture.id(), "Alex", Role.HUNTER);
        verify(fixture.states()).setRole(fixture.id(), Role.SPECTATOR);
        verify(fixture.instance()).deactivate(fixture.id());
        verify(messaging).sendToInstance(fixture.instance(), "game.loseplayer",
                Map.of("player", "Alex", "reason", "zero hp"));
        verify(messaging).playInstanceSound(fixture.instance(), "game.hunter-death");
        verify(onEliminated).accept(fixture.instance());
    }

    @Test
    void losePlayerKillsWithoutTriggerSuppression() {
        Fixture fixture = fixture(Role.HUNTER, true, false);
        SpawnCampService spawnCamp = mock(SpawnCampService.class);
        @SuppressWarnings("unchecked")
        Consumer<GameInstance> onEliminated = mock(Consumer.class);
        MatchEliminationService elimination = new MatchEliminationService(
                new MatchEliminationService.ElimPlayers(fixture.states(),
                        mock(FakeSpectatorService.class)),
                new MatchEliminationService.ElimEdge(mock(TaskScheduler.class), spawnCamp,
                        mock(RoleTeamService.class)),
                mock(CompassManager.class), mock(MatchMessaging.class),
                new MatchEliminationService.ElimMatch(fixture.store(), mock(FlagStore.class),
                        onEliminated));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.id())).thenReturn(fixture.player());

            assertTrue(elimination.losePlayer(7L, "Alex", "fell"));
        }

        verify(spawnCamp, never()).suppressDeathTrigger(any(Player.class), any(Runnable.class));
        verify(spawnCamp).quietKill(fixture.player());
    }

    @Test
    void eliminateAnyRoleRejectsUnknownAndNotLive() {
        Fixture fixture = fixture(Role.SPECTATOR, true, false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.id())).thenReturn(null);

            assertFalse(fixture.elimination().eliminateAnyRole(7L, "Alex", "zero hp"));
        }
        verify(fixture.instance(), never()).deactivate(any());
    }
}
