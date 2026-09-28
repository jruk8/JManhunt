package com.jruk8.jmanhunt.match;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.command.TagContext;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import java.util.List;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ModifierToggleSyncTest {

    private record Fixture(GameStateCommandManager sync, Player player) {
    }

    private static Fixture fixture(boolean begun, boolean ending, boolean effective) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.logger()).thenReturn(mock(JManhuntLogger.class));
        OverrideService overrides = mock(OverrideService.class);
        when(plugin.overrides()).thenReturn(overrides);
        when(overrides.modifierEnabled(any(), eq("m"))).thenReturn(effective);
        ConfigService config = mock(ConfigService.class);
        when(config.behaviorIndexes("m")).thenReturn(List.of(0));
        when(config.runsOn("m", 0)).thenReturn(List.of("ON_START"));
        when(config.preStartOrder("m", 0)).thenReturn("BEFORE");
        when(config.chance("m", 0)).thenReturn(1.0);
        when(config.chanceBehavior("m", 0)).thenReturn("PER_INVOKE");
        when(config.pickBehavior("m", 0)).thenReturn("PER_INVOKE");
        when(config.delayTicks("m", 0)).thenReturn(0L);
        when(config.commandList("m", 0, "console")).thenReturn(List.of("say console"));
        when(config.commandList("m", 0, "player")).thenReturn(List.of("say player"));
        when(config.commandList("m", 0, "speedrunner")).thenReturn(List.of());
        when(config.commandList("m", 0, "hunter")).thenReturn(List.of());
        when(config.commandList("m", 0, "console-cleanup")).thenReturn(List.of("say conclean"));
        when(config.commandList("m", 0, "player-cleanup")).thenReturn(List.of("say playerclean"));
        GameManager game = mock(GameManager.class);
        GameInstance instance = mock(GameInstance.class);
        when(instance.matchId()).thenReturn(7L);
        when(instance.begun()).thenReturn(begun);
        when(instance.ending()).thenReturn(ending);
        when(game.liveInstances()).thenReturn(List.of(instance));
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Steve");
        when(game.onlineParticipants(7L)).thenReturn(List.of(player));
        GameStateCommandManager sync = spy(new GameStateCommandManager(plugin,
                new PlayerStateStore(), config, mock(MessageService.class),
                mock(SoundService.class), game));
        doNothing().when(sync).runCommandList(any(), any(), any(TagContext.class),
                any(TagContext.Provenance.class));
        return new Fixture(sync, player);
    }

    @Test
    void enableFiresOnStartLists() {
        Fixture fixture = fixture(true, false, true);

        fixture.sync().syncModifierToggles(List.of("m"));

        verify(fixture.sync()).runCommandList(eq(List.of("say console")), eq(null),
                any(TagContext.class), any(TagContext.Provenance.class));
        verify(fixture.sync()).runCommandList(eq(List.of("say player")), eq(fixture.player()),
                any(TagContext.class), any(TagContext.Provenance.class));
        verify(fixture.sync(), never()).runCommandList(eq(List.of("say conclean")), any(),
                any(TagContext.class), any(TagContext.Provenance.class));
    }

    @Test
    void disableRunsCleanupOnce() {
        Fixture fixture = fixture(true, false, false);

        fixture.sync().syncModifierToggles(List.of("m"));

        verify(fixture.sync(), times(1)).runCommandList(eq(List.of("say conclean")), eq(null),
                any(TagContext.class), any(TagContext.Provenance.class));
        verify(fixture.sync(), times(1)).runCommandList(eq(List.of("say playerclean")),
                eq(fixture.player()), any(TagContext.class), any(TagContext.Provenance.class));
        verify(fixture.sync(), never()).runCommandList(eq(List.of("say console")), any(),
                any(TagContext.class), any(TagContext.Provenance.class));
    }

    @Test
    void endingMatchSkipsSync() {
        Fixture fixture = fixture(true, true, true);

        fixture.sync().syncModifierToggles(List.of("m"));

        verify(fixture.sync(), never()).runCommandList(any(), any(), any(TagContext.class),
                any(TagContext.Provenance.class));
    }

    @Test
    void preStartEnableSkipsDeferredStarts() {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.logger()).thenReturn(mock(JManhuntLogger.class));
        OverrideService overrides = mock(OverrideService.class);
        when(plugin.overrides()).thenReturn(overrides);
        when(overrides.modifierEnabled(any(), eq("m"))).thenReturn(true);
        ConfigService config = mock(ConfigService.class);
        when(config.behaviorIndexes("m")).thenReturn(List.of(0, 1));
        when(config.runsOn("m", 0)).thenReturn(List.of("ON_START"));
        when(config.runsOn("m", 1)).thenReturn(List.of("ON_START"));
        when(config.preStartOrder("m", 0)).thenReturn("BEFORE");
        when(config.preStartOrder("m", 1)).thenReturn("AFTER");
        when(config.chance("m", 0)).thenReturn(1.0);
        when(config.chance("m", 1)).thenReturn(1.0);
        when(config.chanceBehavior("m", 0)).thenReturn("PER_INVOKE");
        when(config.pickBehavior("m", 0)).thenReturn("PER_INVOKE");
        when(config.delayTicks("m", 0)).thenReturn(0L);
        when(config.commandList("m", 0, "console")).thenReturn(List.of("say now"));
        when(config.commandList("m", 0, "player")).thenReturn(List.of());
        when(config.commandList("m", 0, "hunter")).thenReturn(List.of());
        when(config.commandList("m", 0, "speedrunner")).thenReturn(List.of());
        when(config.commandList("m", 1, "console")).thenReturn(List.of("say later"));
        GameManager game = mock(GameManager.class);
        GameInstance instance = mock(GameInstance.class);
        when(instance.matchId()).thenReturn(7L);
        when(instance.begun()).thenReturn(false);
        when(instance.ending()).thenReturn(false);
        when(game.liveInstances()).thenReturn(List.of(instance));
        when(game.onlineParticipants(7L)).thenReturn(List.of());
        GameStateCommandManager sync = spy(new GameStateCommandManager(plugin,
                new PlayerStateStore(), config, mock(MessageService.class),
                mock(SoundService.class), game));
        doNothing().when(sync).runCommandList(any(), any(), any(TagContext.class),
                any(TagContext.Provenance.class));

        sync.syncModifierToggles(List.of("m"));

        verify(sync).runCommandList(eq(List.of("say now")), eq(null),
                any(TagContext.class), any(TagContext.Provenance.class));
        verify(sync, never()).runCommandList(eq(List.of("say later")), any(),
                any(TagContext.class), any(TagContext.Provenance.class));
    }
}
