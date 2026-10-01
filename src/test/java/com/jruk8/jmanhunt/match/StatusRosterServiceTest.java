package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class StatusRosterServiceTest {

    private record Fixture(StatusRosterService roster, MessageService messages,
            PlayerStateStore players, CommandSender recipient) {
    }

    private static ManhuntMessages texts() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "manhunt.status-limit-speedrunners", "5");
        ConfigPathMapper.set(config, "manhunt.status-limit-hunters", "10");
        ConfigPathMapper.set(config, "manhunt.status-limit-afk", "2");
        ConfigPathMapper.set(config, "manhunt.status-limit-none", "20");
        ConfigPathMapper.set(config, "manhunt.status-limit-spectators", "5");
        ConfigPathMapper.set(config, "manhunt.status-limit-dead", "3");
        ConfigPathMapper.set(config, "manhunt.status-comma", "<gray>,</gray>");
        ConfigPathMapper.set(config, "manhunt.status-and", "<gray>and</gray>");
        ConfigPathMapper.set(config, "manhunt.status-more", "more");
        ConfigPathMapper.set(config, "manhunt.status-dead-word", "dead");
        ConfigPathMapper.set(config, "manhunt.status-skull", "skull");
        ConfigPathMapper.set(config, "manhunt.status-player", "player tpl");
        ConfigPathMapper.set(config, "manhunt.status-dead-line", "dead tpl");
        ConfigPathMapper.set(config, "manhunt.spectators-line", "spect tpl");
        return config.getManhunt();
    }

    private static Fixture fixture() {
        return fixture(texts());
    }

    private static Fixture fixture(ManhuntMessages texts) {
        MessageService messages = mock(MessageService.class);
        PlayerStateStore players = new PlayerStateStore();
        CommandSender recipient = mock(CommandSender.class);
        return new Fixture(new StatusRosterService(messages, texts, players), messages, players,
                recipient);
    }

    private static Player named(String name, Fixture fixture, Role role) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn(name);
        fixture.players().setRole(player, role);
        return player;
    }

    @Test
    void roleSectionJoinsAliveOxfordAndGhostsNewestFirst() {
        Fixture fixture = fixture();
        Player amy = named("amy", fixture, Role.HUNTER);
        Player bob = named("bob", fixture, Role.HUNTER);
        named("zed", fixture, Role.SPEEDRUNNER);
        UUID oldId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        List<GameInstance.DeadPlayer> dead = List.of(
                new GameInstance.DeadPlayer(oldId, "old", Role.HUNTER),
                new GameInstance.DeadPlayer(newId, "new", Role.HUNTER),
                new GameInstance.DeadPlayer(UUID.randomUUID(), "runner",
                        Role.SPEEDRUNNER));

        fixture.roster().sendRoleSection(fixture.recipient(), List.of(bob, amy), Role.HUNTER,
                "header tpl", dead, id -> false);

        verify(fixture.messages()).messageRaw(fixture.recipient(), "header tpl", Map.of());
        verify(fixture.messages()).messageRaw(eq(fixture.recipient()), eq("player tpl"),
                eq(Map.of("player", "amy <gray>and</gray> bob")));
        verify(fixture.messages()).messageRaw(eq(fixture.recipient()),
                eq("dead tpl"),
                eq(Map.of("dead_players", "skullnew <gray>and</gray> skullold")));
    }

    @Test
    void roleSectionTruncatesAliveToRoleLimit() {
        Fixture fixture = fixture();
        List<Player> hunters = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            hunters.add(named("h" + index, fixture, Role.HUNTER));
        }

        fixture.roster().sendRoleSection(fixture.recipient(), hunters, Role.HUNTER,
                "header tpl", List.of(), id -> false);

        verify(fixture.messages()).messageRaw(eq(fixture.recipient()), eq("player tpl"),
                eq(Map.of("player", "h0<gray>,</gray> h1<gray>,</gray> h10<gray>,</gray> "
                        + "h11<gray>,</gray> h2<gray>,</gray> h3<gray>,</gray> h4<gray>,</gray> "
                        + "h5<gray>,</gray> h6<gray>,</gray> h7<gray>,</gray> <gray>and</gray> "
                        + "2 more")));
    }

    @Test
    void roleSectionPrefixesRespawningSkull() {
        Fixture fixture = fixture();
        Player amy = named("amy", fixture, Role.SPEEDRUNNER);

        fixture.roster().sendRoleSection(fixture.recipient(), List.of(amy), Role.SPEEDRUNNER,
                "header tpl", List.of(), amy.getUniqueId()::equals);

        verify(fixture.messages()).messageRaw(eq(fixture.recipient()), eq("player tpl"),
                eq(Map.of("player", "skullamy")));
    }

    @Test
    void deadLimitZeroRendersOverflowWord() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "manhunt.status-limit-dead", "0");
        ConfigPathMapper.set(config, "manhunt.status-dead-line", "dead tpl");
        ConfigPathMapper.set(config, "manhunt.status-dead-word", "dead");
        ConfigPathMapper.set(config, "manhunt.status-comma", "<gray>,</gray>");
        ConfigPathMapper.set(config, "manhunt.status-and", "<gray>and</gray>");
        ConfigPathMapper.set(config, "manhunt.status-more", "more");
        ConfigPathMapper.set(config, "manhunt.status-skull", "skull");
        Fixture fixture = fixture(config.getManhunt());
        List<GameInstance.DeadPlayer> dead = List.of(
                new GameInstance.DeadPlayer(UUID.randomUUID(), "a", Role.HUNTER),
                new GameInstance.DeadPlayer(UUID.randomUUID(), "b", Role.HUNTER));

        fixture.roster().sendRoleSection(fixture.recipient(), List.of(), Role.HUNTER,
                "header tpl", dead, id -> false);

        verify(fixture.messages()).messageRaw(eq(fixture.recipient()),
                eq("dead tpl"), eq(Map.of("dead_players", "2 dead")));
        verify(fixture.messages(), never()).messageRaw(eq(fixture.recipient()),
                eq("player tpl"), anyMap());
    }

    @Test
    void emptyRoleSectionStaysSilent() {
        Fixture fixture = fixture();
        Player zed = named("zed", fixture, Role.SPEEDRUNNER);

        fixture.roster().sendRoleSection(fixture.recipient(), List.of(zed), Role.HUNTER,
                "header tpl", List.of(), id -> false);

        verifyNoInteractions(fixture.messages());
    }

    @Test
    void spectatorLineTruncatesToFive() {
        Fixture fixture = fixture();
        List<Player> players = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            players.add(named("s" + index, fixture, Role.SPECTATOR));
        }

        fixture.roster().sendSpectatorLine(fixture.recipient(), players);

        verify(fixture.messages()).messageRaw(eq(fixture.recipient()), eq("spect tpl"),
                eq(Map.of("value", "s0<gray>,</gray> s1<gray>,</gray> s2<gray>,</gray> "
                        + "s3<gray>,</gray> s4<gray>,</gray> <gray>and</gray> 2 more")));
    }

    @Test
    void emptySpectatorLineStaysSilent() {
        Fixture fixture = fixture();
        Player zed = named("zed", fixture, Role.SPEEDRUNNER);

        fixture.roster().sendSpectatorLine(fixture.recipient(), List.of(zed));

        verifyNoInteractions(fixture.messages());
    }

    @Test
    void lobbyDetailSplitsLobbyAndMatch() {
        assertEquals("1 in lobby, 4 in match", StatusRosterService.lobbyDetail(1, 4));
        assertEquals("4 in match", StatusRosterService.lobbyDetail(0, 4));
        assertEquals("4 in lobby", StatusRosterService.lobbyDetail(4, 0));
    }
}
