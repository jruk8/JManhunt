package com.jruk8.jmanhunt.match.listeners;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.match.CountdownService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.LimboFeedbackService;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

/** Join-hold releases land the player on a fresh origin spawn. */
class PlayerRespawnListenerReleaseTest {

    private record Fixture(PlayerRespawnListener listener, GameInstance instance,
            GameManager game, FakeSpectatorService fakes, LimboFeedbackService limbo,
            AtomicReference<Runnable> done) {
    }

    private static Fixture fixture() {
        TaskScheduler tasks = mock(TaskScheduler.class);
        when(tasks.run(any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return mock(BukkitTask.class);
        });
        FakeSpectatorService fakes = mock(FakeSpectatorService.class);
        CountdownService countdowns = mock(CountdownService.class);
        LimboFeedbackService limbo = mock(LimboFeedbackService.class);
        PlayerRespawnListener.RespawnPlayers players =
                new PlayerRespawnListener.RespawnPlayers(mock(PlayerStateStore.class), fakes,
                        countdowns, limbo);
        GameManager game = mock(GameManager.class);
        GameInstance instance = mock(GameInstance.class);
        when(instance.matchId()).thenReturn(7L);
        when(game.instance(7L)).thenReturn(Optional.of(instance));
        AtomicReference<Runnable> done = new AtomicReference<>();
        doAnswer(invocation -> {
            done.set(invocation.getArgument(3));
            return null;
        }).when(countdowns).start(any(), anyInt(), any(), any());
        PlayerRespawnListener listener = new PlayerRespawnListener(tasks, players, game,
                mock(CompassManager.class), mock(GameMessages.class));
        return new Fixture(listener, instance, game, fakes, limbo, done);
    }

    private static Player namedPlayer(boolean online) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(online);
        return player;
    }

    @Test
    void releaseScattersOnlinePlayerToOriginSpawn() {
        Fixture fixture = fixture();
        Player player = namedPlayer(true);
        when(fixture.game().isActiveInInstance(7L, player.getUniqueId())).thenReturn(true);

        fixture.listener().scheduleJoinHold(player, fixture.instance(), 35);
        fixture.done().get().run();

        verify(fixture.game()).scatterEntrantToSpawn(fixture.instance(), player);
        verify(fixture.fakes()).disable(player);
        verify(fixture.limbo()).announceSpawned(fixture.instance(), player);
    }

    @Test
    void releaseSkipsScatterForOfflinePlayer() {
        Fixture fixture = fixture();
        Player player = namedPlayer(false);
        when(fixture.game().isActiveInInstance(7L, player.getUniqueId())).thenReturn(true);

        fixture.listener().scheduleJoinHold(player, fixture.instance(), 35);
        fixture.done().get().run();

        verify(fixture.game(), never()).scatterEntrantToSpawn(any(), any());
        verify(fixture.fakes()).disable(player);
        verify(fixture.limbo()).announceSpawned(fixture.instance(), player);
    }
}
