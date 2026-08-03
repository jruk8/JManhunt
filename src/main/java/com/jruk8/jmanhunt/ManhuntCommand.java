package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class ManhuntCommand implements CommandExecutor, TabCompleter {
    private static final Set<String> RESTART_REQUIRED_SETTINGS = Set.of(
            "settings.world-engine.enabled"
    );
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final ConfigService config;
    private final SoundService sounds;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final CompassManager compass;
    private final LobbyTeleporter lobbyTeleporter;

    public ManhuntCommand(JManhuntPlugin plugin, MessageService messages, ConfigService config,
                          SoundService sounds, PlayerStateStore playerStates, GameManager game,
                          CompassManager compass, LobbyTeleporter lobbyTeleporter) {
        this.plugin = plugin; this.messages = messages; this.config = config; this.sounds = sounds;
        this.playerStates = playerStates; this.game = game; this.compass = compass;
        this.lobbyTeleporter = lobbyTeleporter;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (!sender.hasPermission("jmanhunt.command." + sub)) return message(sender, "command.no-permission");
        return switch (sub) {
            case "help" -> help(sender);
            case "status" -> status(sender);
            case "setplayer" -> setPlayer(sender, args);
            case "start" -> start(sender);
            case "end" -> end(sender);
            case "modifiers" -> modifiers(sender, args);
            case "worldengine" -> worldEngine(sender, args);
            case "reload" -> reload(sender);
            default -> message(sender, "command.invalid");
        };
    }

    private boolean help(CommandSender sender) {
        message(sender, "manhunt.help-header");
        String[][] lines = {{"/manhunt help", "show commands"}, {"/manhunt", "show match status"},
                {"/manhunt setplayer <selector> <hunter|speedrunner|none>", "assign roles"},
                {"/manhunt start", "start a match"}, {"/manhunt end", "end a match"},
                {"/manhunt modifiers <setting> <true|false>", "view or change a setting"},
                {"/manhunt worldengine", "set lobby or teleport players"},
                {"/manhunt reload", "reload files"}};
        for (String[] line : lines) message(sender, "manhunt.help-line", Map.of("command", line[0], "description", line[1]));
        neutralSound(sender);
        return true;
    }

    private boolean status(CommandSender sender) {
        message(sender, "manhunt.status-header", Map.of("status", game.isActive() ? "ACTIVE" : "INACTIVE"));
        sendRoleSection(sender, Role.SPEEDRUNNER, "manhunt.speedrunners-header");
        sendRoleSection(sender, Role.HUNTER, "manhunt.hunters-header");
        sendRoleSection(sender, Role.NONE, "manhunt.none-header");
        neutralSound(sender);
        return true;
    }

    private void sendRoleSection(CommandSender sender, Role role, String header) {
        List<Player> players = Bukkit.getOnlinePlayers().stream().filter(p -> playerStates.role(p) == role)
                .map(p -> (Player) p).sorted(Comparator.comparing(Player::getName)).toList();
        if (players.isEmpty()) return;
        message(sender, header); players.forEach(player -> message(sender, "manhunt.status-player",
                Map.of("player", player.getName())));
    }

    private boolean setPlayer(CommandSender sender, String[] args) {
        if (args.length < 3) return message(sender, "command.invalid");
        Role role;
        try { role = Role.valueOf(args[2].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return message(sender, "command.invalid"); }
        List<Entity> selected;
        try { selected = Bukkit.selectEntities(sender, args[1]); }
        catch (IllegalArgumentException exception) { return message(sender, "command.invalid"); }
        int changed = 0, unchanged = 0, skipped = 0;
        Set<java.util.UUID> assigned = new HashSet<>();
        for (Entity entity : selected) if (entity instanceof Player player) {
            if (playerStates.role(player) == role) { unchanged++; continue; }
            if (role == Role.HUNTER && !player.hasPermission("jmanhunt.hunter")
                    || role == Role.SPEEDRUNNER && !player.hasPermission("jmanhunt.speedrunner")) { skipped++; continue; }
            playerStates.setRole(player, role); changed++;
            assigned.add(player.getUniqueId());
            message(player, "manhunt.role-assigned", Map.of("role", role.name()));
            sounds.playNeutralSound(player);
            if (game.isActive() && role == Role.NONE) {
                playerStates.setSpeedrunnerAlive(player.getUniqueId(), false);
                playerStates.removeMatchParticipant(player.getUniqueId());
                if (plugin.getConfig().getBoolean("settings.set-none-gamemode-spectator.enabled", true)) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
                compass.removeCompasses(player);
            }
            if (game.isActive() && role != Role.NONE) {
                playerStates.markMatchParticipant(player.getUniqueId());
            }
            if (game.isActive() && role == Role.HUNTER) compass.giveCompass(player);
            if (role == Role.NONE) {
                Bukkit.broadcast(messages.component("manhunt.queue-left", Map.of("player", player.getName())));
            }
        }
        message(sender, unchanged == 0 ? "manhunt.set-success" : "manhunt.set-success-unchanged",
                Map.of("count", String.valueOf(changed), "role", role.name(), "unchanged", String.valueOf(unchanged)));
        if (skipped > 0) message(sender, "manhunt.set-skipped", Map.of("count", String.valueOf(skipped)));
        if (sender instanceof Player player && !assigned.contains(player.getUniqueId())) sounds.playNeutralSound(player);
        if (changed > 0) game.updateAutostartState();
        return true;
    }

    private boolean start(CommandSender sender) {
        if (game.isActive()) return message(sender, "manhunt.already-active");
        if (!game.start()) return message(sender, "manhunt.start-invalid");
        return true;
    }

    private boolean end(CommandSender sender) {
        if (!game.isActive()) return message(sender, "manhunt.not-active");
        game.end(); return true;
    }

    private boolean modifiers(CommandSender sender, String[] args) {
        if (args.length == 1) {
            for (String setting : game.settingNames()) {
                sender.sendMessage(messages.component("manhunt.setting-entry",
                        Map.of("setting", setting, "value", String.valueOf(game.getSetting(setting)))));
            }
            return true;
        }
        String setting = args[1].toLowerCase(Locale.ROOT);
        if (!game.settingNames().contains(setting)) return message(sender, "manhunt.setting-invalid");
        if (args.length < 3) {
            message(sender, "manhunt.setting-status",
                    Map.of("setting", setting, "value", String.valueOf(game.getSetting(setting))));
            return true;
        }
        if (!args[2].equalsIgnoreCase("true") && !args[2].equalsIgnoreCase("false")) {
            return message(sender, "manhunt.setting-invalid-value");
        }
        boolean oldValue = game.getSetting(setting);
        boolean value = Boolean.parseBoolean(args[2]);
        game.setSetting(setting, value);
        message(sender, "manhunt.setting-updated", Map.of("setting", setting, "value", String.valueOf(value), "old-value", String.valueOf(oldValue)));
        if (!oldValue && RESTART_REQUIRED_SETTINGS.contains(setting)) {
            message(sender, "manhunt.setting-restart-required");
        }
        neutralSound(sender);
        return true;
    }

    private boolean worldEngine(CommandSender sender, String[] args) {
        if (!config.getBoolean("settings.world-engine.enabled", false)) {
            return message(sender, "manhunt.worldengine-disabled");
        }
        if (args.length == 1 || args[1].isBlank()) {
            return message(sender, "manhunt.worldengine-usage");
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("setlobby")) {
            return worldEngineSetLobby(sender, args);
        }
        if (sub.equals("lobby")) {
            return worldEngineLobby(sender, args);
        }
        return message(sender, "command.invalid");
    }

    private boolean worldEngineSetLobby(CommandSender sender, String[] args) {
        if (args.length > 3) {
            return message(sender, "command.invalid");
        }

        Location location;
        if (args.length == 2 || args[2].isBlank()) {
            if (!(sender instanceof Player player)) {
                return message(sender, "command.player-only");
            }
            location = player.getLocation();
        } else {
            location = parseLobbyLocation(sender, args[2]);
            if (location == null) {
                return message(sender, "manhunt.worldengine-invalid-location");
            }
        }

        saveLobbyLocation(location);

        // Set the world spawn to the lobby location so that new spawns and
        // deaths without a personal respawn point go to the lobby.
        if (location.getWorld() != null) {
            location.getWorld().setSpawnLocation(location);
        }

        // Update respawn points for players who are not in an active match.
        // When no match is running, all online players are updated; when a
        // match is running, only non-participants are moved so that active
        // hunters/speedrunners keep their in-match respawn points.
        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!game.isActive() || !playerStates.isMatchParticipant(online.getUniqueId())) {
                targets.add(online);
            }
        }
        lobbyTeleporter.setSpawnToLobby(targets);

        message(sender, "manhunt.worldengine-setlobby-success",
                Map.of("location", formatLocation(location)));
        neutralSound(sender);
        return true;
    }

    private boolean worldEngineLobby(CommandSender sender, String[] args) {
        if (args.length > 3) {
            return message(sender, "command.invalid");
        }

        List<Player> targets = new ArrayList<>();
        if (args.length == 2 || args[2].isBlank()) {
            if (!(sender instanceof Player player)) {
                return message(sender, "command.player-only");
            }
            targets.add(player);
        } else {
            try {
                for (Entity entity : Bukkit.selectEntities(sender, args[2])) {
                    if (entity instanceof Player player) {
                        targets.add(player);
                    }
                }
            } catch (IllegalArgumentException exception) {
                return message(sender, "manhunt.worldengine-invalid-selector");
            }
            if (targets.isEmpty()) {
                return message(sender, "manhunt.worldengine-no-targets");
            }
        }

        boolean teleportResult = lobbyTeleporter.teleportToLobby(targets);
        if (!teleportResult) {
            return message(sender, "manhunt.worldengine-teleport-failure");
        }
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
        message(sender, "manhunt.worldengine-teleport-success",
                Map.of("target", targets.size() == 1 ? targets.get(0).getName() : String.valueOf(targets.size())));
        return true;
    }

    private boolean reload(CommandSender sender) {
        plugin.reload();
        boolean result = message(sender, "manhunt.reload-success");
        neutralSound(sender);
        return result;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return partial(args[0], List.of("help", "status", "setplayer", "start", "end", "modifiers", "worldengine", "reload"));
        if (args.length == 2 && args[0].equalsIgnoreCase("modifiers"))
            return partial(args[1], new ArrayList<>(game.settingNames()));
        if (args.length == 3 && args[0].equalsIgnoreCase("modifiers"))
            return partial(args[2], List.of("true", "false"));
        if (args.length == 2 && args[0].equalsIgnoreCase("worldengine"))
            return partial(args[1], List.of("setlobby", "lobby"));
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("lobby")) {
            List<String> selectors = new ArrayList<>(List.of("@a", "@r", "@s", "@p"));
            Bukkit.getOnlinePlayers().forEach(player -> selectors.add(player.getName()));
            return partial(args[2], selectors);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("setlobby")) {
            if (sender instanceof Player player) {
                return partial(args[2], List.of(formatLocation(player.getLocation())));
            }
            return List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setplayer")) {
            List<String> selectors = new ArrayList<>(List.of("@a", "@r", "@s", "@p"));
            Bukkit.getOnlinePlayers().forEach(player -> selectors.add(player.getName()));
            if (args[1].startsWith("@a[")) return partial(args[1], List.of("@a[distance=", "@a[limit=", "@a[name=", "@a[gamemode="));
            return partial(args[1], selectors);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setplayer")) return partial(args[2], List.of("hunter", "speedrunner", "none"));
        return List.of();
    }

    private List<String> partial(String value, List<String> options) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private Location parseLobbyLocation(CommandSender sender, String value) {
        String[] parts = value.split(",");
        if (parts.length < 3 || parts.length > 5) {
            return null;
        }

        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());
            float yaw = 0.0f;
            float pitch = 0.0f;
            if (parts.length >= 4) {
                yaw = Float.parseFloat(parts[3].trim());
            } else if (sender instanceof Player player) {
                yaw = player.getLocation().getYaw();
                pitch = player.getLocation().getPitch();
            }
            if (parts.length == 5) {
                pitch = Float.parseFloat(parts[4].trim());
            }
            World world = sender instanceof Player player ? player.getWorld()
                    : Bukkit.getWorld(plugin.getConfig().getString("settings.world-engine.world-name", "world"));
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void saveLobbyLocation(Location location) {
        String worldName = location.getWorld() == null
                ? plugin.getConfig().getString("settings.world-engine.world-name", "world")
                : location.getWorld().getName();
        plugin.getConfig().set("settings.world-engine.lobby-location.world", worldName);
        plugin.getConfig().set("settings.world-engine.lobby-location.x", location.getX());
        plugin.getConfig().set("settings.world-engine.lobby-location.y", location.getY());
        plugin.getConfig().set("settings.world-engine.lobby-location.z", location.getZ());
        plugin.getConfig().set("settings.world-engine.lobby-location.yaw", location.getYaw());
        plugin.getConfig().set("settings.world-engine.lobby-location.pitch", location.getPitch());
        plugin.saveConfig();
    }

    private String formatLocation(Location location) {
        String worldName = location.getWorld() == null ? "null" : location.getWorld().getName();
        String xFormatted = String.format("%.2f", location.getX());
        String yFormatted = String.format("%.2f", location.getY());
        String zFormatted = String.format("%.2f", location.getZ());
        String yawFormatted = String.format("%.2f", location.getYaw());
        String pitchFormatted = String.format("%.2f", location.getPitch());
        return worldName + " @ " + xFormatted + ", " + yFormatted + ", " + zFormatted
                + ", " + yawFormatted + ", " + pitchFormatted;
    }

    private boolean message(CommandSender sender, String key) { sender.sendMessage(messages.component(key)); return true; }
    private void message(CommandSender sender, String key, Map<String, String> values) { sender.sendMessage(messages.component(key, values)); }
    private void neutralSound(CommandSender sender) {
        if (sender instanceof Player player) sounds.playNeutralSound(player);
    }
}
