package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.DevConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyPreset;
import com.jruk8.jmanhunt.lobby.world.LobbyWorld;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The worldengine tpto action: teleport players to the game or lobby
 * world. Owned by {@link WorldEngineUnit}; split out only to keep both
 * files under the checkstyle limit.
 */
public final class WorldEngineTpto {
    private final GameManager game;
    private final ConfigService config;
    private final DevConfig devConfig;
    private final ManhuntMessages texts;
    private final CommandMessages commandTexts;
    private final CommandSupport support;

    public WorldEngineTpto(GameManager game, ConfigService config, DevConfig devConfig,
            ManhuntMessages texts, CommandMessages commandTexts, CommandSupport support) {
        this.game = game;
        this.config = config;
        this.devConfig = devConfig;
        this.texts = texts;
        this.commandTexts = commandTexts;
        this.support = support;
    }

    /** tpto destination words. */
    public enum TptoTarget {
        LOBBY,
        GAME
    }

    public static Optional<TptoTarget> parseTptoTarget(String raw) {
        if (raw.equalsIgnoreCase("lobbyworld")) {
            return Optional.of(TptoTarget.LOBBY);
        }
        if (raw.equalsIgnoreCase("gameworld")) {
            return Optional.of(TptoTarget.GAME);
        }
        return Optional.empty();
    }

    /**
     * Executes tpto with the full args array. Validation stays inline:
     * arity, target word, preset word, and sender shape interleave.
     */
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 5) {
            return support.message(sender, texts.getWorldengineTptoUsage());
        }
        Optional<TptoTarget> target = parseTptoTarget(args[2]);
        if (target.isEmpty()) {
            return support.message(sender, texts.getWorldengineTptoUsage());
        }
        if (args.length == 5 && target.get() != TptoTarget.LOBBY) {
            return support.message(sender, texts.getWorldengineTptoUsage());
        }
        Optional<LobbyPreset> preset = Optional.empty();
        if (args.length == 5) {
            Optional<LobbyPreset> parsed = LobbyPreset.tryParse(args[4]);
            if (parsed.isEmpty()) {
                return worldEngineTptoInvalidPreset(sender);
            }
            preset = parsed;
        }
        List<Player> targets = new ArrayList<>();
        if (args.length == 3) {
            if (!(sender instanceof Player player)) {
                return support.message(sender, commandTexts.getPlayerOnly());
            }
            targets.add(player);
        } else {
            try {
                for (Entity entity : Bukkit.selectEntities(sender, args[3])) {
                    if (entity instanceof Player player) {
                        targets.add(player);
                    }
                }
            } catch (IllegalArgumentException exception) {
                return support.message(sender, texts.getWorldengineInvalidSelector());
            }
            if (targets.isEmpty()) {
                return support.message(sender, texts.getWorldengineNoTargets());
            }
        }
        if (target.get() == TptoTarget.GAME) {
            return worldEngineTptoGame(sender, targets);
        }
        return worldEngineTptoLobby(sender, targets, preset);
    }

    /** tpto completion legs: target word, selector, lobby preset. */
    public List<String> completeTptoTab(String[] args) {
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("tpto")) {
            return CommandSupport.partial(args[2], List.of("lobbyworld", "gameworld"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("tpto")) {
            return CommandSupport.partial(args[3], CommandSupport.selectorOptions());
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("tpto")
                && args[2].equalsIgnoreCase("lobbyworld")) {
            return CommandSupport.partial(args[4], lobbyPresetOptions());
        }
        return null;
    }

    /**
     * Invalid preset notice, naming the valid presets. Falls back to the
     * usage line when the key is missing (pre-existing messages.yml files
     * predate it and get no migration).
     */
    private boolean worldEngineTptoInvalidPreset(CommandSender sender) {
        if (texts.getWorldengineTptoInvalidPreset() == null) {
            return support.message(sender, texts.getWorldengineTptoUsage());
        }
        support.message(sender, texts.getWorldengineTptoInvalidPreset(),
                Map.of("presets", String.join(", ", lobbyPresetOptions())));
        return true;
    }

    /**
     * Preset names for completion and error text: the dev-data
     * lobby-presets keys that name a real preset, else every preset.
     */
    private List<String> lobbyPresetOptions() {
        List<String> options = new ArrayList<>();
        for (String key : devConfig.presetKeys()) {
            if (LobbyPreset.tryParse(key).isPresent() && !options.contains(key)) {
                options.add(key);
            }
        }
        if (options.isEmpty()) {
            for (LobbyPreset preset : LobbyPreset.values()) {
                options.add(preset.name());
            }
        }
        return options;
    }

    /** Teleports targets to the game world spawn. Never generates anything. */
    private boolean worldEngineTptoGame(CommandSender sender, List<Player> targets) {
        String worldName = config.getString("world-engine.world-name", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            support.message(sender, texts.getWorldengineTptoNoWorld(), Map.of("world", worldName));
            return true;
        }
        Location spawn = world.getSpawnLocation();
        for (Player target : targets) {
            target.teleport(spawn);
        }
        support.message(sender, texts.getWorldengineTptoSuccess(),
                Map.of("count", String.valueOf(targets.size()), "world", worldName));
        support.neutralSound(sender);
        return true;
    }

    /** Teleports targets to the lobby world, generating it once confirmed. */
    private boolean worldEngineTptoLobby(CommandSender sender, List<Player> targets, Optional<LobbyPreset> preset) {
        if (game.lobbyWorldNameClashes()) {
            return support.message(sender, texts.getWorldengineTptoLobbyWorldClash());
        }
        String worldName = game.lobbyWorldName();
        String senderKey = sender instanceof Player player ? player.getUniqueId().toString() : "console";
        if (!game.lobbyWorldExists()) {
            if (!game.confirmLobbyGeneration(senderKey)) {
                support.message(sender, texts.getWorldengineTptoConfirm(),
                        Map.of("world", worldName, "seconds", "10"));
                return true;
            }
            support.message(sender, texts.getWorldengineTptoCreating(), Map.of("world", worldName));
        }
        Optional<LobbyWorld> ensured = game.ensureLobbyWorld(preset);
        if (ensured.isEmpty()) {
            support.message(sender, texts.getWorldengineTptoFailed(), Map.of("world", worldName));
            return true;
        }
        World world = ensured.get().world();
        Location spawn = game.lowestLobbyTeleport().orElseGet(world::getSpawnLocation);
        spawn.setWorld(world);
        if (ensured.get().lobbyZeroSet()) {
            support.message(sender, texts.getWorldengineLobbyconfigSetlobbytpSuccess(), Map.of(
                    "lobby", "0", "location", WorldEngineUnit.formatLocation(spawn)));
        }
        for (Player target : targets) {
            target.teleport(spawn);
        }
        support.message(sender, texts.getWorldengineTptoSuccess(),
                Map.of("count", String.valueOf(targets.size()), "world", worldName));
        support.neutralSound(sender);
        return true;
    }
}
