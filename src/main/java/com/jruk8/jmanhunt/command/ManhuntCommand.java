package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.command.units.ConfigUnit;
import com.jruk8.jmanhunt.command.units.DebugUnit;
import com.jruk8.jmanhunt.command.units.EndUnit;
import com.jruk8.jmanhunt.command.units.GameUnit;
import com.jruk8.jmanhunt.command.units.HelpUnit;
import com.jruk8.jmanhunt.command.units.LobbyUnit;
import com.jruk8.jmanhunt.command.units.QuickStartUnit;
import com.jruk8.jmanhunt.command.units.ReloadUnit;
import com.jruk8.jmanhunt.command.units.SetPlayerUnit;
import com.jruk8.jmanhunt.command.units.SetupUnit;
import com.jruk8.jmanhunt.command.units.StartUnit;
import com.jruk8.jmanhunt.command.units.StatusUnit;
import com.jruk8.jmanhunt.command.units.WorldEngineUnit;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.core.DebugService;
import com.jruk8.jmanhunt.gui.dialog.ModifierDialogs;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.gui.menus.ManhuntMenus;
import com.jruk8.jmanhunt.gui.menus.ModifierEditorMemory;
import com.jruk8.jmanhunt.gui.menus.ModifierMenus;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyService;
import com.jruk8.jmanhunt.lobby.world.LobbySchematicService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.ModifierTestService;
import com.jruk8.jmanhunt.match.StatusRosterService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.jruk8.jmanhunt.setup.SetupService;
import java.util.HashMap;
import java.util.UUID;

public final class ManhuntCommand implements CommandExecutor, TabCompleter {
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
    private final OverrideCommand overrideCmd;
    private final SetupService setupService;
    private final StatusRosterService roster;
    private ModifierMenus modifierMenus;
    private final ManhuntMenus menus;
    private final Map<String, SubcommandUnit> units = new HashMap<>();
    // Init-once in initUnits (blank finals cannot assign from a helper).
    private HelpUnit helpUnit;
    private StartUnit startUnit;
    private EndUnit endUnit;
    private QuickStartUnit quickStartUnit;
    private ReloadUnit reloadUnit;
    private DebugUnit debugUnit;
    private StatusUnit statusUnit;
    private GameUnit gameUnit;
    private LobbyUnit lobbyUnit;
    private SetPlayerUnit setPlayerUnit;
    private ConfigUnit configUnit;
    private WorldEngineUnit worldEngineUnit;
    private SetupUnit setupUnit;
    private CommandSupport support;

    public ManhuntCommand(JManhuntPlugin plugin, MessageService messages, ConfigService config,
                          SoundService sounds, PlayerStateStore playerStates, GameManager game,
                          LobbyTeleporter lobbyTeleporter, DebugService debugService,
                          LobbyService lobbyService) {
        this.plugin = plugin; this.messages = messages; this.config = config; this.sounds = sounds;
        this.playerStates = playerStates; this.game = game;
        this.lobbyTeleporter = lobbyTeleporter; this.debugService = debugService;
        this.lobbies = lobbyService;
        this.roster = new StatusRosterService(messages, messages.manhunt(), playerStates);
        this.devSchem = newDevSchem(plugin, messages);
        this.feedback = new SettingFeedback(messages, messages.manhunt(), config, sounds);
        this.modifiersCmd = newModifiersCmd(plugin, config, messages, sounds, playerStates,
                game);
        this.overrideCmd = newOverrideCmd(plugin, config, messages, sounds, feedback);
        this.setupService = newSetupService(plugin, game, messages, sounds, feedback);
        SettingDialogs dialogs = new SettingDialogs(
                new SettingDialogs.SettingStores(config, plugin.overrides()),
                new SettingDialogs.SettingTexts(messages, messages.manhuntGui(), sounds),
                new SettingDialogs.SettingUi(plugin.guiService(), feedback, plugin.guiConfig()),
                plugin);
        ModifierDialogs modifierDialogs = new ModifierDialogs(messages, messages.modifiersGui(),
                messages.manhuntGui(), sounds, plugin);
        this.modifierMenus = new ModifierMenus(config.modifiers(),
                new ModifierMenus.MenusTexts(messages, messages.modifiersGui(),
                        messages.manhuntGui(), messages.modifiers(), messages.command(), sounds),
                new ModifierMenus.MenusDeps(plugin.guiService(), modifiersCmd, dialogs,
                        modifierDialogs,
                        () -> config.getBoolean(
                                "advanced.misc.modifier-editor.validate-commands", true),
                        plugin.overrides(), feedback,
                        new ModifierEditorMemory(plugin.engineStates(), plugin.logger(),
                                () -> config.getBoolean(
                                        "advanced.misc.modifier-editor.remember-gui-commands",
                                        false))));
        this.menus = new ManhuntMenus(
                new SettingDialogs.SettingStores(config, plugin.overrides()),
                new SettingDialogs.SettingTexts(messages, messages.manhuntGui(), sounds),
                new SettingDialogs.SettingUi(plugin.guiService(), feedback,
                        plugin.guiConfig()),
                new ManhuntMenus.ManhuntDeps(dialogs, plugin.stats(), modifierMenus,
                        modifierDialogs));
        initUnits();
    }

    /** Builds the extracted units and routes verbs to them. */
    private void initUnits() {
        support = new CommandSupport(messages, sounds, confirms);
        CapSupport caps = new CapSupport(playerStates, game, config);
        initEarlyUnits();
        initLateUnits(caps);
    }

    private void initEarlyUnits() {
        helpUnit = new HelpUnit(messages.manhunt(), support);
        startUnit = new StartUnit(game, lobbies, messages.manhunt(), support);
        endUnit = new EndUnit(game, messages.manhunt(), support);
        quickStartUnit = new QuickStartUnit(game, lobbies, messages.manhunt(), support);
        reloadUnit = new ReloadUnit(plugin, game, messages.manhunt(), support);
        debugUnit = new DebugUnit(debugService, messages.manhunt(), support);
        statusUnit = new StatusUnit(
                new StatusUnit.StatusDeps(game, lobbies, config, roster,
                        plugin::respawnListener),
                new StatusUnit.StatusTexts(messages.manhunt(), messages.command(), support));
        gameUnit = new GameUnit(
                new GameUnit.GameDeps(game, playerStates, confirms),
                new GameUnit.GameTexts(messages.manhunt(), messages.game(), messages.command(),
                        support));
        registerUnit(helpUnit);
        registerUnit(startUnit);
        registerUnit(endUnit);
        registerUnit(quickStartUnit);
        registerUnit(reloadUnit);
        registerUnit(debugUnit);
        registerUnit(statusUnit);
        registerUnit(gameUnit);
    }

    private void initLateUnits(CapSupport caps) {
        lobbyUnit = new LobbyUnit(
                new LobbyUnit.LobbyDeps(game, lobbies, playerStates, config, lobbyTeleporter,
                        plugin.roleTeams(), caps),
                new LobbyUnit.LobbyTexts(messages.manhunt(), messages.command(), support));
        registerUnit(lobbyUnit);
        setPlayerUnit = new SetPlayerUnit(
                new SetPlayerUnit.SetPlayerDeps(playerStates, game, lobbies, caps,
                        plugin.roleTeams(), plugin.getLogger(), confirms),
                new SetPlayerUnit.SetPlayerTexts(messages.manhunt(), messages.command(),
                        support));
        registerUnit(setPlayerUnit);
        configUnit = new ConfigUnit(
                new ConfigUnit.ConfigDeps(game, config, feedback, plugin::observeWorldEngine),
                new ConfigUnit.ConfigTexts(messages.manhunt(), support));
        registerUnit(configUnit);
        worldEngineUnit = new WorldEngineUnit(
                new WorldEngineUnit.WorldEngineDeps(game, config, lobbies, lobbyTeleporter,
                        plugin.lobbyConfig(), plugin.devConfig(), confirms),
                new WorldEngineUnit.WorldEngineTexts(messages.manhunt(), messages.command(),
                        support));
        registerUnit(worldEngineUnit);
        setupUnit = new SetupUnit(
                new SetupUnit.SetupDeps(plugin.guiService(), plugin.tutorial(), setupService,
                        menus, plugin::isSetupDone, plugin::markSetupDone),
                new SetupUnit.SetupTexts(messages.command(), support));
        registerUnit(setupUnit);
    }

    private void registerUnit(SubcommandUnit unit) {
        units.put(unit.primaryName(), unit);
        for (String alias : unit.aliases()) {
            units.put(alias, unit);
        }
    }

    private DevSchemCommand newDevSchem(JManhuntPlugin plugin, MessageService messages) {
        return new DevSchemCommand(
                new LobbySchematicService(new JmhLobbyService(plugin.logger(), plugin.lobbyConfig(),
                        plugin.configRoot().getWorldEngine(),
                        plugin.configRoot().getAdvanced().getLobbies()),
                        plugin.devConfig(), plugin.getDataFolder().toPath(), plugin.logger(),
                        plugin::getResource),
                plugin.lobbyConfig(), plugin.configRoot().getWorldEngine(), plugin.logger(),
                new DevSchemCommand.Texts(messages, messages.dev(), messages.command()));
    }

    private ModifiersCommand newModifiersCmd(JManhuntPlugin plugin, ConfigService config,
            MessageService messages, SoundService sounds, PlayerStateStore playerStates,
            GameManager game) {
        return new ModifiersCommand(config,
                new ModifiersCommand.ModifiersDeps(plugin.guiService(),
                        viewer -> modifierMenus.mainMenu(viewer, null),
                        new ModifierTestService(game.stateCommands(), playerStates,
                                messages, messages.modifiers(), sounds)),
                new ModifiersCommand.ModifiersTexts(messages, messages.modifiers(),
                        messages.command(), sounds));
    }

    private OverrideCommand newOverrideCmd(JManhuntPlugin plugin, ConfigService config,
            MessageService messages, SoundService sounds, SettingFeedback feedback) {
        return new OverrideCommand(plugin.overrides(), config,
                new OverrideCommand.OverrideTexts(messages, messages.manhunt(),
                        messages.modifiers(), sounds),
                feedback, lobbies);
    }

    private SetupService newSetupService(JManhuntPlugin plugin, GameManager game,
            MessageService messages, SoundService sounds, SettingFeedback feedback) {
        return new SetupService(game,
                new SetupService.Announcer(messages, messages.manhunt(), sounds), feedback,
                plugin::observeWorldEngine, plugin::markSetupDone);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (setupUnit.routeGui(sender, args)) {
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (!canUseSubcommand(sender, sub)) {
            return support.message(sender, messages.command().getNoPermission());
        }
        return switch (sub) {
            case "help" -> helpUnit.execute(sender, args);
            case "status" -> statusUnit.execute(sender, args);
            case "challenges" -> helpUnit.execute(sender, args);
            case "setplayer" -> setPlayerUnit.execute(sender, args);
            case "start" -> startUnit.execute(sender, args);
            case "end" -> endUnit.execute(sender, args);
            case "game" -> gameUnit.execute(sender, args);
            case "config" -> configUnit.execute(sender, args);
            case "modifiers" -> modifiers(sender, args);
            case "override" -> overrideCmd.execute(sender, args);
            case "worldengine" -> worldEngineUnit.execute(sender, args);
            case "quickstart", "qs" -> quickStartUnit.execute(sender, args);
            case "reload" -> reloadUnit.execute(sender, args);
            case "debug" -> debugUnit.execute(sender, args);
            case "lobby" -> lobbyUnit.execute(sender, args);
            case "setup" -> setupUnit.execute(sender, args);
            case "support" -> helpUnit.execute(sender, args);
            case "dev" -> dev(sender, args);
            default -> support.message(sender, messages.command().getInvalid());
        };
    }

    /** Live pending lobby corners for the debug particle draft boxes. */
    public Map<UUID, Location> boundPos1View() {
        return worldEngineUnit.boundPos1View();
    }

    /** Live pending lobby corners for the debug particle draft boxes. */
    public Map<UUID, Location> boundPos2View() {
        return worldEngineUnit.boundPos2View();
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
    public static boolean canUseStatusArgs(CommandSender sender) {
        return sender.hasPermission(STATUS_OTHER_PERMISSION);
    }

    /**
     * Whether the sender may run the given first level subcommand. Aliases
     * share their canonical permission node, and setplayer additionally
     * accepts the self-only node (setPlayer enforces the self target).
     */
    public static boolean canUseSubcommand(CommandSender sender, String sub) {
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
    public static boolean canUseWorldEngineAction(CommandSender sender, String action) {
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

    public static String rolePermissionNode(Role role) {
        return switch (role) {
            case HUNTER -> "jmanhunt.hunter";
            case SPEEDRUNNER -> "jmanhunt.speedrunner";
            case AFK -> "jmanhunt.afk";
            case NONE -> "jmanhunt.none";
            case SPECTATOR -> "jmanhunt.spectator";
        };
    }

    public static boolean isSelfOnlySelection(CommandSender sender, List<Entity> selected) {
        if (!(sender instanceof Player self) || selected.size() != 1) {
            return false;
        }
        return selected.get(0) instanceof Player target
                && target.getUniqueId().equals(self.getUniqueId());
    }


    /** Developer tools. Only schem exists for now; usage covers the rest. */
    private boolean dev(CommandSender sender, String[] args) {
        if (args.length < 2 || !args[1].equalsIgnoreCase("schem")) {
            return support.message(sender, messages.dev().getUsage());
        }
        return devSchem.execute(sender, java.util.Arrays.copyOfRange(args, 2, args.length));
    }

    private boolean modifiers(CommandSender sender, String[] args) {
        return modifiersCmd.execute(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Only suggest subcommands the sender may actually run.
            List<String> options = subcommandOptions();
            options.removeIf(option -> !canUseSubcommand(sender, option));
            return CommandSupport.partial(args[0], options);
        }
        SubcommandUnit unit = units.get(args[0].toLowerCase(Locale.ROOT));
        if (unit != null) {
            List<String> unitCompletion = unit.complete(sender, args);
            if (unitCompletion != null) {
                return unitCompletion;
            }
        }
        if (args[0].equalsIgnoreCase("modifiers")) {
            return modifiersCmd.completeModifiersTab(args);
        }
        if (args[0].equalsIgnoreCase("override")) {
            return overrideCmd.completeOverrideTab(args);
        }
        if (args[0].equalsIgnoreCase("dev")) {
            return devSchem.completeDevTab(args);
        }
        return List.of();
    }


    /**
     * First-level completions. {@code dev} stays out on purpose (developer
     * tooling), but everything after a typed {@code dev} still completes.
     */
    static List<String> subcommandOptions() {
        return new ArrayList<>(List.of("challenges", "help", "support", "reload", "worldengine", "config",
                "modifiers", "override", "debug", "lobby", "qs", "quickstart", "game", "end",
                "start", "setplayer", "setup", "status"));
    }
}
