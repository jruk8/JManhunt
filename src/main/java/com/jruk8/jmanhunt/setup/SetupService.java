package com.jruk8.jmanhunt.setup;

import com.jruk8.jmanhunt.command.ManhuntCommand;
import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.world.LobbyWorld;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.ManhuntMessages;
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

    /** Chat plus sound half of setup confirmation. */
    public record Announcer(MessageService messages, ManhuntMessages manhunt, SoundService sounds) {
    }

    private final GameManager game;
    private final Announcer announcer;
    private final SettingFeedback feedback;
    private final Runnable observeWorldEngine;
    private final Runnable markSetupDone;

    public SetupService(GameManager game, Announcer announcer, SettingFeedback feedback,
            Runnable observeWorldEngine, Runnable markSetupDone) {
        this.game = game;
        this.announcer = announcer;
        this.feedback = feedback;
        this.observeWorldEngine = observeWorldEngine;
        this.markSetupDone = markSetupDone;
    }

    /** Runs the recommended setup for the clicking player. */
    public void recommendedSetup(Player clicker) {
        ConfigService.SetOutcome enabled = game.setSetting("world-engine.enabled", "true");
        if (!enabled.ok()) {
            feedback.failed(clicker, enabled);
            return;
        }
        observeWorldEngine.run();
        MessageService messages = announcer.messages();
        ManhuntMessages manhunt = announcer.manhunt();
        if (game.lobbyWorldNameClashes()) {
            messages.messageRaw(clicker, manhunt.getWorldengineTptoLobbyWorldClash());
            return;
        }
        String worldName = game.lobbyWorldName();
        if (!game.lobbyWorldExists()) {
            messages.messageRaw(clicker, manhunt.getWorldengineTptoCreating(),
                    Map.of("world", worldName));
        }
        Optional<LobbyWorld> ensured = game.ensureLobbyWorld();
        if (ensured.isEmpty()) {
            messages.messageRaw(clicker, manhunt.getWorldengineTptoFailed(),
                    Map.of("world", worldName));
            return;
        }
        World world = ensured.get().world();
        Location spawn = world.getSpawnLocation();
        boolean zeroSet = ensured.get().lobbyZeroSet() || game.ensureLobbyZero(spawn);
        if (zeroSet) {
            messages.messageRaw(clicker, manhunt.getWorldengineLobbyconfigSetlobbytpSuccess(),
                    Map.of("lobby", "0",
                            "location", ManhuntCommand.formatLocation(spawn)));
        }
        int teleported = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            target.teleport(spawn);
            teleported++;
        }
        messages.messageRaw(clicker, manhunt.getWorldengineTptoSuccess(),
                Map.of("count", String.valueOf(teleported), "world", worldName));
        markSetupDone.run();
        messages.messageRaw(clicker, manhunt.getSetupOneclickDone(),
                Map.of("count", String.valueOf(teleported)));
        announcer.sounds().playNeutralSound(clicker);
    }
}
