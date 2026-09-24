package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.config.SettingType;
import com.jruk8.jmanhunt.config.DurationFormat;
import com.jruk8.jmanhunt.core.DebugService;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.gui.menus.ManhuntMenus;
import com.jruk8.jmanhunt.gui.menus.ModifierMenus;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.bounds.LobbyBounds;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyPreset;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.world.LobbyWorld;
import com.jruk8.jmanhunt.lobby.MidMatchPolicy;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.ModifierTriggers;
import com.jruk8.jmanhunt.match.lifecycle.QuickStartOutcome;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.CapLimits;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.function.Function;
import java.util.UUID;

public final class ManhuntCommand implements CommandExecutor, TabCompleter {
    /**
     * Readonly announcement printed by the challenges subcommand. This text is
     * intentionally not loaded from messages.yml so it cannot be edited or
     * removed by server owners; change it here instead. It is always parsed as
     * MiniMessage; the {link} token becomes a clickable link to
     * {@link #CHALLENGES_URL} and {status} becomes the companion plugin status.
     */
    static final String CHALLENGES_MESSAGE = """
            
            <gray>[<gradient:#5e42f4:#b742f4>JMHChallenges</gradient>]</gray>
            <#de7766>JManhunt</#de7766> is a free plugin for configurable manhunts. \
            For lucky blocks and other fun challenges, you can find the optional addon {link}.
            
            <gray> » Challenges status: [{status}<gray>]</gray>
            
            <gray>Looking for modifiers instead? Try \
            <white>/mh modifiers</white>.</gray>
            """;
    public static final String CHALLENGES_URL = "https://builtbybit.com/resources/jmanhunt-challenges.121574/";
    private static final String CHALLENGES_LINK_TOKEN = "{link}";
    private static final String CHALLENGES_LINK_TEXT = "here";
    private static final String CHALLENGES_STATUS_TOKEN = "{status}";
    private static final String CHALLENGES_STATUS_ACTIVE = "ACTIVE";
    private static final String CHALLENGES_STATUS_INACTIVE = "INACTIVE";
    // Candidate plugin.yml names of the JManhunt-Challenges companion plugin;
    // the status line shows ACTIVE when any of these is loaded and enabled.
    private static final Set<String> CHALLENGES_PLUGIN_NAMES =
            Set.of("JManhunt-Challenges", "JManhuntChallenges", "JMHChallenges");
    /**
     * Extra node for the optional status arguments: bare status needs
     * only the base node, while an instance id or all needs this too.
     */
    static final String STATUS_OTHER_PERMISSION = "jmanhunt.command.status.other";
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final ConfigService config;
    private final SoundService sounds;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final LobbyTeleporter lobbyTeleporter;
    private final DebugService debugService;
    private final LobbyService lobbies;
    private final PendingConfirmations confirms = new PendingConfirmations();
    private final DevSchemCommand devSchem;
    private final SettingFeedback feedback;
    private final ModifiersCommand modifiersCmd;
    private ModifierMenus modifierMenus;
    private final ManhuntMenus menus;
    /** Lobby-bounds corners per player, separate from the dev schem selection. */
    private final Map<UUID, Location> boundPos1 = new HashMap<>();
    private final Map<UUID, Location> boundPos2 = new HashMap<>();

    public ManhuntCommand(JManhuntPlugin plugin, MessageService messages, ConfigService config,
                          SoundService sounds, PlayerStateStore playerStates, GameManager game,
                          LobbyTeleporter lobbyTeleporter, DebugService debugService,
                          LobbyService lobbyService) {
        this.plugin = plugin; this.messages = messages; this.config = config; this.sounds = sounds;
        this.playerStates = playerStates; this.game = game;
        this.lobbyTeleporter = lobbyTeleporter; this.debugService = debugService;
        this.lobbies = lobbyService;
        this.devSchem = new DevSchemCommand(plugin, messages);
        this.feedback = new SettingFeedback(messages, config, sounds);
        this.modifiersCmd = new ModifiersCommand(config, messages,
                plugin.guiService(), () -> modifierMenus.mainMenu(), sounds);
        SettingDialogs dialogs = new SettingDialogs(config, messages, sounds,
                plugin.guiService(), feedback, plugin);
        this.modifierMenus = new ModifierMenus(config.modifiers(), messages, sounds,
                plugin.guiService(), modifiersCmd, dialogs);
        this.menus = new ManhuntMenus(config, plugin.guiConfig(), messages, sounds,
                plugin.guiService(), dialogs, feedback, plugin.stats(), modifierMenus);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (opensGui(args, sender)) {
            return openGui((Player) sender);
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (!canUseSubcommand(sender, sub)) {
            return message(sender, "command.no-permission");
        }
        return switch (sub) {
            case "help" -> help(sender);
            case "status" -> status(sender, args);
            case "challenges" -> challenges(sender);
            case "setplayer" -> setPlayer(sender, args);
            case "start" -> start(sender, args);
            case "end" -> end(sender, args);
            case "game" -> game(sender, args);
            case "config" -> configCommand(sender, args);
            case "modifiers" -> modifiers(sender, args);
            case "worldengine" -> worldEngine(sender, args);
            case "quickstart", "qs" -> quickStart(sender, args);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender, args);
            case "lobby" -> lobby(sender, args);
            case "setup" -> setup(sender);
            case "dev" -> dev(sender, args);
            default -> message(sender, "command.invalid");
        };
    }

    private boolean help(CommandSender sender) {
        message(sender, "manhunt.help-header");
        String[][] lines = {{"/manhunt help", "show commands"}, {"/manhunt setup", "interactive setup guide"},
                {"/manhunt", "open the GUI or show match status"},
                {"/manhunt setplayer <selector> <hunter|speedrunner|spectator|afk|none>", "assign roles"},
                {"/manhunt lobby join <selector> <lobby-id> [role] [-notp]", "move players to a lobby"},
                {"/manhunt lobby leave [selector]", "remove players from their lobby"},
                {"/manhunt start [lobby-id]", "start a match"}, {"/manhunt end [id] [-i|-immediate]", "cancel a match"},
                {"/manhunt game join <id> [role] [selector]", "add players to a running match"},
                {"/manhunt game leave [id] [selector]", "remove players from a running match"},
                {"/manhunt status [id|all]", "show match status"},
                {"/manhunt quickstart [percentage]", "assign teams and start immediately"},
                {"/manhunt config <category> <key...> <value>", "view or change a setting"},
                {"/manhunt modifiers [setmod|setpreset]", "browse or toggle gameplay modifiers"},
                {"/manhunt worldengine", "manage lobbies or teleport players"},
                {"/manhunt debug [on|off]", "toggle debug output"},
                {"/manhunt challenges", "show Challenges addon info"},
                {"/manhunt dev schem <pos1|pos2|save|load|list>", "dev schematic tools"},
                {"/manhunt reload", "reload files"}};
        for (String[] line : lines) {
            message(sender, "manhunt.help-line", Map.of("command", line[0], "description", line[1]));
        }
        // Clickable links use hardcoded MiniMessage instead of living in
        // messages.yml.
        sender.sendMessage(messages.miniMessage(
                "\n<green>Still need help? Check <#de7766><click:open_url:'https://jruk8.github.io/JManhunt/'>"
                        + "<underlined>Docs</underlined></click></#de7766> or join our "
                        + "<#de7766><click:open_url:'https://discord.gg/hkWmCVmWDC'>"
                        + "<underlined>Discord server</underlined></click></#de7766>!</green>"));
        sender.sendMessage(messages.miniMessage(
                "<green>Support our development on <#de7766><click:open_url:'https://ko-fi.com/jruk'>"
                        + "<underlined>Ko-fi</underlined></click></#de7766>.</green>"));
        neutralSound(sender);
        return true;
    }

    /** Starts the interactive setup tutorial. Players only: it runs over chat. */
    private boolean setup(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return message(sender, "command.player-only");
        }
        plugin.markSetupDone();
        plugin.tutorial().start(player);
        return true;
    }

    /**
     * Bare /manhunt opens the admin GUI, but only for players holding
     * the GUI node. Everyone else, including the console, gets status.
     */
    static boolean opensGui(String[] args, CommandSender sender) {
        return args.length == 0 && sender instanceof Player
                && sender.hasPermission("jmanhunt.gui");
    }

    /**
     * Opens the root GUI, showing the Setup First nudge once when the
     * global flag is unset. Confirm starts the setup guide, Cancel
     * dismisses forever and opens the GUI.
     */
    private boolean openGui(Player player) {
        if (!plugin.isSetupDone()) {
            plugin.guiService().open(player, menus.setupFirstMenu(
                    confirmed -> {
                        plugin.markSetupDone();
                        confirmed.closeInventory();
                        plugin.tutorial().start(confirmed);
                    },
                    skipped -> {
                        plugin.markSetupDone();
                        plugin.guiService().navigate(skipped, menus.rootMenu());
                        sounds.playNeutralSound(skipped);
                    }));
            return true;
        }
        plugin.guiService().open(player, menus.rootMenu());
        sounds.playNeutralSound(player);
        return true;
    }

    /**
     * Shows match status with instance context: an id shows one match
     * roster, {@code all} lists every running match, and no argument shows
     * the sender's own match, else their lobby queue.
     */
    private boolean status(CommandSender sender, String[] args) {
        if (args.length > 2) {
            return message(sender, "manhunt.status-usage");
        }
        if (args.length == 2) {
            if (!canUseStatusArgs(sender)) {
                return message(sender, "command.no-permission");
            }
            if (args[1].equalsIgnoreCase("all")) {
                return statusAll(sender);
            }
            Optional<GameInstance> instance = game.resolveInstance(args[1]);
            if (instance.isEmpty()) {
                return message(sender, "manhunt.invalid-instance-id");
            }
            return statusInstance(sender, instance.get());
        }
        if (sender instanceof Player player) {
            Optional<GameInstance> own = game.instanceOf(player.getUniqueId());
            if (own.isPresent()) {
                return statusInstance(sender, own.get());
            }
            Optional<Lobby> lobby = lobbies.lobbyOf(player.getUniqueId());
            if (lobby.isEmpty()) {
                return message(sender, "manhunt.not-in-match");
            }
            return statusLobby(sender, lobby.get());
        }
        return message(sender, "manhunt.console-requires-id");
    }

    /** One match roster: every online assignee grouped by role. */
    private boolean statusInstance(CommandSender sender, GameInstance instance) {
        List<Player> players = game.onlineAssignedPlayers(instance);
        message(sender, "manhunt.status-header", Map.of("status", instance.ending() ? "ENDING" : "ACTIVE"));
        sendRoleSection(sender, players, Role.SPEEDRUNNER, "manhunt.speedrunners-header");
        sendRoleSection(sender, players, Role.HUNTER, "manhunt.hunters-header");
        sendRoleSection(sender, players, Role.AFK, "manhunt.afk-header");
        sendRoleSection(sender, players, Role.NONE, "manhunt.none-header");
        sendSpectatorLine(sender, players);
        sendWinConditionLines(sender);
        sendModifiersLine(sender);
        sendElapsedLine(sender, instance);
        sendIdLine(sender, instance.lobbyTag() + "|G" + instance.matchId());
        neutralSound(sender);
        return true;
    }

    /** One lobby queue: every online member grouped by role. */
    private boolean statusLobby(CommandSender sender, Lobby lobby) {
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> lobby.contains(p.getUniqueId())).map(p -> (Player) p).toList();
        message(sender, "manhunt.status-header", Map.of("status", "INACTIVE"));
        sendRoleSection(sender, players, Role.SPEEDRUNNER, "manhunt.speedrunners-header");
        sendRoleSection(sender, players, Role.HUNTER, "manhunt.hunters-header");
        sendRoleSection(sender, players, Role.AFK, "manhunt.afk-header");
        sendRoleSection(sender, players, Role.NONE, "manhunt.none-header");
        sendSpectatorLine(sender, players);
        sendWinConditionLines(sender);
        sendIdLine(sender, "L" + lobby.id());
        neutralSound(sender);
        return true;
    }

    /** Bottom spectator roll call, shown only when someone is watching. */
    private void sendSpectatorLine(CommandSender sender, List<Player> players) {
        String names = players.stream().filter(p -> playerStates.role(p) == Role.SPECTATOR)
                .map(Player::getName).sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        if (!names.isEmpty()) {
            message(sender, "manhunt.spectators-line", Map.of("value", names));
        }
    }

    /** Optional per-side win rules, off by default to keep status compact. */
    private void sendWinConditionLines(CommandSender sender) {
        if (!config.getBoolean("settings.server.status.show-win-conditions", false)) {
            return;
        }
        message(sender, "manhunt.status-win-speedrunners",
                Map.of("conditions", game.speedrunnerWinConditions()));
        message(sender, "manhunt.status-win-hunters", Map.of("conditions", game.hunterWinConditions()));
    }

    /** Optional enabled-modifier roll call: hidden when off or when none are enabled. */
    private void sendModifiersLine(CommandSender sender) {
        if (!config.getBoolean("settings.server.status.show-modifiers", false)) {
            return;
        }
        List<String> enabled = new ArrayList<>();
        for (String name : config.modifierNames()) {
            if (config.modifierEnabled(name)) {
                enabled.add(name);
            }
        }
        if (enabled.isEmpty()) {
            return;
        }
        enabled.sort(String.CASE_INSENSITIVE_ORDER);
        message(sender, "manhunt.status-modifiers", Map.of("modifiers", ListFormatter.joinOxford(enabled)));
    }

    /** Optional match runtime, off by default. */
    private void sendElapsedLine(CommandSender sender, GameInstance instance) {
        if (!config.getBoolean("settings.server.status.show-elapsed-time", false)) {
            return;
        }
        message(sender, "manhunt.status-elapsed", Map.of("duration",
                DurationFormat.format(instance.elapsedSeconds(System.currentTimeMillis()))));
    }

    /** Optional lobby/game tag (L1, L1-0|G2 when sublobbed), on by default. */
    private void sendIdLine(CommandSender sender, String value) {
        if (!config.getBoolean("settings.server.status.show-ids", true)) {
            return;
        }
        message(sender, "manhunt.status-ids", Map.of("value", value));
    }

    /**
     * Every running match plus every populated lobby queue: ids with
     * active and assigned counts and durations first, then one line per
     * lobby holding at least one online member. Quiet servers still print
     * the games empty line so the lobby section never hides alone.
     */
    private boolean statusAll(CommandSender sender) {
        List<GameInstance> live = game.liveInstances();
        if (live.isEmpty()) {
            message(sender, "manhunt.status-all-empty");
        } else {
            message(sender, "manhunt.status-all-header");
            long now = System.currentTimeMillis();
            for (GameInstance instance : live) {
                message(sender, "manhunt.status-all-entry", Map.of(
                        "lobby", instance.lobbyTag(),
                        "id", String.valueOf(instance.matchId()),
                        "remaining", String.valueOf(instance.activeIds().size()),
                        "assigned", String.valueOf(instance.assignedCount()),
                        "duration", DurationFormat.format(instance.elapsedSeconds(now))));
            }
        }
        sendLobbyQueues(sender);
        neutralSound(sender);
        return true;
    }

    /** One line per lobby holding at least one online member, by lobby id. */
    private void sendLobbyQueues(CommandSender sender) {
        List<Lobby> queued = new ArrayList<>();
        for (int id : lobbies.lobbyIds()) {
            lobbies.get(id).ifPresent(queued::add);
        }
        queued.sort(Comparator.comparingInt(Lobby::id));
        boolean header = false;
        for (Lobby lobby : queued) {
            long online = Bukkit.getOnlinePlayers().stream()
                    .filter(player -> lobby.contains(player.getUniqueId())).count();
            if (online < 1) {
                continue;
            }
            if (!header) {
                message(sender, "manhunt.status-all-lobbies-header");
                header = true;
            }
            message(sender, "manhunt.status-all-lobby-entry", Map.of(
                    "lobby", String.valueOf(lobby.id()),
                    "count", String.valueOf(online)));
        }
    }

    private void sendRoleSection(CommandSender sender, List<Player> players, Role role, String header) {
        List<String> names = players.stream().filter(p -> playerStates.role(p) == role)
                .map(Player::getName).sorted().toList();
        if (names.isEmpty()) {
            return;
        }
        message(sender, header);
        for (String line : ListFormatter.chunk(names, 10)) {
            message(sender, "manhunt.status-player", Map.of("player", line));
        }
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
    public static List<Component> challengesComponents(MessageService messages, boolean companionEnabled) {
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

    /**
     * Whether the sender may run the given first level subcommand. Aliases
     * share their canonical permission node, and setplayer additionally
     * accepts the self-only node (setPlayer enforces the self target).
     */
    /** Live pending lobby corners for the debug particle draft boxes. */
    public Map<UUID, Location> boundPos1View() {
        return Collections.unmodifiableMap(boundPos1);
    }

    /** Live pending lobby corners for the debug particle draft boxes. */
    public Map<UUID, Location> boundPos2View() {
        return Collections.unmodifiableMap(boundPos2);
    }

    /** Live pending dev schem corners for the debug particle draft boxes. */
    public Map<UUID, Location> devPos1View() {
        return devSchem.pos1View();
    }

    /** Live pending dev schem corners for the debug particle draft boxes. */
    public Map<UUID, Location> devPos2View() {
        return devSchem.pos2View();
    }

    /**
     * Whether the sender may pass an argument to /manhunt status. Bare
     * status needs only the base node; an instance id or all needs the
     * args node on top of it.
     */
    static boolean canUseStatusArgs(CommandSender sender) {
        return sender.hasPermission(STATUS_OTHER_PERMISSION);
    }

    static boolean canUseSubcommand(CommandSender sender, String sub) {
        return switch (sub.toLowerCase(Locale.ROOT)) {
            case "dev" -> sender.hasPermission("jmanhunt.command.dev.schem");
            case "modifiers" -> sender.hasPermission("jmanhunt.modifiers");
            case "setplayer" -> sender.hasPermission("jmanhunt.command.setplayer")
                    || sender.hasPermission("jmanhunt.command.setplayer.self");
            case "config" -> sender.hasPermission("jmanhunt.command.config");
            case "qs", "quickstart" -> sender.hasPermission("jmanhunt.command.quickstart");
            default -> sender.hasPermission("jmanhunt.command." + sub.toLowerCase(Locale.ROOT));
        };
    }

    /**
     * Whether the sender may run the given worldengine action. Holding the
     * base worldengine node implies every action; otherwise each action
     * needs its own node.
     */
    static boolean canUseWorldEngineAction(CommandSender sender, String action) {
        if (sender.hasPermission("jmanhunt.command.worldengine")) {
            return true;
        }
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "lobbyconfig" -> sender.hasPermission("jmanhunt.command.worldengine.lobbyconfig");
            case "tpto" -> sender.hasPermission("jmanhunt.command.worldengine.tpto");
            case "cellindex" -> sender.hasPermission("jmanhunt.command.worldengine.cellindex");
            default -> false;
        };
    }

    static String rolePermissionNode(Role role) {
        return switch (role) {
            case HUNTER -> "jmanhunt.hunter";
            case SPEEDRUNNER -> "jmanhunt.speedrunner";
            case AFK -> "jmanhunt.afk";
            case NONE -> "jmanhunt.none";
            case SPECTATOR -> "jmanhunt.spectator";
        };
    }

    static boolean isSelfOnlySelection(CommandSender sender, List<Entity> selected) {
        if (!(sender instanceof Player self) || selected.size() != 1) {
            return false;
        }
        return selected.get(0) instanceof Player target
                && target.getUniqueId().equals(self.getUniqueId());
    }

    /**
     * Assigns queued roles. While the target's lobby has no running match
     * this assigns directly; with a live match and the world engine on,
     * lobbies.mid-match-setplayer decides between holding the player for
     * the next game and joining them mid-match. With the engine off the
     * in-match block stays, and -force never bypasses it. Waking someone
     * else's AFK role needs a second run within 10 seconds.
     */
    private boolean setPlayer(CommandSender sender, String[] args) {
        SetPlayerFlags flags = parseSetPlayerFlags(args);
        if (flags.end() != 3) {
            return message(sender, "manhunt.setplayer-usage");
        }
        Optional<Role> parsed = Role.parse(args[2]);
        if (parsed.isEmpty()) {
            return message(sender, "manhunt.setplayer-usage");
        }
        Role role = parsed.get();
        List<Entity> selected;
        try { selected = Bukkit.selectEntities(sender, args[1]); }
        catch (IllegalArgumentException exception) { return message(sender, "manhunt.setplayer-usage"); }
        // The full setplayer permission overrides every other check: any
        // selector and any role. Otherwise the sender needs the self node,
        // the selector must resolve to exactly the sender, and the sender
        // must hold the permission for the requested role.
        if (!sender.hasPermission("jmanhunt.command.setplayer")
                && (!isSelfOnlySelection(sender, selected)
                || !sender.hasPermission(rolePermissionNode(role)))) {
            return message(sender, "command.no-permission");
        }
        if (selected.stream().noneMatch(entity -> entity instanceof Player)) {
            return message(sender, "command.no-targets");
        }
        int afkWakes = countAfkWakes(sender, selected, role, flags.force());
        if (afkWakes > 0 && !confirms.confirm("setplayer-afk:" + senderKey(sender))) {
            message(sender, "manhunt.set-afk-confirm", Map.of("count", String.valueOf(afkWakes)));
            return true;
        }
        SetPlayerTally tally = new SetPlayerTally();
        for (Entity entity : selected) {
            if (entity instanceof Player player) {
                applySetPlayerToPlayer(sender, player, role, flags.force(), flags.silent(), tally);
            }
        }
        reportSetPlayerResults(sender, role, tally);
        return true;
    }

    /** Trailing -force/-silent flags plus the remaining argument count. */
    private SetPlayerFlags parseSetPlayerFlags(String[] args) {
        boolean force = false;
        boolean silent = false;
        int end = args.length;
        while (end > 3 && (isForceFlag(args[end - 1]) || isSilentFlag(args[end - 1]))) {
            if (isForceFlag(args[end - 1])) {
                force = true;
            } else {
                silent = true;
            }
            end--;
        }
        return new SetPlayerFlags(force, silent, end);
    }

    /** Assigns one selected player their role, recording the outcome. */
    private void applySetPlayerToPlayer(CommandSender sender, Player player, Role role, boolean force,
            boolean silent, SetPlayerTally tally) {
        if (playerStates.role(player) == role) {
            tally.unchanged++;
            return;
        }
        if (!player.hasPermission(rolePermissionNode(role))) {
            tally.skipped++;
            return;
        }
        Optional<Lobby> targetLobby = lobbies.lobbyOf(player.getUniqueId());
        Optional<GameInstance> live = targetLobby.flatMap(lobby -> game.instanceForLobby(lobby.id()));
        if (live.isPresent()) {
            applySetPlayerInMatch(sender, player, role, force, silent, targetLobby, live.get(), tally);
            return;
        }
        if (!capAllows(lobbies.lobbyOf(player.getUniqueId()), role, force, tally.cappedIn)) {
            return;
        }
        assignSetPlayerRole(sender, player, role, silent, tally);
    }

    /** Assigns a player whose lobby has a live match: mid-match join or hold. */
    private void applySetPlayerInMatch(CommandSender sender, Player player, Role role, boolean force,
            boolean silent, Optional<Lobby> targetLobby, GameInstance live, SetPlayerTally tally) {
        if (!lobbies.multiLobbyAllowed()) {
            message(sender, "manhunt.set-in-match", Map.of("player", player.getName()));
            return;
        }
        MidMatchPolicy policy = MidMatchPolicy.parse(
                config.getString("lobbies.mid-match-setplayer", "SUBLOBBY"));
        if (policy.joinsMidMatch(role)
                && game.joinPlayers(live, List.of(player), role) == 1) {
            tally.joined++;
            tally.assigned.add(player.getUniqueId());
            return;
        }
        boolean member = live.isActive(player.getUniqueId());
        if (!member && !capAllows(targetLobby, role, force, tally.cappedIn)) {
            return;
        }
        assignSetPlayerRole(sender, player, role, silent, tally);
        if (!member) {
            tally.held++;
            if (!silent && role != Role.AFK) {
                message(player, "manhunt.setplayer-held", Map.of("role", messages.roleName(role)));
            }
        }
    }

    /** Sets one player's role with teams sync and announcements. */
    private void assignSetPlayerRole(CommandSender sender, Player player, Role role, boolean silent,
            SetPlayerTally tally) {
        Role from = playerStates.role(player);
        playerStates.setRole(player, role);
        tally.changed++;
        plugin.roleTeams().sync(player);
        if (!silent) {
            game.announceRoleChange(player, from, role);
        }
        tally.assigned.add(player.getUniqueId());
        if (!silent) {
            message(player, "manhunt.role-assigned", Map.of("role", messages.roleName(role)));
            sounds.playNeutralSound(player);
        }
    }

    /** Reports one setplayer run: summary, skips, joins, holds, and caps. */
    private void reportSetPlayerResults(CommandSender sender, Role role, SetPlayerTally tally) {
        message(sender, tally.unchanged == 0 ? "manhunt.set-success" : "manhunt.set-success-unchanged",
                Map.of("count", String.valueOf(tally.changed), "role", messages.roleName(role),
                        "unchanged", String.valueOf(tally.unchanged)));
        if (tally.skipped > 0) {
            message(sender, "manhunt.set-skipped", Map.of("count", String.valueOf(tally.skipped)));
        }
        if (tally.joined > 0) {
            message(sender, "manhunt.setplayer-joined",
                    Map.of("count", String.valueOf(tally.joined), "role", messages.roleName(role)));
        }
        if (tally.held > 0) {
            message(sender, "manhunt.setplayer-held-summary", Map.of("count", String.valueOf(tally.held)));
        }
        for (Map.Entry<Integer, Set<Role>> entry : tally.cappedIn.entrySet()) {
            for (Role cappedRole : entry.getValue()) {
                message(sender, "manhunt.lobby-full", Map.of("lobby", String.valueOf(entry.getKey()),
                        "role", messages.roleName(cappedRole)));
            }
        }
        if (sender instanceof Player player && !tally.assigned.contains(player.getUniqueId())) {
            sounds.playNeutralSound(player);
        }
        if (tally.changed > 0) {
            game.updateAutostartState();
        }
    }

    /** Trailing flags parsed from a setplayer invocation. */
    private record SetPlayerFlags(boolean force, boolean silent, int end) {
    }

    /** Mutable counters for one setplayer run. */
    private static final class SetPlayerTally {
        int changed;
        int unchanged;
        int skipped;
        int joined;
        int held;
        final Set<java.util.UUID> assigned = new HashSet<>();
        final Map<Integer, Set<Role>> cappedIn = new LinkedHashMap<>();
    }

    /**
     * Queue-cap gate shared by idle and held assignments. Records the cap
     * and returns false when the lobby is full for the role.
     */
    private boolean capAllows(Optional<Lobby> lobby, Role role, boolean force,
            Map<Integer, Set<Role>> cappedIn) {
        if (force || !role.isParticipant() || lobby.isEmpty()) {
            return true;
        }
        if (CapLimits.allows(lobbyRoleCount(lobby.get(), role), capFor(role))) {
            return true;
        }
        cappedIn.computeIfAbsent(lobby.get().id(), key -> new HashSet<>()).add(role);
        return false;
    }

    /** AFK players a setplayer run would actually wake (non-self only). */
    private int countAfkWakes(CommandSender sender, List<Entity> selected, Role role, boolean force) {
        int wakes = 0;
        for (Entity entity : selected) {
            if (!(entity instanceof Player player)) {
                continue;
            }
            if (!needsAfkGuard(playerStates.role(player), role, isSelfTarget(sender, player))) {
                continue;
            }
            if (!player.hasPermission(rolePermissionNode(role))) {
                continue;
            }
            Optional<Lobby> lobby = lobbies.lobbyOf(player.getUniqueId());
            if (lobby.isPresent() && game.instanceForLobby(lobby.get().id()).isPresent()) {
                continue;
            }
            if (!force && role.isParticipant() && lobby.isPresent()
                    && !CapLimits.allows(lobbyRoleCount(lobby.get(), role), capFor(role))) {
                continue;
            }
            wakes++;
        }
        return wakes;
    }

    /** True when waking someone else's AFK role, which needs confirmation. Pure for tests. */
    static boolean needsAfkGuard(Role from, Role to, boolean self) {
        return from == Role.AFK && to != Role.AFK && !self;
    }

    private static boolean isSelfTarget(CommandSender sender, Player player) {
        return sender instanceof Player self && self.getUniqueId().equals(player.getUniqueId());
    }

    private boolean lobby(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            return message(sender, "manhunt.lobby-usage");
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("join")) {
            return lobbyJoin(sender, args);
        }
        if (action.equals("leave")) {
            return lobbyLeave(sender, args);
        }
        return message(sender, "manhunt.lobby-usage");
    }

    /**
     * Moves players to a lobby: join &lt;selector&gt; &lt;lobby-id&gt; [role]
     * [-f|-force] [-notp] [-s|-silent]. The role defaults to none and
     * joiners teleport to the lobby unless -notp is given. Silent joiners
     * get no role message or sound.
     */
    private boolean lobbyJoin(CommandSender sender, String[] args) {
        LobbyJoinFlags flags = parseLobbyJoinFlags(args);
        if (flags.end() != 4 && flags.end() != 5) {
            return message(sender, "manhunt.lobby-join-usage");
        }
        OptionalInt lobbyId = LobbyService.parseId(args[3]);
        if (lobbyId.isEmpty()) {
            return message(sender, "manhunt.lobby-invalid-id");
        }
        Role role = Role.NONE;
        if (flags.end() == 5) {
            Optional<Role> parsed = Role.parse(args[4]);
            if (parsed.isEmpty()) {
                return message(sender, "manhunt.lobby-join-usage");
            }
            role = parsed.get();
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId.getAsInt() != 0) {
            return message(sender, "manhunt.lobby-worldengine-required");
        }
        List<Player> targets = selectPlayers(sender, args[2]);
        if (targets == null) {
            return message(sender, "manhunt.lobby-join-usage");
        }
        if (targets.isEmpty()) {
            return message(sender, "command.no-targets");
        }
        List<Player> moved = new ArrayList<>();
        Set<Role> capped = new HashSet<>();
        for (Player target : targets) {
            applyLobbyJoinToPlayer(sender, target, lobbyId.getAsInt(), role, flags.force(),
                    moved, capped);
        }
        reportLobbyJoinResults(sender, moved, capped, lobbyId.getAsInt(), role, flags.noTeleport());
        return true;
    }

    /** Trailing join flags plus the remaining argument count. */
    private LobbyJoinFlags parseLobbyJoinFlags(String[] args) {
        boolean force = false;
        boolean noTeleport = false;
        int end = args.length;
        while (end > 2 && (isForceFlag(args[end - 1]) || isNoTeleportFlag(args[end - 1]))) {
            if (isForceFlag(args[end - 1])) {
                force = true;
            } else {
                noTeleport = true;
            }
            end--;
        }
        return new LobbyJoinFlags(force, noTeleport, end);
    }

    /** Moves one player into a lobby, recording moves and caps. */
    private void applyLobbyJoinToPlayer(CommandSender sender, Player target, int lobbyId, Role role,
            boolean force, List<Player> moved, Set<Role> capped) {
        if (game.instanceOf(target.getUniqueId()).isPresent()) {
            message(sender, "manhunt.lobby-join-in-match", Map.of("player", target.getName()));
            return;
        }
        Optional<Lobby> current = lobbies.lobbyOf(target.getUniqueId());
        if (current.isPresent() && current.get().id() == lobbyId
                && playerStates.role(target) == role) {
            message(sender, "manhunt.lobby-already-member", Map.of("player", target.getName(),
                    "lobby", String.valueOf(lobbyId), "role", messages.roleName(role)));
            return;
        }
        Lobby lobby = lobbies.get(lobbyId).orElse(null);
        int count = lobby == null ? 0 : lobbyRoleCount(lobby, role);
        if (!force && role.isParticipant() && !CapLimits.allows(count, capFor(role))) {
            capped.add(role);
            return;
        }
        OptionalInt before = current.map(own -> OptionalInt.of(own.id())).orElseGet(OptionalInt::empty);
        lobbies.setLobby(target.getUniqueId(), lobbyId);
        playerStates.setRole(target, role);
        plugin.roleTeams().sync(target);
        moved.add(target);
        lobbies.announceLobbyChange(target, before, OptionalInt.of(lobbyId));
    }

    /** Reports one lobby join run and teleports the movers. */
    private void reportLobbyJoinResults(CommandSender sender, List<Player> moved, Set<Role> capped,
            int lobbyId, Role role, boolean noTeleport) {
        for (Role cappedRole : capped) {
            message(sender, "manhunt.lobby-full", Map.of("lobby", String.valueOf(lobbyId),
                    "role", messages.roleName(cappedRole)));
        }
        message(sender, "manhunt.lobby-join-success", Map.of("count", String.valueOf(moved.size()),
                "lobby", String.valueOf(lobbyId), "role", messages.roleName(role)));
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
        if (!moved.isEmpty() && !noTeleport
                && config.getBoolean("lobbies.join-teleports-to-lobby", true)) {
            teleportJoinersToLobby(sender, moved, lobbyId);
        }
        if (!moved.isEmpty()) {
            game.updateAutostartState();
        }
    }

    /** Trailing flags parsed from a lobby join invocation. */
    private record LobbyJoinFlags(boolean force, boolean noTeleport, int end) {
    }

    /**
     * Teleports lobby joiners to their lobby location when join teleporting
     * is enabled. Lobbies without a location keep their joiners in place.
     */
    private void teleportJoinersToLobby(CommandSender sender, List<Player> moved, int lobbyId) {
        if (!lobbies.multiLobbyAllowed()) {
            return;
        }
        if (!lobbyTeleporter.teleportToLobby(moved, lobbyId)) {
            message(sender, "manhunt.lobby-no-location", Map.of("lobby", String.valueOf(lobbyId)));
        }
    }

    /** Removes players from whatever lobby they are in: leave [selector]. */
    private boolean lobbyLeave(CommandSender sender, String[] args) {
        if (!lobbies.multiLobbyAllowed()) {
            return message(sender, "manhunt.lobby-worldengine-required");
        }
        if (args.length > 3) {
            return message(sender, "manhunt.lobby-leave-usage");
        }
        List<Player> targets = selectPlayers(sender, args.length == 3 ? args[2] : "@s");
        if (targets == null) {
            return message(sender, "manhunt.lobby-leave-usage");
        }
        if (targets.isEmpty()) {
            return message(sender, "command.no-targets");
        }
        for (Player target : targets) {
            Optional<Lobby> lobby = lobbies.lobbyOf(target.getUniqueId());
            if (lobby.isEmpty()) {
                message(sender, "manhunt.lobby-leave-not-member", Map.of("player", target.getName()));
                continue;
            }
            if (game.instanceOf(target.getUniqueId()).isPresent()) {
                message(sender, "manhunt.lobby-leave-in-match", Map.of("player", target.getName()));
                continue;
            }
            lobbies.remove(target.getUniqueId());
            message(sender, "manhunt.lobby-leave-success", Map.of("player", target.getName(),
                    "lobby", String.valueOf(lobby.get().id())));
        }
        neutralSound(sender);
        game.updateAutostartState();
        return true;
    }

    /** True for the -f and -force flags accepted by setplayer and lobby join. */
    static boolean isForceFlag(String arg) {
        return arg.equalsIgnoreCase("-f") || arg.equalsIgnoreCase("-force");
    }

    /** True for the -s and -silent flags accepted by setplayer and lobby join. */
    static boolean isSilentFlag(String arg) {
        return arg.equalsIgnoreCase("-s") || arg.equalsIgnoreCase("-silent");
    }

    /** True for the -notp flag accepted by lobby join (no shorthand, for clarity). */
    static boolean isNoTeleportFlag(String arg) {
        return arg.equalsIgnoreCase("-notp");
    }

    /** Online members of a lobby currently holding a role. */
    private int lobbyRoleCount(Lobby lobby, Role role) {
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (playerStates.role(online) == role && lobby.contains(online.getUniqueId())) {
                count++;
            }
        }
        return count;
    }

    /** Configured queue cap for a participant role, -1 when uncapped. */
    private int capFor(Role role) {
        return config.getInt(
                "lobbies.queue-caps." + role.name().toLowerCase(Locale.ROOT), -1);
    }

    private boolean start(CommandSender sender, String[] args) {
        int lobbyId;
        if (args.length == 1) {
            lobbyId = startLobbyFor(sender);
        } else if (args.length == 2) {
            OptionalInt parsed = LobbyService.parseId(args[1]);
            if (parsed.isEmpty()) {
                return message(sender, "manhunt.lobby-invalid-id");
            }
            lobbyId = parsed.getAsInt();
        } else {
            return message(sender, "manhunt.start-usage");
        }
        if (lobbyId < 0) {
            return message(sender, "manhunt.start-invalid");
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId != 0) {
            return message(sender, "manhunt.lobby-worldengine-required");
        }
        if (game.instanceForLobby(lobbyId).isPresent()) {
            return message(sender, "manhunt.already-active");
        }
        Location surroundOrigin = sender instanceof Player executor ? executor.getLocation() : null;
        if (!game.start(lobbyId, surroundOrigin)) {
            return message(sender, "manhunt.start-invalid");
        }
        return true;
    }

    /** Lobby a bare start targets: the sender's lobby, else the default. */
    private int startLobbyFor(CommandSender sender) {
        if (sender instanceof Player player) {
            return lobbies.lobbyOf(player.getUniqueId()).map(Lobby::id)
                    .orElseGet(() -> lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0);
        }
        return lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0;
    }

    private boolean end(CommandSender sender, String[] args) {
        EndArgs parsed = parseEndArgs(args);
        if (!parsed.valid()) {
            return message(sender, "manhunt.end-usage");
        }
        GameInstance instance;
        if (parsed.instanceId().isPresent()) {
            Optional<GameInstance> resolved = game.resolveInstance(parsed.instanceId().get());
            if (resolved.isEmpty()) {
                return message(sender, "manhunt.invalid-instance-id");
            }
            instance = resolved.get();
        } else if (sender instanceof Player player) {
            Optional<GameInstance> own = game.instanceOf(player.getUniqueId());
            if (own.isEmpty()) {
                if (!game.isActive()) {
                    return message(sender, "manhunt.not-active");
                }
                return message(sender, "manhunt.not-in-match");
            }
            instance = own.get();
        } else {
            return message(sender, "manhunt.console-requires-id");
        }
        game.cancel(instance, parsed.immediate());
        return true;
    }


    static EndArgs parseEndArgs(String[] args) {
        Optional<String> instanceId = Optional.empty();
        boolean immediate = false;
        for (int i = 1; i < args.length; i++) {
            if (isImmediateFlag(args[i])) {
                immediate = true;
            } else if (instanceId.isEmpty()) {
                instanceId = Optional.of(args[i]);
            } else {
                return new EndArgs(Optional.empty(), false, false);
            }
        }
        return new EndArgs(instanceId, immediate, true);
    }

    /** True for the -i and -immediate flags accepted by end. */
    static boolean isImmediateFlag(String arg) {
        return arg.equalsIgnoreCase("-i") || arg.equalsIgnoreCase("-immediate");
    }

    /** Joins players to or removes them from a running match. */
    private boolean game(CommandSender sender, String[] args) {
        if (args.length < 2 || args[1].isBlank()) {
            return message(sender, "manhunt.game-usage");
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("join")) {
            return gameJoin(sender, args);
        }
        if (action.equals("leave")) {
            return gameLeave(sender, args);
        }
        return message(sender, "manhunt.game-usage");
    }

    /** Adds players to a running match: game join <id> [role] [selector]. */
    private boolean gameJoin(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 5) {
            return message(sender, "manhunt.game-join-usage");
        }
        Optional<GameInstance> instance = game.resolveInstance(args[2]);
        if (instance.isEmpty() || instance.get().ending()) {
            return message(sender, "manhunt.invalid-instance-id");
        }
        Role role = Role.SPECTATOR;
        if (args.length >= 4) {
            Optional<Role> parsed = Role.parse(args[3]);
            if (parsed.isEmpty() || parsed.get() == Role.AFK) {
                return message(sender, "game.join-invalid-role");
            }
            role = parsed.get();
        }
        List<Player> targets = selectPlayers(sender, args.length == 5 ? args[4] : "@s");
        if (targets == null) {
            return message(sender, "manhunt.game-join-usage");
        }
        if (targets.isEmpty()) {
            return message(sender, "command.no-targets");
        }
        Map<UUID, Role> before = new HashMap<>();
        for (Player target : targets) {
            before.put(target.getUniqueId(), playerStates.role(target));
        }
        int added = game.joinPlayers(instance.get(), targets, role);
        if (added == 0) {
            return message(sender, "game.join-no-change");
        }
        for (Player target : targets) {
            Role from = before.get(target.getUniqueId());
            Role after = playerStates.role(target);
            if (after != from) {
                game.announceRoleChange(target, from, after);
            }
        }
        message(sender, "game.join-success", Map.of("count", String.valueOf(added),
                "id", String.valueOf(instance.get().matchId()), "role", messages.roleName(role)));
        neutralSound(sender);
        game.updateAutostartState();
        return true;
    }

    /**
     * Removes players from a match: game leave [id] [selector]. Leaving
     * alive participants drop their gear and need a second run within 10
     * seconds; everyone else leaves at once.
     */
    private boolean gameLeave(CommandSender sender, String[] args) {
        if (args.length > 4) {
            return message(sender, "manhunt.game-leave-usage");
        }
        List<Player> targets = selectPlayers(sender, args.length == 4 ? args[3] : "@s");
        if (targets == null) {
            return message(sender, "manhunt.game-leave-usage");
        }
        if (targets.isEmpty()) {
            return message(sender, "command.no-targets");
        }
        Map<GameInstance, List<Player>> byMatch = groupLeaversByMatch(sender, targets, args);
        if (byMatch == null) {
            return true;
        }
        if (byMatch.isEmpty()) {
            return message(sender, "game.leave-not-in-match");
        }
        boolean needsConfirm = byMatch.values().stream().flatMap(List::stream)
                .anyMatch(player -> playerStates.role(player).isParticipant());
        if (needsConfirm && !confirms.confirm("gameleave:" + senderKey(sender))) {
            return message(sender, "game.leave-confirm");
        }
        int removed = removeLeavers(byMatch);
        Set<java.util.UUID> leaverIds = byMatch.values().stream().flatMap(List::stream)
                .map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
        if (!(sender instanceof Player self) || !leaverIds.contains(self.getUniqueId())) {
            message(sender, "game.leave-removed", Map.of("count", String.valueOf(removed)));
        }
        neutralSound(sender);
        game.updateAutostartState();
        return true;
    }

    /**
     * Groups leavers by match: the named instance, or each player's own.
     * Null when the named instance is invalid (message already sent).
     */
    private Map<GameInstance, List<Player>> groupLeaversByMatch(CommandSender sender, List<Player> targets,
            String[] args) {
        Map<GameInstance, List<Player>> byMatch = new LinkedHashMap<>();
        if (args.length >= 3) {
            Optional<GameInstance> instance = game.resolveInstance(args[2]);
            if (instance.isEmpty()) {
                message(sender, "manhunt.invalid-instance-id");
                return null;
            }
            List<Player> leavers = targets.stream()
                    .filter(player -> instance.get().isActive(player.getUniqueId())).toList();
            if (!leavers.isEmpty()) {
                byMatch.put(instance.get(), leavers);
            }
        } else {
            for (Player target : targets) {
                game.instanceOf(target.getUniqueId()).ifPresent(instance ->
                        byMatch.computeIfAbsent(instance, key -> new ArrayList<>()).add(target));
            }
        }
        return byMatch;
    }

    /** Removes grouped leavers from their matches, announcing role changes. */
    private int removeLeavers(Map<GameInstance, List<Player>> byMatch) {
        Map<java.util.UUID, Role> before = new HashMap<>();
        for (List<Player> leavers : byMatch.values()) {
            for (Player leaver : leavers) {
                before.put(leaver.getUniqueId(), playerStates.role(leaver));
            }
        }
        int removed = 0;
        for (Map.Entry<GameInstance, List<Player>> entry : byMatch.entrySet()) {
            removed += game.leaveMatch(entry.getKey(), entry.getValue(), entry.getKey().begun());
        }
        for (List<Player> leavers : byMatch.values()) {
            for (Player leaver : leavers) {
                Role from = before.get(leaver.getUniqueId());
                Role after = playerStates.role(leaver);
                if (after != from) {
                    game.announceRoleChange(leaver, from, after);
                }
            }
        }
        return removed;
    }

    /**
     * Resolves a player selector: null when the selector itself is broken,
     * an empty list when it matches no players.
     */
    private List<Player> selectPlayers(CommandSender sender, String selector) {
        List<Player> targets = new ArrayList<>();
        try {
            for (Entity entity : Bukkit.selectEntities(sender, selector)) {
                if (entity instanceof Player player) {
                    targets.add(player);
                }
            }
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return targets;
    }

    /** Stable confirm key for a sender: player uuid, or "console". */
    private static String senderKey(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getUniqueId().toString();
        }
        return "console";
    }

    /**
     * Views or changes configuration by category: /manhunt config
     * &lt;category&gt; &lt;path...&gt; [value]. Every argument drills one level
     * deeper and tab completion only suggests the children of the current
     * level. A path resolving to a section lists its settings; a path
     * resolving to an editable leaf views it, or sets it when a value follows.
     */
    private boolean configCommand(CommandSender sender, String[] args) {
        List<String> segments = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            segments.add(args[i]);
        }
        if (segments.isEmpty()) {
            Map<String, String> categories = new LinkedHashMap<>();
            for (String category : SettingRegistry.topCategories()) {
                categories.put(category, "");
            }
            listEntries(sender, "config", categories);
            neutralSound(sender);
            return true;
        }
        DrillResolve resolved = resolveDrill(segments, config::getStringList);
        if (resolved == null) {
            return message(sender, "manhunt.setting-invalid");
        }
        if (resolved.leaf()) {
            return showOrUpdateSetting(sender, resolved.path(), resolved.remainder());
        }
        if (SettingRegistry.isListPath(resolved.path())) {
            return listCommand(sender, resolved.path(), resolved.remainder());
        }
        if (!resolved.section() || !resolved.remainder().isEmpty()) {
            return message(sender, "manhunt.config-usage");
        }
        SettingRegistry.DrillChildren listing = SettingRegistry.children(resolved.path());
        Map<String, String> entries = new LinkedHashMap<>();
        for (String section : listing.sections()) {
            entries.put(section, "");
        }
        for (String leaf : listing.leaves()) {
            entries.put(leaf, ": " + ConfigService.displayValue(
                    game.getSettingValue(resolved.path() + "." + leaf)));
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
        String template = messages.string("manhunt.config-entry",
                "\n<green>» <white>{key}</white><gray>{suffix}</gray></white>");
        message(sender, "manhunt.config-list",
                Map.of("key", key, "entries", renderEntries(entries, template)));
    }

    public static String renderEntries(Map<String, String> entries, String entryTemplate) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            out.append(entryTemplate.replace("{key}", entry.getKey()).replace("{suffix}", entry.getValue()));
        }
        return out.toString();
    }

    /**
     * Browses or edits one string list: bare lists entries by index with the
     * add, remove, and reset forms; {@code add} appends, {@code remove}
     * deletes, {@code reset} restores the schema defaults.
     */
    private boolean listCommand(CommandSender sender, String listPath, List<String> args) {
        if (args.isEmpty()) {
            return listListEntries(sender, listPath);
        }
        if (args.get(0).equalsIgnoreCase("add")) {
            return listAddEntry(sender, listPath, args);
        }
        if (args.get(0).equalsIgnoreCase("remove") && args.size() == 2) {
            return listRemoveEntry(sender, listPath, args.get(1));
        }
        if (args.get(0).equalsIgnoreCase("reset") && args.size() == 1) {
            return listResetEntries(sender, listPath);
        }
        return message(sender, "manhunt.config-usage");
    }

    private boolean listListEntries(CommandSender sender, String listPath) {
        List<String> entries = config.getStringList(listPath);
        Map<String, String> rows = new LinkedHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            rows.put(String.valueOf(index), ": " + entries.get(index));
        }
        rows.put("add <value>", "");
        rows.put("remove <index>", "");
        rows.put("reset", "");
        listEntries(sender, listPath, rows);
        neutralSound(sender);
        return true;
    }

    private boolean listAddEntry(CommandSender sender, String listPath, List<String> args) {
        if (args.size() < 2) {
            return message(sender, "manhunt.config-usage");
        }
        ConfigService.SetOutcome outcome = config.listAdd(
                listPath, String.join(" ", args.subList(1, args.size())));
        if (!outcome.ok()) {
            feedback.failed(sender, outcome);
            return true;
        }
        feedback.listAdded(sender, listPath, outcome);
        return true;
    }

    private boolean listRemoveEntry(CommandSender sender, String listPath, String rawIndex) {
        int index;
        try {
            index = Integer.parseInt(rawIndex.trim());
        } catch (NumberFormatException expected) {
            message(sender, "manhunt.setting-index-invalid", Map.of("setting", listPath,
                    "index", rawIndex.trim(),
                    "size", String.valueOf(config.getStringList(listPath).size())));
            return true;
        }
        ConfigService.SetOutcome outcome = config.listRemove(listPath, index);
        if (!outcome.ok()) {
            feedback.failed(sender, outcome);
            return true;
        }
        feedback.listRemoved(sender, listPath, outcome);
        return true;
    }

    private boolean listResetEntries(CommandSender sender, String listPath) {
        ConfigService.SetOutcome outcome = config.listReset(listPath);
        if (!outcome.ok()) {
            feedback.failed(sender, outcome);
            return true;
        }
        feedback.listReset(sender, listPath, outcome);
        return true;
    }

    private boolean showOrUpdateSetting(CommandSender sender, String setting, List<String> values) {
        Object oldValue = game.getSettingValue(setting);
        if (values.isEmpty()) {
            message(sender, "manhunt.setting-status",
                    Map.of("setting", setting, "value", ConfigService.displayValue(oldValue)));
            neutralSound(sender);
            return true;
        }
        SettingDescriptor descriptor = config.describe(setting);
        boolean freeform = (descriptor != null && descriptor.type() == SettingType.STRING)
                || config.isIndexPath(setting);
        String raw;
        if (freeform) {
            raw = String.join(" ", values);
        } else if (values.size() > 1) {
            return message(sender, "manhunt.config-usage");
        } else {
            raw = values.get(0);
        }
        ConfigService.SetOutcome outcome = game.setSetting(setting, raw);
        if (!outcome.ok()) {
            feedback.failed(sender, outcome);
            return true;
        }
        feedback.scalarUpdated(sender, setting, outcome);
        if (setting.equals("world-engine.enabled")) {
            plugin.observeWorldEngine();
        }
        return true;
    }

    /**
     * Broadcasts a setting or modifier change to every online player except
     * the one who made it, when announcing is enabled. Changes made from the
     * console reach every online player.
     */
    static void announceSettingChange(MessageService messages, boolean announceEnabled,
            CommandSender sender, String messageKey, String keySlot, String valueSlot) {
        if (!announceEnabled) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player changer && online.getUniqueId().equals(changer.getUniqueId())) {
                continue;
            }
            messages.message(online, messageKey,
                    Map.of("player", sender.getName(), "key", keySlot, "value", valueSlot));
        }
    }


    /**
     * Resolves drill segments against the setting registry: each segment
     * matches a child case-insensitively, list indices descend into list
     * entries, and anything past a leaf is the value remainder.
     */
    public static DrillResolve resolveDrill(List<String> segments,
            Function<String, List<String>> lists) {
        String current = "";
        int consumed = 0;
        for (String segment : segments) {
            String match = matchChild(current, segment, lists);
            if (match == null) {
                break;
            }
            current = current.isEmpty() ? match : current + "." + match;
            consumed++;
            if (SettingRegistry.byPath(current) != null || isIndexPath(current, lists)) {
                break;
            }
        }
        if (consumed == 0) {
            return null;
        }
        boolean leaf = SettingRegistry.byPath(current) != null || isIndexPath(current, lists);
        boolean section = !leaf
                && (SettingRegistry.isSection(current) || SettingRegistry.isListPath(current));
        return new DrillResolve(current, leaf, section,
                List.copyOf(segments.subList(consumed, segments.size())));
    }

    private static String matchChild(String parent, String segment,
            Function<String, List<String>> lists) {
        if (!parent.isEmpty() && SettingRegistry.isListPath(parent)) {
            List<String> entries = lists == null ? null : lists.apply(parent);
            if (entries == null) {
                return null;
            }
            try {
                int index = Integer.parseInt(segment.trim());
                return index >= 0 && index < entries.size() ? String.valueOf(index) : null;
            } catch (NumberFormatException expected) {
                return null;
            }
        }
        List<String> candidates = new ArrayList<>();
        if (parent.isEmpty()) {
            candidates.addAll(SettingRegistry.topCategories());
        } else {
            SettingRegistry.DrillChildren children = SettingRegistry.children(parent);
            candidates.addAll(children.sections());
            candidates.addAll(children.leaves());
        }
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(segment)) {
                return candidate;
            }
        }
        return null;
    }

    /** True when the path addresses one list entry with a live index. */
    private static boolean isIndexPath(String path, Function<String, List<String>> lists) {
        int dot = path.lastIndexOf('.');
        if (dot == -1 || !SettingRegistry.isListPath(path.substring(0, dot))) {
            return false;
        }
        List<String> entries = lists == null ? null : lists.apply(path.substring(0, dot));
        if (entries == null) {
            return false;
        }
        try {
            int index = Integer.parseInt(path.substring(dot + 1).trim());
            return index >= 0 && index < entries.size();
        } catch (NumberFormatException expected) {
            return false;
        }
    }

    /**
     * Next-level completion options for the given prefix: child sections
     * and leaves, or list indices with add and remove under lists, so
     * completion never suggests dead ends.
     */
    public static List<String> drillChildren(List<String> prefix,
            Function<String, List<String>> lists) {
        String current = "";
        for (String segment : prefix) {
            String match = matchChild(current, segment, lists);
            if (match == null) {
                return List.of();
            }
            current = current.isEmpty() ? match : current + "." + match;
            if (SettingRegistry.byPath(current) != null || isIndexPath(current, lists)) {
                return List.of();
            }
        }
        List<String> options = new ArrayList<>();
        if (current.isEmpty()) {
            options.addAll(SettingRegistry.topCategories());
        } else if (SettingRegistry.isListPath(current)) {
            List<String> entries = lists == null ? null : lists.apply(current);
            if (entries != null) {
                for (int index = 0; index < entries.size(); index++) {
                    options.add(String.valueOf(index));
                }
            }
            options.add("add");
            options.add("remove");
            options.add("reset");
        } else {
            SettingRegistry.DrillChildren children = SettingRegistry.children(current);
            options.addAll(children.sections());
            options.addAll(children.leaves());
        }
        options.sort(String.CASE_INSENSITIVE_ORDER);
        return options;
    }

    private boolean worldEngine(CommandSender sender, String[] args) {
        if (!config.getBoolean("world-engine.enabled", false)) {
            return message(sender, "manhunt.worldengine-disabled");
        }
        if (args.length == 1 || args[1].isBlank()) {
            return message(sender, "manhunt.worldengine-usage");
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        if (!canUseWorldEngineAction(sender, sub)) {
            return message(sender, "command.no-permission");
        }
        if (sub.equals("lobbyconfig")) {
            return worldEngineLobbyConfig(sender, args);
        }
        if (sub.equals("tpto")) {
            return worldEngineTpto(sender, args);
        }
        if (sub.equals("cellindex")) {
            return worldEngineCellIndex(sender, args);
        }
        return message(sender, "manhunt.worldengine-usage");
    }

    private boolean worldEngineCellIndex(CommandSender sender, String[] args) {
        if (args.length < 3 || args[2].isBlank()) {
            return message(sender, "manhunt.worldengine-cellindex-usage");
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("buffer")) {
            return cellIndexBuffer(sender, args);
        }
        if (action.equals("get")) {
            return cellIndexGet(sender, args);
        }
        if (action.equals("set")) {
            return cellIndexSet(sender, args);
        }
        return message(sender, "manhunt.worldengine-cellindex-usage");
    }

    /** Lists the buffered world-engine cell indexes. */
    private boolean cellIndexBuffer(CommandSender sender, String[] args) {
        if (args.length > 3) {
            return message(sender, "manhunt.worldengine-cellindex-usage");
        }
        message(sender, "manhunt.worldengine-cellindex-buffer-header");
        for (long index : game.bufferedCellIndexes()) {
            message(sender, "manhunt.worldengine-cellindex-buffer-entry",
                    Map.of("index", String.valueOf(index)));
        }
        neutralSound(sender);
        return true;
    }

    /** Shows the current world-engine cell index. */
    private boolean cellIndexGet(CommandSender sender, String[] args) {
        if (args.length > 3) {
            return message(sender, "manhunt.worldengine-cellindex-usage");
        }
        OptionalLong index = game.cellIndex();
        if (index.isEmpty()) {
            return message(sender, "manhunt.worldengine-cellindex-unavailable");
        }
        message(sender, "manhunt.worldengine-cellindex-get",
                Map.of("index", String.valueOf(index.getAsLong())));
        neutralSound(sender);
        return true;
    }

    /** Sets the world-engine cell index, clamped to the cap. */
    private boolean cellIndexSet(CommandSender sender, String[] args) {
        if (args.length != 4) {
            return message(sender, "manhunt.worldengine-cellindex-usage");
        }
        long max = game.cellIndexCap();
        long value;
        try {
            value = Long.parseLong(args[3].trim());
        } catch (NumberFormatException exception) {
            message(sender, "manhunt.worldengine-cellindex-invalid",
                    Map.of("max", String.valueOf(max)));
            return true;
        }
        OptionalLong before = game.cellIndex();
        long clamped = WorldEngineService.clampCellIndex(value, max);
        if (!game.cellIndex(clamped)) {
            return message(sender, "manhunt.worldengine-cellindex-unavailable");
        }
        message(sender, "manhunt.worldengine-cellindex-set", Map.of("index", String.valueOf(clamped),
                "was", before.isPresent() ? String.valueOf(before.getAsLong()) : "none",
                "max", String.valueOf(max)));
        neutralSound(sender);
        return true;
    }

    /**
     * Stores a lobby teleport: setlobbytp &lt;lobby-id&gt;
     * [x y z yaw pitch]. Coords are all-or-none; omitted coords use the
     * sender's position, so the console must pass all five. The
     * position path requires standing in the lobby world; explicit
     * coords skip that check.
     */
    private boolean lobbyConfigSetTp(CommandSender sender, String[] args) {
        if (args.length != 4 && args.length != 9) {
            return message(sender, "manhunt.worldengine-lobbyconfig-setlobbytp-usage");
        }
        OptionalInt lobbyId = LobbyService.parseId(args[3]);
        if (lobbyId.isEmpty()) {
            return message(sender, "manhunt.lobby-invalid-id");
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId.getAsInt() != 0) {
            return message(sender, "manhunt.lobby-worldengine-required");
        }
        LobbyTpTarget target = resolveLobbyTpTarget(sender, args);
        if (target == null) {
            return true;
        }
        saveLobbyTp(lobbyId.getAsInt(), target.location());
        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (game.instanceOf(online.getUniqueId()).isEmpty()) {
                targets.add(online);
            }
        }
        lobbyTeleporter.setSpawnToLobby(targets, lobbyId.getAsInt());
        message(sender, "manhunt.worldengine-lobbyconfig-setlobbytp-success", Map.of(
                "lobby", String.valueOf(lobbyId.getAsInt()), "location", target.shown()));
        neutralSound(sender);
        return true;
    }

    /**
     * Resolves the setlobbytp target: explicit coords, or the sender's
     * spot in the lobby world. Null when invalid (message already sent).
     */
    private LobbyTpTarget resolveLobbyTpTarget(CommandSender sender, String[] args) {
        if (args.length == 9) {
            double[] coords = parseLobbyTpCoords(args[4], args[5], args[6], args[7], args[8]);
            if (coords == null) {
                message(sender, "manhunt.worldengine-invalid-location");
                return null;
            }
            Location location = new Location(null, coords[0], coords[1], coords[2],
                    (float) coords[3], (float) coords[4]);
            return new LobbyTpTarget(location,
                    formatCoords(coords[0], coords[1], coords[2], coords[3], coords[4]));
        }
        if (!(sender instanceof Player player)) {
            message(sender, "command.player-only");
            return null;
        }
        String lobbyWorld = game.lobbyWorldName();
        if (player.getWorld() == null || !player.getWorld().getName().equals(lobbyWorld)) {
            message(sender, "manhunt.worldengine-lobbyconfig-setlobbytp-wrong-world",
                    Map.of("world", lobbyWorld));
            return null;
        }
        Location location = player.getLocation();
        return new LobbyTpTarget(location, formatLocation(location));
    }

    /** A resolved setlobbytp target: where, plus how to show it. */
    private record LobbyTpTarget(Location location, String shown) {
    }

    /**
     * Parses setlobbytp coords: x, y, z, yaw, pitch. Null when any part
     * is not a number. Pure for tests.
     */
    static double[] parseLobbyTpCoords(String xText, String yText, String zText,
            String yawText, String pitchText) {
        try {
            return new double[]{
                    Double.parseDouble(xText.trim()),
                    Double.parseDouble(yText.trim()),
                    Double.parseDouble(zText.trim()),
                    Double.parseDouble(yawText.trim()),
                    Double.parseDouble(pitchText.trim())};
        } catch (NumberFormatException expected) {
            return null;
        }
    }

    private static String formatCoords(double x, double y, double z, double yaw, double pitch) {
        return String.format("%.2f, %.2f, %.2f, %.2f, %.2f", x, y, z, yaw, pitch);
    }

    /**
     * Manages lobby teleports and boundary boxes in the lobby store:
     * pos1|pos2 records the executing player's feet block as a bounds
     * corner, setbounds stores both corners, setlobbytp stores a
     * teleport, and deletelobby removes the whole entry.
     */
    private boolean worldEngineLobbyConfig(CommandSender sender, String[] args) {
        if (args.length < 3 || args[2].isBlank()) {
            return message(sender, "manhunt.worldengine-lobbyconfig-usage");
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "pos1", "pos2" -> {
                return lobbyConfigPos(sender, action.equals("pos1"), args);
            }
            case "setbounds" -> {
                return lobbyConfigSetBounds(sender, args);
            }
            case "setlobbytp" -> {
                return lobbyConfigSetTp(sender, args);
            }
            case "deletelobby" -> {
                return lobbyConfigDelete(sender, args);
            }
            default -> {
                return message(sender, "manhunt.worldengine-lobbyconfig-usage");
            }
        }
    }

    private boolean lobbyConfigPos(CommandSender sender, boolean first, String[] args) {
        if (!(sender instanceof Player player)) {
            return message(sender, "command.player-only");
        }
        if (args.length != 3) {
            return message(sender, "manhunt.worldengine-lobbyconfig-usage");
        }
        (first ? boundPos1 : boundPos2).put(player.getUniqueId(), player.getLocation().clone());
        message(sender, first ? "manhunt.worldengine-lobbyconfig-pos1"
                        : "manhunt.worldengine-lobbyconfig-pos2",
                Map.of("pos", blockCoords(player.getLocation())));
        neutralSound(sender);
        return true;
    }

    /**
     * Stores both recorded corners as a lobby's bounds. Player-only,
     * since the corners come from the sender. Overwriting existing
     * bounds needs a second run within 10 seconds.
     */
    private boolean lobbyConfigSetBounds(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return message(sender, "command.player-only");
        }
        if (args.length != 4) {
            return message(sender, "manhunt.worldengine-lobbyconfig-setbounds-usage");
        }
        OptionalInt lobbyId = LobbyService.parseId(args[3]);
        if (lobbyId.isEmpty()) {
            return message(sender, "manhunt.lobby-invalid-id");
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId.getAsInt() != 0) {
            return message(sender, "manhunt.lobby-worldengine-required");
        }
        Location first = boundPos1.get(player.getUniqueId());
        Location second = boundPos2.get(player.getUniqueId());
        if (first == null || second == null) {
            return message(sender, "manhunt.worldengine-lobbyconfig-need-selection");
        }
        if (first.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            return message(sender, "manhunt.worldengine-lobbyconfig-world-mismatch");
        }
        LobbyConfig lobbyConfig = plugin.lobbyConfig();
        OptionalInt duplicate = LobbyBounds.duplicateOf(lobbyConfig.getLobbies(), lobbyId.getAsInt(),
                first.getBlockX(), first.getBlockY(), first.getBlockZ(),
                second.getBlockX(), second.getBlockY(), second.getBlockZ());
        if (duplicate.isPresent()) {
            message(sender, "manhunt.worldengine-lobbyconfig-duplicate-bounds",
                    Map.of("other", String.valueOf(duplicate.getAsInt())));
            return true;
        }
        LobbyConfig.LobbyEntry entry =
                lobbyConfig.getLobbies().get(String.valueOf(lobbyId.getAsInt()));
        boolean hasBounds = entry != null && entry.getBounds() != null
                && entry.getBounds().getPos1() != null && entry.getBounds().getPos2() != null;
        if (hasBounds && !confirms.confirm(
                "lobbyconfig-setbounds:" + senderKey(sender) + ":" + lobbyId.getAsInt())) {
            message(sender, "manhunt.worldengine-lobbyconfig-setbounds-confirm",
                    Map.of("lobby", String.valueOf(lobbyId.getAsInt())));
            return true;
        }
        writeLobbyBounds(sender, lobbyId.getAsInt(), first, second);
        return true;
    }

    /** Writes one lobby's bounds box from two corners and reports it. */
    private void writeLobbyBounds(CommandSender sender, int lobbyId, Location first, Location second) {
        LobbyConfig lobbyConfig = plugin.lobbyConfig();
        LobbyConfig.LobbyEntry entry =
                lobbyConfig.getLobbies().get(String.valueOf(lobbyId));
        if (entry == null) {
            entry = new LobbyConfig.LobbyEntry();
            lobbyConfig.getLobbies().put(String.valueOf(lobbyId), entry);
        }
        LobbyConfig.BoundsData bounds = new LobbyConfig.BoundsData();
        bounds.setPos1(LobbyConfig.Position.of(first.getBlockX(), first.getBlockY(), first.getBlockZ()));
        bounds.setPos2(LobbyConfig.Position.of(second.getBlockX(), second.getBlockY(), second.getBlockZ()));
        entry.setBounds(bounds);
        lobbyConfig.save();
        message(sender, "manhunt.worldengine-lobbyconfig-setbounds-success", Map.of(
                "lobby", String.valueOf(lobbyId),
                "from", blockCoords(first), "to", blockCoords(second)));
        neutralSound(sender);
    }

    /**
     * Deletes a lobby entry (teleport plus bounds) from the lobby
     * store. Live lobbies, members, and matches are untouched.
     * Console-capable. Needs a second run within 10 seconds.
     */
    private boolean lobbyConfigDelete(CommandSender sender, String[] args) {
        if (args.length != 4) {
            return message(sender, "manhunt.worldengine-lobbyconfig-deletelobby-usage");
        }
        OptionalInt lobbyId = LobbyService.parseId(args[3]);
        if (lobbyId.isEmpty()) {
            return message(sender, "manhunt.lobby-invalid-id");
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId.getAsInt() != 0) {
            return message(sender, "manhunt.lobby-worldengine-required");
        }
        LobbyConfig lobbyConfig = plugin.lobbyConfig();
        if (!lobbyConfigHasLobby(lobbyConfig, lobbyId.getAsInt())) {
            message(sender, "manhunt.worldengine-lobbyconfig-deletelobby-missing",
                    Map.of("lobby", String.valueOf(lobbyId.getAsInt())));
            return true;
        }
        if (!confirms.confirm(
                "lobbyconfig-delete:" + senderKey(sender) + ":" + lobbyId.getAsInt())) {
            message(sender, "manhunt.worldengine-lobbyconfig-deletelobby-confirm",
                    Map.of("lobby", String.valueOf(lobbyId.getAsInt())));
            return true;
        }
        lobbyConfig.getLobbies().remove(String.valueOf(lobbyId.getAsInt()));
        lobbyConfig.save();
        message(sender, "manhunt.worldengine-lobbyconfig-deletelobby-success",
                Map.of("lobby", String.valueOf(lobbyId.getAsInt())));
        neutralSound(sender);
        return true;
    }

    /**
     * True when the lobby store defines the index. A lobby counts as
     * existing with an index alone, even without a teleport or bounds.
     * Pure for tests.
     */
    static boolean lobbyConfigHasLobby(LobbyConfig lobbyConfig, int lobbyId) {
        return lobbyConfig != null && lobbyConfig.getLobbies() != null
                && lobbyConfig.getLobbies().containsKey(String.valueOf(lobbyId));
    }

    /** Smallest lobby id without bounds yet. Pure for tests. */
    static int nextFreeBoundsId(Set<Integer> boundedIds) {
        int id = 0;
        while (boundedIds.contains(id)) {
            id++;
        }
        return id;
    }

    private static String blockCoords(Location corner) {
        return corner.getBlockX() + ", " + corner.getBlockY() + ", " + corner.getBlockZ();
    }

    private boolean reload(CommandSender sender) {
        plugin.reload();
        game.validateLobbyWorldName();
        boolean result = message(sender, "manhunt.reload-success");
        neutralSound(sender);
        return result;
    }

    private boolean debug(CommandSender sender, String[] args) {
        boolean enabled;
        if (args.length == 1) {
            enabled = toggleDebug(sender);
        } else if (args.length == 2 && args[1].equalsIgnoreCase("on")) {
            enabled = setDebug(sender, true);
        } else if (args.length == 2 && args[1].equalsIgnoreCase("off")) {
            enabled = setDebug(sender, false);
        } else {
            return message(sender, "manhunt.debug-usage");
        }
        message(sender, enabled ? "manhunt.debug-enabled" : "manhunt.debug-disabled");
        neutralSound(sender);
        return true;
    }

    /** Developer tools. Only schem exists for now; usage covers the rest. */
    private boolean dev(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("schem")) {
            return message(sender, "dev.usage");
        }
        return devSchem.execute(sender, java.util.Arrays.copyOfRange(args, 2, args.length));
    }

    private boolean modifiers(CommandSender sender, String[] args) {
        return modifiersCmd.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
    }

    private boolean toggleDebug(CommandSender sender) {
        if (sender instanceof Player player) {
            return debugService.togglePlayer(player.getUniqueId());
        }
        return debugService.toggleConsole();
    }

    private boolean setDebug(CommandSender sender, boolean enabled) {
        if (sender instanceof Player player) {
            return debugService.setPlayerEnabled(player.getUniqueId(), enabled);
        }
        return debugService.setConsoleEnabled(enabled);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Only suggest subcommands the sender may actually run.
            List<String> options = subcommandOptions();
            options.removeIf(option -> !canUseSubcommand(sender, option));
            return partial(args[0], options);
        }
        List<String> completion = completeStatusStartEndTab(sender, args);
        if (completion == null) {
            completion = completeGameTab(args);
        }
        if (completion == null) {
            completion = completeDevDebugTab(args);
        }
        if (args.length >= 2
                && args[0].equalsIgnoreCase("config")) {
            return completeDrill(args);
        }
        if (completion == null) {
            completion = completeWorldEngineTab(sender, args);
        }
        if (completion == null) {
            completion = completeSetPlayerTab(args);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("quickstart") || args[0].equalsIgnoreCase("qs"))) {
            return partial(args[1], List.of("50"));
        }
        if (completion == null) {
            completion = completeLobbyTab(args);
        }
        if (completion == null) {
            completion = completeModifiersTab(args);
        }
        return completion == null ? List.of() : completion;
    }

    /** Tab completion for modifiers toggles. Null when inapplicable. */
    private List<String> completeModifiersTab(String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("modifiers")) {
            return partial(args[1], List.of("setmod", "setpreset", "export", "import", "create"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("modifiers")) {
            if (args[1].equalsIgnoreCase("setmod")) {
                return partial(args[2], modifiersCmd.modifierNameOptions());
            }
            if (args[1].equalsIgnoreCase("setpreset")) {
                return partial(args[2], modifiersCmd.presetIdOptions());
            }
            if (args[1].equalsIgnoreCase("export") || args[1].equalsIgnoreCase("import")
                    || args[1].equalsIgnoreCase("create")) {
                return partial(args[2], List.of("modifier", "preset"));
            }
            return null;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("modifiers")
                && args[1].equalsIgnoreCase("create")) {
            return completeCreateTab(args);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("modifiers")
                && (args[1].equalsIgnoreCase("setmod") || args[1].equalsIgnoreCase("setpreset"))) {
            return partial(args[3], List.of("true", "false"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("modifiers")
                && args[1].equalsIgnoreCase("export")) {
            if (args[2].equalsIgnoreCase("preset")) {
                return partial(args[3], modifiersCmd.presetIdOptions());
            }
            return partial(args[3], modifiersCmd.modifierNameOptions());
        }
        return null;
    }

    /** Flag and flag-value completion for modifiers create. */
    private List<String> completeCreateTab(String[] args) {
        boolean preset = args[2].equalsIgnoreCase("preset");
        String current = args[args.length - 1];
        if (current.startsWith("--")) {
            return partial(current, ModifierCreateArgs.flagsFor(preset));
        }
        String previous = args[args.length - 2].toLowerCase(Locale.ROOT);
        return switch (previous) {
            case "--trigger" -> partial(current, ModifierTriggers.KNOWN);
            case "--member" -> partial(current, modifiersCmd.modifierNameOptions());
            case "--on-start", "--selection" -> partial(current, List.of("IN_ORDER", "PICK_RANDOM"));
            case "--interval-scope", "--chance-scope", "--pick-scope" ->
                    partial(current, List.of("PER_INVOKE", "PER_EXECUTOR"));
            default -> null;
        };
    }

    /** Tab completion for status, start, and end. Null when inapplicable. */
    private List<String> completeStatusStartEndTab(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
            if (!canUseStatusArgs(sender)) {
                return List.of();
            }
            List<String> options = new ArrayList<>(List.of("all"));
            options.addAll(instanceIdOptions());
            return partial(args[1], options);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return partial(args[1], lobbyIdOptions());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("end")) {
            List<String> options = new ArrayList<>(List.of("-i", "-immediate"));
            options.addAll(instanceIdOptions());
            return partial(args[1], options);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("end")) {
            if (isImmediateFlag(args[1])) {
                return partial(args[2], instanceIdOptions());
            }
            return partial(args[2], List.of("-i", "-immediate"));
        }
        return null;
    }

    /** Tab completion for game join and leave. Null when inapplicable. */
    private List<String> completeGameTab(String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("game")) {
            return partial(args[1], List.of("join", "leave"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("game")
                && (args[1].equalsIgnoreCase("join") || args[1].equalsIgnoreCase("leave"))) {
            return partial(args[2], instanceIdOptions());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("join")) {
            return partial(args[3], List.of("hunter", "speedrunner", "spectator", "none"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("leave")) {
            return partial(args[3], selectorOptions());
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("join")) {
            return partial(args[4], selectorOptions());
        }
        return null;
    }

    /** Tab completion for dev and debug. Null when inapplicable. */
    private List<String> completeDevDebugTab(String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("dev")) {
            return partial(args[1], List.of("schem"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("dev") && args[1].equalsIgnoreCase("schem")) {
            return partial(args[2], List.of("pos1", "pos2", "save", "load", "list"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("dev") && args[1].equalsIgnoreCase("schem")
                && args[2].equalsIgnoreCase("load")) {
            return partial(args[3], devSchem.schematicNames());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return partial(args[1], List.of("on", "off"));
        }
        return null;
    }

    /** Tab completion for worldengine actions. Null when inapplicable. */
    private List<String> completeWorldEngineTab(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("worldengine")) {
            List<String> actions = new ArrayList<>(List.of("lobbyconfig", "cellindex", "tpto"));
            actions.removeIf(action -> !canUseWorldEngineAction(sender, action));
            return partial(args[1], actions);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("worldengine")
                && args[1].equalsIgnoreCase("lobbyconfig")) {
            return completeWorldEngineLobbyConfigTab(sender, args);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("cellindex")) {
            if (!canUseWorldEngineAction(sender, "cellindex")) {
                return List.of();
            }
            return partial(args[2], List.of("get", "set", "buffer"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("tpto")) {
            return partial(args[2], List.of("lobbyworld", "gameworld"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("tpto")) {
            return partial(args[3], selectorOptions());
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("tpto")
                && args[2].equalsIgnoreCase("lobbyworld")) {
            return partial(args[4], lobbyPresetOptions());
        }
        return null;
    }

    /** Tab completion for worldengine lobbyconfig. Null when inapplicable. */
    private List<String> completeWorldEngineLobbyConfigTab(CommandSender sender, String[] args) {
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine")
                && args[1].equalsIgnoreCase("lobbyconfig")) {
            if (!canUseWorldEngineAction(sender, "lobbyconfig")) {
                return List.of();
            }
            return partial(args[2], List.of("pos1", "pos2", "setbounds", "setlobbytp", "deletelobby"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("worldengine")
                && args[1].equalsIgnoreCase("lobbyconfig") && args[2].equalsIgnoreCase("setbounds")) {
            if (!canUseWorldEngineAction(sender, "lobbyconfig")) {
                return List.of();
            }
            return partial(args[3], lobbyBoundsSetOptions());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("worldengine")
                && args[1].equalsIgnoreCase("lobbyconfig") && args[2].equalsIgnoreCase("setlobbytp")) {
            if (!canUseWorldEngineAction(sender, "lobbyconfig")) {
                return List.of();
            }
            return partial(args[3], lobbyIdOptions());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("worldengine")
                && args[1].equalsIgnoreCase("lobbyconfig") && args[2].equalsIgnoreCase("deletelobby")) {
            if (!canUseWorldEngineAction(sender, "lobbyconfig")) {
                return List.of();
            }
            return partial(args[3], lobbyConfigIdOptions());
        }
        return null;
    }

    /** Tab completion for setplayer. Null when inapplicable. */
    private List<String> completeSetPlayerTab(String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("setplayer")) {
            if (args[1].startsWith("@a[")) {
                return partial(args[1], List.of("@a[distance=", "@a[limit=", "@a[name=", "@a[gamemode="));
            }
            return partial(args[1], selectorOptions());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setplayer")) {
            return partial(args[2], List.of("hunter", "speedrunner", "spectator", "afk", "none"));
        }
        if ((args.length == 4 || args.length == 5) && args[0].equalsIgnoreCase("setplayer")) {
            return partial(args[args.length - 1], List.of("-f", "-force", "-s", "-silent"));
        }
        return null;
    }

    /** Tab completion for lobby join and leave. Null when inapplicable. */
    private List<String> completeLobbyTab(String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("lobby")) {
            return partial(args[1], List.of("join", "leave"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return partial(args[2], selectorOptions());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("leave")) {
            return partial(args[2], selectorOptions());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return partial(args[3], lobbyIdOptions());
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return partial(args[4], List.of("hunter", "speedrunner", "spectator", "afk", "none",
                    "-f", "-force", "-notp", "-s", "-silent"));
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return partial(args[5], List.of("-f", "-force", "-notp", "-s", "-silent"));
        }
        if (args.length == 7 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return partial(args[6], List.of("-f", "-force", "-notp", "-s", "-silent"));
        }
        if (args.length == 8 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return partial(args[7], List.of("-f", "-force", "-notp", "-s", "-silent"));
        }
        return null;
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
        if (!prefix.isEmpty() && prefix.get(prefix.size() - 1).equalsIgnoreCase("remove")) {
            DrillResolve parent = resolveDrill(
                    prefix.subList(0, prefix.size() - 1), config::getStringList);
            if (parent != null && SettingRegistry.isListPath(parent.path())
                    && parent.remainder().isEmpty()) {
                List<String> indices = new ArrayList<>();
                for (int index = 0; index < config.getStringList(parent.path()).size(); index++) {
                    indices.add(String.valueOf(index));
                }
                return partial(completing, indices);
            }
            return List.of();
        }
        if (prefix.isEmpty()) {
            return partial(completing, SettingRegistry.topCategories());
        }
        DrillResolve resolved = resolveDrill(prefix, config::getStringList);
        if (resolved != null && resolved.leaf() && resolved.remainder().isEmpty()) {
            SettingDescriptor descriptor = config.describe(resolved.path());
            if (descriptor != null && descriptor.type() == SettingType.BOOL) {
                return partial(completing, List.of("true", "false"));
            }
            if (descriptor != null && descriptor.type() == SettingType.OPTION) {
                return partial(completing, descriptor.options());
            }
            Object defaultValue = config.defaultValue(resolved.path());
            if (defaultValue != null) {
                return partial(completing, List.of(ConfigService.displayValue(defaultValue)));
            }
            return List.of();
        }
        return partial(completing, drillChildren(prefix, config::getStringList));
    }

    /** Lobby id completion: live lobby ids, falling back to 0 when none exist. */
    private List<String> lobbyIdOptions() {
        List<String> ids = new ArrayList<>(lobbies.lobbyIds().stream().sorted().map(String::valueOf).toList());
        if (ids.isEmpty()) {
            ids.add("0");
        }
        return ids;
    }

    /**
     * Lobby id completion for lobbyconfig setbounds: the next id
     * without bounds first, then live lobby ids. Any valid id stays
     * accepted.
     */
    private List<String> lobbyBoundsSetOptions() {
        Set<Integer> bounded = new HashSet<>();
        LobbyConfig lobbyConfig = plugin.lobbyConfig();
        if (lobbyConfig != null && lobbyConfig.getLobbies() != null) {
            for (Map.Entry<String, LobbyConfig.LobbyEntry> entry : lobbyConfig.getLobbies().entrySet()) {
                int id;
                try {
                    id = Integer.parseInt(entry.getKey().trim());
                } catch (NumberFormatException expected) {
                    continue;
                }
                LobbyConfig.LobbyEntry value = entry.getValue();
                if (id >= 0 && value != null && value.getBounds() != null
                        && value.getBounds().getPos1() != null && value.getBounds().getPos2() != null) {
                    bounded.add(id);
                }
            }
        }
        List<String> options = new ArrayList<>();
        options.add(String.valueOf(nextFreeBoundsId(bounded)));
        for (String id : lobbyIdOptions()) {
            if (!options.contains(id)) {
                options.add(id);
            }
        }
        return options;
    }

    /**
     * Lobby id completion for lobbyconfig deletelobby: indexes defined
     * in the lobby store, sorted. Empty when none are defined.
     */
    private List<String> lobbyConfigIdOptions() {
        List<Integer> ids = new ArrayList<>();
        LobbyConfig lobbyConfig = plugin.lobbyConfig();
        if (lobbyConfig != null && lobbyConfig.getLobbies() != null) {
            for (String key : lobbyConfig.getLobbies().keySet()) {
                try {
                    int id = Integer.parseInt(key.trim());
                    if (id >= 0) {
                        ids.add(id);
                    }
                } catch (NumberFormatException expected) {
                    // Skip non-numeric indexes.
                }
            }
        }
        return ids.stream().sorted().map(String::valueOf).toList();
    }

    /**
     * Top-level subcommand completion, in display order. The client shows
     * these bottom-up, so the source lists them reversed to read properly
     * in game.
     */
    /**
     * First-level completions. {@code dev} stays out on purpose (developer
     * tooling), but everything after a typed {@code dev} still completes.
     */
    static List<String> subcommandOptions() {
        return new ArrayList<>(List.of("challenges", "help", "reload", "worldengine", "config",
                "modifiers", "debug", "lobby", "qs", "quickstart", "game", "end", "start",
                "setplayer", "setup", "status"));
    }

    /** Instance id completion: live match ids, oldest first. */
    private List<String> instanceIdOptions() {
        return game.liveInstances().stream().map(instance -> String.valueOf(instance.matchId())).toList();
    }

    /** Selector completion: vanilla selectors plus online player names. */
    private static List<String> selectorOptions() {
        List<String> selectors = new ArrayList<>(List.of("@a", "@r", "@s", "@p"));
        Bukkit.getOnlinePlayers().forEach(player -> selectors.add(player.getName()));
        return selectors;
    }

    private List<String> partial(String value, List<String> options) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    /**
     * Teleports to the lobby world or the game world: tpto
     * &lt;lobbyworld|gameworld&gt; [selector] [preset]. The lobby world is
     * generated on a confirmed second run when it does not exist yet. The
     * preset only applies to lobbyworld fresh generation and is ignored
     * once the world exists.
     */
    private boolean worldEngineTpto(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 5) {
            return message(sender, "manhunt.worldengine-tpto-usage");
        }
        Optional<TptoTarget> target = parseTptoTarget(args[2]);
        if (target.isEmpty()) {
            return message(sender, "manhunt.worldengine-tpto-usage");
        }
        if (args.length == 5 && target.get() != TptoTarget.LOBBY) {
            return message(sender, "manhunt.worldengine-tpto-usage");
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
                return message(sender, "command.player-only");
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
                return message(sender, "manhunt.worldengine-invalid-selector");
            }
            if (targets.isEmpty()) {
                return message(sender, "manhunt.worldengine-no-targets");
            }
        }
        if (target.get() == TptoTarget.GAME) {
            return worldEngineTptoGame(sender, targets);
        }
        return worldEngineTptoLobby(sender, targets, preset);
    }

    /**
     * Invalid preset notice, naming the valid presets. Falls back to the
     * usage line when the key is missing (pre-existing messages.yml files
     * predate it and get no migration).
     */
    private boolean worldEngineTptoInvalidPreset(CommandSender sender) {
        if (messages.string("manhunt.worldengine-tpto-invalid-preset", null) == null) {
            return message(sender, "manhunt.worldengine-tpto-usage");
        }
        message(sender, "manhunt.worldengine-tpto-invalid-preset",
                Map.of("presets", String.join(", ", lobbyPresetOptions())));
        return true;
    }

    /**
     * Preset names for completion and error text: the configured
     * lobby-presets keys that name a real preset, else every preset.
     */
    private List<String> lobbyPresetOptions() {
        List<String> options = new ArrayList<>();
        for (String key : config.lobbyPresetKeys()) {
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
            message(sender, "manhunt.worldengine-tpto-no-world", Map.of("world", worldName));
            return true;
        }
        Location spawn = world.getSpawnLocation();
        for (Player target : targets) {
            target.teleport(spawn);
        }
        message(sender, "manhunt.worldengine-tpto-success",
                Map.of("count", String.valueOf(targets.size()), "world", worldName));
        neutralSound(sender);
        return true;
    }

    /** Teleports targets to the lobby world, generating it once confirmed. */
    private boolean worldEngineTptoLobby(CommandSender sender, List<Player> targets, Optional<LobbyPreset> preset) {
        if (game.lobbyWorldNameClashes()) {
            return message(sender, "manhunt.worldengine-tpto-lobby-world-clash");
        }
        String worldName = game.lobbyWorldName();
        String senderKey = sender instanceof Player player ? player.getUniqueId().toString() : "console";
        if (!game.lobbyWorldExists()) {
            if (!game.confirmLobbyGeneration(senderKey)) {
                message(sender, "manhunt.worldengine-tpto-confirm",
                        Map.of("world", worldName, "seconds", "10"));
                return true;
            }
            message(sender, "manhunt.worldengine-tpto-creating", Map.of("world", worldName));
        }
        Optional<LobbyWorld> ensured = game.ensureLobbyWorld(preset);
        if (ensured.isEmpty()) {
            message(sender, "manhunt.worldengine-tpto-failed", Map.of("world", worldName));
            return true;
        }
        World world = ensured.get().world();
        if (ensured.get().lobbyZeroSet()) {
            message(sender, "manhunt.worldengine-lobbyconfig-setlobbytp-success", Map.of(
                    "lobby", "0", "location", formatLocation(world.getSpawnLocation())));
        }
        Location spawn = world.getSpawnLocation();
        for (Player target : targets) {
            target.teleport(spawn);
        }
        message(sender, "manhunt.worldengine-tpto-success",
                Map.of("count", String.valueOf(targets.size()), "world", worldName));
        neutralSound(sender);
        return true;
    }

    /** tpto destination words. */
    enum TptoTarget {
        LOBBY,
        GAME
    }

    static Optional<TptoTarget> parseTptoTarget(String raw) {
        if (raw.equalsIgnoreCase("lobbyworld")) {
            return Optional.of(TptoTarget.LOBBY);
        }
        if (raw.equalsIgnoreCase("gameworld")) {
            return Optional.of(TptoTarget.GAME);
        }
        return Optional.empty();
    }

    /**
     * Saves a lobby teleport in the lobby store. The world is never
     * stored: teleports always resolve in the lobby world.
     */
    private void saveLobbyTp(int lobbyId, Location location) {
        LobbyConfig lobbyConfig = plugin.lobbyConfig();
        LobbyConfig.LobbyEntry entry = lobbyConfig.getLobbies()
                .computeIfAbsent(String.valueOf(lobbyId), key -> new LobbyConfig.LobbyEntry());
        entry.setLobbytp(LobbyConfig.LobbyTp.of(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()));
        lobbyConfig.save();
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

    private boolean quickStart(CommandSender sender, String[] args) {
        QuickStartArgs parsed = parseQuickStartArgs(args);
        if (!parsed.valid()) {
            return message(sender, "manhunt.quickstart-usage");
        }
        int percent = parsed.percent() == null ? -1 : parsed.percent();
        if (parsed.percent() != null && (percent < 0 || percent > 100)) {
            return message(sender, "manhunt.quickstart-invalid-percent");
        }
        int lobbyId;
        if (sender instanceof Player player) {
            lobbyId = lobbies.lobbyOf(player.getUniqueId()).map(Lobby::id)
                    .orElseGet(() -> lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0);
        } else {
            lobbyId = lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0;
        }
        if (lobbyId < 0) {
            return message(sender, "manhunt.quickstart-failed");
        }
        if (game.instanceForLobby(lobbyId).isPresent()) {
            return message(sender, "manhunt.already-active");
        }
        Location surroundOrigin = sender instanceof Player executor ? executor.getLocation() : null;
        QuickStartOutcome outcome = game.quickStart(percent, lobbyId, surroundOrigin);
        if (!outcome.started()) {
            return message(sender, "manhunt.quickstart-failed");
        }
        return true;
    }


    static QuickStartArgs parseQuickStartArgs(String[] args) {
        if (args.length > 2) {
            return new QuickStartArgs(null, false);
        }
        if (args.length == 1) {
            return new QuickStartArgs(null, true);
        }
        try {
            return new QuickStartArgs(Integer.parseInt(args[1]), true);
        } catch (NumberFormatException exception) {
            return new QuickStartArgs(null, false);
        }
    }

    private boolean message(CommandSender sender, String key) { messages.message(sender, key); return true; }
    private void message(CommandSender sender, String key, Map<String, String> values) {
        messages.message(sender, key, values);
    }
    private void neutralSound(CommandSender sender) {
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
    }
}
