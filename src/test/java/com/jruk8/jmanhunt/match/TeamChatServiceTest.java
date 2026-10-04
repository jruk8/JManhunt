package com.jruk8.jmanhunt.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.jruk8.jmanhunt.player.Role;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Team chat prefix parsing and recipient filtering. */
class TeamChatServiceTest {

    private static final List<String> PREFIXES = List.of("@team", "@t");

    @Test
    void parsePrefixMatchesEachAlias() {
        assertEquals(Optional.of("hello"), TeamChatService.parsePrefix("@team hello", PREFIXES));
        assertEquals(Optional.of("hello"), TeamChatService.parsePrefix("@t hello", PREFIXES));
    }

    @Test
    void parsePrefixIsCaseBlindAndStrips() {
        assertEquals(Optional.of("hello"), TeamChatService.parsePrefix("@TEAM   hello  ", PREFIXES));
        assertEquals(Optional.of("hello"), TeamChatService.parsePrefix("  @t hello", PREFIXES));
    }

    @Test
    void parsePrefixRejectsWordBoundaryViolations() {
        assertEquals(Optional.empty(), TeamChatService.parsePrefix("@teammate hello", PREFIXES));
        assertEquals(Optional.empty(), TeamChatService.parsePrefix("hello @team", PREFIXES));
        assertEquals(Optional.empty(), TeamChatService.parsePrefix("hello", PREFIXES));
    }

    @Test
    void parsePrefixEmptyBodyIsUsageError() {
        assertEquals(Optional.of(""), TeamChatService.parsePrefix("@team", PREFIXES));
        assertEquals(Optional.of(""), TeamChatService.parsePrefix("@team   ", PREFIXES));
        assertEquals(Optional.of(""), TeamChatService.parsePrefix("@t", PREFIXES));
    }

    @Test
    void parsePrefixSkipsBlankAliases() {
        List<String> prefixes = List.of(" ", "@t");
        assertEquals(Optional.of("hi"), TeamChatService.parsePrefix("@t hi", prefixes));
        assertEquals(Optional.empty(), TeamChatService.parsePrefix("@team hi", prefixes));
    }

    @Test
    void filterRecipientsKeepsSameRole() {
        List<TeamChatService.Member> members = List.of(
                new TeamChatService.Member(Role.HUNTER, false),
                new TeamChatService.Member(Role.HUNTER, false),
                new TeamChatService.Member(Role.SPEEDRUNNER, false));

        List<TeamChatService.Member> recipients =
                TeamChatService.filterRecipients(members, Role.HUNTER, true);

        assertEquals(2, recipients.size());
        assertTrue(recipients.stream().allMatch(member -> member.role() == Role.HUNTER));
    }

    @Test
    void filterRecipientsSpectatorToggle() {
        TeamChatService.Member watcher = new TeamChatService.Member(Role.NONE, true);
        TeamChatService.Member idler = new TeamChatService.Member(Role.SPECTATOR, false);
        List<TeamChatService.Member> members = List.of(
                new TeamChatService.Member(Role.HUNTER, false), watcher, idler);

        List<TeamChatService.Member> shown =
                TeamChatService.filterRecipients(members, Role.HUNTER, true);
        assertEquals(2, shown.size());
        assertTrue(shown.contains(watcher));

        List<TeamChatService.Member> hidden =
                TeamChatService.filterRecipients(members, Role.HUNTER, false);
        assertEquals(1, hidden.size());
    }

    @Test
    void filterLobbyRecipientsKeepsSameLobbyAndRole() {
        List<TeamChatService.LobbyMember> members = List.of(
                new TeamChatService.LobbyMember(0, Role.HUNTER, false, false),
                new TeamChatService.LobbyMember(0, Role.HUNTER, false, false),
                new TeamChatService.LobbyMember(1, Role.HUNTER, false, false),
                new TeamChatService.LobbyMember(0, Role.SPEEDRUNNER, false, false),
                new TeamChatService.LobbyMember(0, Role.HUNTER, false, true));

        List<TeamChatService.LobbyMember> recipients =
                TeamChatService.filterLobbyRecipients(members, 0, Role.HUNTER, true);

        assertEquals(2, recipients.size());
        assertTrue(recipients.stream().allMatch(member -> member.lobbyId() == 0
                && member.role() == Role.HUNTER && !member.inMatch()));
    }

    @Test
    void filterLobbyRecipientsSpectatorToggle() {
        TeamChatService.LobbyMember watcher = new TeamChatService.LobbyMember(0, Role.NONE, true, false);
        List<TeamChatService.LobbyMember> members = List.of(
                new TeamChatService.LobbyMember(0, Role.HUNTER, false, false), watcher);

        List<TeamChatService.LobbyMember> shown =
                TeamChatService.filterLobbyRecipients(members, 0, Role.HUNTER, true);
        assertEquals(2, shown.size());
        assertTrue(shown.contains(watcher));

        List<TeamChatService.LobbyMember> hidden =
                TeamChatService.filterLobbyRecipients(members, 0, Role.HUNTER, false);
        assertEquals(1, hidden.size());
    }
}
