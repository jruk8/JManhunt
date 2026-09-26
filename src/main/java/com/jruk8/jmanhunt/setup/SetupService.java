package com.jruk8.jmanhunt.setup;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.command.ManhuntCommand;
import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.world.LobbyWorld;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Canonical recommended setup: the one-click panel button runs it,
 * and the tutorial graph points its recommended answers at the same
 * steps. Enables the world engine, ensures the lobby world plus
 * lobby 0, teleports every online player there, marks setup done,
 * and confirms in chat. Each step reuses the chat command messages;
 * failures abort early with the matching error.
 */
public final class SetupService {

    private final JManhuntPlugin plugin;
    private final GameManager game;
    private final MessageService messages;
    private final SoundService sounds;
    private final SettingFeedback feedback;

    public SetupService(JManhuntPlugin plugin, GameManager game, MessageService messages,
            SoundService sounds, SettingFeedback feedback) {
        this.plugin = plugin;
        this.game = game;
        this.messages = messages;
        this.sounds = sounds;
        this.feedback = feedback;
    }

    /** Runs the recommended setup for the clicking player. */
    public void recommendedSetup(Player clicker) {
        ConfigService.SetOutcome enabled = game.setSetting("world-engine.enabled", "true");
        if (!enabled.ok()) {
            feedback.failed(clicker, enabled);
            return;
        }
        plugin.observeWorldEngine();
        if (game.lobbyWorldNameClashes()) {
            messages.message(clicker, "manhunt.worldengine-tpto-lobby-world-clash");
            return;
        }
        String worldName = game.lobbyWorldName();
        if (!game.lobbyWorldExists()) {
            messages.message(clicker, "manhunt.worldengine-tpto-creating",
                    Map.of("world", worldName));
        }
        Optional<LobbyWorld> ensured = game.ensureLobbyWorld();
        if (ensured.isEmpty()) {
            messages.message(clicker, "manhunt.worldengine-tpto-failed",
                    Map.of("world", worldName));
            return;
        }
        World world = ensured.get().world();
        Location spawn = world.getSpawnLocation();
        boolean zeroSet = ensured.get().lobbyZeroSet() || game.ensureLobbyZero(spawn);
        if (zeroSet) {
            messages.message(clicker, "manhunt.worldengine-lobbyconfig-setlobbytp-success",
                    Map.of("lobby", "0",
                            "location", ManhuntCommand.formatLocation(spawn)));
        }
        int teleported = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            target.teleport(spawn);
            teleported++;
        }
        messages.message(clicker, "manhunt.worldengine-tpto-success",
                Map.of("count", String.valueOf(teleported), "world", worldName));
        plugin.markSetupDone();
        messages.message(clicker, "manhunt.setup-oneclick-done",
                Map.of("count", String.valueOf(teleported)));
        sounds.playNeutralSound(clicker);
    }
}
