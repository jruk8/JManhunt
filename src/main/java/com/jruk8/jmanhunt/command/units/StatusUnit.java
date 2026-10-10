package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.ManhuntCommand;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.command.UnitSyntaxException;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.DurationFormat;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.StatusRosterService;
import com.jruk8.jmanhunt.match.listeners.PlayerRespawnListener;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** The status verb: own roster, one roster, or every match. */
public final class StatusUnit implements SubcommandUnit {
    public record StatusDeps(GameManager game, LobbyService lobbies, ConfigService config,
            StatusRosterService roster, Supplier<PlayerRespawnListener> respawns) {
    }

    public record StatusTexts(ManhuntMessages manhunt, CommandMessages command,
            CommandSupport support) {
    }

    private final GameManager game;
    private final LobbyService lobbies;
    private final ConfigService config;
    private final StatusRosterService roster;
    private final Supplier<PlayerRespawnListener> respawns;
    private final ManhuntMessages texts;
    private final CommandMessages commandTexts;
    private final CommandSupport support;

    public StatusUnit(StatusDeps deps, StatusTexts texts) {
        this.game = deps.game();
        this.lobbies = deps.lobbies();
        this.config = deps.config();
        this.roster = deps.roster();
        this.respawns = deps.respawns();
        this.texts = texts.manhunt();
        this.commandTexts = texts.command();
        this.support = texts.support();
    }

    /** Empty target means the sender's own roster. */
    public record StatusArgs(Optional<String> target) {
    }

    public static StatusArgs parse(String[] args, ManhuntMessages texts)
            throws UnitSyntaxException {
        if (args.length > 2) {
            throw new UnitSyntaxException(texts.getStatusUsage(), Map.of());
        }
        if (args.length == 2) {
            return new StatusArgs(Optional.of(args[1]));
        }
        return new StatusArgs(Optional.empty());
    }

    @Override public String primaryName() {
        return "status";
    }

    @Override public Set<String> aliases() {
        return Set.of();
    }

    @Override public boolean execute(CommandSender sender, String[] args) {
        try {
            return executeParsed(sender, parse(args, texts));
        } catch (UnitSyntaxException failure) {
            support.message(sender, failure.template(), failure.placeholders());
            return true;
        }
    }

    public boolean executeParsed(CommandSender sender, StatusArgs args) {
        if (args.target().isPresent()) {
            if (!ManhuntCommand.canUseStatusArgs(sender)) {
                return support.message(sender, commandTexts.getNoPermission());
            }
            if (args.target().get().equalsIgnoreCase("all")) {
                return statusAll(sender);
            }
            Optional<GameInstance> instance = game.resolveInstance(args.target().get());
            if (instance.isEmpty()) {
                return support.message(sender, texts.getInvalidInstanceId());
            }
            return statusInstance(sender, instance.get());
        }
        if (sender instanceof Player player) {
            Optional<GameInstance> own = game.instanceOf(player.getUniqueId());
            if (own.isPresent()) {
                return statusInstance(sender, own.get());
            }
            // Eliminated watchers stay assigned but inactive: they see
            // their match, while never-assigned queuers see the lobby.
            Optional<GameInstance> watched = game.assignedInstanceOf(player.getUniqueId());
            if (watched.isPresent()) {
                return statusInstance(sender, watched.get());
            }
            Optional<Lobby> lobby = lobbies.lobbyOf(player.getUniqueId());
            if (lobby.isEmpty()) {
                return support.message(sender, texts.getNotInMatch());
            }
            return statusLobby(sender, lobby.get());
        }
        return support.message(sender, texts.getConsoleRequiresId());
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("status")) {
            if (!ManhuntCommand.canUseStatusArgs(sender)) {
                return List.of();
            }
            List<String> options = new ArrayList<>(List.of("all"));
            options.addAll(CommandSupport.instanceIdOptions(game));
            return CommandSupport.partial(args[1], options);
        }
        return null;
    }

    /** One match roster: every online member grouped by role. */
    private boolean statusInstance(CommandSender sender, GameInstance instance) {
        List<Player> players = game.onlineMatchRoster(instance);
        support.message(sender, texts.getStatusHeader(),
                Map.of("status", instance.ending() ? "ENDING" : "ACTIVE"));
        sendElapsedLine(sender, instance);
        sendMatchRoleBlocks(sender, players, instance.deadPlayers());
        sendWinConditionLines(sender);
        sendModifiersLine(sender);
        sendIdLine(sender, instance.lobbyTag() + "|G" + instance.matchId());
        support.neutralSound(sender);
        return true;
    }

    /** One lobby queue: every online member outside matches, grouped by role. */
    private boolean statusLobby(CommandSender sender, Lobby lobby) {
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> lobby.contains(p.getUniqueId()))
                .filter(p -> game.instanceOf(p.getUniqueId()).isEmpty())
                .map(p -> (Player) p).toList();
        support.message(sender, texts.getStatusHeader(), Map.of("status", "INACTIVE"));
        sendMatchRoleBlocks(sender, players, List.of());
        sendWinConditionLines(sender);
        sendIdLine(sender, "L" + lobby.id());
        support.neutralSound(sender);
        return true;
    }

    /** The four role blocks plus the spectator roll call, shared by both rosters. */
    private void sendMatchRoleBlocks(CommandSender sender, List<Player> players,
            List<GameInstance.DeadPlayer> dead) {
        Predicate<UUID> respawning = StatusRosterService.respawning(respawns.get());
        roster.sendRoleSection(sender, players, Role.SPEEDRUNNER, texts.getSpeedrunnersHeader(),
                dead, respawning);
        roster.sendRoleSection(sender, players, Role.HUNTER, texts.getHuntersHeader(), dead,
                respawning);
        roster.sendRoleSection(sender, players, Role.AFK, texts.getAfkHeader(), dead, respawning);
        roster.sendRoleSection(sender, players, Role.NONE, texts.getNoneHeader(), dead, respawning);
        roster.sendSpectatorLine(sender, players);
    }

    /** Optional per-side win rules, off by default to keep status compact. */
    private void sendWinConditionLines(CommandSender sender) {
        if (!config.getBoolean("settings.server.status.show-win-conditions", false)) {
            return;
        }
        support.message(sender, texts.getStatusWinSpeedrunners(),
                Map.of("conditions", game.speedrunnerWinConditions()));
        support.message(sender, texts.getStatusWinHunters(), Map.of("conditions", game.hunterWinConditions()));
    }

    /** Optional enabled-modifier roll call: hidden when off or when none are enabled. */
    private void sendModifiersLine(CommandSender sender) {
        if (!config.getBoolean("settings.server.status.show-modifiers", true)) {
            return;
        }
        List<String> enabled = new ArrayList<>();
        for (String name : config.modifierNames()) {
            if (config.modifierEnabled(name)) {
                String display = config.modifiers().metaName(name);
                enabled.add(display == null || display.isBlank() ? name : display);
            }
        }
        if (enabled.isEmpty()) {
            return;
        }
        enabled.sort(String.CASE_INSENSITIVE_ORDER);
        support.message(sender, texts.getStatusModifiers(),
                Map.of("modifiers",
                        ListFormatter.joinOxfordColored(enabled, "<white>", "</white>")));
    }

    /** Optional match runtime, on by default. */
    private void sendElapsedLine(CommandSender sender, GameInstance instance) {
        if (!config.getBoolean("settings.server.status.show-elapsed-time", true)) {
            return;
        }
        support.message(sender, texts.getStatusElapsed(), Map.of("duration",
                DurationFormat.format(instance.elapsedSeconds(System.currentTimeMillis()))));
    }

    /** Optional lobby/game tag (L1, L1-0|G2 when sublobbed), on by default. */
    private void sendIdLine(CommandSender sender, String value) {
        if (!config.getBoolean("settings.server.status.show-ids", true)) {
            return;
        }
        support.message(sender, texts.getStatusIds(), Map.of("value", value));
    }

    /**
     * Every running match plus every populated lobby queue: ids with
     * active and assigned counts and durations first, then one line per
     * lobby holding at least one queued member or match member. Quiet
     * servers still print the games empty line so the lobby section
     * never hides alone.
     */
    private boolean statusAll(CommandSender sender) {
        List<GameInstance> live = game.liveInstances();
        if (live.isEmpty()) {
            support.message(sender, texts.getStatusAllEmpty());
        } else {
            support.message(sender, texts.getStatusAllHeader());
            long now = System.currentTimeMillis();
            for (GameInstance instance : live) {
                support.message(sender, texts.getStatusAllEntry(), Map.of(
                        "lobby", instance.lobbyTag(),
                        "id", String.valueOf(instance.matchId()),
                        "remaining", String.valueOf(instance.activeIds().size()),
                        "assigned", String.valueOf(instance.assignedCount()),
                        "duration", DurationFormat.format(instance.elapsedSeconds(now))));
            }
        }
        sendLobbyQueues(sender);
        support.neutralSound(sender);
        return true;
    }

    /**
     * One line per lobby holding queued or match members, by lobby id.
     * The in-match count unions active ids across the lobby's matches,
     * so disconnected members read until they are kicked; the in-lobby
     * count is online queue members outside any match.
     */
    private void sendLobbyQueues(CommandSender sender) {
        List<Lobby> queued = new ArrayList<>();
        for (int id : lobbies.lobbyIds()) {
            lobbies.get(id).ifPresent(queued::add);
        }
        queued.sort(Comparator.comparingInt(Lobby::id));
        boolean header = false;
        for (Lobby lobby : queued) {
            Set<UUID> inMatch = new HashSet<>();
            for (GameInstance instance : game.instancesForLobby(lobby.id())) {
                inMatch.addAll(instance.activeIds());
            }
            long inLobby = Bukkit.getOnlinePlayers().stream()
                    .filter(player -> lobby.contains(player.getUniqueId()))
                    .filter(player -> game.instanceOf(player.getUniqueId()).isEmpty())
                    .count();
            if (inLobby < 1 && inMatch.isEmpty()) {
                continue;
            }
            if (!header) {
                support.message(sender, texts.getStatusAllLobbiesHeader());
                header = true;
            }
            support.message(sender, texts.getStatusAllLobbyEntry(), Map.of(
                    "lobby", String.valueOf(lobby.id()),
                    "detail", StatusRosterService.lobbyDetail(inLobby, inMatch.size())));
        }
    }
}
