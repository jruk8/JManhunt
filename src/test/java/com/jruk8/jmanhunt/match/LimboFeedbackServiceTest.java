package com.jruk8.jmanhunt.match;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.command.TagCooldownStore;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.config.MatchSettingsFacade;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.match.lifecycle.MatchStore;
import com.jruk8.jmanhunt.match.prestart.HeadstartState;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Limbo census, mark-gated solo notes, and cooldown-gated multi notes. */
class LimboFeedbackServiceTest {

    private record Fixture(LimboFeedbackService limbo, GameInstance instance, MatchStore store,
            MessageService messages, ManhuntMessages manhunt, MatchMessaging messaging,
            HeadstartState hunterHeld, HeadstartState runnerHeld, AtomicLong clock,
            PlayerStateStore states, TaskScheduler tasks) {
    }

    private static Player namedPlayer(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static Fixture fixture(List<Player> online) {
        PlayerStateStore states = new PlayerStateStore();
        MatchStore store = mock(MatchStore.class);
        MatchSettingsFacade match = mock(MatchSettingsFacade.class);
        when(match.limboMultiBroadcastInterval(any())).thenReturn(20);
        AtomicLong clock = new AtomicLong(1_000_000L);
        TagCooldownStore cooldowns = new TagCooldownStore(clock::get);
        MessageService messages = mock(MessageService.class);
        when(messages.roleColor(any())).thenReturn("<red>");
        ManhuntMessages manhunt = new ManhuntMessages();
        MatchMessaging messaging = mock(MatchMessaging.class);
        GameInstance instance = mock(GameInstance.class);
        when(instance.active()).thenReturn(true);
        when(instance.matchId()).thenReturn(7L);
        when(instance.originLobbyId()).thenReturn(0);
        when(instance.isActive(any())).thenReturn(true);
        HeadstartState hunterHeld = new HeadstartState();
        HeadstartState runnerHeld = new HeadstartState();
        when(instance.headstart(any())).thenAnswer(
                invocation -> invocation.getArgument(0) == Role.HUNTER ? hunterHeld : runnerHeld);
        when(store.onlineAssignedPlayers(instance)).thenReturn(online);
        when(store.instance(7L)).thenReturn(Optional.of(instance));
        TaskScheduler tasks = mock(TaskScheduler.class);
        LimboFeedbackService limbo = new LimboFeedbackService(
                new LimboFeedbackService.LimboReads(store, states, match, cooldowns, tasks),
                new LimboFeedbackService.LimboTexts(messages, manhunt, messaging));
        return new Fixture(limbo, instance, store, messages, manhunt, messaging, hunterHeld,
                runnerHeld, clock, states, tasks);
    }

    /** Runs the scheduled spawn flush, failing when none was scheduled. */
    private static void flushSpawns(Fixture fixture) {
        ArgumentCaptor<Runnable> flush = ArgumentCaptor.forClass(Runnable.class);
        verify(fixture.tasks()).run(flush.capture());
        flush.getValue().run();
    }

    @Test
    void soloHoldSendsPersonalAndSingleOnLadder() {
        Player player = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(player));
        fixture.limbo().trackHold(player.getUniqueId());

        fixture.limbo().limboTick(fixture.instance(), 15, true);
        UUID playerId = player.getUniqueId();

        verify(fixture.messages()).sendToRaw(eq(List.of(player)),
                eq(fixture.manhunt().getLimboSelf()), eq(Map.of("time", "15s")));
        verify(fixture.messaging()).sendToInstanceExcept(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSingle()),
                eq(Map.of("rolecolor", "<red>", "player", "Alex", "time", "15s")),
                eq(Set.of(playerId)));
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
        verify(fixture.messaging()).playInstanceSound(fixture.instance(),
                "game.autostart-countdown");
        verify(fixture.messaging(), never()).sendToInstanceExcept(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboMulti()), any(), any());
    }

    @Test
    void nonMarkTickStaysSilentForSolo() {
        Player player = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(player));
        fixture.limbo().trackHold(player.getUniqueId());

        fixture.limbo().limboTick(fixture.instance(), 14, false);

        verify(fixture.messages(), never()).sendToRaw(any(), any(), any());
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
        verify(fixture.messaging(), never()).sendToInstanceExcept(any(), any(), any(), any());
        verify(fixture.messaging(), never()).playInstanceSound(any(), any());
    }

    @Test
    void multiBroadcastGatesOnCooldown() {
        Player first = namedPlayer("Alex");
        Player second = namedPlayer("Bo");
        Fixture fixture = fixture(List.of(first, second));
        UUID firstId = first.getUniqueId();
        UUID secondId = second.getUniqueId();
        fixture.limbo().trackHold(firstId);
        fixture.limbo().trackHold(secondId);

        fixture.limbo().limboTick(fixture.instance(), 14, false);
        fixture.limbo().limboTick(fixture.instance(), 14, false);
        verify(fixture.messaging(), times(1)).sendToInstanceExcept(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboMulti()), eq(Map.of("total", "2")),
                eq(Set.of(firstId, secondId)));

        fixture.clock().addAndGet(20_000L);
        fixture.limbo().limboTick(fixture.instance(), 14, false);
        verify(fixture.messaging(), times(2)).sendToInstanceExcept(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboMulti()), eq(Map.of("total", "2")),
                eq(Set.of(firstId, secondId)));
    }

    @Test
    void censusSkipsOfflineAndInactive() {
        Player online = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(online));
        UUID onlineId = online.getUniqueId();
        when(fixture.instance().isActive(any())).thenAnswer(
                invocation -> invocation.getArgument(0).equals(onlineId));
        fixture.limbo().trackHold(onlineId);
        fixture.limbo().trackHold(UUID.randomUUID());
        fixture.limbo().trackHold(UUID.randomUUID());

        fixture.limbo().limboTick(fixture.instance(), 10, true);

        verify(fixture.messaging()).sendToInstanceExcept(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSingle()),
                eq(Map.of("rolecolor", "<red>", "player", "Alex", "time", "10s")),
                eq(Set.of(onlineId)));
    }

    @Test
    void headstartScopeSendsPersonalOnly() {
        Player first = namedPlayer("Alex");
        Player second = namedPlayer("Bo");
        Fixture fixture = fixture(List.of(first, second));
        fixture.hunterHeld().returnPoints().put(first.getUniqueId(), mock(Location.class));
        fixture.runnerHeld().returnPoints().put(second.getUniqueId(), mock(Location.class));

        fixture.limbo().limboTick(fixture.instance(), 10, true);

        verify(fixture.messages()).sendToRaw(eq(List.of(first)),
                eq(fixture.manhunt().getLimboSelf()), eq(Map.of("time", "10s")));
        verify(fixture.messages()).sendToRaw(eq(List.of(second)),
                eq(fixture.manhunt().getLimboSelf()), eq(Map.of("time", "10s")));
        verify(fixture.messaging(), never()).sendToInstanceExcept(any(), any(), any(), any());
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
        verify(fixture.messaging(), never()).playInstanceSound(any(), any());
    }

    @Test
    void inactiveMatchSkips() {
        Player player = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(player));
        fixture.limbo().trackHold(player.getUniqueId());
        when(fixture.instance().active()).thenReturn(false);

        fixture.limbo().limboTick(fixture.instance(), 10, true);

        verify(fixture.messages(), never()).sendToRaw(any(), any(), any());
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
        verify(fixture.messaging(), never()).sendToInstanceExcept(any(), any(), any(), any());
    }

    @Test
    void announceSpawnedRendersRoleColor() {
        Player player = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(player));

        fixture.limbo().announceSpawned(fixture.instance(), player);
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
        flushSpawns(fixture);

        verify(fixture.messaging()).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSpawned()),
                eq(Map.of("rolecolor", "<red>", "player", "Alex")));
    }

    @Test
    void sameTickSpawnsCollapseIntoCountLine() {
        Player first = namedPlayer("Alex");
        Player second = namedPlayer("Blair");
        Fixture fixture = fixture(List.of(first, second));
        fixture.states().setRole(first.getUniqueId(), Role.HUNTER);
        fixture.states().setRole(second.getUniqueId(), Role.HUNTER);

        fixture.limbo().announceSpawned(fixture.instance(), first);
        fixture.limbo().announceSpawned(fixture.instance(), second);
        flushSpawns(fixture);

        verify(fixture.tasks(), times(1)).run(any());
        verify(fixture.messaging(), never()).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSpawned()), any());
        verify(fixture.messaging()).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSpawnedMulti()),
                eq(Map.of("rolecolor", "<red>", "count", "2", "role", "hunter")));
    }

    @Test
    void mixedRoleSpawnsFlushOneLinePerRole() {
        Player hunterFirst = namedPlayer("Alex");
        Player hunterSecond = namedPlayer("Blair");
        Player runner = namedPlayer("Casey");
        Fixture fixture = fixture(List.of(hunterFirst, hunterSecond, runner));
        fixture.states().setRole(hunterFirst.getUniqueId(), Role.HUNTER);
        fixture.states().setRole(hunterSecond.getUniqueId(), Role.HUNTER);
        fixture.states().setRole(runner.getUniqueId(), Role.SPEEDRUNNER);

        fixture.limbo().announceSpawned(fixture.instance(), hunterFirst);
        fixture.limbo().announceSpawned(fixture.instance(), runner);
        fixture.limbo().announceSpawned(fixture.instance(), hunterSecond);
        flushSpawns(fixture);

        verify(fixture.messaging()).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSpawnedMulti()),
                eq(Map.of("rolecolor", "<red>", "count", "2", "role", "hunter")));
        verify(fixture.messaging()).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSpawned()),
                eq(Map.of("rolecolor", "<red>", "player", "Casey")));
    }

    @Test
    void flushSkipsPlayersWhoLeftWithinTheTick() {
        Player player = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(player));
        when(fixture.instance().isActive(player.getUniqueId())).thenReturn(false);

        fixture.limbo().announceSpawned(fixture.instance(), player);
        flushSpawns(fixture);

        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
    }

    @Test
    void doubleQueuedSpawnAnnouncesOnce() {
        Player player = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(player));

        fixture.limbo().announceSpawned(fixture.instance(), player);
        fixture.limbo().announceSpawned(fixture.instance(), player);
        flushSpawns(fixture);

        verify(fixture.messaging(), times(1)).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSpawned()), any());
    }
}
