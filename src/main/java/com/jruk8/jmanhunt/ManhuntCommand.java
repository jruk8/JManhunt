package com.jruk8.jmanhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
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
            "settings.world-engine.enabled",
            "settings.world-engine.nether-structures.enabled",
            "settings.world-engine.overworld-structures.enabled"
    );

    /**
     * Readonly announcement printed by the challenges subcommand. This text is
     * intentionally not loaded from messages.yml so it cannot be edited or
     * removed by server owners; change it here instead. It is always parsed as
     * MiniMessage; the {link} token becomes a clickable link to
     * {@link #CHALLENGES_URL} and {status} becomes the companion plugin status.
     */
    static final String CHALLENGES_MESSAGE = """
            
            <gray>[<gradient:#5e42f4:#b742f4>JMHChallenges</gradient>]</gray>
            <gold>JManhunt</gold> is a free plugin for configurable manhunts. For lucky blocks and other fun challenges, you can find the optional addon {link}.
            
            <gray> » Challenges status: [{status}<gray>]</gray>
            
            <dark_gray>Looking for modifiers instead? Try <white>/mh modifiers <setting> <value></white>.</dark_gray>
            """;
    static final String CHALLENGES_URL = "https://builtbybit.com/resources/jmanhunt-challenges.121574/";
    private static final String CHALLENGES_LINK_TOKEN = "{link}";
    private static final String CHALLENGES_LINK_TEXT = "here";
    private static final String CHALLENGES_STATUS_TOKEN = "{status}";
    private static final String CHALLENGES_STATUS_ACTIVE = "ACTIVE";
    private static final String CHALLENGES_STATUS_INACTIVE = "INACTIVE";
    // Candidate plugin.yml names of the JManhunt-Challenges companion plugin;
    // the status line shows ACTIVE when any of these is loaded and enabled.
    private static final Set<String> CHALLENGES_PLUGIN_NAMES =
            Set.of("JManhunt-Challenges", "JManhuntChallenges", "JMHChallenges");
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
            case "challenges" -> challenges(sender);
            case "setplayer" -> setPlayer(sender, args);
            case "start" -> start(sender);
            case "end" -> end(sender);
            case "modifiers" -> modifiers(sender, args);
            case "worldengine" -> worldEngine(sender, args);
            case "quickstart", "qs" -> quickStart(sender, args);
            case "reload" -> reload(sender);
            default -> message(sender, "command.invalid");
        };
    }

    private boolean help(CommandSender sender) {
        message(sender, "manhunt.help-header");
        String[][] lines = {{"/manhunt help", "show commands"}, {"/manhunt", "show match status"},
                {"/manhunt setplayer <selector> <hunter|speedrunner|afk|none>", "assign roles"},
                {"/manhunt start", "start a match"}, {"/manhunt end", "end a match"},
                {"/manhunt quickstart [percentage]", "assign teams and start immediately"},
                {"/manhunt modifiers <setting> <value>", "view or change a setting"},
                {"/manhunt worldengine", "set lobby or teleport players"},
                {"/manhunt challenges", "show Challenges addon info"},
                {"/manhunt reload", "reload files"}};
        for (String[] line : lines) message(sender, "manhunt.help-line", Map.of("command", line[0], "description", line[1]));
        neutralSound(sender);
        return true;
    }

    private boolean status(CommandSender sender) {
        message(sender, "manhunt.status-header", Map.of("status", game.isActive() ? "ACTIVE" : "INACTIVE"));
        sendRoleSection(sender, Role.SPEEDRUNNER, "manhunt.speedrunners-header");
        sendRoleSection(sender, Role.HUNTER, "manhunt.hunters-header");
        sendRoleSection(sender, Role.AFK, "manhunt.afk-header");
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

    private boolean challenges(CommandSender sender) {
        for (Component line : challengesComponents(messages, isCompanionEnabled())) {
            sender.sendMessage(line);
        }
        neutralSound(sender);
        return true;
    }

    /**
     * The announcement lines from {@link #CHALLENGES_MESSAGE}, with the {link}
     * token turned into a clickable link and the {status} token turned into the
     * companion plugin status.
     */
    static List<Component> challengesComponents(MessageService messages, boolean companionEnabled) {
        List<Component> lines = new ArrayList<>();
        for (String line : CHALLENGES_MESSAGE.split("\n")) {
            lines.add(renderChallengesLine(messages, line, companionEnabled));
        }
        return lines;
    }

    /**
     * Renders one line of {@link #CHALLENGES_MESSAGE}, replacing every
     * braced {token} with its dynamic component and parsing the rest of the
     * text as MiniMessage.
     */
    private static Component renderChallengesLine(MessageService messages, String line, boolean companionEnabled) {
        Component rendered = Component.empty();
        int cursor = 0;
        while (cursor < line.length()) {
            int open = line.indexOf('{', cursor);
            int close = open < 0 ? -1 : line.indexOf('}', open);
            if (open < 0 || close < 0) {
                rendered = rendered.append(messages.miniMessage(line.substring(cursor)));
                break;
            }
            if (open > cursor) {
                rendered = rendered.append(messages.miniMessage(line.substring(cursor, open)));
            }
            rendered = rendered.append(tokenComponent(messages, line.substring(open, close + 1), companionEnabled));
            cursor = close + 1;
        }
        return rendered;
    }

    private static Component tokenComponent(MessageService messages, String token, boolean companionEnabled) {
        return switch (token) {
            case CHALLENGES_LINK_TOKEN -> challengesLink();
            case CHALLENGES_STATUS_TOKEN -> challengesStatus(companionEnabled);
            default -> messages.miniMessage(token);
        };
    }

    private static Component challengesLink() {
        return Component.text(CHALLENGES_LINK_TEXT, NamedTextColor.GOLD, TextDecoration.UNDERLINED)
                .clickEvent(ClickEvent.openUrl(CHALLENGES_URL))
                .hoverEvent(HoverEvent.showText(Component.text(CHALLENGES_URL, NamedTextColor.GRAY)));
    }

    private static Component challengesStatus(boolean companionEnabled) {
        String status = companionEnabled ? CHALLENGES_STATUS_ACTIVE : CHALLENGES_STATUS_INACTIVE;
        NamedTextColor color = companionEnabled ? NamedTextColor.GREEN : NamedTextColor.RED;
        return Component.text(status, color);
    }

    private boolean isCompanionEnabled() {
        for (String name : CHALLENGES_PLUGIN_NAMES) {
            if (Bukkit.getPluginManager().isPluginEnabled(name)) {
                return true;
            }
        }
        return false;
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
            if (game.isActive() && !role.isParticipant()) {
                playerStates.setSpeedrunnerAlive(player.getUniqueId(), false);
                playerStates.removeMatchParticipant(player.getUniqueId());
                if (plugin.getConfig().getBoolean("settings.set-none-gamemode-spectator.enabled", true)) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
                compass.removeCompasses(player);
            }
            if (game.isActive() && role.isParticipant()) {
                playerStates.markMatchParticipant(player.getUniqueId());
            }
            if (game.isActive() && role.isParticipant()) compass.giveCompass(player);
            if (!role.isParticipant()) {
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
                        Map.of("setting", setting, "value", String.valueOf(game.getSettingValue(setting)))));
            }
            return true;
        }
        String setting = resolveSetting(args[1]);
        if (setting == null) return message(sender, "manhunt.setting-invalid");
        Object oldValue = game.getSettingValue(setting);
        if (args.length < 3) {
            message(sender, "manhunt.setting-status",
                    Map.of("setting", setting, "value", String.valueOf(oldValue)));
            return true;
        }
        boolean isBoolean = oldValue instanceof Boolean;
        if (isBoolean && !args[2].equalsIgnoreCase("true") && !args[2].equalsIgnoreCase("false")) {
            return message(sender, "manhunt.setting-invalid-value");
        }
        if (!game.setSetting(setting, args[2])) {
            return message(sender, "manhunt.setting-invalid-number");
        }
        Object newValue = game.getSettingValue(setting);
        message(sender, "manhunt.setting-updated", Map.of("setting", setting,
                "value", String.valueOf(newValue), "old-value", String.valueOf(oldValue)));
        if (RESTART_REQUIRED_SETTINGS.contains(setting)) {
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
        if (args.length == 1) return partial(args[0], List.of("help", "status", "challenges", "setplayer",
                "start", "end", "modifiers", "worldengine", "quickstart", "qs", "reload"));
        if (args.length == 2 && args[0].equalsIgnoreCase("modifiers"))
            return partial(args[1], new ArrayList<>(game.settingNames()));
        if (args.length == 3 && args[0].equalsIgnoreCase("modifiers")) {
            String setting = resolveSetting(args[1]);
            if (setting == null) return List.of();
            Object current = game.getSettingValue(setting);
            if (current instanceof Boolean) {
                return partial(args[2], List.of("true", "false"));
            }
            // Tab-complete the default value from the bundled default config.
            Object defaultValue = defaultConfigValue(setting);
            if (defaultValue != null) {
                return partial(args[2], List.of(String.valueOf(defaultValue)));
            }
            return List.of();
        }
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
        if (args.length == 3 && args[0].equalsIgnoreCase("setplayer")) return partial(args[2], List.of("hunter", "speedrunner", "afk", "none"));
        return List.of();
    }

    /**
     * Resolves a case-insensitive setting name to its real config path
     * (e.g. "settings.win-conditions.survivetime.time" matches
     * "settings.win-conditions.surviveTime.time"). Returns null if no
     * setting matches.
     */
    private String resolveSetting(String raw) {
        String normalized = raw.toLowerCase(Locale.ROOT);
        for (String name : game.settingNames()) {
            if (name.toLowerCase(Locale.ROOT).equals(normalized)) return name;
        }
        return null;
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

    /**
     * Loads the default value for a setting from the bundled default
     * config.yml so that tab completion can suggest the default entry.
     */
    private Object defaultConfigValue(String setting) {
        try (var stream = plugin.getResource("config.yml")) {
            if (stream == null) return null;
            return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream,
                    java.nio.charset.StandardCharsets.UTF_8)).get(setting);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private boolean quickStart(CommandSender sender, String[] args) {
        if (game.isActive()) return message(sender, "manhunt.already-active");
        int percent = -1;
        if (args.length >= 2) {
            try {
                percent = Integer.parseInt(args[1]);
                if (percent < 0 || percent > 100) return message(sender, "manhunt.quickstart-invalid-percent");
            } catch (NumberFormatException e) {
                return message(sender, "manhunt.quickstart-invalid-percent");
            }
        }
        if (!game.quickStart(percent)) return message(sender, "manhunt.quickstart-failed");
        return true;
    }

    private boolean message(CommandSender sender, String key) { sender.sendMessage(messages.component(key)); return true; }
    private void message(CommandSender sender, String key, Map<String, String> values) { sender.sendMessage(messages.component(key, values)); }
    private void neutralSound(CommandSender sender) {
        if (sender instanceof Player player) sounds.playNeutralSound(player);
    }
}
