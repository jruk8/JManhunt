package com.jruk8.jmanhunt.match;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import java.util.List;
import java.util.OptionalLong;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ModifierToggleSyncTest {

    private record Fixture(ModifierToggleService sync, ModifierToggleService.Commands commands,
            Player player) {
    }

    private static Fixture fixture(boolean begun, boolean ending, boolean effective) {
        return fixture(begun, ending, effective, true);
    }

    private static Fixture fixture(boolean begun, boolean ending, boolean effective, boolean dedup) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.logger()).thenReturn(mock(JManhuntLogger.class));
        OverrideService overrides = mock(OverrideService.class);
        when(plugin.overrides()).thenReturn(overrides);
        when(overrides.modifierEnabled(any(), eq("m"))).thenReturn(effective);
        when(overrides.getBoolean(any(), eq("advanced.misc.modifier-editor.prevent-duplicate-toggle"),
                eq(true))).thenReturn(dedup);
        ConfigService config = mock(ConfigService.class);
        when(config.behaviorIndexes("m")).thenReturn(List.of(0));
        when(config.runsOn("m", 0)).thenReturn(List.of("ON_START"));
        when(config.preStartOrder("m", 0)).thenReturn("BEFORE");
        when(config.intervalSeconds("m", 0)).thenReturn(-1.0);
        GameManager game = mock(GameManager.class);
        GameInstance instance = new GameInstance(7L, 0, OptionalLong.empty(), 1_000L);
        instance.setBegun(begun);
        instance.setEnding(ending);
        when(game.liveInstances()).thenReturn(List.of(instance));
        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Steve");
        when(game.onlineParticipants(7L)).thenReturn(List.of(player));
        ModifierToggleService.Commands commands = mock(ModifierToggleService.Commands.class);
        when(commands.afterPrestart("m", 0)).thenReturn(false);
        ModifierToggleService sync = new ModifierToggleService(plugin, config, game,
                mock(IntervalDispatcher.class), commands);
        return new Fixture(sync, commands, player);
    }

    @Test
    void enableFiresOnStartBehaviors() {
        Fixture fixture = fixture(true, false, true);

        fixture.sync().syncModifierToggles(List.of("m"));

        verify(fixture.commands()).fireBehavior("m", 0, 7L);
        verify(fixture.commands(), never()).cleanModifier(eq("m"), eq(7L), anyList());
    }

    @Test
    void disableRunsCleanupOnce() {
        Fixture fixture = fixture(true, false, false);

        fixture.sync().syncModifierToggles(List.of("m"));

        verify(fixture.commands(), times(1)).cleanModifier("m", 7L, List.of(fixture.player()));
        verify(fixture.commands(), never()).fireBehavior(eq("m"), eq(0), eq(7L));
    }

    @Test
    void repeatToggleOnFiresOnlyOnce() {
        Fixture fixture = fixture(true, false, true);

        fixture.sync().syncModifierToggles(List.of("m"));
        fixture.sync().syncModifierToggles(List.of("m"));

        verify(fixture.commands(), times(1)).fireBehavior("m", 0, 7L);
    }

    @Test
    void repeatToggleOffCleansOnlyOnce() {
        Fixture fixture = fixture(true, false, false);

        fixture.sync().syncModifierToggles(List.of("m"));
        fixture.sync().syncModifierToggles(List.of("m"));

        verify(fixture.commands(), times(1)).cleanModifier("m", 7L, List.of(fixture.player()));
    }

    @Test
    void repeatToggleOnFiresEveryTimeWhenDedupOff() {
        Fixture fixture = fixture(true, false, true, false);

        fixture.sync().syncModifierToggles(List.of("m"));
        fixture.sync().syncModifierToggles(List.of("m"));

        verify(fixture.commands(), times(2)).fireBehavior("m", 0, 7L);
    }

    @Test
    void repeatToggleOffCleansEveryTimeWhenDedupOff() {
        Fixture fixture = fixture(true, false, false, false);

        fixture.sync().syncModifierToggles(List.of("m"));
        fixture.sync().syncModifierToggles(List.of("m"));

        verify(fixture.commands(), times(2)).cleanModifier("m", 7L, List.of(fixture.player()));
    }

    @Test
    void newMatchInstanceFiresAgain() {
        Fixture first = fixture(true, false, true);
        Fixture second = fixture(true, false, true);

        first.sync().syncModifierToggles(List.of("m"));
        second.sync().syncModifierToggles(List.of("m"));

        verify(first.commands(), times(1)).fireBehavior("m", 0, 7L);
        verify(second.commands(), times(1)).fireBehavior("m", 0, 7L);
    }

    @Test
    void endingMatchSkipsSync() {
        Fixture fixture = fixture(true, true, true);

        fixture.sync().syncModifierToggles(List.of("m"));

        verify(fixture.commands(), never()).fireBehavior(eq("m"), eq(0), eq(7L));
        verify(fixture.commands(), never()).cleanModifier(eq("m"), eq(7L), anyList());
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
        when(config.intervalSeconds("m", 0)).thenReturn(-1.0);
        when(config.intervalSeconds("m", 1)).thenReturn(-1.0);
        GameManager game = mock(GameManager.class);
        GameInstance instance = new GameInstance(7L, 0, OptionalLong.empty(), 1_000L);
        when(game.liveInstances()).thenReturn(List.of(instance));
        when(game.onlineParticipants(7L)).thenReturn(List.of());
        ModifierToggleService.Commands commands = mock(ModifierToggleService.Commands.class);
        when(commands.afterPrestart("m", 0)).thenReturn(false);
        when(commands.afterPrestart("m", 1)).thenReturn(true);
        ModifierToggleService sync = new ModifierToggleService(plugin, config, game,
                mock(IntervalDispatcher.class), commands);

        sync.syncModifierToggles(List.of("m"));

        verify(commands).fireBehavior("m", 0, 7L);
        verify(commands, never()).fireBehavior("m", 1, 7L);
    }
}
