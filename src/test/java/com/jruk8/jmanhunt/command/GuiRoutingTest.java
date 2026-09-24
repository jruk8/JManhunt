package com.jruk8.jmanhunt.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bare-command routing: only bare invocations from GUI-holding
 * players open the menu; everything else keeps status behavior.
 */
class GuiRoutingTest {

    @Test
    void bareCommandFromGuiHolderOpens() {
        assertTrue(ManhuntCommand.opensGui(new String[0], playerWithGui(true)));
    }

    @Test
    void bareCommandFromPlayerWithoutNodeKeepsStatus() {
        assertFalse(ManhuntCommand.opensGui(new String[0], playerWithGui(false)));
    }

    @Test
    void bareCommandFromConsoleKeepsStatus() {
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission("jmanhunt.gui")).thenReturn(true);

        assertFalse(ManhuntCommand.opensGui(new String[0], console));
    }

    @Test
    void commandWithArgumentsKeepsStatus() {
        assertFalse(ManhuntCommand.opensGui(new String[]{"all"}, playerWithGui(true)));
        assertFalse(ManhuntCommand.opensGui(new String[]{"status"}, playerWithGui(true)));
    }

    private static Player playerWithGui(boolean allowed) {
        Player player = mock(Player.class);
        when(player.hasPermission("jmanhunt.gui")).thenReturn(allowed);
        return player;
    }
}
