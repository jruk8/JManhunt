package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.PendingConfirmations;
import com.jruk8.jmanhunt.command.units.EndUnit;
import com.jruk8.jmanhunt.command.units.HelpUnit;
import com.jruk8.jmanhunt.command.units.LobbyUnit;
import com.jruk8.jmanhunt.command.units.QuickStartUnit;
import com.jruk8.jmanhunt.command.units.SetPlayerUnit;
import com.jruk8.jmanhunt.command.units.WorldEngineTpto;
import com.jruk8.jmanhunt.command.units.WorldEngineUnit;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Unit-testable ManhuntCommand argument helpers. */
class ManhuntCommandTest {

    @Test
    void forceFlagAcceptsShortAndLongForms() {
        assertTrue(SetPlayerUnit.isForceFlag("-f"));
        assertTrue(SetPlayerUnit.isForceFlag("-force"));
        assertTrue(SetPlayerUnit.isForceFlag("-F"));
        assertTrue(SetPlayerUnit.isForceFlag("-FORCE"));
    }

    @Test
    void forceFlagRejectsAnythingElse() {
        assertFalse(SetPlayerUnit.isForceFlag("force"));
        assertFalse(SetPlayerUnit.isForceFlag("-i"));
        assertFalse(SetPlayerUnit.isForceFlag(""));
        assertFalse(SetPlayerUnit.isForceFlag("-forced"));
    }

    @Test
    void immediateFlagAcceptsShortAndLongForms() {
        assertTrue(EndUnit.isImmediateFlag("-i"));
        assertTrue(EndUnit.isImmediateFlag("-immediate"));
        assertTrue(EndUnit.isImmediateFlag("-I"));
        assertTrue(EndUnit.isImmediateFlag("-IMMEDIATE"));
    }

    @Test
    void immediateFlagRejectsAnythingElse() {
        assertFalse(EndUnit.isImmediateFlag("-f"));
        assertFalse(EndUnit.isImmediateFlag("3"));
        assertFalse(EndUnit.isImmediateFlag(""));
    }

    @Test
    void noTeleportFlagAcceptsOnlyLongForm() {
        assertTrue(LobbyUnit.isNoTeleportFlag("-notp"));
        assertTrue(LobbyUnit.isNoTeleportFlag("-NOTP"));
        assertFalse(LobbyUnit.isNoTeleportFlag("-n"));
        assertFalse(LobbyUnit.isNoTeleportFlag("notp"));
        assertFalse(LobbyUnit.isNoTeleportFlag(""));
    }

    @Test
    void subcommandOptionsAreReversedForDisplay() {
        assertEquals(List.of("challenges", "help", "support", "reload", "worldengine", "config",
                "modifiers", "override", "debug", "lobby", "qs", "quickstart", "game", "end",
                "start", "setplayer", "setup", "status"),
                ManhuntCommand.subcommandOptions());
    }

    @Test
    void subcommandOptionsHideDevOnPurpose() {
        assertFalse(ManhuntCommand.subcommandOptions().contains("dev"));
    }

    @Test
    void nextFreeBoundsIdFindsFirstGap() {
        assertEquals(0, WorldEngineUnit.nextFreeBoundsId(Set.of()));
        assertEquals(1, WorldEngineUnit.nextFreeBoundsId(Set.of(0)));
        assertEquals(0, WorldEngineUnit.nextFreeBoundsId(Set.of(1, 2)));
        assertEquals(2, WorldEngineUnit.nextFreeBoundsId(Set.of(0, 1, 3)));
    }

    @Test
    void parseLobbyTpCoordsAcceptsFiveNumbers() {
        assertArrayEquals(new double[]{1.5, 65.0, -3.0, 90.0, 0.0},
                WorldEngineUnit.parseLobbyTpCoords("1.5", "65", "-3", "90", "0"));
        assertArrayEquals(new double[]{0.0, 0.0, 0.0, 0.0, 0.0},
                WorldEngineUnit.parseLobbyTpCoords(" 0 ", "0", "0", "0", "0"));
    }

    @Test
    void parseLobbyTpCoordsRejectsNonNumbers() {
        assertNull(WorldEngineUnit.parseLobbyTpCoords("1", "65", "three", "0", "0"));
        assertNull(WorldEngineUnit.parseLobbyTpCoords("1", "65", "3", "0", ""));
    }

    @Test
    void lobbyConfigHasLobbyCountsBareIndexesAsExisting() {
        LobbyConfig lobbyConfig = new LobbyConfig();
        lobbyConfig.getLobbies().clear();

        assertFalse(WorldEngineUnit.lobbyConfigHasLobby(lobbyConfig, 0));
        assertFalse(WorldEngineUnit.lobbyConfigHasLobby(null, 0));

        lobbyConfig.getLobbies().put("1", new LobbyConfig.LobbyEntry());
        assertTrue(WorldEngineUnit.lobbyConfigHasLobby(lobbyConfig, 1));
        assertFalse(WorldEngineUnit.lobbyConfigHasLobby(lobbyConfig, 2));
    }

    @Test
    void silentFlagAcceptsShortAndLongForms() {
        assertTrue(SetPlayerUnit.isSilentFlag("-s"));
        assertTrue(SetPlayerUnit.isSilentFlag("-silent"));
        assertTrue(SetPlayerUnit.isSilentFlag("-S"));
        assertTrue(SetPlayerUnit.isSilentFlag("-SILENT"));
    }

    @Test
    void silentFlagRejectsAnythingElse() {
        assertFalse(SetPlayerUnit.isSilentFlag("silent"));
        assertFalse(SetPlayerUnit.isSilentFlag("-f"));
        assertFalse(SetPlayerUnit.isSilentFlag(""));
        assertFalse(SetPlayerUnit.isSilentFlag("-silence"));
    }

    @Test
    void parseQuickStartArgsAcceptsForms() {
        assertEquals(new QuickStartArgs(null, true),
                QuickStartUnit.parseQuickStartArgs(new String[]{"qs"}));
        assertEquals(new QuickStartArgs(50, true),
                QuickStartUnit.parseQuickStartArgs(new String[]{"qs", "50"}));
    }

    @Test
    void parseQuickStartArgsRejectsJunk() {
        assertFalse(QuickStartUnit.parseQuickStartArgs(new String[]{"qs", "soon"}).valid());
        assertFalse(QuickStartUnit.parseQuickStartArgs(new String[]{"qs", "50", "60"}).valid());
        assertFalse(QuickStartUnit.parseQuickStartArgs(new String[]{"qs", "-f"}).valid());
        assertFalse(QuickStartUnit.parseQuickStartArgs(new String[]{"qs", "50", "-force"}).valid());
        assertFalse(QuickStartUnit.parseQuickStartArgs(new String[]{"qs", "50", "-f", "x"}).valid());
    }

    @Test
    void afkGuardTripsOnlyForWakingOthers() {
        assertTrue(SetPlayerUnit.needsAfkGuard(Role.AFK, Role.HUNTER, false));
        assertTrue(SetPlayerUnit.needsAfkGuard(Role.AFK, Role.NONE, false));
        assertFalse(SetPlayerUnit.needsAfkGuard(Role.AFK, Role.HUNTER, true));
        assertFalse(SetPlayerUnit.needsAfkGuard(Role.AFK, Role.AFK, false));
        assertFalse(SetPlayerUnit.needsAfkGuard(Role.NONE, Role.HUNTER, false));
        assertFalse(SetPlayerUnit.needsAfkGuard(Role.HUNTER, Role.AFK, false));
    }

    @Test
    void parseEndArgsAcceptsBareEnd() {
        EndArgs parsed = EndUnit.parseEndArgs(new String[]{"end"});

        assertTrue(parsed.valid());
        assertEquals(Optional.empty(), parsed.instanceId());
        assertFalse(parsed.immediate());
    }

    @Test
    void parseEndArgsAcceptsIdAndFlagInEitherOrder() {
        EndArgs idFirst = EndUnit.parseEndArgs(new String[]{"end", "3", "-i"});
        EndArgs flagFirst = EndUnit.parseEndArgs(new String[]{"end", "-immediate", "3"});

        for (EndArgs parsed : new EndArgs[]{idFirst, flagFirst}) {
            assertTrue(parsed.valid());
            assertEquals(Optional.of("3"), parsed.instanceId());
            assertTrue(parsed.immediate());
        }
    }

    @Test
    void parseEndArgsRejectsTwoIds() {
        EndArgs parsed = EndUnit.parseEndArgs(new String[]{"end", "3", "4"});

        assertFalse(parsed.valid());
    }

    @Test
    void parseEndArgsAcceptsAllAloneAndWithFlagInEitherOrder() {
        EndArgs bare = EndUnit.parseEndArgs(new String[]{"end", "all"});
        EndArgs allFirst = EndUnit.parseEndArgs(new String[]{"end", "ALL", "-i"});
        EndArgs flagFirst = EndUnit.parseEndArgs(new String[]{"end", "-immediate", "All"});

        assertTrue(bare.valid());
        assertTrue(bare.all());
        assertEquals(Optional.empty(), bare.instanceId());
        assertFalse(bare.immediate());
        for (EndArgs parsed : new EndArgs[]{allFirst, flagFirst}) {
            assertTrue(parsed.valid());
            assertTrue(parsed.all());
            assertEquals(Optional.empty(), parsed.instanceId());
            assertTrue(parsed.immediate());
        }
    }

    @Test
    void parseEndArgsRejectsAllWithIdOrTwice() {
        assertFalse(EndUnit.parseEndArgs(new String[]{"end", "3", "all"}).valid());
        assertFalse(EndUnit.parseEndArgs(new String[]{"end", "all", "3"}).valid());
        assertFalse(EndUnit.parseEndArgs(new String[]{"end", "all", "all"}).valid());
    }

    @Test
    void parseTptoTargetAcceptsBothWords() {
        assertEquals(Optional.of(WorldEngineTpto.TptoTarget.LOBBY),
                WorldEngineTpto.parseTptoTarget("lobbyworld"));
        assertEquals(Optional.of(WorldEngineTpto.TptoTarget.LOBBY),
                WorldEngineTpto.parseTptoTarget("LobbyWorld"));
        assertEquals(Optional.of(WorldEngineTpto.TptoTarget.GAME),
                WorldEngineTpto.parseTptoTarget("gameworld"));
    }

    @Test
    void parseTptoTargetRejectsAnythingElse() {
        assertEquals(Optional.empty(), WorldEngineTpto.parseTptoTarget("lobby"));
        assertEquals(Optional.empty(), WorldEngineTpto.parseTptoTarget(""));
    }

    @Test
    void supportShowsBlankThenParsedPrefix() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "prefix", "<gray>[T]</gray> ");
        MessageService messages = new MessageService();
        messages.reload(config);

        CommandSupport support = new CommandSupport(messages, mock(SoundService.class),
                new PendingConfirmations());
        List<Component> lines = HelpUnit.supportMessages(support);

        assertEquals(5, lines.size());
        assertEquals(Component.empty(), lines.get(0));
        assertEquals(messages.componentRaw(messages.prefix(), Map.of()), lines.get(1));
        assertEquals("[T] ", PlainTextComponentSerializer.plainText().serialize(lines.get(1)));
    }
}
