package com.jruk8.jmanhunt.tutorial.jmanhunt;

import com.jruk8.jmanhunt.tutorial.ports.TutorialCommandRunner;
import org.bukkit.entity.Player;

/**
 * Runs tutorial commands as the player, never the console: normal
 * permission checks apply and nothing is auto-opped.
 */
public final class JManhuntTutorialCommands implements TutorialCommandRunner {

    @Override
    public void runAsPlayer(Player player, String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        String parsed = command.startsWith("/") ? command.substring(1) : command;
        player.performCommand(parsed);
    }
}
