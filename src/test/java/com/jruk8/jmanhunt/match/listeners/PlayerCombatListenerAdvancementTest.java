package com.jruk8.jmanhunt.match.listeners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.compass.CompassManager;
import com.jruk8.jmanhunt.config.PlayerSettings;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.GameStateCommandManager;
import com.jruk8.jmanhunt.match.WinConditionEngine;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import com.jruk8.jmanhunt.player.SpawnCampService;
import com.jruk8.jmanhunt.player.SpeedrunnerDisconnectTracker;
import com.jruk8.jmanhunt.stats.StatsManager;
import com.jruk8.jmanhunt.world.WorldEngineService;
import io.papermc.paper.advancement.AdvancementDisplay;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.junit.jupiter.api.Test;

/** Advancement handling: world gamerule flip plus match-scoped posts. */
class PlayerCombatListenerAdvancementTest {

    private record Fixture(PlayerCombatListener listener, GameInstance instance,
            MatchMessaging messaging, ManhuntMessages manhunt, Player player, UUID playerId,
            World world, PlayerAdvancementDoneEvent event, Advancement advancement,
            AdvancementDisplay display) {
    }

    private static Fixture fixture(boolean begun, boolean active, boolean ending,
            boolean gameruleOn, String key, boolean announced) {
        PlayerStateStore states = new PlayerStateStore();
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Alex");
        states.setRole(player, Role.HUNTER);
        World world = mock(World.class);
        when(player.getWorld()).thenReturn(world);
        when(world.getGameRuleValue(GameRule.ANNOUNCE_ADVANCEMENTS)).thenReturn(gameruleOn);
        Advancement advancement = mock(Advancement.class);
        when(advancement.getKey()).thenReturn(NamespacedKey.fromString(key));
        AdvancementDisplay display = mock(AdvancementDisplay.class);
        when(advancement.getDisplay()).thenReturn(display);
        when(display.doesAnnounceToChat()).thenReturn(announced);
        when(display.title()).thenReturn(Component.text("Stone Age"));
        PlayerAdvancementDoneEvent event = mock(PlayerAdvancementDoneEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getAdvancement()).thenReturn(advancement);
        GameInstance instance = mock(GameInstance.class);
        when(instance.matchId()).thenReturn(7L);
        when(instance.begun()).thenReturn(begun);
        when(instance.active()).thenReturn(active);
        when(instance.ending()).thenReturn(ending);
        MatchMessaging messaging = mock(MatchMessaging.class);
        GameManager game = mock(GameManager.class);
        when(game.instanceOf(playerId)).thenReturn(Optional.of(instance));
        when(game.messaging()).thenReturn(messaging);
        when(game.stateCommands()).thenReturn(mock(GameStateCommandManager.class));
        MessageService messages = mock(MessageService.class);
        when(messages.roleColor(any())).thenReturn("<red>");
        ManhuntMessages manhunt = new ManhuntMessages();
        PlayerCombatListener listener = buildListener(states, game, messages, manhunt);
        return new Fixture(listener, instance, messaging, manhunt, player, playerId, world,
                event, advancement, display);
    }

    private static PlayerCombatListener buildListener(PlayerStateStore states, GameManager game,
            MessageService messages, ManhuntMessages manhunt) {
        return new PlayerCombatListener(
                new PlayerCombatListener.CombatReads(states, mock(FakeSpectatorService.class),
                        new PlayerSettings(), new GameMessages(), messages, manhunt),
                new PlayerCombatListener.CombatMatch(game, mock(StatsManager.class),
                        mock(WinConditionEngine.class),
                        mock(SpeedrunnerDisconnectTracker.class), new HashMap<>()),
                new PlayerCombatListener.CombatWorld(mock(CompassManager.class),
                        mock(LobbyService.class), mock(WorldEngineService.class),
                        mock(PlayerRespawnListener.class)),
                new PlayerCombatListener.CombatEdge(mock(SpawnCampService.class),
                        mock(RoleTeamService.class), mock(JManhuntLogger.class),
                        mock(LobbyConfig.class)),
                mock(TaskScheduler.class));
    }

    @Test
    void runningMatchFlipsGameruleAndPostsScopedLine() {
        Fixture fixture = fixture(true, true, false, true, "minecraft:story/mine_stone", true);

        fixture.listener().onAdvancement(fixture.event());

        verify(fixture.world()).setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        verify(fixture.event()).message(isNull());
        verify(fixture.messaging()).sendToInstance(fixture.instance(),
                fixture.manhunt().getAdvancementMade(), Map.of("rolecolor", "<red>",
                        "player", "Alex", "advancement", "Stone Age"));
    }

    @Test
    void gameruleAlreadyOffSkipsFlip() {
        Fixture fixture = fixture(true, true, false, false, "minecraft:story/mine_stone", true);

        fixture.listener().onAdvancement(fixture.event());

        verify(fixture.world(), never()).setGameRule(any(), any());
        verify(fixture.messaging()).sendToInstance(any(), any(), any());
    }

    @Test
    void preStartMatchFlipsButPostsNothing() {
        Fixture fixture = fixture(false, true, false, true, "minecraft:story/mine_stone", true);

        fixture.listener().onAdvancement(fixture.event());

        verify(fixture.world()).setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        verify(fixture.event(), never()).message(any());
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
    }

    @Test
    void endingMatchSuppressesButPostsNothing() {
        Fixture fixture = fixture(true, true, true, true, "minecraft:story/mine_stone", true);

        fixture.listener().onAdvancement(fixture.event());

        verify(fixture.event()).message(isNull());
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
    }

    @Test
    void recipeUnlockStaysFullySilent() {
        Fixture fixture = fixture(true, true, false, true, "minecraft:recipes/stone", true);

        fixture.listener().onAdvancement(fixture.event());

        verify(fixture.world()).setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        verify(fixture.event(), never()).message(any());
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
    }

    @Test
    void displaylessAdvancementSuppressesButPostsNothing() {
        Fixture fixture = fixture(true, true, false, true, "minecraft:story/mine_stone", true);
        when(fixture.advancement().getDisplay()).thenReturn(null);

        fixture.listener().onAdvancement(fixture.event());

        verify(fixture.event()).message(isNull());
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
    }

    @Test
    void unannouncedAdvancementPostsNothing() {
        Fixture fixture = fixture(true, true, false, true, "minecraft:story/mine_stone", false);

        fixture.listener().onAdvancement(fixture.event());

        verify(fixture.event()).message(isNull());
        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
    }

    @Test
    void advancementTitleShapes() {
        Advancement shown = mock(Advancement.class);
        AdvancementDisplay display = mock(AdvancementDisplay.class);
        when(shown.getDisplay()).thenReturn(display);
        when(display.doesAnnounceToChat()).thenReturn(true);
        when(display.title()).thenReturn(Component.text("Stone Age"));
        Advancement hidden = mock(Advancement.class);
        when(hidden.getDisplay()).thenReturn(null);
        Advancement quiet = mock(Advancement.class);
        AdvancementDisplay quietDisplay = mock(AdvancementDisplay.class);
        when(quiet.getDisplay()).thenReturn(quietDisplay);
        when(quietDisplay.doesAnnounceToChat()).thenReturn(false);

        assertEquals("Stone Age", PlayerCombatListener.advancementTitle(shown));
        assertNull(PlayerCombatListener.advancementTitle(hidden));
        assertNull(PlayerCombatListener.advancementTitle(quiet));
    }
}
