package com.jruk8.jmanhunt.tutorial.jmanhunt;

import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.tutorial.ports.TutorialCommandRunner;
import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * Runs tutorial commands as the player, never the console: normal
 * permission checks apply and nothing is auto-opped. Nested command
 * neutrals stay silent so the tutorial plays the only sound per click.
 */
public final class JManhuntTutorialCommands implements TutorialCommandRunner {

    private final SoundService sounds;

    public JManhuntTutorialCommands(SoundService sounds) {
        this.sounds = sounds;
    }

    @Override
    public void runAsPlayer(Player player, String command) {
        if (command == null || command.isBlank()) {
            return;
        }
        String parsed = command.startsWith("/") ? command.substring(1) : command;
        UUID playerId = player.getUniqueId();
        sounds.suppressNeutral(playerId);
        try {
            player.performCommand(parsed);
        } finally {
            sounds.releaseNeutral(playerId);
        }
    }
}
