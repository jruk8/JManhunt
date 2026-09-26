package com.jruk8.jmanhunt.command;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SetPlayerFlagsTest {

    @Test
    void noFlagsLeavesArgsAlone() {
        ManhuntCommand.SetPlayerFlags flags = ManhuntCommand.parseSetPlayerFlags(
                new String[]{"setplayer", "@s", "hunter"});

        assertFalse(flags.force());
        assertFalse(flags.silent());
        assertEquals(3, flags.end());
    }

    @Test
    void forceAndSilentShorthands() {
        ManhuntCommand.SetPlayerFlags flags = ManhuntCommand.parseSetPlayerFlags(
                new String[]{"setplayer", "@s", "hunter", "-f", "-s"});

        assertTrue(flags.force());
        assertTrue(flags.silent());
        assertEquals(3, flags.end());
    }

    @Test
    void longFlagsInAnyOrder() {
        ManhuntCommand.SetPlayerFlags flags = ManhuntCommand.parseSetPlayerFlags(
                new String[]{"setplayer", "@s", "hunter", "-silent", "-force"});

        assertTrue(flags.force());
        assertTrue(flags.silent());
        assertEquals(3, flags.end());
    }

    @Test
    void nonFlagTailStopsParsing() {
        ManhuntCommand.SetPlayerFlags flags = ManhuntCommand.parseSetPlayerFlags(
                new String[]{"setplayer", "@s", "hunter", "-f", "bogus"});

        assertFalse(flags.force());
        assertFalse(flags.silent());
        assertEquals(5, flags.end());
    }
}
