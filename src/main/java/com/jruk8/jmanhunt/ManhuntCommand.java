package com.jruk8.jmanhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public final class ManhuntCommand implements CommandExecutor, TabCompleter {
    private static final Set<String> RESTART_REQUIRED_SETTINGS = Set.of(
            "world-engine.enabled",
            "settings.game-boosts.nether-structures.enabled",
            "settings.game-boosts.overworld-structures.enabled",
            "database.enabled",
            "database.type",
            "database.sqlite.file",
            "database.postgresql.host",
            "database.postgresql.port",
            "database.postgresql.database",
            "database.postgresql.username",
            "database.postgresql.password",
            "database.postgresql.ssl",
            "database.pool-size"
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
            
            <gray>Looking for settings instead? Try <white>/mh configuration <category> <key> <value></white>.</gray>
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
        // The config alias shares the configuration permission node; every
        // other subcommand keeps its own jmanhunt.command.<sub> node.
        String node = sub.equals("config") ? "configuration" : sub;
        if (!sender.hasPermission("jmanhunt.command." + node)) return message(sender, "command.no-permission");
        return switch (sub) {
            case "help" -> help(sender);
            case "status" -> status(sender);
            case "challenges" -> challenges(sender);
            case "setplayer" -> setPlayer(sender, args);
            case "start" -> start(sender);
            case "end" -> end(sender, args);
            case "configuration", "config" -> configuration(sender, args);
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
                {"/manhunt start", "start a match"}, {"/manhunt end [-i|-immediate]", "end a match"},
                {"/manhunt quickstart [percentage]", "assign teams and start immediately"},
                {"/manhunt configuration <category> <key...> <value>", "view or change a setting"},
                {"/manhunt worldengine", "set lobby or teleport players"},
                {"/manhunt challenges", "show Challenges addon info"},
                {"/manhunt reload", "reload files"}};
        for (String[] line : lines) message(sender, "manhunt.help-line", Map.of("command", line[0], "description", line[1]));
        // Clickable links need MiniMessage parsing regardless of text-format,
        // so this footer stays hardcoded instead of living in messages.yml.
        sender.sendMessage(messages.miniMessage(
                "\n<green>Still need help? Join our <gold><click:open_url:'https://discord.gg/hkWmCVmWDC'>"
                        + "<underlined>Discord server</underlined></click></gold>!</green>"));
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
                if (plugin.getConfig().getBoolean("settings.roles.none-gamemode-spectator.enabled", true)) {
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

    private boolean end(CommandSender sender, String[] args) {
        if (!game.isActive()) return message(sender, "manhunt.not-active");
        boolean immediate = false;
        if (args.length >= 2) {
            if (args.length > 2 || (!args[1].equalsIgnoreCase("-i") && !args[1].equalsIgnoreCase("-immediate"))) {
                return message(sender, "command.invalid");
            }
            immediate = true;
        }
        game.end(immediate); return true;
    }

    /**
     * Views or changes configuration by category: /manhunt configuration
     * &lt;category&gt; &lt;path...&gt; [value]. Every argument drills one level
     * deeper and tab completion only suggests the children of the current
     * level. A path resolving to a section lists its settings; a path
     * resolving to an editable leaf views it, or sets it when a value follows.
     */
    private boolean configuration(CommandSender sender, String[] args) {
        List<String> segments = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            segments.add(args[i]);
        }
        if (segments.isEmpty()) {
            Map<String, String> categories = new LinkedHashMap<>();
            for (String category : drillCategories(plugin.getConfig())) {
                categories.put(category, "");
            }
            listEntries(sender, "config", categories);
            neutralSound(sender);
            return true;
        }
        DrillResolve resolved = resolveDrill(plugin.getConfig(), game.settingNames(), segments);
        if (resolved == null) {
            return message(sender, "manhunt.setting-invalid");
        }
        if (resolved.leaf()) {
            return showOrUpdateSetting(sender, resolved.path(), resolved.remainder());
        }
        if (!resolved.section() || !resolved.remainder().isEmpty()) {
            return message(sender, "command.invalid");
        }
        Map<String, String> entries = new LinkedHashMap<>();
        for (String setting : game.settingNames()) {
            if (isUnder(setting, resolved.path())) {
                entries.put(setting, ": " + game.getSettingValue(setting));
            }
        }
        listEntries(sender, resolved.path(), entries);
        neutralSound(sender);
        return true;
    }

    /**
     * Lists entries dir-style under one header, so browsing never spams one
     * prefixed line per entry.
     */
    private void listEntries(CommandSender sender, String key, Map<String, String> entries) {
        String template = messages.string("manhunt.configuration-entry",
                "\n<green>» <white>{key}</white><gray>{suffix}</gray></white>");
        message(sender, "manhunt.configuration-list",
                Map.of("key", key, "entries", renderEntries(entries, template)));
    }

    static String renderEntries(Map<String, String> entries, String entryTemplate) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            out.append(entryTemplate.replace("{key}", entry.getKey()).replace("{suffix}", entry.getValue()));
        }
        return out.toString();
    }

    private boolean showOrUpdateSetting(CommandSender sender, String setting, List<String> values) {
        Object oldValue = game.getSettingValue(setting);
        if (values.isEmpty()) {
            message(sender, "manhunt.setting-status",
                    Map.of("setting", setting, "value", String.valueOf(oldValue)));
            neutralSound(sender);
            return true;
        }
        if (values.size() > 1) {
            return message(sender, "command.invalid");
        }
        String raw = values.get(0);
        boolean isBoolean = oldValue instanceof Boolean;
        if (isBoolean && !raw.equalsIgnoreCase("true") && !raw.equalsIgnoreCase("false")) {
            return message(sender, "manhunt.setting-invalid-value");
        }
        if (!game.setSetting(setting, raw)) {
            return message(sender, "manhunt.setting-invalid-number");
        }
        Object newValue = game.getSettingValue(setting);
        message(sender, "manhunt.setting-updated", Map.of("setting", setting,
                "value", String.valueOf(newValue), "old-value", String.valueOf(oldValue)));
        if (RESTART_REQUIRED_SETTINGS.contains(setting)) {
            message(sender, "manhunt.setting-restart-required");
        }
        announceSettingChange(sender, setting, newValue);
        neutralSound(sender);
        return true;
    }

    /**
     * Broadcasts a config change to every online player except the one who
     * made it, when settings.announce-config-changes is enabled. Changes made
     * from the console reach every online player.
     */
    private void announceSettingChange(CommandSender sender, String setting, Object newValue) {
        if (!plugin.getConfig().getBoolean("settings.announce-config-changes", false)) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player changer && online.getUniqueId().equals(changer.getUniqueId())) {
                continue;
            }
            online.sendMessage(messages.component("manhunt.setting-change-announced",
                    Map.of("player", sender.getName(), "key", setting, "value", String.valueOf(newValue))));
        }
    }

    /**
     * Result of resolving drill-down path segments against the live config:
     * the canonical path, whether it is an editable leaf or a section, and
     * any segments left over after the longest resolvable prefix.
     */
    record DrillResolve(String path, boolean leaf, boolean section, List<String> remainder) {
    }

    static DrillResolve resolveDrill(
            ConfigurationSection root, Set<String> editable, List<String> segments) {
        ConfigurationSection current = root;
        StringBuilder path = new StringBuilder();
        int consumed = 0;
        for (String segment : segments) {
            String child = findChild(current, segment);
            if (child == null) {
                break;
            }
            if (!path.isEmpty()) {
                path.append('.');
            }
            path.append(child);
            consumed++;
            ConfigurationSection next = current.getConfigurationSection(child);
            if (next == null) {
                break;
            }
            current = next;
        }
        if (consumed == 0) {
            return null;
        }
        String canonical = path.toString();
        boolean section = root.getConfigurationSection(canonical) != null;
        return new DrillResolve(canonical, isEditable(editable, canonical), section,
                List.copyOf(segments.subList(consumed, segments.size())));
    }

    private static String findChild(ConfigurationSection section, String segment) {
        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase(segment)) {
                return key;
            }
        }
        return null;
    }

    private static boolean isEditable(Set<String> editable, String path) {
        for (String name : editable) {
            if (name.equalsIgnoreCase(path)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isUnder(String setting, String path) {
        return setting.length() > path.length()
                && setting.regionMatches(true, 0, path, 0, path.length())
                && setting.charAt(path.length()) == '.';
    }

    /** Categories are the top-level config sections, excluding the version key. */
    static List<String> drillCategories(ConfigurationSection root) {
        List<String> categories = new ArrayList<>();
        for (String key : root.getKeys(false)) {
            if (!key.equals("config-version") && root.getConfigurationSection(key) != null) {
                categories.add(key);
            }
        }
        categories.sort(String.CASE_INSENSITIVE_ORDER);
        return categories;
    }

    /**
     * Next-level completion options for the given resolved prefix: children
     * that are editable themselves or lead to an editable setting, so
     * completion never suggests dead ends.
     */
    static List<String> drillChildren(
            ConfigurationSection root, Set<String> editable, List<String> prefix) {
        ConfigurationSection current = root;
        StringBuilder path = new StringBuilder();
        for (String segment : prefix) {
            String child = findChild(current, segment);
            if (child == null) {
                return List.of();
            }
            if (!path.isEmpty()) {
                path.append('.');
            }
            path.append(child);
            ConfigurationSection next = current.getConfigurationSection(child);
            if (next == null) {
                return List.of();
            }
            current = next;
        }
        String base = path.toString();
        List<String> options = new ArrayList<>();
        for (String key : current.getKeys(false)) {
            String full = base.isEmpty() ? key : base + "." + key;
            if (isEditable(editable, full) || leadsToEditable(editable, full)) {
                options.add(key);
            }
        }
        options.sort(String.CASE_INSENSITIVE_ORDER);
        return options;
    }

    private static boolean leadsToEditable(Set<String> editable, String path) {
        String prefix = path + ".";
        for (String name : editable) {
            if (name.length() > path.length() && name.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return true;
            }
        }
        return false;
    }

    private boolean worldEngine(CommandSender sender, String[] args) {
        if (!config.getBoolean("world-engine.enabled", false)) {
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
                "start", "end", "configuration", "config", "worldengine", "quickstart", "qs", "reload"));
        if (args.length == 2 && args[0].equalsIgnoreCase("end"))
            return partial(args[1], List.of("-i", "-immediate"));
        if (args.length >= 2
                && (args[0].equalsIgnoreCase("configuration") || args[0].equalsIgnoreCase("config"))) {
            return completeDrill(args);
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
     * Completes one drill-down argument: categories at the first position,
     * value candidates after an editable leaf, otherwise only the children of
     * the resolved prefix.
     */
    private List<String> completeDrill(String[] args) {
        List<String> prefix = new ArrayList<>();
        for (int i = 1; i < args.length - 1; i++) {
            prefix.add(args[i]);
        }
        String completing = args[args.length - 1];
        if (prefix.isEmpty()) {
            return partial(completing, drillCategories(plugin.getConfig()));
        }
        DrillResolve resolved = resolveDrill(plugin.getConfig(), game.settingNames(), prefix);
        if (resolved != null && resolved.leaf() && resolved.remainder().isEmpty()) {
            Object current = game.getSettingValue(resolved.path());
            if (current instanceof Boolean) {
                return partial(completing, List.of("true", "false"));
            }
            // Tab-complete the default value from the bundled default config.
            Object defaultValue = defaultConfigValue(resolved.path());
            if (defaultValue != null) {
                return partial(completing, List.of(String.valueOf(defaultValue)));
            }
            return List.of();
        }
        return partial(completing, drillChildren(plugin.getConfig(), game.settingNames(), prefix));
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
                    : Bukkit.getWorld(plugin.getConfig().getString("world-engine.world-name", "world"));
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void saveLobbyLocation(Location location) {
        String worldName = location.getWorld() == null
                ? plugin.getConfig().getString("world-engine.world-name", "world")
                : location.getWorld().getName();
        plugin.getConfig().set("world-engine.lobby-location.world", worldName);
        plugin.getConfig().set("world-engine.lobby-location.x", location.getX());
        plugin.getConfig().set("world-engine.lobby-location.y", location.getY());
        plugin.getConfig().set("world-engine.lobby-location.z", location.getZ());
        plugin.getConfig().set("world-engine.lobby-location.yaw", location.getYaw());
        plugin.getConfig().set("world-engine.lobby-location.pitch", location.getPitch());
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
