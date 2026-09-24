package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Core /manhunt command responses. */
@SuppressWarnings("FieldMayBeFinal")
public class ManhuntMessages extends OkaeriConfig {

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

    @CustomKey("setting-unchanged")
    private String settingUnchanged = "{prefix}<yellow>Nothing changed. <white>{setting}<yellow> was already " +
            "<white>{value}<yellow>.";

    @CustomKey("setting-list-reset")
    private String settingListReset = "{prefix}<green>Reset <white>{setting}<green> to defaults.";

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
