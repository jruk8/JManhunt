package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed root of messages.yml. Every chat, actionbar, title, and GUI
 * string lives here and is read through {@link MessageService} by
 * dotted path, so renaming a key means updating the reads too.
 * An explicitly empty string disables that message wherever it
 * would be sent.
 */
@SuppressWarnings("FieldMayBeFinal")
@Header({
        "JManhunt messages.",
        "",
        "Placeholders: {prefix}, {player}, {role}, {distance}, {world},",
        "{winner}, {stat}, {value}, {effect}, {seconds}, {rank-color}.",
        "Both MiniMessage and legacy &-codes are accepted."
})
public class MessagesConfig extends OkaeriConfig {

    @CustomKey("prefix")
    private String prefix = "<#a6a6a6>[<gradient:#e66550:#de7766><bold>J</bold>Manhunt</gradient>]</#a6a6a6> ";

    @CustomKey("command")
    private Command command = new Command();

    @CustomKey("manhunt")
    private Manhunt manhunt = new Manhunt();

    @CustomKey("game")
    private Game game = new Game();

    @CustomKey("compass")
    private Compass compass = new Compass();

    @CustomKey("role-colors")
    @Comment({
            "One color tag per role. Used directly in templates as",
            "{role-color-<role>} and by code that builds colored role",
            "names. Legacy-format servers can set &-codes here instead",
            "of the default MiniMessage hex tags."
    })
    private RoleColors roleColors = new RoleColors();

    @CustomKey("wincon")
    @Comment({
            "Win-condition sentence fragments, joined with \", \" and",
            "\"and\" into the {conditions} of the status-win lines above."
    })
    private Wincon wincon = new Wincon();

    @CustomKey("debug")
    private Debug debug = new Debug();

    @CustomKey("dev")
    @Comment("Developer schematic tools (/manhunt dev schem). Not for production use.")
    private Dev dev = new Dev();

    @CustomKey("modifiers")
    @Comment("Modifier toggles (/manhunt modifiers ...). {state} is on or off.")
    private Modifiers modifiers = new Modifiers();

    @CustomKey("modifiers-gui")
    @Comment({
            "Modifiers GUI text. Names render white, lore gray; user tags",
            "override. {enabled} and {total} count enabled entries.",
            "Never add {prefix} here."
    })
    private ModifiersGui modifiersGui = new ModifiersGui();

    @CustomKey("manhunt-gui")
    @Comment({
            "Manhunt GUI chrome shared by the settings browser, confirm",
            "panels, and value dialogs. Names render white, lore gray;",
            "user tags override. Never add {prefix} here."
    })
    private ManhuntGui manhuntGui = new ManhuntGui();

    /** Shared command responses. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Command extends OkaeriConfig {

        @CustomKey("player-only")
        private String playerOnly = "{prefix}<red>Only players can use this command.";

        @CustomKey("no-permission")
        private String noPermission = "{prefix}<red>You do not have permission to use this command.";

        @CustomKey("invalid")
        private String invalid = "{prefix}<red>Unknown command. Use <yellow>/manhunt help<red>.";

        @CustomKey("no-targets")
        private String noTargets = "{prefix}<yellow>No players matched that selector.";
    }

    /** Core /manhunt command responses. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Manhunt extends OkaeriConfig {

        @CustomKey("help-header")
        private String helpHeader = "\n{prefix}\n<#de7766>JManhunt commands:";

        @CustomKey("help-line")
        private String helpLine = "<white>» {command}<gray>: {description}</gray></white>";

        @CustomKey("status-header")
        private String statusHeader = "\n{prefix}\n<#de7766>Manhunt status: <white>{status}";

        @CustomKey("speedrunners-header")
        private String speedrunnersHeader = "\n{role-color-speedrunner}Speedrunners:";

        @CustomKey("hunters-header")
        private String huntersHeader = "\n{role-color-hunter}Hunters:";

        @CustomKey("afk-header")
        private String afkHeader = "\n{role-color-afk}AFK:";

        @CustomKey("none-header")
        private String noneHeader = "\n{role-color-none}None:";

        @CustomKey("status-player")
        private String statusPlayer = "<white>» <gray>{player}";

        @CustomKey("spectators-line")
        private String spectatorsLine = "\n{role-color-spectator}👁 Spectators: <gray>{value}";

        @CustomKey("status-win-speedrunners")
        private String statusWinSpeedrunners = "\n{role-color-speedrunner}Speedrunners <gray>🏆 on: <white>{conditions}";

        @CustomKey("status-win-hunters")
        private String statusWinHunters = "\n{role-color-hunter}Hunters <gray>🏆 on: <white>{conditions}";

        @CustomKey("status-modifiers")
        private String statusModifiers = "\n<white>Modifiers: <gray>{modifiers}";

        @CustomKey("status-elapsed")
        private String statusElapsed = "\n<gray>Elapsed: <white>{duration}";

        @CustomKey("status-ids")
        private String statusIds = "\n<gray>{value}";

        @CustomKey("no-match")
        private String noMatch = "{prefix}<yellow>No match is active.";

        @CustomKey("set-success")
        private String setSuccess = "{prefix}<green>Updated <white>{count}<green> player(s) to <white>{role}<green>.";

        @CustomKey("set-success-unchanged")
        private String setSuccessUnchanged = "{prefix}<green>Updated <white>{count}<green> player(s) to " +
                "<white>{role}<green> (<white>{unchanged}<green> unchanged).";

        @CustomKey("role-assigned")
        private String roleAssigned = "{prefix}<green>You have been assigned the role <white>{role}<green>.";

        @CustomKey("role-is-now")
        private String roleIsNow = "{prefix}<green>{player} is now a {active-role}<green>.";

        @CustomKey("role-no-longer")
        private String roleNoLonger = "{prefix}<yellow>{player} is no longer a {active-role}<yellow>.";

        @CustomKey("set-skipped")
        private String setSkipped = "{prefix}<yellow>Skipped <white>{count}<yellow> player(s) without the required " +
                "permission.";

        @CustomKey("start-success")
        private String startSuccess = "\n{prefix}<green>Manhunt has started.";

        @CustomKey("role-announce-chat")
        private String roleAnnounceChat = "{prefix}<green>You are a <white>{role}<green>.";

        @CustomKey("role-announce-title")
        private String roleAnnounceTitle = "<gray>Role: <white>{role}";

        @CustomKey("role-announce-subtitle-hunter")
        private String roleAnnounceSubtitleHunter = "<red>Kill all Speedrunners";

        @CustomKey("role-announce-subtitle-speedrunner")
        private String roleAnnounceSubtitleSpeedrunner = "<green>Outsmart the Hunters";

        @CustomKey("role-announce-subtitle-spectator")
        private String roleAnnounceSubtitleSpectator = "<gray>Watch the Hunt";

        @CustomKey("autostart-eligible")
        private String autostartEligible = "{prefix}<yellow>Manhunt auto-starts in <white>{seconds}<yellow>s.";

        @CustomKey("autostart-countdown")
        private String autostartCountdown = "{prefix}<yellow>Manhunt starts in <white>{seconds}<yellow>s.";

        @CustomKey("autostart-cancelled")
        private String autostartCancelled = "{prefix}<yellow>Auto-start cancelled.";

        @CustomKey("autostart-needs-more")
        private String autostartNeedsMore = "{prefix}<yellow>The game needs {details} to begin.";

        @CustomKey("waiting-for-damage")
        private String waitingForDamage = "{prefix}<yellow>A speedrunner must hit a hunter within " +
                "<white>{seconds}s</white> to start the game.";

        @CustomKey("waiting-for-damage-indefinite")
        private String waitingForDamageIndefinite = "{prefix}<yellow>A speedrunner must hit a hunter to start the " +
                "game.";

        @CustomKey("waiting-for-damage-exhausted")
        private String waitingForDamageExhausted = "{prefix}<red>No speedrunner hit a hunter within " +
                "<white>{seconds}s</white>. The game will not start.";

        @CustomKey("waiting-for-damage-force-started")
        private String waitingForDamageForceStarted = "{prefix}<yellow>The game has automatically started because no " +
                "speedrunner hit a hunter on time.";

        @CustomKey("started-by-damage")
        private String startedByDamage = "{prefix}<green>The Manhunt game has begun!";

        @CustomKey("headstart-active")
        private String headstartActive = "{prefix}<yellow>{role}s <yellow>are in spectator mode for " +
                "<white>{seconds}s</white>.";

        @CustomKey("headstart-ending")
        private String headstartEnding = "{prefix}<yellow>{role}s <yellow>return in <white>{seconds}s</white>.";

        @CustomKey("headstart-ended")
        private String headstartEnded = "{prefix}<green>{role}s <green>have spawned!";

        @CustomKey("start-invalid")
        private String startInvalid = "{prefix}<red>A match needs at least one hunter and one speedrunner.";

        @CustomKey("already-active")
        private String alreadyActive = "{prefix}<red>A match is already active.";

        @CustomKey("not-active")
        private String notActive = "{prefix}<yellow>No match is active.";

        @CustomKey("reload-success")
        private String reloadSuccess = "{prefix}<green>Reloaded config.yml, messages.yml, and settings.";

        @CustomKey("setting-invalid")
        private String settingInvalid = "{prefix}<red>Unknown setting.";

        @CustomKey("setting-invalid-value")
        private String settingInvalidValue = "{prefix}<red>Setting values must be true or false.";

        @CustomKey("setting-invalid-number")
        private String settingInvalidNumber = "{prefix}<red>That value is not valid for this setting.";

        @CustomKey("setting-invalid-option")
        private String settingInvalidOption = "{prefix}<red>Unknown value for {setting}. Valid: <white>{valid}</white>";

        @CustomKey("setting-out-of-range")
        private String settingOutOfRange = "{prefix}<red>Value for {setting} must be {bounds}.";

        @CustomKey("setting-index-invalid")
        private String settingIndexInvalid = "{prefix}<red>Index {index} is out of range for {setting} (size {size}).";

        @CustomKey("setting-list-added")
        private String settingListAdded = "{prefix}<green>Added to <white>{setting}<green>: <white>{value}<green>.";

        @CustomKey("setting-list-removed")
        private String settingListRemoved = "{prefix}<green>Removed from <white>{setting}<green>: " +
                "<white>{value}<green>.";

        @CustomKey("setting-updated")
        private String settingUpdated = "{prefix}<green>Set <white>{setting}<green> to <white>{value}<green>. " +
                "<gray>(was <white>{old-value}</white>)</gray>";

        @CustomKey("setting-change-announced")
        private String settingChangeAnnounced = "{prefix}<white>{player} <yellow>set setting <gray>{key}</gray> to " +
                "<white>{value}</white>.</yellow></white>";

        @CustomKey("setting-restart-required")
        private String settingRestartRequired = "{prefix}<gray>This setting requires a server restart to take effect.";

        @CustomKey("setting-entry")
        private String settingEntry = "{prefix}<gray>{setting}:</gray> <white>{value}";

        @CustomKey("config-list")
        private String configList = "\n{prefix}<white>Available configs in <gray>{key}:{entries}";

        @CustomKey("config-entry")
        private String configEntry = "\n<green>» <white>{key}</white><gray>{suffix}</gray>";

        @CustomKey("setting-status")
        private String settingStatus = "{prefix}<white>{setting}<gray> is currently <white>{value}<gray>.";

        @CustomKey("worldengine-usage")
        private String worldengineUsage = "{prefix}<yellow>Usage: /manhunt worldengine <lobbyconfig|cellindex|tpto>";

        @CustomKey("worldengine-cellindex-usage")
        private String worldengineCellindexUsage = "{prefix}<yellow>Usage: /manhunt worldengine cellindex <get|set " +
                "<value>|buffer>";

        @CustomKey("worldengine-cellindex-get")
        private String worldengineCellindexGet = "{prefix}<green>World-engine cell index: <white>{index}<green>.";

        @CustomKey("worldengine-cellindex-set")
        private String worldengineCellindexSet = "{prefix}<green>Set world-engine cell index to " +
                "<white>{index}<green> (was <white>{was}<green>, max " +
                "<white>{max}<green>).";

        @CustomKey("worldengine-cellindex-invalid")
        private String worldengineCellindexInvalid = "{prefix}<red>Invalid cell index. Use a number between 0 and " +
                "<white>{max}<red>.";

        @CustomKey("worldengine-cellindex-unavailable")
        private String worldengineCellindexUnavailable = "{prefix}<red>Cell index storage is unavailable.";

        @CustomKey("worldengine-disabled")
        private String worldengineDisabled = "{prefix}<red>World engine is disabled. Enable it through " +
                "<gray>\"/manhunt config world-engine enabled true\"</gray> to use this " +
                "command.";

        @CustomKey("worldengine-no-targets")
        private String worldengineNoTargets = "{prefix}<yellow>No players matched that selector.";

        @CustomKey("worldengine-invalid-location")
        private String worldengineInvalidLocation = "{prefix}<red>Invalid lobby location. Use <yellow>x y z yaw " +
                "pitch<red>.";

        @CustomKey("worldengine-tpto-confirm")
        private String worldengineTptoConfirm = "{prefix}<yellow>Lobby world <white>{world}</white> does not exist. " +
                "Run the command again within <white>{seconds}s</white> to generate " +
                "it.";

        @CustomKey("worldengine-tpto-creating")
        private String worldengineTptoCreating = "{prefix}<yellow>Creating lobby world <white>{world}</white>...";

        @CustomKey("worldengine-tpto-success")
        private String worldengineTptoSuccess = "{prefix}<green>Teleported <white>{count}</white> player(s) to " +
                "<white>{world}</white>.";

        @CustomKey("worldengine-tpto-no-world")
        private String worldengineTptoNoWorld = "{prefix}<red>World <white>{world}</white> is not loaded.";

        @CustomKey("worldengine-tpto-failed")
        private String worldengineTptoFailed = "{prefix}<red>Could not create lobby world <white>{world}</white>.";

        @CustomKey("worldengine-invalid-selector")
        private String worldengineInvalidSelector = "{prefix}<red>Invalid selector.";

        @CustomKey("worldengine-invalid-lobby")
        private String worldengineInvalidLobby = "{prefix}<red>The world-engine lobby world is not loaded.";

        @CustomKey("quickstart-invalid-percent")
        private String quickstartInvalidPercent = "{prefix}<red>Quick start percentage must be a number between 0 " +
                "and 100.";

        @CustomKey("quickstart-failed")
        private String quickstartFailed = "{prefix}<red>Quick start requires at least one hunter and one " +
                "speedrunner. AFK players are not assigned roles.";

        @CustomKey("status-all-header")
        private String statusAllHeader = "\n{prefix}\n<#de7766>Running instances:";

        @CustomKey("status-all-entry")
        private String statusAllEntry = "<white>» <white>{lobby}|G{id} <gray>({remaining}/{assigned}) " +
                "<white>{duration}";

        @CustomKey("status-all-empty")
        private String statusAllEmpty = "{prefix}<yellow>No matches are running.";

        @CustomKey("status-all-lobbies-header")
        private String statusAllLobbiesHeader = "\n{prefix}\n<#de7766>Lobby queues:";

        @CustomKey("status-all-lobby-entry")
        private String statusAllLobbyEntry = "<white>» Lobby {lobby} <gray>({count} players)";

        @CustomKey("invalid-instance-id")
        private String invalidInstanceId = "{prefix}<red>Unknown instance id.";

        @CustomKey("not-in-match")
        private String notInMatch = "{prefix}<yellow>You are not in a match.";

        @CustomKey("console-requires-id")
        private String consoleRequiresId = "{prefix}<red>Specify an instance id or <yellow>all<red>.";

        @CustomKey("lobby-join-success")
        private String lobbyJoinSuccess = "{prefix}<green>Moved <white>{count}<green> player(s) to lobby " +
                "<white>{lobby}<green> as <white>{role}<green>.";

        @CustomKey("lobby-leave-success")
        private String lobbyLeaveSuccess = "{prefix}<green>Removed <white>{player}<green> from lobby " +
                "<white>{lobby}<green>.";

        @CustomKey("lobby-invalid-id")
        private String lobbyInvalidId = "{prefix}<red>Invalid lobby id. Use a number between 0 and " +
                "<white>2147483647<red>.";

        @CustomKey("lobby-full")
        private String lobbyFull = "{prefix}<red>Lobby <white>{lobby}<red> is full for role <white>{role}<red>.";

        @CustomKey("lobby-no-location")
        private String lobbyNoLocation = "{prefix}<yellow>No lobby location exists for lobby <white>{lobby}<yellow>. " +
                "Create one with <white>/manhunt worldengine lobbyconfig setlobbytp " +
                "{lobby}<yellow>.";

        @CustomKey("lobby-worldengine-required")
        private String lobbyWorldengineRequired = "{prefix}<red>Enable the world engine to use multiple lobbies.";

        @CustomKey("debug-enabled")
        private String debugEnabled = "{prefix}<green>Debug mode enabled.";

        @CustomKey("debug-disabled")
        private String debugDisabled = "{prefix}<yellow>Debug mode disabled.";

        @CustomKey("worldengine-cellindex-buffer-header")
        private String worldengineCellindexBufferHeader = "\n{prefix}\n<#de7766>Buffered cell ids:";

        @CustomKey("worldengine-cellindex-buffer-entry")
        private String worldengineCellindexBufferEntry = "<white>» <white>{index}";

        @CustomKey("status-usage")
        private String statusUsage = "{prefix}<yellow>Usage: /manhunt status [id|all]";

        @CustomKey("setplayer-usage")
        private String setplayerUsage = "{prefix}<yellow>Usage: /manhunt setplayer <selector> " +
                "<hunter|speedrunner|spectator|afk|none> [-f|-force] [-s|-silent]";

        @CustomKey("set-in-match")
        private String setInMatch = "{prefix}<yellow>{player} is in a running match. Manage them with " +
                "<white>/manhunt game leave<yellow> and <white>/manhunt game join<yellow>.";

        @CustomKey("setplayer-held")
        private String setplayerHeld = "{prefix}<yellow>A match is in progress. You are queued as " +
                "<white>{role}<yellow> for the next game.";

        @CustomKey("setplayer-held-summary")
        private String setplayerHeldSummary = "{prefix}<yellow>{count} player(s) held for the next game (match in " +
                "progress).";

        @CustomKey("setplayer-joined")
        private String setplayerJoined = "{prefix}<green>Joined <white>{count}<green> player(s) to the running match " +
                "as <white>{role}<green>.";

        @CustomKey("set-afk-confirm")
        private String setAfkConfirm = "{prefix}<yellow>Run again within <white>10 seconds<yellow> to assign roles " +
                "to <white>{count}<yellow> AFK player(s).";

        @CustomKey("start-usage")
        private String startUsage = "{prefix}<yellow>Usage: /manhunt start [lobby-id]";

        @CustomKey("end-usage")
        private String endUsage = "{prefix}<yellow>Usage: /manhunt end [id] [-i|-immediate]";

        @CustomKey("game-usage")
        private String gameUsage = "{prefix}<yellow>Usage: /manhunt game <join|leave> ...";

        @CustomKey("game-join-usage")
        private String gameJoinUsage = "{prefix}<yellow>Usage: /manhunt game join <id> [role] [selector]";

        @CustomKey("game-leave-usage")
        private String gameLeaveUsage = "{prefix}<yellow>Usage: /manhunt game leave [id] [selector]";

        @CustomKey("lobby-usage")
        private String lobbyUsage = "{prefix}<yellow>Usage: /manhunt lobby <join|leave> ...";

        @CustomKey("lobby-join-usage")
        private String lobbyJoinUsage = "{prefix}<yellow>Usage: /manhunt lobby join <selector> <lobby-id> [role] " +
                "[-f|-force] [-notp]";

        @CustomKey("lobby-leave-usage")
        private String lobbyLeaveUsage = "{prefix}<yellow>Usage: /manhunt lobby leave [selector]";

        @CustomKey("lobby-already-member")
        private String lobbyAlreadyMember = "{prefix}<yellow>{player} is already in lobby <white>{lobby}<yellow> as " +
                "<white>{role}<yellow>.";

        @CustomKey("lobby-left")
        private String lobbyLeft = "{prefix}<yellow>Left lobby <white>{lobby}</white>.";

        @CustomKey("lobby-left-member")
        private String lobbyLeftMember = "{prefix}<white>{player}</white> <yellow>left lobby <white>{lobby}</white>.";

        @CustomKey("lobby-joined")
        private String lobbyJoined = "{prefix}<yellow>Joined lobby <white>{lobby}</white>.";

        @CustomKey("lobby-joined-member")
        private String lobbyJoinedMember = "{prefix}<white>{player}</white> <yellow>joined lobby " +
                "<white>{lobby}</white>.";

        @CustomKey("worldengine-lobbyconfig-duplicate-bounds")
        private String worldengineLobbyconfigDuplicateBounds = "{prefix}<red>Those exact bounds are already set on " +
                "lobby <white>{other}<red>.";

        @CustomKey("lobby-join-in-match")
        private String lobbyJoinInMatch = "{prefix}<yellow>{player} is in a running match. Use <white>/manhunt game " +
                "join<yellow> to move them.";

        @CustomKey("lobby-leave-not-member")
        private String lobbyLeaveNotMember = "{prefix}<yellow>{player} is not in a lobby.";

        @CustomKey("lobby-leave-in-match")
        private String lobbyLeaveInMatch = "{prefix}<yellow>{player} is in a running match. Use <white>/manhunt game " +
                "leave<yellow> to remove them.";

        @CustomKey("quickstart-usage")
        private String quickstartUsage = "{prefix}<yellow>Usage: /manhunt quickstart [percentage]";

        @CustomKey("config-usage")
        private String configUsage = "{prefix}<yellow>Usage: /manhunt config <category> <key...> [value]";

        @CustomKey("debug-usage")
        private String debugUsage = "{prefix}<yellow>Usage: /manhunt debug [on|off]";

        @CustomKey("worldengine-lobbyconfig-usage")
        private String worldengineLobbyconfigUsage = "{prefix}<yellow>Usage: /manhunt worldengine lobbyconfig " +
                "<pos1|pos2|setbounds <lobby-id>|setlobbytp <lobby-id> [x y z " +
                "yaw pitch]|deletelobby <lobby-id>>";

        @CustomKey("worldengine-lobbyconfig-pos1")
        private String worldengineLobbyconfigPos1 = "{prefix}<green>Bounds position 1 set to <white>{pos}<green>.";

        @CustomKey("worldengine-lobbyconfig-pos2")
        private String worldengineLobbyconfigPos2 = "{prefix}<green>Bounds position 2 set to <white>{pos}<green>.";

        @CustomKey("worldengine-lobbyconfig-need-selection")
        private String worldengineLobbyconfigNeedSelection = "{prefix}<red>Set both corners first with " +
                "<white>lobbyconfig pos1<red> and <white>lobbyconfig " +
                "pos2<red>.";

        @CustomKey("worldengine-lobbyconfig-world-mismatch")
        private String worldengineLobbyconfigWorldMismatch = "{prefix}<red>Both corners must be in the same world.";

        @CustomKey("worldengine-lobbyconfig-setbounds-usage")
        private String worldengineLobbyconfigSetboundsUsage = "{prefix}<yellow>Usage: /manhunt worldengine " +
                "lobbyconfig setbounds <lobby-id>";

        @CustomKey("worldengine-lobbyconfig-setbounds-confirm")
        private String worldengineLobbyconfigSetboundsConfirm = "{prefix}<yellow>Lobby <white>{lobby}<yellow> " +
                "already has bounds. Run again within <white>10 " +
                "seconds<yellow> to overwrite them.";

        @CustomKey("worldengine-lobbyconfig-setbounds-success")
        private String worldengineLobbyconfigSetboundsSuccess = "{prefix}<green>Set bounds for lobby " +
                "<white>{lobby}<green> to <white>{from}<green> to " +
                "<white>{to}<green>.";

        @CustomKey("worldengine-lobbyconfig-setlobbytp-usage")
        private String worldengineLobbyconfigSetlobbytpUsage = "{prefix}<yellow>Usage: /manhunt worldengine " +
                "lobbyconfig setlobbytp <lobby-id> [x y z yaw pitch]";

        @CustomKey("worldengine-lobbyconfig-setlobbytp-wrong-world")
        private String worldengineLobbyconfigSetlobbytpWrongWorld = "{prefix}<yellow>You must stand in the lobby " +
                "world (<white>{world}</white>) to set a lobby " +
                "teleport.";

        @CustomKey("worldengine-lobbyconfig-setlobbytp-success")
        private String worldengineLobbyconfigSetlobbytpSuccess = "{prefix}<green>Set lobby <white>{lobby}<green> " +
                "teleport to <white>{location}<green>.";

        @CustomKey("worldengine-lobbyconfig-deletelobby-usage")
        private String worldengineLobbyconfigDeletelobbyUsage = "{prefix}<yellow>Usage: /manhunt worldengine " +
                "lobbyconfig deletelobby <lobby-id>";

        @CustomKey("worldengine-lobbyconfig-deletelobby-missing")
        private String worldengineLobbyconfigDeletelobbyMissing = "{prefix}<red>Lobby <white>{lobby}<red> is not " +
                "defined in the lobby config.";

        @CustomKey("worldengine-lobbyconfig-deletelobby-confirm")
        private String worldengineLobbyconfigDeletelobbyConfirm = "{prefix}<yellow>This deletes lobby " +
                "<white>{lobby}<yellow> (teleport and bounds). Run " +
                "again within <white>10 seconds<yellow> to confirm.";

        @CustomKey("worldengine-lobbyconfig-deletelobby-success")
        private String worldengineLobbyconfigDeletelobbySuccess = "{prefix}<green>Deleted lobby " +
                "<white>{lobby}<green> from the lobby config.";

        @CustomKey("lobby-no-location-anywhere")
        private String lobbyNoLocationAnywhere = "{prefix}<red>No lobby teleport exists for lobby " +
                "<white>{lobby}<red> or any fallback lobby. Please contact an " +
                "administrator.";

        @CustomKey("worldengine-tpto-usage")
        private String worldengineTptoUsage = "{prefix}<yellow>Usage: /manhunt worldengine tpto " +
                "<lobbyworld|gameworld> [selector] [preset]";

        @CustomKey("worldengine-tpto-invalid-preset")
        private String worldengineTptoInvalidPreset = "{prefix}<red>Invalid lobby preset. Use one of: " +
                "<white>{presets}<red>.";

        @CustomKey("worldengine-tpto-lobby-world-clash")
        private String worldengineTptoLobbyWorldClash = "{prefix}<red>Lobby world name matches the game world. " +
                "Rename <white>world-engine.lobby-world-name<red> first.";
    }

    /** Live match announcements and end-of-match stat lines. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Game extends OkaeriConfig {

        @CustomKey("separator")
        private String separator = "<strikethrough><gradient:#bababa:#dedede:#bababa>"
                + " ".repeat(52) + "</gradient></strikethrough>";

        @CustomKey("hunter-death")
        private String hunterDeath = "{prefix}<yellow>A hunter has died!";

        @CustomKey("hunter-respawn-scheduled")
        private String hunterRespawnScheduled = "{prefix}<yellow>{player} will respawn in <white>{seconds}s</white>.";

        @CustomKey("hunter-respawn-imminent")
        private String hunterRespawnImminent = "{prefix}<green>{player} has respawned.";

        @CustomKey("speedrunner-respawn-scheduled")
        private String speedrunnerRespawnScheduled = "{prefix}<yellow>{player} will respawn in " +
                "<white>{seconds}s</white>.";

        @CustomKey("speedrunner-respawn-imminent")
        private String speedrunnerRespawnImminent = "{prefix}<green>{player} has respawned.";

        @CustomKey("hunter-out-of-lives")
        private String hunterOutOfLives = "{prefix}<yellow>A hunter has run out of lives and is eliminated.";

        @CustomKey("speedrunner-out-of-lives")
        private String speedrunnerOutOfLives = "{prefix}<yellow>A speedrunner has run out of lives and is eliminated.";

        @CustomKey("speedrunners-unlimited-lives")
        private String speedrunnersUnlimitedLives = "{prefix}<yellow>Speedrunners have unlimited lives.";

        @CustomKey("hunters-unlimited-lives")
        private String huntersUnlimitedLives = "{prefix}<yellow>Hunters have unlimited lives.";

        @CustomKey("speedrunner-death")
        private String speedrunnerDeath = "{prefix}<yellow>A speedrunner has died! <white>({value} remaining)</white>";

        @CustomKey("speedrunner-disconnect-warning")
        private String speedrunnerDisconnectWarning = "{prefix}<yellow>A speedrunner has disconnected! They will be " +
                "removed after <white>{seconds} seconds</white> unless they " +
                "rejoin.";

        @CustomKey("speedrunner-disconnect-cancelled")
        private String speedrunnerDisconnectCancelled = "{prefix}<green>A disconnected speedrunner has rejoined the " +
                "game.";

        @CustomKey("speedrunner-disconnect-removed")
        private String speedrunnerDisconnectRemoved = "{prefix}<yellow>A speedrunner has been removed for " +
                "disconnecting.";

        @CustomKey("hunter-disconnect-warning")
        private String hunterDisconnectWarning = "{prefix}<yellow>A hunter has disconnected! They will be removed " +
                "after <white>{seconds} seconds</white> unless they rejoin.";

        @CustomKey("hunter-disconnect-cancelled")
        private String hunterDisconnectCancelled = "{prefix}<green>A disconnected hunter has rejoined the game.";

        @CustomKey("hunter-disconnect-removed")
        private String hunterDisconnectRemoved = "{prefix}<yellow>A hunter has been removed for disconnecting.";

        @CustomKey("speedrunners-win")
        private String speedrunnersWin = "{prefix}<green>Speedrunners Win!";

        @CustomKey("hunters-win")
        private String huntersWin = "{prefix}<red>Hunters Win!";

        @CustomKey("hunters-title")
        private String huntersTitle = "<red>Hunters Win!";

        @CustomKey("speedrunners-title")
        private String speedrunnersTitle = "<green>Speedrunners Win!";

        @CustomKey("cancelled")
        private String cancelled = "{prefix}<yellow>Manhunt match cancelled.";

        @CustomKey("cancelled-title")
        private String cancelledTitle = "<gray>Cancelled";

        @CustomKey("join-success")
        private String joinSuccess = "{prefix}<green>Added <white>{count}<green> player(s) to instance " +
                "<white>{id}<green> as <white>{role}<green>.";

        @CustomKey("join-announce")
        private String joinAnnounce = "{prefix}<green>{player} has joined as a {role}<green>.";

        @CustomKey("join-invalid-role")
        private String joinInvalidRole = "{prefix}<red>Role must be hunter, speedrunner, spectator, or none.";

        @CustomKey("leave-confirm")
        private String leaveConfirm = "{prefix}<yellow>Run the command again within <white>10 seconds<yellow> to " +
                "leave the match.";

        @CustomKey("leave-not-in-match")
        private String leaveNotInMatch = "{prefix}<yellow>You are not in a match.";

        @CustomKey("leave-success")
        private String leaveSuccess = "{prefix}<gray>You left the match.";

        @CustomKey("leave-removed")
        private String leaveRemoved = "{prefix}<green>Removed <white>{count}<green> player(s) from the match.";

        @CustomKey("hunter-left")
        private String hunterLeft = "{prefix}<yellow>{player} left the match. <white>({remaining} hunters " +
                "remaining)</white>";

        @CustomKey("join-no-change")
        private String joinNoChange = "{prefix}<yellow>No players were added (already in a match).";

        @CustomKey("spawncamp-kill")
        private String spawncampKill = "{prefix}<red><white>{player}</white> was slain for spawn camping " +
                "<white>{victim}</white> <gray>({count} kills)</gray>";

        @CustomKey("spawncamp-gear-wipe")
        private String spawncampGearWipe = "{prefix}<red><white>{player}</white>'s gear was wiped for spawn camping " +
                "<white>{victim}</white> <gray>({count} kills)</gray>";

        @CustomKey("spawncamp-warning")
        private String spawncampWarning = "{prefix}<yellow>One more kill on <white>{victim}<yellow> and you will be " +
                "punished for spawn camping.";

        @CustomKey("speedrunner-left")
        private String speedrunnerLeft = "{prefix}<yellow>{player} left the match. <white>({remaining} speedrunners " +
                "remaining)</white>";

        @CustomKey("auto-left-bounds")
        private String autoLeftBounds = "{prefix}<yellow>You left the match area and were removed from the match.";

        @CustomKey("auto-left-lobby-world")
        private String autoLeftLobbyWorld = "{prefix}<yellow>You entered the lobby world and were removed from the " +
                "match.";

        @CustomKey("time-left")
        private String timeLeft = "{prefix}<yellow>{winner} win in <white>{time}<yellow>.";

        @CustomKey("cancel-in")
        private String cancelIn = "{prefix}<yellow>Match cancels in <white>{time}<yellow>.";

        @CustomKey("stat-header")
        private String statHeader = "\n{stat-prefix}{stat}:";

        @CustomKey("stat-header-prefix")
        private String statHeaderPrefix = "<bold><gradient:white:#dbdbdb:white>";

        @CustomKey("stat-entry")
        private String statEntry = "{rank-color}#{rank}: {player}: {value}";

        @CustomKey("rank-colors")
        private Map<String, String> rankColors = new LinkedHashMap<>(Map.of(
                "first", "<#f5af5b>",
                "second", "<#cccccc>",
                "third", "<#85553c>",
                "other", "<gray>"));

        @CustomKey("stat-names")
        private Map<String, String> statNames = new LinkedHashMap<>(Map.of(
                "DAMAGE_DEALT", "Damage Dealt",
                "HUNTER_FINAL_KILLS", "Hunter Final Kills",
                "SPEEDRUNNER_KILLS", "Speedrunner Kills",
                "PROGRESSION", "Progression"));

        @CustomKey("progression-names")
        private Map<String, String> progressionNames = new LinkedHashMap<>(Map.of(
                "got_wood", "<#874f2a>Got Wood",
                "got_iron", "<#c4c4c4>Got Iron",
                "entered_nether", "<#c237de>Entered the Nether",
                "found_bastion", "<#de802f>Found Bastion",
                "found_fortress", "<#912d4b>Found Fortress",
                "entered_stronghold", "<#88a698>Entered Stronghold",
                "entered_end", "<#5fe888>Entered the End"));
    }

    /** Tracking compass names, lore, and actionbars. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Compass extends OkaeriConfig {

        @CustomKey("hunter-name")
        private String hunterName = "{role-color-hunter}Hunter's Compass";

        @CustomKey("hunter-lore")
        private List<String> hunterLore = new ArrayList<>(List.of(
                "<gray>Tracks the nearest speedrunner.",
                "<gray><white>Right-click</white> to refresh when enabled.",
                "<gray><white>Left-click</white> to cycle targets when enabled."));

        @CustomKey("speedrunner-name")
        private String speedrunnerName = "{role-color-speedrunner}Speedrunner's Compass";

        @CustomKey("speedrunner-lore")
        private List<String> speedrunnerLore = new ArrayList<>(List.of(
                "<gray>Tracks the nearest hunter.",
                "<gray><white>Right-click</white> to refresh when enabled.",
                "<gray><white>Left-click</white> to cycle targets when enabled."));

        @CustomKey("compass-actionbar")
        private String compassActionbar = "<#de7766>Tracking <white>{player}<#de7766> • <white>{distance}m";

        @CustomKey("compass-last-seen-actionbar")
        private String compassLastSeenActionbar = "<#de7766>Tracking <white>{player}<#de7766>'s Last Seen • " +
                "<white>{distance}m <gray>({reason})";

        @CustomKey("compass-locked-actionbar")
        private String compassLockedActionbar = "<#de7766>Tracking <white>{player}<#de7766> • <white>{distance}m " +
                "<gray>[LOCKED]";

        @CustomKey("compass-last-seen-locked-actionbar")
        private String compassLastSeenLockedActionbar = "<#de7766>Tracking <white>{player}<#de7766>'s Last Seen • " +
                "<white>{distance}m <gray>({reason}) [LOCKED]";

        @CustomKey("no-target-actionbar")
        private String noTargetActionbar = "<gray>No {role}<gray> location available.";

        @CustomKey("nearby-actionbar")
        private String nearbyActionbar = "<green>{player} is nearby! The compass is disabled.";

        @CustomKey("too-far-actionbar")
        private String tooFarActionbar = "<yellow>{player} is out of tracking range.";

        @CustomKey("analyzing-actionbar")
        private String analyzingActionbar = "<gray>Analyzing...";

        @CustomKey("bad-signal-actionbar")
        private String badSignalActionbar = "<gray>☹ Bad signal";
    }

    /** One color tag per role. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class RoleColors extends OkaeriConfig {

        @CustomKey("speedrunner")
        private String speedrunner = "<#74de66>";

        @CustomKey("hunter")
        private String hunter = "<#de666e>";

        @CustomKey("spectator")
        private String spectator = "<#6e728a>";

        @CustomKey("afk")
        private String afk = "<#a18e68>";

        @CustomKey("none")
        private String none = "<#7d7d7d>";
    }

    /** Win-condition sentence fragments for the status-win lines. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Wincon extends OkaeriConfig {

        @CustomKey("eliminate-hunters")
        private String eliminateHunters = "eliminate all hunters";

        @CustomKey("eliminate-speedrunners")
        private String eliminateSpeedrunners = "eliminate all speedrunners";

        @CustomKey("credits")
        private String credits = "credits screen";

        @CustomKey("survive")
        private String survive = "survive {time}";

        @CustomKey("acquired")
        private String acquired = "{item} acquired";

        @CustomKey("advancement")
        private String advancement = "advancement {advancement}";

        @CustomKey("killed")
        private String killed = "{mob} killed";

        @CustomKey("time-limit")
        private String timeLimit = "time limit {time}";
    }

    /** Debug channel lines. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Debug extends OkaeriConfig {

        @CustomKey("prefix")
        private String prefix = "<gray>[<bold>J</bold>ManhuntDebug]</gray> ";

        @CustomKey("cell-fetched")
        private String cellFetched = "{debug-prefix}<gray>Fetched cell <white>{index} <gray>at <white>{x}<gray>, " +
                "<white>{z}<gray>.";

        @CustomKey("cell-buffer-add")
        private String cellBufferAdd = "{debug-prefix}<gray>Buffered cell <white>{index} <gray>({count}/{target}).";

        @CustomKey("cell-fetch-failed")
        private String cellFetchFailed = "{debug-prefix}<gray>Cell fetch failed, retrying in <white>{seconds}s<gray>.";

        @CustomKey("match-start")
        private String matchStart = "{debug-prefix}<gray>Match started in lobby <white>{lobby} <gray>on cell " +
                "<white>{index}<gray>.";

        @CustomKey("match-end")
        private String matchEnd = "{debug-prefix}<gray>Match on cell <white>{index} <gray>ended.";

        @CustomKey("end-cell-reserved")
        private String endCellReserved = "{debug-prefix}<gray>Reserved <white>{cell} <gray>for instance " +
                "<white>{id}<gray>.";

        @CustomKey("end-cell-created")
        private String endCellCreated = "{debug-prefix}<gray>Created end dimension <white>{cell}<gray>.";

        @CustomKey("end-cell-reset")
        private String endCellReset = "{debug-prefix}<gray>Reset end dimension <white>{cell}<gray>.";

        @CustomKey("end-cell-pruned")
        private String endCellPruned = "{debug-prefix}<gray>Pruned end dimension <white>{cell}<gray>.";

        @CustomKey("border-mode")
        private String borderMode = "{debug-prefix}<gray>Border mode: <white>{mode}<gray>.";

        @CustomKey("portal-reroute")
        private String portalReroute = "{debug-prefix}<gray>Rerouted <white>{player} <gray>to <white>{cell}<gray>.";

        @CustomKey("lobby-fallback")
        private String lobbyFallback = "{debug-prefix}<gray>Failed to fetch lobbytp for lobby <white>{lobby}<gray>, " +
                "using fallback <white>{fallback}<gray>.";

        @CustomKey("lobby-missing")
        private String lobbyMissing = "{debug-prefix}<gray>No lobbytp for lobby <white>{lobby}<gray> anywhere (or " +
                "the lobby world is missing); targets were told to contact an administrator.";
    }

    /** Developer schematic tool responses. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Dev extends OkaeriConfig {

        @CustomKey("usage")
        private String usage = "{prefix}<yellow>Usage: <white>/manhunt dev schem <pos1|pos2|save|load|list><yellow>.";

        @CustomKey("pos1")
        private String pos1 = "{prefix}<green>Position 1 set to <white>{pos}<green>.";

        @CustomKey("pos2")
        private String pos2 = "{prefix}<green>Position 2 set to <white>{pos}<green>.";

        @CustomKey("need-selection")
        private String needSelection = "{prefix}<red>Set both corners first with <white>pos1<red> and " +
                "<white>pos2<red>.";

        @CustomKey("world-mismatch")
        private String worldMismatch = "{prefix}<red>Both corners must be in the same world.";

        @CustomKey("invalid-name")
        private String invalidName = "{prefix}<red>Invalid schematic name.";

        @CustomKey("saved")
        private String saved = "{prefix}<green>Saved schematic <white>{name}<green> ({size}).";

        @CustomKey("save-failed")
        private String saveFailed = "{prefix}<red>Could not save schematic <white>{name}<red>.";

        @CustomKey("pasted")
        private String pasted = "{prefix}<green>Pasted schematic <white>{name}<green>.";

        @CustomKey("load-missing")
        private String loadMissing = "{prefix}<red>No schematic named <white>{name}<red>.";

        @CustomKey("load-failed")
        private String loadFailed = "{prefix}<red>Could not paste schematic <white>{name}<red>.";

        @CustomKey("list")
        private String list = "{prefix}<green>Schematics: <white>{value}";

        @CustomKey("list-empty")
        private String listEmpty = "{prefix}<yellow>No schematics saved yet.";
    }

    /** Modifier toggle command responses. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Modifiers extends OkaeriConfig {

        @CustomKey("usage")
        private String usage = "{prefix}<yellow>Usage: <white>/manhunt modifiers [setmod <name> " +
                "<true|false>|setpreset <id> <true|false>]<yellow>.";

        @CustomKey("setmod-usage")
        private String setmodUsage = "{prefix}<yellow>Usage: <white>/manhunt modifiers setmod <name> " +
                "<true|false><yellow>.";

        @CustomKey("setpreset-usage")
        private String setpresetUsage = "{prefix}<yellow>Usage: <white>/manhunt modifiers setpreset <id> " +
                "<true|false><yellow>.";

        @CustomKey("unknown-modifier")
        private String unknownModifier = "{prefix}<red>Unknown modifier <white>{name}<red>. Use one of: " +
                "<white>{valid}<red>.";

        @CustomKey("unknown-preset")
        private String unknownPreset = "{prefix}<red>Unknown preset <white>{name}<red>. Use one of: " +
                "<white>{valid}<red>.";

        @CustomKey("invalid-state")
        private String invalidState = "{prefix}<red>State must be <white>true<red> or <white>false<red>.";

        @CustomKey("setmod-success")
        private String setmodSuccess = "{prefix}<green>Modifier <white>{name}<green> is now <white>{state}<green>.";

        @CustomKey("setpreset-success")
        private String setpresetSuccess = "{prefix}<green>Preset <white>{name}<green> is now <white>{state}<green> " +
                "(<white>{count}<green> modifiers).";

        @CustomKey("list-header")
        private String listHeader = "\n{prefix}\n<#de7766>Modifiers:";

        @CustomKey("list-entry-on")
        private String listEntryOn = "<white>» {name} <green>[on]";

        @CustomKey("list-entry-off")
        private String listEntryOff = "<white>» {name} <gray>[off]";

        @CustomKey("list-presets-header")
        private String listPresetsHeader = "\n<#de7766>Presets:";

        @CustomKey("list-empty")
        private String listEmpty = "{prefix}<yellow>No modifiers defined.";

        @CustomKey("toggle-announced")
        private String toggleAnnounced = "{prefix}<white>{player} <yellow>set <gray>{key}</gray> to " +
                "<white>{value}</white>.</yellow></white>";

        @CustomKey("toggle-all-success")
        private String toggleAllSuccess = "{prefix}<green>Toggled all <white>{count} {kind}</white> to " +
                "<white>{state}</white>.";

        @CustomKey("toggle-all-announced")
        private String toggleAllAnnounced = "{prefix}<white>{player} <yellow>toggled all <gray>{count} {kind}</gray> " +
                "to <white>{state}</white>.</yellow></white>";
    }

    /** Modifiers GUI labels. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class ModifiersGui extends OkaeriConfig {

        @CustomKey("title-main")
        private String titleMain = "Modifiers";

        @CustomKey("title-modifiers")
        private String titleModifiers = "Modifiers";

        @CustomKey("title-presets")
        private String titlePresets = "Presets";

        @CustomKey("to-modifiers")
        private String toModifiers = "Modifiers";

        @CustomKey("to-modifiers-lore")
        private String toModifiersLore = "{enabled}/{total} enabled";

        @CustomKey("to-presets")
        private String toPresets = "Presets";

        @CustomKey("to-presets-lore")
        private String toPresetsLore = "{enabled}/{total} enabled";

        @CustomKey("scroll-up")
        private String scrollUp = "Scroll up";

        @CustomKey("scroll-down")
        private String scrollDown = "Scroll down";

        @CustomKey("back")
        private String back = "Back";

        @CustomKey("toggle-all")
        private String toggleAll = "Toggle all";

        @CustomKey("toggle-all-modifiers-lore")
        private String toggleAllModifiersLore = "{total} modifiers";

        @CustomKey("toggle-all-presets-lore")
        private String toggleAllPresetsLore = "{total} presets";

        @CustomKey("preset-more")
        private String presetMore = "and {count} more";

        @CustomKey("state-on")
        private String stateOn = "<green>Enabled";

        @CustomKey("state-off")
        private String stateOff = "<red>Disabled";
    }

    /** Settings browser, confirm panel, and dialog labels. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class ManhuntGui extends OkaeriConfig {

        @CustomKey("back")
        private String back = "Back";

        @CustomKey("cancel")
        private String cancel = "Cancel";

        @CustomKey("confirm")
        private String confirm = "Confirm";

        @CustomKey("scroll-up")
        private String scrollUp = "Scroll up";

        @CustomKey("scroll-down")
        private String scrollDown = "Scroll down";

        @CustomKey("dialog-submit")
        private String dialogSubmit = "Submit";

        @CustomKey("dialog-cancel")
        private String dialogCancel = "Cancel";

        @CustomKey("dialog-current")
        private String dialogCurrent = "Current value: <white>{value}";

        @CustomKey("dialog-bounds")
        private String dialogBounds = "Allowed: <white>{bounds}";

        @CustomKey("dialog-title-edit")
        private String dialogTitleEdit = "Edit {name}";

        @CustomKey("setting-value")
        private String settingValue = "Value: <white>{value}";

        @CustomKey("setting-path")
        private String settingPath = "Path: <white>{path}";

        @CustomKey("setting-type")
        private String settingType = "Type: <white>{type}";

        @CustomKey("setting-default")
        private String settingDefault = "Default: <white>{value}";

        @CustomKey("setting-hint-toggle")
        private String settingHintToggle = "Click to toggle";

        @CustomKey("setting-hint-cycle")
        private String settingHintCycle = "Click to cycle";

        @CustomKey("setting-hint-edit")
        private String settingHintEdit = "Click to edit";

        @CustomKey("setting-hint-reset")
        private String settingHintReset = "Right-click to reset";

        @CustomKey("setting-already-default")
        private String settingAlreadyDefault = "{prefix}<yellow>That setting is already the default.";

        @CustomKey("setting-reset-title")
        private String settingResetTitle = "Reset {name}?";

        @CustomKey("title-root")
        private String titleRoot = "Manhunt";

        @CustomKey("title-settings")
        private String titleSettings = "Settings";

        @CustomKey("title-category")
        private String titleCategory = "{name} Settings";

        @CustomKey("to-settings")
        private String toSettings = "Settings";

        @CustomKey("to-settings-lore")
        private String toSettingsLore = "Match, Compass, Players, Server";

        @CustomKey("to-history")
        private String toHistory = "History";

        @CustomKey("to-modifiers")
        private String toModifiers = "Modifiers";

        @CustomKey("to-modifiers-lore")
        private String toModifiersLore = "Toggle modifiers and presets";

        @CustomKey("category-lore")
        private String categoryLore = "{count} entries";

        @CustomKey("list-hint-open")
        private String listHintOpen = "Click to open";

        @CustomKey("list-entry-name")
        private String listEntryName = "#{index}";

        @CustomKey("list-hint-edit")
        private String listHintEdit = "Click to edit";

        @CustomKey("list-hint-delete")
        private String listHintDelete = "Right-click to delete";

        @CustomKey("list-add-name")
        private String listAddName = "Add entry";

        @CustomKey("list-add-lore")
        private String listAddLore = "Click to append";

        @CustomKey("dialog-title-edit-entry")
        private String dialogTitleEditEntry = "Edit entry {index}";

        @CustomKey("dialog-title-add-entry")
        private String dialogTitleAddEntry = "Add entry";

        @CustomKey("dialog-title-delete-entry")
        private String dialogTitleDeleteEntry = "Delete entry {index}?";

        @CustomKey("history-line-matches")
        private String historyLineMatches = "Matches played: <white>{value}";

        @CustomKey("history-line-kills")
        private String historyLineKills = "Total kills: <white>{value}";

        @CustomKey("history-line-hunter-kills")
        private String historyLineHunterKills = "Hunter kills: <white>{value}";

        @CustomKey("history-line-speedrunner-kills")
        private String historyLineSpeedrunnerKills = "Speedrunner kills: <white>{value}";

        @CustomKey("history-line-hunter-wins")
        private String historyLineHunterWins = "Hunter wins: <white>{value}";

        @CustomKey("history-line-speedrunner-wins")
        private String historyLineSpeedrunnerWins = "Speedrunner wins: <white>{value}";

        @CustomKey("history-line-damage")
        private String historyLineDamage = "Damage dealt: <white>{value}";

        @CustomKey("history-line-playtime")
        private String historyLinePlaytime = "Total playtime: <white>{value}";

        @CustomKey("setup-first-title")
        private String setupFirstTitle = "First-Time Setup";

        @CustomKey("setup-first-line1")
        private String setupFirstLine1 = "JManhunt is not set up yet.";

        @CustomKey("setup-first-line2")
        private String setupFirstLine2 = "Start the interactive setup guide?";

        @CustomKey("setup-first-confirm")
        private String setupFirstConfirm = "Start setup";

        @CustomKey("setup-first-cancel")
        private String setupFirstCancel = "Skip forever";
    }
}
