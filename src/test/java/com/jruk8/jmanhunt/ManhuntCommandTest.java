package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

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
    void parseEndArgsAcceptsBareEnd() {
        ManhuntCommand.EndArgs parsed = ManhuntCommand.parseEndArgs(new String[]{"end"});

        assertTrue(parsed.valid());
        assertEquals(Optional.empty(), parsed.instanceId());
        assertFalse(parsed.immediate());
    }

    @Test
    void parseEndArgsAcceptsIdAndFlagInEitherOrder() {
        ManhuntCommand.EndArgs idFirst = ManhuntCommand.parseEndArgs(new String[]{"end", "3", "-i"});
        ManhuntCommand.EndArgs flagFirst = ManhuntCommand.parseEndArgs(new String[]{"end", "-immediate", "3"});

        for (ManhuntCommand.EndArgs parsed : new ManhuntCommand.EndArgs[]{idFirst, flagFirst}) {
            assertTrue(parsed.valid());
            assertEquals(Optional.of("3"), parsed.instanceId());
            assertTrue(parsed.immediate());
        }
    }

    @Test
    void parseEndArgsRejectsTwoIds() {
        ManhuntCommand.EndArgs parsed = ManhuntCommand.parseEndArgs(new String[]{"end", "3", "4"});

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
