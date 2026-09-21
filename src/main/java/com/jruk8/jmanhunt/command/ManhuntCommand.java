package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.DurationFormat;
import com.jruk8.jmanhunt.core.DebugService;
import com.jruk8.jmanhunt.core.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.LobbyWorld;
import com.jruk8.jmanhunt.lobby.MidMatchPolicy;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.QuickStartOutcome;
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
import com.jruk8.jmanhunt.world.WorldEngineService;
import java.util.ArrayList;
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
import java.util.TreeSet;
import java.util.UUID;

public final class ManhuntCommand implements CommandExecutor, TabCompleter {
    private static final Set<String> RESTART_REQUIRED_SETTINGS = Set.of(
            "world-engine.enabled",
            "settings.game-boosts.nether-structures.enabled",
            "settings.game-boosts.overworld-structures.enabled",
            "statistics.enabled",
            "statistics.type",
            "statistics.sqlite.file",
            "statistics.postgresql.host",
            "statistics.postgresql.port",
            "statistics.postgresql.database",
            "statistics.postgresql.username",
            "statistics.postgresql.password",
            "statistics.postgresql.ssl",
            "statistics.pool-size"
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
            <#de7766>JManhunt</#de7766> is a free plugin for configurable manhunts. For lucky blocks and other fun challenges, you can find the optional addon {link}.
            
            <gray> » Challenges status: [{status}<gray>]</gray>
            
            <gray>Looking for modifiers instead? Try <white>/mh configuration custom-modifiers <key> <value></white>.</gray>
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

    public ManhuntCommand(JManhuntPlugin plugin, MessageService messages, ConfigService config,
                          SoundService sounds, PlayerStateStore playerStates, GameManager game,
                          LobbyTeleporter lobbyTeleporter, DebugService debugService,
                          LobbyService lobbyService) {
        this.plugin = plugin; this.messages = messages; this.config = config; this.sounds = sounds;
        this.playerStates = playerStates; this.game = game;
        this.lobbyTeleporter = lobbyTeleporter; this.debugService = debugService;
        this.lobbies = lobbyService;
        this.devSchem = new DevSchemCommand(plugin, messages);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (!canUseSubcommand(sender, sub)) return message(sender, "command.no-permission");
        return switch (sub) {
            case "help" -> help(sender);
            case "status" -> status(sender, args);
            case "challenges" -> challenges(sender);
            case "setplayer" -> setPlayer(sender, args);
            case "start" -> start(sender, args);
            case "end" -> end(sender, args);
            case "game" -> game(sender, args);
            case "configuration", "config" -> configuration(sender, args);
            case "worldengine" -> worldEngine(sender, args);
            case "quickstart", "qs" -> quickStart(sender, args);
            case "reload" -> reload(sender);
            case "debug" -> debug(sender, args);
            case "lobby" -> lobby(sender, args);
            case "dev" -> dev(sender, args);
            default -> message(sender, "command.invalid");
        };
    }

    private boolean help(CommandSender sender) {
        message(sender, "manhunt.help-header");
        String[][] lines = {{"/manhunt help", "show commands"}, {"/manhunt", "show match status"},
                {"/manhunt setplayer <selector> <hunter|speedrunner|spectator|afk|none>", "assign roles"},
                {"/manhunt lobby join <selector> <lobby-id> [role] [-notp]", "move players to a lobby"},
                {"/manhunt lobby leave [selector]", "remove players from their lobby"},
                {"/manhunt start [lobby-id]", "start a match"}, {"/manhunt end [id] [-i|-immediate]", "cancel a match"},
                {"/manhunt game join <id> [role] [selector]", "add players to a running match"},
                {"/manhunt game leave [id] [selector]", "remove players from a running match"},
                {"/manhunt status [id|all]", "show match status"},
                {"/manhunt quickstart [percentage]", "assign teams and start immediately"},
                {"/manhunt configuration <category> <key...> <value>", "view or change a setting"},
                {"/manhunt worldengine", "set lobby or teleport players"},
                {"/manhunt debug [on|off]", "toggle debug output"},
                {"/manhunt challenges", "show Challenges addon info"},
                {"/manhunt dev schem <pos1|pos2|save|load|list>", "dev schematic tools"},
                {"/manhunt reload", "reload files"}};
        for (String[] line : lines) message(sender, "manhunt.help-line", Map.of("command", line[0], "description", line[1]));
        // Clickable links need MiniMessage parsing regardless of text-format,
        // so this footer stays hardcoded instead of living in messages.yml.
        sender.sendMessage(messages.miniMessage(
                "\n<green>Still need help? Check <#de7766><click:open_url:'https://jruk8.github.io/JManhunt/'>"
                        + "<underlined>Docs</underlined></click></#de7766> or join our <#de7766><click:open_url:'https://discord.gg/hkWmCVmWDC'>"
                        + "<underlined>Discord server</underlined></click></#de7766>!</green>"));
        sender.sendMessage(messages.miniMessage(
                "<green>Support our development on <#de7766><click:open_url:'https://ko-fi.com/jruk'>"
                        + "<underlined>Ko-fi</underlined></click></#de7766>.</green>"));
        neutralSound(sender);
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
        if (!plugin.getConfig().getBoolean("settings.status.show-win-conditions", false)) {
            return;
        }
        message(sender, "manhunt.status-win-speedrunners",
                Map.of("conditions", game.speedrunnerWinConditions()));
        message(sender, "manhunt.status-win-hunters", Map.of("conditions", game.hunterWinConditions()));
    }

    /** Optional enabled-modifier roll call: hidden when off or when none are enabled. */
    private void sendModifiersLine(CommandSender sender) {
        if (!plugin.getConfig().getBoolean("settings.status.show-modifiers", false)) {
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
        if (!plugin.getConfig().getBoolean("settings.status.show-elapsed-time", false)) {
            return;
        }
        message(sender, "manhunt.status-elapsed", Map.of("duration",
                DurationFormat.format(instance.elapsedSeconds(System.currentTimeMillis()))));
    }

    /** Optional lobby/game tag (L1, L1-0|G2 when sublobbed), on by default. */
    private void sendIdLine(CommandSender sender, String value) {
        if (!plugin.getConfig().getBoolean("settings.status.show-ids", true)) {
            return;
        }
        message(sender, "manhunt.status-ids", Map.of("value", value));
    }

    /** Every running match: id, active/assigned counts, and duration. */
    private boolean statusAll(CommandSender sender) {
        List<GameInstance> live = game.liveInstances();
        if (live.isEmpty()) {
            return message(sender, "manhunt.status-all-empty");
        }
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
        neutralSound(sender);
        return true;
    }

    private void sendRoleSection(CommandSender sender, List<Player> players, Role role, String header) {
        List<String> names = players.stream().filter(p -> playerStates.role(p) == role)
                .map(Player::getName).sorted().toList();
        if (names.isEmpty()) return;
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
    static boolean canUseSubcommand(CommandSender sender, String sub) {
        return switch (sub.toLowerCase(Locale.ROOT)) {
            case "dev" -> sender.hasPermission("jmanhunt.command.dev.schem");
            case "setplayer" -> sender.hasPermission("jmanhunt.command.setplayer")
                    || sender.hasPermission("jmanhunt.command.setplayer.self");
            case "config", "configuration" -> sender.hasPermission("jmanhunt.command.configuration");
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
            case "setlobby" -> sender.hasPermission("jmanhunt.command.worldengine.setlobby");
            case "setlobbytp" -> sender.hasPermission("jmanhunt.command.worldengine.setlobbytp");
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
        if (args.length < 3 || args.length > 4
                || (args.length == 4 && !isForceFlag(args[3]))) {
            return message(sender, "manhunt.setplayer-usage");
        }
        boolean force = args.length == 4;
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
        int afkWakes = countAfkWakes(sender, selected, role, force);
        if (afkWakes > 0 && !confirms.confirm("setplayer-afk:" + senderKey(sender))) {
            message(sender, "manhunt.set-afk-confirm", Map.of("count", String.valueOf(afkWakes)));
            return true;
        }
        int changed = 0, unchanged = 0, skipped = 0, joined = 0, held = 0;
        Set<java.util.UUID> assigned = new HashSet<>();
        Map<Integer, Set<Role>> cappedIn = new LinkedHashMap<>();
        for (Entity entity : selected) if (entity instanceof Player player) {
            if (playerStates.role(player) == role) { unchanged++; continue; }
            if (!player.hasPermission(rolePermissionNode(role))) { skipped++; continue; }
            Optional<Lobby> targetLobby = lobbies.lobbyOf(player.getUniqueId());
            Optional<GameInstance> live = targetLobby.flatMap(lobby -> game.instanceForLobby(lobby.id()));
            if (live.isPresent()) {
                if (!lobbies.multiLobbyAllowed()) {
                    message(sender, "manhunt.set-in-match", Map.of("player", player.getName()));
                    continue;
                }
                MidMatchPolicy policy = MidMatchPolicy.parse(
                        plugin.getConfig().getString("lobbies.mid-match-setplayer", "SUBLOBBY"));
                if (policy.joinsMidMatch(role)
                        && game.joinPlayers(live.get(), List.of(player), role) == 1) {
                    joined++;
                    assigned.add(player.getUniqueId());
                    continue;
                }
                boolean member = live.get().isActive(player.getUniqueId());
                if (!member && !capAllows(targetLobby, role, force, cappedIn)) {
                    continue;
                }
                Role from = playerStates.role(player);
                playerStates.setRole(player, role); changed++;
                plugin.roleTeams().sync(player);
                game.announceRoleChange(player, from, role);
                assigned.add(player.getUniqueId());
                message(player, "manhunt.role-assigned", Map.of("role", messages.roleName(role)));
                sounds.playNeutralSound(player);
                if (!member) {
                    held++;
                    if (role != Role.AFK) {
                        message(player, "manhunt.setplayer-held", Map.of("role", messages.roleName(role)));
                    }
                }
                continue;
            }
            if (!capAllows(lobbies.lobbyOf(player.getUniqueId()), role, force, cappedIn)) {
                continue;
            }
            Role from = playerStates.role(player);
            playerStates.setRole(player, role); changed++;
            plugin.roleTeams().sync(player);
            game.announceRoleChange(player, from, role);
            assigned.add(player.getUniqueId());
            message(player, "manhunt.role-assigned", Map.of("role", messages.roleName(role)));
            sounds.playNeutralSound(player);
        }
        message(sender, unchanged == 0 ? "manhunt.set-success" : "manhunt.set-success-unchanged",
                Map.of("count", String.valueOf(changed), "role", messages.roleName(role), "unchanged", String.valueOf(unchanged)));
        if (skipped > 0) message(sender, "manhunt.set-skipped", Map.of("count", String.valueOf(skipped)));
        if (joined > 0) message(sender, "manhunt.setplayer-joined",
                Map.of("count", String.valueOf(joined), "role", messages.roleName(role)));
        if (held > 0) message(sender, "manhunt.setplayer-held-summary",
                Map.of("count", String.valueOf(held)));
        for (Map.Entry<Integer, Set<Role>> entry : cappedIn.entrySet()) {
            for (Role cappedRole : entry.getValue()) {
                message(sender, "manhunt.lobby-full", Map.of("lobby", String.valueOf(entry.getKey()),
                        "role", messages.roleName(cappedRole)));
            }
        }
        if (sender instanceof Player player && !assigned.contains(player.getUniqueId())) sounds.playNeutralSound(player);
        if (changed > 0) game.updateAutostartState();
        return true;
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
     * [-f|-force] [-notp]. The role defaults to none and joiners teleport
     * to the lobby unless -notp is given.
     */
    private boolean lobbyJoin(CommandSender sender, String[] args) {
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
        if (end != 4 && end != 5) {
            return message(sender, "manhunt.lobby-join-usage");
        }
        OptionalInt lobbyId = LobbyService.parseId(args[3]);
        if (lobbyId.isEmpty()) {
            return message(sender, "manhunt.lobby-invalid-id");
        }
        Role role = Role.NONE;
        if (end == 5) {
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
            if (game.instanceOf(target.getUniqueId()).isPresent()) {
                message(sender, "manhunt.lobby-join-in-match", Map.of("player", target.getName()));
                continue;
            }
            Optional<Lobby> current = lobbies.lobbyOf(target.getUniqueId());
            if (current.isPresent() && current.get().id() == lobbyId.getAsInt()
                    && playerStates.role(target) == role) {
                message(sender, "manhunt.lobby-already-member", Map.of("player", target.getName(),
                        "lobby", String.valueOf(lobbyId.getAsInt()), "role", messages.roleName(role)));
                continue;
            }
            Lobby lobby = lobbies.get(lobbyId.getAsInt()).orElse(null);
            int count = lobby == null ? 0 : lobbyRoleCount(lobby, role);
            if (!force && role.isParticipant() && !CapLimits.allows(count, capFor(role))) {
                capped.add(role);
                continue;
            }
            Role previous = playerStates.role(target);
            lobbies.setLobby(target.getUniqueId(), lobbyId.getAsInt());
            playerStates.setRole(target, role);
            plugin.roleTeams().sync(target);
            moved.add(target);
            if (previous != role) {
                message(target, "manhunt.role-assigned", Map.of("role", messages.roleName(role)));
                sounds.playNeutralSound(target);
            }
        }
        for (Role cappedRole : capped) {
            message(sender, "manhunt.lobby-full", Map.of("lobby", String.valueOf(lobbyId.getAsInt()),
                    "role", messages.roleName(cappedRole)));
        }
        message(sender, "manhunt.lobby-join-success", Map.of("count", String.valueOf(moved.size()),
                "lobby", String.valueOf(lobbyId.getAsInt()), "role", messages.roleName(role)));
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
        if (!moved.isEmpty() && !noTeleport
                && plugin.getConfig().getBoolean("lobbies.join-teleports-to-lobby", true)) {
            teleportJoinersToLobby(sender, moved, lobbyId.getAsInt());
        }
        if (!moved.isEmpty()) {
            game.updateAutostartState();
        }
        return true;
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

    /** True for the -f and -force flags accepted by setplayer, quickstart, and lobby join. */
    static boolean isForceFlag(String arg) {
        return arg.equalsIgnoreCase("-f") || arg.equalsIgnoreCase("-force");
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
        return plugin.getConfig().getInt(
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
        if (!game.start(lobbyId)) return message(sender, "manhunt.start-invalid");
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
        Map<GameInstance, List<Player>> byMatch = new LinkedHashMap<>();
        if (args.length >= 3) {
            Optional<GameInstance> instance = game.resolveInstance(args[2]);
            if (instance.isEmpty()) {
                return message(sender, "manhunt.invalid-instance-id");
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
        if (byMatch.isEmpty()) {
            return message(sender, "game.leave-not-in-match");
        }
        boolean needsConfirm = byMatch.values().stream().flatMap(List::stream)
                .anyMatch(player -> playerStates.role(player).isParticipant());
        if (needsConfirm && !confirms.confirm("gameleave:" + senderKey(sender))) {
            return message(sender, "game.leave-confirm");
        }
        Map<UUID, Role> before = new HashMap<>();
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
        Set<UUID> leaverIds = byMatch.values().stream().flatMap(List::stream)
                .map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
        if (!(sender instanceof Player self) || !leaverIds.contains(self.getUniqueId())) {
            message(sender, "game.leave-removed", Map.of("count", String.valueOf(removed)));
        }
        neutralSound(sender);
        game.updateAutostartState();
        return true;
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
            return message(sender, "manhunt.configuration-usage");
        }
        DrillListing listing = nextSegments(game.settingNames(), resolved.path());
        Map<String, String> entries = new LinkedHashMap<>();
        for (String section : listing.sections()) {
            entries.put(section, "");
        }
        for (String leaf : listing.leaves()) {
            entries.put(leaf, ": " + game.getSettingValue(resolved.path() + "." + leaf));
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

    public static String renderEntries(Map<String, String> entries, String entryTemplate) {
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
            return message(sender, "manhunt.configuration-usage");
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
            messages.message(online, "manhunt.setting-change-announced",
                    Map.of("player", sender.getName(), "key", setting, "value", String.valueOf(newValue)));
        }
    }


    public static DrillResolve resolveDrill(
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
    public static List<String> drillCategories(ConfigurationSection root) {
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
    public static List<String> drillChildren(
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

    /**
     * Next drill level under a resolved section path: intermediate segments
     * of editable settings become bare sub-sections, terminal segments
     * become leaves. Derived purely from editable paths via {@link #isUnder},
     * so non-editable subtrees never surface. Pure for tests.
     */
    public static DrillListing nextSegments(Set<String> settings, String path) {
        if (path.isEmpty()) {
            return new DrillListing(List.of(), List.of());
        }
        Set<String> sections = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> leaves = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String setting : settings) {
            if (!isUnder(setting, path)) {
                continue;
            }
            String rest = setting.substring(path.length() + 1);
            int dot = rest.indexOf('.');
            if (dot == -1) {
                leaves.add(rest);
            } else {
                sections.add(rest.substring(0, dot));
            }
        }
        return new DrillListing(List.copyOf(sections), List.copyOf(leaves));
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
        if (!canUseWorldEngineAction(sender, sub)) {
            return message(sender, "command.no-permission");
        }
        if (sub.equals("setlobby")) {
            return worldEngineSetLobby(sender, args);
        }
        if (sub.equals("setlobbytp")) {
            return worldEngineSetLobbyTp(sender, args);
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
        if (action.equals("get")) {
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
        if (action.equals("set")) {
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
        return message(sender, "manhunt.worldengine-cellindex-usage");
    }

    private boolean worldEngineSetLobby(CommandSender sender, String[] args) {
        if (args.length > 3) {
            return message(sender, "manhunt.worldengine-setlobby-usage");
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

        saveLobbyLocation(location, 0);

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
            if (game.instanceOf(online.getUniqueId()).isEmpty()) {
                targets.add(online);
            }
        }
        lobbyTeleporter.setSpawnToLobby(targets, 0);

        message(sender, "manhunt.worldengine-setlobby-success",
                Map.of("location", formatLocation(location)));
        neutralSound(sender);
        return true;
    }

    /**
     * Saves the sender's position as a lobby's location: setlobbytp
     * &lt;lobby-id&gt;. Player-only, since the position is read from the sender.
     */
    private boolean worldEngineSetLobbyTp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return message(sender, "command.player-only");
        }
        if (args.length != 3) {
            return message(sender, "manhunt.worldengine-setlobbytp-usage");
        }
        OptionalInt lobbyId = LobbyService.parseId(args[2]);
        if (lobbyId.isEmpty()) {
            return message(sender, "manhunt.lobby-invalid-id");
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId.getAsInt() != 0) {
            return message(sender, "manhunt.lobby-worldengine-required");
        }
        Location location = player.getLocation();
        saveLobbyLocation(location, lobbyId.getAsInt());
        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (game.instanceOf(online.getUniqueId()).isEmpty()) {
                targets.add(online);
            }
        }
        lobbyTeleporter.setSpawnToLobby(targets, lobbyId.getAsInt());
        message(sender, "manhunt.worldengine-setlobby-success",
                Map.of("location", formatLocation(location)));
        neutralSound(sender);
        return true;
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
        if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
            List<String> options = new ArrayList<>(List.of("all"));
            options.addAll(instanceIdOptions());
            return partial(args[1], options);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start"))
            return partial(args[1], lobbyIdOptions());
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
        if (args.length == 2 && args[0].equalsIgnoreCase("game"))
            return partial(args[1], List.of("join", "leave"));
        if (args.length == 3 && args[0].equalsIgnoreCase("game")
                && (args[1].equalsIgnoreCase("join") || args[1].equalsIgnoreCase("leave")))
            return partial(args[2], instanceIdOptions());
        if (args.length == 4 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("join"))
            return partial(args[3], List.of("hunter", "speedrunner", "spectator", "none"));
        if (args.length == 4 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("leave"))
            return partial(args[3], selectorOptions());
        if (args.length == 5 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("join"))
            return partial(args[4], selectorOptions());
        if (args.length == 2 && args[0].equalsIgnoreCase("dev"))
            return partial(args[1], List.of("schem"));
        if (args.length == 3 && args[0].equalsIgnoreCase("dev") && args[1].equalsIgnoreCase("schem"))
            return partial(args[2], List.of("pos1", "pos2", "save", "load", "list"));
        if (args.length == 4 && args[0].equalsIgnoreCase("dev") && args[1].equalsIgnoreCase("schem")
                && args[2].equalsIgnoreCase("load"))
            return partial(args[3], devSchem.schematicNames());
        if (args.length == 2 && args[0].equalsIgnoreCase("debug"))
            return partial(args[1], List.of("on", "off"));
        if (args.length >= 2
                && (args[0].equalsIgnoreCase("configuration") || args[0].equalsIgnoreCase("config"))) {
            return completeDrill(args);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("worldengine")) {
            List<String> actions = new ArrayList<>(List.of("setlobby", "setlobbytp", "cellindex", "tpto"));
            actions.removeIf(action -> !canUseWorldEngineAction(sender, action));
            return partial(args[1], actions);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("cellindex")) {
            if (!canUseWorldEngineAction(sender, "cellindex")) {
                return List.of();
            }
            return partial(args[2], List.of("get", "set", "buffer"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("setlobbytp"))
            return partial(args[2], lobbyIdOptions());
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("tpto"))
            return partial(args[2], List.of("lobbyworld", "gameworld"));
        if (args.length == 4 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("tpto"))
            return partial(args[3], selectorOptions());
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("setlobby")) {
            if (sender instanceof Player player) {
                return partial(args[2], List.of(formatLocation(player.getLocation())));
            }
            return List.of();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("setplayer")) {
            if (args[1].startsWith("@a[")) return partial(args[1], List.of("@a[distance=", "@a[limit=", "@a[name=", "@a[gamemode="));
            return partial(args[1], selectorOptions());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setplayer")) return partial(args[2], List.of("hunter", "speedrunner", "spectator", "afk", "none"));
        if (args.length == 4 && args[0].equalsIgnoreCase("setplayer")) return partial(args[3], List.of("-f", "-force"));
        if (args.length == 2 && (args[0].equalsIgnoreCase("quickstart") || args[0].equalsIgnoreCase("qs")))
            return partial(args[1], List.of("50", "-f", "-force"));
        if (args.length == 3 && (args[0].equalsIgnoreCase("quickstart") || args[0].equalsIgnoreCase("qs")))
            return partial(args[2], List.of("-f", "-force"));
        if (args.length == 2 && args[0].equalsIgnoreCase("lobby")) return partial(args[1], List.of("join", "leave"));
        if (args.length == 3 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join"))
            return partial(args[2], selectorOptions());
        if (args.length == 3 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("leave"))
            return partial(args[2], selectorOptions());
        if (args.length == 4 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join"))
            return partial(args[3], lobbyIdOptions());
        if (args.length == 5 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join"))
            return partial(args[4], List.of("hunter", "speedrunner", "spectator", "afk", "none",
                    "-f", "-force", "-notp"));
        if (args.length == 6 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join"))
            return partial(args[5], List.of("-f", "-force", "-notp"));
        if (args.length == 7 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join"))
            return partial(args[6], List.of("-f", "-force", "-notp"));
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

    /** Lobby id completion: live lobby ids, falling back to 0 when none exist. */
    private List<String> lobbyIdOptions() {
        List<String> ids = new ArrayList<>(lobbies.lobbyIds().stream().sorted().map(String::valueOf).toList());
        if (ids.isEmpty()) {
            ids.add("0");
        }
        return ids;
    }

    /**
     * Top-level subcommand completion, in display order. The client shows
     * these bottom-up, so the source lists them reversed to read properly
     * in game.
     */
    static List<String> subcommandOptions() {
        return new ArrayList<>(List.of("challenges", "help", "reload", "worldengine", "config",
                "configuration", "debug", "lobby", "qs", "quickstart", "game", "end", "start",
                "setplayer", "status", "dev"));
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

    /**
     * Teleports to the lobby world or the game world: tpto
     * &lt;lobbyworld|gameworld&gt; [selector]. The lobby world is generated
     * on a confirmed second run when it does not exist yet.
     */
    private boolean worldEngineTpto(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 4) {
            return message(sender, "manhunt.worldengine-tpto-usage");
        }
        Optional<TptoTarget> target = parseTptoTarget(args[2]);
        if (target.isEmpty()) {
            return message(sender, "manhunt.worldengine-tpto-usage");
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
        return worldEngineTptoLobby(sender, targets);
    }

    /** Teleports targets to the game world spawn. Never generates anything. */
    private boolean worldEngineTptoGame(CommandSender sender, List<Player> targets) {
        String worldName = plugin.getConfig().getString("world-engine.world-name", "world");
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
    private boolean worldEngineTptoLobby(CommandSender sender, List<Player> targets) {
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
        Optional<LobbyWorld> ensured = game.ensureLobbyWorld();
        if (ensured.isEmpty()) {
            message(sender, "manhunt.worldengine-tpto-failed", Map.of("world", worldName));
            return true;
        }
        World world = ensured.get().world();
        if (ensured.get().lobbyZeroSet()) {
            message(sender, "manhunt.worldengine-setlobby-success",
                    Map.of("location", formatLocation(world.getSpawnLocation())));
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

    private void saveLobbyLocation(Location location, int lobbyId) {
        String worldName = location.getWorld() == null
                ? plugin.getConfig().getString("world-engine.world-name", "world")
                : location.getWorld().getName();
        String base = "world-engine.lobby-locations." + lobbyId + ".";
        plugin.getConfig().set(base + "world", worldName);
        plugin.getConfig().set(base + "x", location.getX());
        plugin.getConfig().set(base + "y", location.getY());
        plugin.getConfig().set(base + "z", location.getZ());
        plugin.getConfig().set(base + "yaw", location.getYaw());
        plugin.getConfig().set(base + "pitch", location.getPitch());
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
        QuickStartArgs parsed = parseQuickStartArgs(args);
        if (!parsed.valid()) {
            return message(sender, "manhunt.quickstart-usage");
        }
        boolean force = parsed.force();
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
        if (lobbyId < 0) return message(sender, "manhunt.quickstart-failed");
        if (game.instanceForLobby(lobbyId).isPresent()) return message(sender, "manhunt.already-active");
        QuickStartOutcome outcome = game.quickStart(percent, force, lobbyId);
        for (Role cappedRole : outcome.cappedRoles()) {
            message(sender, "manhunt.lobby-full", Map.of("lobby", String.valueOf(lobbyId),
                    "role", messages.roleName(cappedRole)));
        }
        if (!outcome.started()) return message(sender, "manhunt.quickstart-failed");
        return true;
    }


    static QuickStartArgs parseQuickStartArgs(String[] args) {
        Integer percent = null;
        boolean force = false;
        for (int i = 1; i < args.length; i++) {
            if (isForceFlag(args[i])) {
                if (force) {
                    return new QuickStartArgs(null, false, false);
                }
                force = true;
            } else if (percent == null) {
                try {
                    percent = Integer.parseInt(args[i]);
                } catch (NumberFormatException exception) {
                    return new QuickStartArgs(null, false, false);
                }
            } else {
                return new QuickStartArgs(null, false, false);
            }
        }
        return new QuickStartArgs(percent, force, true);
    }

    private boolean message(CommandSender sender, String key) { messages.message(sender, key); return true; }
    private void message(CommandSender sender, String key, Map<String, String> values) { messages.message(sender, key, values); }
    private void neutralSound(CommandSender sender) {
        if (sender instanceof Player player) sounds.playNeutralSound(player);
    }
}
