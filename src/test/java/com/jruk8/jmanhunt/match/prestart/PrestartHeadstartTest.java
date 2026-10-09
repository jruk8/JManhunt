package com.jruk8.jmanhunt.match.prestart;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.command.TagCooldownStore;
import com.jruk8.jmanhunt.config.MatchSettings;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.config.MatchSettingsFacade;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.match.CountdownService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.LimboFeedbackService;
import com.jruk8.jmanhunt.match.lifecycle.MatchControl;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.match.lifecycle.MatchStore;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

/** Headstart open: initial mark fires at once, waiters skip broadcasts. */
class PrestartHeadstartTest {

    private record Fixture(PrestartService prestart, GameInstance instance, Player runner,
            Player watcher, MatchMessaging messaging, MessageService messages,
            ManhuntMessages manhunt) {
    }

    private static Player namedPlayer(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        when(player.getLocation()).thenReturn(mock(Location.class));
        return player;
    }

    private static Fixture fixture() {
        PlayerStateStore states = new PlayerStateStore();
        Player runner = namedPlayer("Alex");
        Player watcher = namedPlayer("Bo");
        states.setRole(runner, Role.SPEEDRUNNER);
        states.setRole(watcher, Role.HUNTER);
        MatchStore store = mock(MatchStore.class);
        when(store.onlineActivePlayers(any())).thenReturn(List.of(runner, watcher));
        when(store.onlineAssignedPlayers(any())).thenReturn(List.of(runner, watcher));
        GameInstance instance = mock(GameInstance.class);
        when(instance.matchId()).thenReturn(7L);
        when(instance.active()).thenReturn(true);
        when(instance.originLobbyId()).thenReturn(0);
        when(instance.isActive(any())).thenReturn(true);
        HeadstartState hunterSide = new HeadstartState();
        HeadstartState runnerSide = new HeadstartState();
        hunterSide.setArmed(true);
        hunterSide.setRemaining(35);
        when(instance.headstart(any())).thenAnswer(invocation ->
                invocation.getArgument(0) == Role.HUNTER ? hunterSide : runnerSide);
        when(store.instance(7L)).thenReturn(Optional.of(instance));
        MatchSettingsFacade match = mock(MatchSettingsFacade.class);
        when(match.limboMultiBroadcastInterval(any())).thenReturn(20);
        MessageService messages = mock(MessageService.class);
        when(messages.roleName(any())).thenReturn("speedrunner");
        when(messages.roleColor(any())).thenReturn("<red>");
        ManhuntMessages manhunt = new ManhuntMessages();
        MatchMessaging messaging = mock(MatchMessaging.class);
        LimboFeedbackService limbo = new LimboFeedbackService(
                new LimboFeedbackService.LimboReads(store, states, match,
                        new TagCooldownStore(System::currentTimeMillis),
                        mock(TaskScheduler.class)),
                new LimboFeedbackService.LimboTexts(messages, manhunt, messaging));
        PrestartService prestart = new PrestartService(
                new PrestartService.PrestartConfig(mock(MatchSettings.Headstarts.class), match,
                        mock(PlayersSettingsFacade.class), mock(OverrideService.class)),
                new PrestartService.PrestartServices(states, mock(StatsManager.class),
                        mock(GameStateCommandManager.class), store, mock(MatchControl.class),
                        mock(FakeSpectatorService.class), mock(TaskScheduler.class),
                        new CountdownService(mock(TaskScheduler.class)), limbo),
                messages, messaging, manhunt);
        return new Fixture(prestart, instance, runner, watcher, messaging, messages, manhunt);
    }

    @Test
    void initialMarkAnnouncesAtStartAndSkipsHeld() {
        Fixture fixture = fixture();

        fixture.prestart().beginHeadstarts(fixture.instance());
        UUID runnerId = fixture.runner().getUniqueId();

        verify(fixture.messaging()).sendToInstanceExcept(eq(fixture.instance()),
                eq(fixture.manhunt().getHeadstartEnding()),
                eq(Map.of("time", "35s", "role", "speedrunner")),
                eq(Set.of(runnerId)));
        verify(fixture.messages()).sendToRaw(eq(List.of(fixture.runner())),
                eq(fixture.manhunt().getLimboSelf()), eq(Map.of("time", "35s")));
        verify(fixture.messaging()).sendToInstanceExcept(eq(fixture.instance()),
                eq(fixture.manhunt().getLimboSingle()),
                eq(Map.of("rolecolor", "<red>", "player", "Alex", "time", "35s")),
                eq(Set.of(runnerId)));
        verify(fixture.messaging(), never()).sendToInstance(eq(fixture.instance()),
                eq(fixture.manhunt().getHeadstartEnding()), any());
        verify(fixture.messaging(), times(2)).playInstanceSound(fixture.instance(),
                "game.autostart-countdown");
    }
}
