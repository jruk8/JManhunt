package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit-testable ManhuntCommand argument helpers. */
class ManhuntCommandTest {

    @Test
    void forceFlagAcceptsShortAndLongForms() {
        assertTrue(ManhuntCommand.isForceFlag("-f"));
        assertTrue(ManhuntCommand.isForceFlag("-force"));
        assertTrue(ManhuntCommand.isForceFlag("-F"));
        assertTrue(ManhuntCommand.isForceFlag("-FORCE"));
    }

    @Test
    void forceFlagRejectsAnythingElse() {
        assertFalse(ManhuntCommand.isForceFlag("force"));
        assertFalse(ManhuntCommand.isForceFlag("-i"));
        assertFalse(ManhuntCommand.isForceFlag(""));
        assertFalse(ManhuntCommand.isForceFlag("-forced"));
    }

    @Test
    void immediateFlagAcceptsShortAndLongForms() {
        assertTrue(ManhuntCommand.isImmediateFlag("-i"));
        assertTrue(ManhuntCommand.isImmediateFlag("-immediate"));
        assertTrue(ManhuntCommand.isImmediateFlag("-I"));
        assertTrue(ManhuntCommand.isImmediateFlag("-IMMEDIATE"));
    }

    @Test
    void immediateFlagRejectsAnythingElse() {
        assertFalse(ManhuntCommand.isImmediateFlag("-f"));
        assertFalse(ManhuntCommand.isImmediateFlag("3"));
        assertFalse(ManhuntCommand.isImmediateFlag(""));
    }

    @Test
    void noTeleportFlagAcceptsOnlyLongForm() {
        assertTrue(ManhuntCommand.isNoTeleportFlag("-notp"));
        assertTrue(ManhuntCommand.isNoTeleportFlag("-NOTP"));
        assertFalse(ManhuntCommand.isNoTeleportFlag("-n"));
        assertFalse(ManhuntCommand.isNoTeleportFlag("notp"));
        assertFalse(ManhuntCommand.isNoTeleportFlag(""));
    }

    @Test
    void subcommandOptionsAreReversedForDisplay() {
        assertEquals(List.of("challenges", "help", "reload", "worldengine", "config",
                "configuration", "debug", "lobby", "qs", "quickstart", "game", "end", "start",
                "setplayer", "status"), ManhuntCommand.subcommandOptions());
    }

    @Test
    void parseQuickStartArgsAcceptsForms() {
        assertEquals(new QuickStartArgs(null, false, true),
                ManhuntCommand.parseQuickStartArgs(new String[]{"qs"}));
        assertEquals(new QuickStartArgs(50, false, true),
                ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "50"}));
        assertEquals(new QuickStartArgs(null, true, true),
                ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "-f"}));
        assertEquals(new QuickStartArgs(50, true, true),
                ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "50", "-force"}));
        assertEquals(new QuickStartArgs(50, true, true),
                ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "-f", "50"}));
    }

    @Test
    void parseQuickStartArgsRejectsJunk() {
        assertFalse(ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "soon"}).valid());
        assertFalse(ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "50", "60"}).valid());
        assertFalse(ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "-f", "-force"}).valid());
        assertFalse(ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "50", "-f", "x"}).valid());
    }

    @Test
    void afkGuardTripsOnlyForWakingOthers() {
        assertTrue(ManhuntCommand.needsAfkGuard(Role.AFK, Role.HUNTER, false));
        assertTrue(ManhuntCommand.needsAfkGuard(Role.AFK, Role.NONE, false));
        assertFalse(ManhuntCommand.needsAfkGuard(Role.AFK, Role.HUNTER, true));
        assertFalse(ManhuntCommand.needsAfkGuard(Role.AFK, Role.AFK, false));
        assertFalse(ManhuntCommand.needsAfkGuard(Role.NONE, Role.HUNTER, false));
        assertFalse(ManhuntCommand.needsAfkGuard(Role.HUNTER, Role.AFK, false));
    }

    @Test
    void parseEndArgsAcceptsBareEnd() {
        EndArgs parsed = ManhuntCommand.parseEndArgs(new String[]{"end"});

        assertTrue(parsed.valid());
        assertEquals(Optional.empty(), parsed.instanceId());
        assertFalse(parsed.immediate());
    }

    @Test
    void parseEndArgsAcceptsIdAndFlagInEitherOrder() {
        EndArgs idFirst = ManhuntCommand.parseEndArgs(new String[]{"end", "3", "-i"});
        EndArgs flagFirst = ManhuntCommand.parseEndArgs(new String[]{"end", "-immediate", "3"});

        for (EndArgs parsed : new EndArgs[]{idFirst, flagFirst}) {
            assertTrue(parsed.valid());
            assertEquals(Optional.of("3"), parsed.instanceId());
            assertTrue(parsed.immediate());
        }
    }

    @Test
    void parseEndArgsRejectsTwoIds() {
        EndArgs parsed = ManhuntCommand.parseEndArgs(new String[]{"end", "3", "4"});

        assertFalse(parsed.valid());
    }

    @Test
    void parseTptoTargetAcceptsBothWords() {
        assertEquals(Optional.of(ManhuntCommand.TptoTarget.LOBBY),
                ManhuntCommand.parseTptoTarget("lobbyworld"));
        assertEquals(Optional.of(ManhuntCommand.TptoTarget.LOBBY),
                ManhuntCommand.parseTptoTarget("LobbyWorld"));
        assertEquals(Optional.of(ManhuntCommand.TptoTarget.GAME),
                ManhuntCommand.parseTptoTarget("gameworld"));
    }

    @Test
    void parseTptoTargetRejectsAnythingElse() {
        assertEquals(Optional.empty(), ManhuntCommand.parseTptoTarget("lobby"));
        assertEquals(Optional.empty(), ManhuntCommand.parseTptoTarget(""));
    }
}
