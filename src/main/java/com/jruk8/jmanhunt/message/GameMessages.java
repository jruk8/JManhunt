package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.LinkedHashMap;
import java.util.Map;

/** Live match announcements and end-of-match stat lines. */
@SuppressWarnings("FieldMayBeFinal")
public class GameMessages extends OkaeriConfig {

    @CustomKey("separator")
    private String separator = "<strikethrough><gradient:#bababa:#dedede:#bababa>"
            + " ".repeat(52) + "</gradient></strikethrough>";

    @CustomKey("hunter-death")
    private String hunterDeath = "{prefix}<yellow>A hunter has died!";

    @CustomKey("roles-swapped")
    private String rolesSwapped = "{prefix}<yellow>Roles swapped: hunters and speedrunners trade places!";

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
