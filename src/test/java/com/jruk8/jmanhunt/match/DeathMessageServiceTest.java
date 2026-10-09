package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** One death announces exactly one line, teamkills first. */
class DeathMessageServiceTest {

    private record Fixture(DeathMessageService deaths, GameInstance instance,
            MatchMessaging messaging, GameMessages texts, PlayerStateStore states,
            AtomicBoolean friendlyFire) {
    }

    private static Player namedPlayer(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        return player;
    }

    private static Fixture fixture() {
        MatchMessaging messaging = mock(MatchMessaging.class);
        GameMessages texts = new MessagesConfig().getGame();
        PlayerStateStore states = mock(PlayerStateStore.class);
        AtomicBoolean friendlyFire = new AtomicBoolean(true);
        DeathMessageService deaths = new DeathMessageService(
                new DeathMessageService.DeathTexts(messaging, texts), states,
                friendlyFire::get);
        GameInstance instance = mock(GameInstance.class);
        return new Fixture(deaths, instance, messaging, texts, states, friendlyFire);
    }

    @Test
    void sameParticipantRoleCountsAsFriendlyFire() {
        assertTrue(DeathMessageService.isFriendlyFireKill(Role.HUNTER, Role.HUNTER, false));
        assertTrue(DeathMessageService.isFriendlyFireKill(
                Role.SPEEDRUNNER, Role.SPEEDRUNNER, false));
    }

    @Test
    void crossTeamSuicideAndNonParticipantKillsAreExcluded() {
        assertFalse(DeathMessageService.isFriendlyFireKill(Role.HUNTER, Role.SPEEDRUNNER, false));
        assertFalse(DeathMessageService.isFriendlyFireKill(Role.SPEEDRUNNER, Role.HUNTER, false));
        assertFalse(DeathMessageService.isFriendlyFireKill(Role.HUNTER, Role.HUNTER, true));
        assertFalse(DeathMessageService.isFriendlyFireKill(Role.SPECTATOR, Role.SPECTATOR, false));
        assertFalse(DeathMessageService.isFriendlyFireKill(Role.NONE, Role.NONE, false));
        assertFalse(DeathMessageService.isFriendlyFireKill(Role.AFK, Role.AFK, false));
    }

    @Test
    void friendlyFireTemplatesCycleThreeLines() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "game.friendly-fire-1", "line one");
        ConfigPathMapper.set(config, "game.friendly-fire-2", "line two");
        ConfigPathMapper.set(config, "game.friendly-fire-3", "line three");
        GameMessages texts = config.getGame();
        assertEquals("line one", DeathMessageService.friendlyFireTemplate(texts, 0));
        assertEquals("line two", DeathMessageService.friendlyFireTemplate(texts, 1));
        assertEquals("line three", DeathMessageService.friendlyFireTemplate(texts, 2));
        assertEquals("line one", DeathMessageService.friendlyFireTemplate(texts, 3));
    }

    @Test
    void friendlyFireKillSendsOnlyTeamkillLine() {
        Fixture fixture = fixture();
        Player victim = namedPlayer("Alex");
        Player killer = namedPlayer("Bo");
        when(victim.getKiller()).thenReturn(killer);
        when(fixture.states().role(killer)).thenReturn(Role.HUNTER);

        fixture.deaths().announceDeath(fixture.instance(), victim, Role.HUNTER, false, 0, false);

        ArgumentCaptor<String> template = ArgumentCaptor.forClass(String.class);
        verify(fixture.messaging(), times(1)).sendToInstance(eq(fixture.instance()),
                template.capture(), eq(Map.of("dead", "Alex", "killer", "Bo")));
        assertTrue(Set.of(fixture.texts().getFriendlyFire1(), fixture.texts().getFriendlyFire2(),
                fixture.texts().getFriendlyFire3()).contains(template.getValue()));
    }

    @Test
    void friendlyFireEliminationStillSendsOnlyTeamkillLine() {
        Fixture fixture = fixture();
        Player victim = namedPlayer("Alex");
        Player killer = namedPlayer("Bo");
        when(victim.getKiller()).thenReturn(killer);
        when(fixture.states().role(killer)).thenReturn(Role.SPEEDRUNNER);

        fixture.deaths().announceDeath(fixture.instance(), victim, Role.SPEEDRUNNER, true, 1,
                false);

        verify(fixture.messaging(), times(1)).sendToInstance(eq(fixture.instance()), any(),
                any());
        verify(fixture.messaging(), never()).sendToInstance(eq(fixture.instance()),
                eq(fixture.texts().getSpeedrunnerOutOfLives()), any());
        verify(fixture.messaging(), never()).sendToInstance(eq(fixture.instance()),
                eq(fixture.texts().getSpeedrunnerDeath()), any());
    }

    @Test
    void regularHunterDeathSendsDiedLine() {
        Fixture fixture = fixture();
        Player victim = namedPlayer("Alex");

        fixture.deaths().announceDeath(fixture.instance(), victim, Role.HUNTER, false, 0, false);

        verify(fixture.messaging(), times(1)).sendToInstance(fixture.instance(),
                fixture.texts().getHunterDeath(), Map.of());
    }

    @Test
    void survivedRunnerDeathSendsTally() {
        Fixture fixture = fixture();
        Player victim = namedPlayer("Alex");

        fixture.deaths().announceDeath(fixture.instance(), victim, Role.SPEEDRUNNER, false, 2,
                false);

        verify(fixture.messaging(), times(1)).sendToInstance(fixture.instance(),
                fixture.texts().getSpeedrunnerDeath(), Map.of("value", "2"));
    }

    @Test
    void hunterEliminationSendsOutOfLives() {
        Fixture fixture = fixture();
        Player victim = namedPlayer("Alex");

        fixture.deaths().announceDeath(fixture.instance(), victim, Role.HUNTER, true, 0, false);

        verify(fixture.messaging(), times(1)).sendToInstance(fixture.instance(),
                fixture.texts().getHunterOutOfLives(), Map.of());
    }

    @Test
    void runnerEliminationSendsOutOfLives() {
        Fixture fixture = fixture();
        Player victim = namedPlayer("Alex");

        fixture.deaths().announceDeath(fixture.instance(), victim, Role.SPEEDRUNNER, true, 1,
                false);

        verify(fixture.messaging(), times(1)).sendToInstance(fixture.instance(),
                fixture.texts().getSpeedrunnerOutOfLives(), Map.of());
    }

    @Test
    void quietStaysSilent() {
        Fixture fixture = fixture();
        Player victim = namedPlayer("Alex");

        fixture.deaths().announceDeath(fixture.instance(), victim, Role.HUNTER, false, 0, true);

        verify(fixture.messaging(), never()).sendToInstance(any(), any(), any());
    }

    @Test
    void disabledFriendlyFireFallsBackToRegular() {
        Fixture fixture = fixture();
        fixture.friendlyFire().set(false);
        Player victim = namedPlayer("Alex");
        Player killer = namedPlayer("Bo");
        when(victim.getKiller()).thenReturn(killer);
        when(fixture.states().role(killer)).thenReturn(Role.HUNTER);

        fixture.deaths().announceDeath(fixture.instance(), victim, Role.HUNTER, false, 0, false);

        verify(fixture.messaging(), times(1)).sendToInstance(fixture.instance(),
                fixture.texts().getHunterDeath(), Map.of());
    }

    @Test
    void suicideFallsBackToRegular() {
        Fixture fixture = fixture();
        Player victim = namedPlayer("Alex");
        when(victim.getKiller()).thenReturn(victim);
        when(fixture.states().role(victim)).thenReturn(Role.HUNTER);

        fixture.deaths().announceDeath(fixture.instance(), victim, Role.HUNTER, false, 0, false);

        verify(fixture.messaging(), times(1)).sendToInstance(fixture.instance(),
                fixture.texts().getHunterDeath(), Map.of());
    }
}
