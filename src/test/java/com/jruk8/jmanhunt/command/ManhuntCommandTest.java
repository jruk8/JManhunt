package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.player.Role;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    void subcommandOptionsHideDevOnPurpose() {
        assertFalse(ManhuntCommand.subcommandOptions().contains("dev"));
    }

    @Test
    void nextFreeBoundsIdFindsFirstGap() {
        assertEquals(0, ManhuntCommand.nextFreeBoundsId(Set.of()));
        assertEquals(1, ManhuntCommand.nextFreeBoundsId(Set.of(0)));
        assertEquals(0, ManhuntCommand.nextFreeBoundsId(Set.of(1, 2)));
        assertEquals(2, ManhuntCommand.nextFreeBoundsId(Set.of(0, 1, 3)));
    }

    @Test
    void silentFlagAcceptsShortAndLongForms() {
        assertTrue(ManhuntCommand.isSilentFlag("-s"));
        assertTrue(ManhuntCommand.isSilentFlag("-silent"));
        assertTrue(ManhuntCommand.isSilentFlag("-S"));
        assertTrue(ManhuntCommand.isSilentFlag("-SILENT"));
    }

    @Test
    void silentFlagRejectsAnythingElse() {
        assertFalse(ManhuntCommand.isSilentFlag("silent"));
        assertFalse(ManhuntCommand.isSilentFlag("-f"));
        assertFalse(ManhuntCommand.isSilentFlag(""));
        assertFalse(ManhuntCommand.isSilentFlag("-silence"));
    }

    @Test
    void parseQuickStartArgsAcceptsForms() {
        assertEquals(new QuickStartArgs(null, true),
                ManhuntCommand.parseQuickStartArgs(new String[]{"qs"}));
        assertEquals(new QuickStartArgs(50, true),
                ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "50"}));
    }

    @Test
    void parseQuickStartArgsRejectsJunk() {
        assertFalse(ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "soon"}).valid());
        assertFalse(ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "50", "60"}).valid());
        assertFalse(ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "-f"}).valid());
        assertFalse(ManhuntCommand.parseQuickStartArgs(new String[]{"qs", "50", "-force"}).valid());
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
