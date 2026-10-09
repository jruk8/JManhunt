package com.jruk8.jmanhunt.match;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.command.TagCooldownStore;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Limbo census, ladder-gated solo notes, and cooldown-gated multi notes. */
class LimboFeedbackServiceTest {

    private record Fixture(LimboFeedbackService limbo, GameInstance instance, MatchStore store,
            MessageService messages, ManhuntMessages manhunt, MatchMessaging messaging,
            HeadstartState hunterHeld, HeadstartState runnerHeld, AtomicLong clock) {
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
        LimboFeedbackService limbo = new LimboFeedbackService(
                new LimboFeedbackService.LimboReads(store, states, match, cooldowns),
                new LimboFeedbackService.LimboTexts(messages, manhunt, messaging));
        return new Fixture(limbo, instance, store, messages, manhunt, messaging, hunterHeld,
                runnerHeld, clock);
    }

    @Test
    void soloLimboSendsPersonalAndSingleOnLadder() {
        Player player = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(player));
        fixture.hunterHeld().returnPoints().put(player.getUniqueId(), mock(Location.class));

        fixture.limbo().limboTick(fixture.instance(), 15);

        verify(fixture.messages()).sendToRaw(eq(List.of(player)),
                eq(fixture.manhunt().getLimboSelf()), eq(Map.of("time", "15s")));
        verify(fixture.messaging()).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSingle()),
                eq(Map.of("player", "Alex", "time", "15s")));
        verify(fixture.messaging()).playInstanceSound(fixture.instance(),
                "game.autostart-countdown");
        verify(fixture.messaging(), never()).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboMulti()), any());
    }

    @Test
    void offLadderTickStaysSilentForSolo() {
        Player player = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(player));
        fixture.limbo().trackHold(player.getUniqueId());

        fixture.limbo().limboTick(fixture.instance(), 14);

        verify(fixture.messages(), never()).sendToRaw(any(), any(), any());
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
        verify(fixture.messaging(), never()).playInstanceSound(any(), any());
    }

    @Test
    void multiBroadcastGatesOnCooldown() {
        Player first = namedPlayer("Alex");
        Player second = namedPlayer("Bo");
        Fixture fixture = fixture(List.of(first, second));
        fixture.limbo().trackHold(first.getUniqueId());
        fixture.limbo().trackHold(second.getUniqueId());

        fixture.limbo().limboTick(fixture.instance(), 14);
        fixture.limbo().limboTick(fixture.instance(), 14);
        verify(fixture.messaging(), times(1)).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboMulti()), eq(Map.of("total", "2")));

        fixture.clock().addAndGet(20_000L);
        fixture.limbo().limboTick(fixture.instance(), 14);
        verify(fixture.messaging(), times(2)).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboMulti()), eq(Map.of("total", "2")));
    }

    @Test
    void censusSkipsOfflineAndInactive() {
        Player online = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(online));
        when(fixture.instance().isActive(any())).thenAnswer(
                invocation -> invocation.getArgument(0).equals(online.getUniqueId()));
        fixture.hunterHeld().returnPoints().put(online.getUniqueId(), mock(Location.class));
        fixture.hunterHeld().returnPoints().put(UUID.randomUUID(), mock(Location.class));
        fixture.limbo().trackHold(UUID.randomUUID());

        fixture.limbo().limboTick(fixture.instance(), 10);

        verify(fixture.messaging()).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSingle()),
                eq(Map.of("player", "Alex", "time", "10s")));
    }

    @Test
    void inactiveMatchSkips() {
        Player player = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(player));
        fixture.limbo().trackHold(player.getUniqueId());
        when(fixture.instance().active()).thenReturn(false);

        fixture.limbo().limboTick(fixture.instance(), 10);

        verify(fixture.messages(), never()).sendToRaw(any(), any(), any());
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
    }

    @Test
    void announceSpawnedRendersRoleColor() {
        Player player = namedPlayer("Alex");
        Fixture fixture = fixture(List.of(player));

        fixture.limbo().announceSpawned(fixture.instance(), player);

        verify(fixture.messaging()).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSpawned()),
                eq(Map.of("rolecolor", "<red>", "player", "Alex")));
    }
}
