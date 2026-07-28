package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class ManhuntCommand implements CommandExecutor, TabCompleter {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final CompassManager compass;

    public ManhuntCommand(JManhuntPlugin plugin, MessageService messages, PlayerStateStore playerStates,
                          GameManager game, CompassManager compass) {
        this.plugin = plugin; this.messages = messages; this.playerStates = playerStates;
        this.game = game; this.compass = compass;
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
            game.playNeutralSound(player);
            if (game.isActive() && role == Role.NONE) {
                playerStates.setSpeedrunnerAlive(player.getUniqueId(), false); player.setGameMode(GameMode.SPECTATOR);
                compass.removeCompasses(player);
            }
            if (game.isActive() && role == Role.HUNTER) compass.giveCompass(player);
        }
        message(sender, unchanged == 0 ? "manhunt.set-success" : "manhunt.set-success-unchanged",
                Map.of("count", String.valueOf(changed), "role", role.name(), "unchanged", String.valueOf(unchanged)));
        if (skipped > 0) message(sender, "manhunt.set-skipped", Map.of("count", String.valueOf(skipped)));
        if (sender instanceof Player player && !assigned.contains(player.getUniqueId())) game.playNeutralSound(player);
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
        boolean value = Boolean.parseBoolean(args[2]);
        game.setSetting(setting, value);
        message(sender, "manhunt.setting-updated", Map.of("setting", setting, "value", String.valueOf(value)));
        neutralSound(sender);
        return true;
    }

    private boolean reload(CommandSender sender) {
        YamlFileUpdater.update(plugin, "config.yml", "config-version", 1);
        YamlFileUpdater.update(plugin, "messages.yml", "messages-version", 1);
        plugin.reloadConfig();
        messages.reload(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "messages.yml")), plugin.getConfig().getString("text-format", "minimessage"));
        boolean result = message(sender, "manhunt.reload-success");
        neutralSound(sender);
        return result;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return partial(args[0], List.of("help", "status", "setplayer", "start", "end", "modifiers", "reload"));
        if (args.length == 2 && args[0].equalsIgnoreCase("modifiers"))
            return partial(args[1], new ArrayList<>(game.settingNames()));
        if (args.length == 3 && args[0].equalsIgnoreCase("modifiers"))
            return partial(args[2], List.of("true", "false"));
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
    private boolean message(CommandSender sender, String key) { sender.sendMessage(messages.component(key)); return true; }
    private void message(CommandSender sender, String key, Map<String, String> values) { sender.sendMessage(messages.component(key, values)); }
    private void neutralSound(CommandSender sender) {
        if (sender instanceof Player player) game.playNeutralSound(player);
    }
}
