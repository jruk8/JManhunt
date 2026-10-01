package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CapSupport;
import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.command.UnitSyntaxException;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.player.CapLimits;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** The lobby verb: join players to or remove them from lobbies. */
public final class LobbyUnit implements SubcommandUnit {
    public record LobbyDeps(GameManager game, LobbyService lobbies, PlayerStateStore playerStates,
            ConfigService config, LobbyTeleporter teleporter, RoleTeamService teams,
            CapSupport caps) {
    }

    public record LobbyTexts(ManhuntMessages manhunt, CommandMessages command,
            CommandSupport support) {
    }

    private final GameManager game;
    private final LobbyService lobbies;
    private final PlayerStateStore playerStates;
    private final ConfigService config;
    private final LobbyTeleporter teleporter;
    private final RoleTeamService teams;
    private final CapSupport caps;
    private final ManhuntMessages texts;
    private final CommandMessages commandTexts;
    private final CommandSupport support;

    public LobbyUnit(LobbyDeps deps, LobbyTexts texts) {
        this.game = deps.game();
        this.lobbies = deps.lobbies();
        this.playerStates = deps.playerStates();
        this.config = deps.config();
        this.teleporter = deps.teleporter();
        this.teams = deps.teams();
        this.caps = deps.caps();
        this.texts = texts.manhunt();
        this.commandTexts = texts.command();
        this.support = texts.support();
    }

    /**
     * Validated verb plus the raw line: join and leave keep their own
     * shape checks because their arities diverge.
     */
    public record LobbyArgs(String action, String[] args) {
    }

    public static LobbyArgs parse(String[] args, ManhuntMessages texts)
            throws UnitSyntaxException {
        if (args.length < 2 || args[1].isBlank()) {
            throw new UnitSyntaxException(texts.getLobbyUsage(), Map.of());
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("join") || action.equals("leave")) {
            return new LobbyArgs(action, args);
        }
        throw new UnitSyntaxException(texts.getLobbyUsage(), Map.of());
    }

    /** True for the -notp flag accepted by lobby join. */
    public static boolean isNoTeleportFlag(String arg) {
        return arg.equalsIgnoreCase("-notp");
    }

    @Override public String primaryName() {
        return "lobby";
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

    public boolean executeParsed(CommandSender sender, LobbyArgs args) {
        if (args.action().equals("join")) {
            return lobbyJoin(sender, args.args());
        }
        return lobbyLeave(sender, args.args());
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("lobby")) {
            return CommandSupport.partial(args[1], List.of("join", "leave"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return CommandSupport.partial(args[2], CommandSupport.selectorOptions());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("leave")) {
            return CommandSupport.partial(args[2], CommandSupport.selectorOptions());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return CommandSupport.partial(args[3], CommandSupport.lobbyIdOptions(lobbies));
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return CommandSupport.partial(args[4], List.of("hunter", "speedrunner", "spectator", "afk", "none",
                    "-f", "-force", "-notp", "-s", "-silent"));
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return CommandSupport.partial(args[5], List.of("-f", "-force", "-notp", "-s", "-silent"));
        }
        if (args.length == 7 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return CommandSupport.partial(args[6], List.of("-f", "-force", "-notp", "-s", "-silent"));
        }
        if (args.length == 8 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("join")) {
            return CommandSupport.partial(args[7], List.of("-f", "-force", "-notp", "-s", "-silent"));
        }
        return null;
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
            return support.message(sender, texts.getLobbyJoinUsage());
        }
        OptionalInt lobbyId = LobbyService.parseId(args[3]);
        if (lobbyId.isEmpty()) {
            return support.message(sender, texts.getLobbyInvalidId());
        }
        Role role = Role.NONE;
        if (flags.end() == 5) {
            Optional<Role> parsed = Role.parse(args[4]);
            if (parsed.isEmpty()) {
                return support.message(sender, texts.getLobbyJoinUsage());
            }
            role = parsed.get();
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId.getAsInt() != 0) {
            return support.message(sender, texts.getLobbyWorldengineRequired());
        }
        List<Player> targets = CommandSupport.selectPlayers(sender, args[2]);
        if (targets == null) {
            return support.message(sender, texts.getLobbyJoinUsage());
        }
        if (targets.isEmpty()) {
            return support.message(sender, commandTexts.getNoTargets());
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
        while (end > 2 && (SetPlayerUnit.isForceFlag(args[end - 1]) || isNoTeleportFlag(args[end - 1]))) {
            if (SetPlayerUnit.isForceFlag(args[end - 1])) {
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
            support.message(sender, texts.getLobbyJoinInMatch(), Map.of("player", target.getName()));
            return;
        }
        Optional<Lobby> current = lobbies.lobbyOf(target.getUniqueId());
        if (current.isPresent() && current.get().id() == lobbyId
                && playerStates.role(target) == role) {
            support.message(sender, texts.getLobbyAlreadyMember(), Map.of("player", target.getName(),
                    "lobby", String.valueOf(lobbyId), "role", support.roleName(role)));
            return;
        }
        Lobby lobby = lobbies.get(lobbyId).orElse(null);
        int count = lobby == null ? 0 : caps.lobbyRoleCount(lobby, role);
        if (!force && role.isParticipant() && !CapLimits.allows(count, caps.capFor(role))) {
            capped.add(role);
            return;
        }
        OptionalInt before = current.map(own -> OptionalInt.of(own.id())).orElseGet(OptionalInt::empty);
        lobbies.setLobby(target.getUniqueId(), lobbyId);
        lobbies.applyLobbyCollisions(target);
        playerStates.setRole(target, role);
        teams.sync(target);
        moved.add(target);
        lobbies.announceLobbyChange(target, before, OptionalInt.of(lobbyId));
    }

    /** Reports one lobby join run and teleports the movers. */
    private void reportLobbyJoinResults(CommandSender sender, List<Player> moved, Set<Role> capped,
            int lobbyId, Role role, boolean noTeleport) {
        for (Role cappedRole : capped) {
            support.message(sender, texts.getLobbyFull(), Map.of("lobby", String.valueOf(lobbyId),
                    "role", support.roleName(cappedRole)));
        }
        support.message(sender, texts.getLobbyJoinSuccess(), Map.of("count", String.valueOf(moved.size()),
                "lobby", String.valueOf(lobbyId), "role", support.roleName(role)));
        support.neutralSound(sender);
        if (!moved.isEmpty() && !noTeleport
                && config.getBoolean("advanced.lobbies.join-teleports-to-lobby", true)) {
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
        if (!teleporter.teleportToLobby(moved, lobbyId)) {
            support.message(sender, texts.getLobbyNoLocation(), Map.of("lobby", String.valueOf(lobbyId)));
        }
    }

    /** Removes players from whatever lobby they are in: leave [selector]. */
    private boolean lobbyLeave(CommandSender sender, String[] args) {
        if (!lobbies.multiLobbyAllowed()) {
            return support.message(sender, texts.getLobbyWorldengineRequired());
        }
        if (args.length > 3) {
            return support.message(sender, texts.getLobbyLeaveUsage());
        }
        List<Player> targets = CommandSupport.selectPlayers(sender, args.length == 3 ? args[2] : "@s");
        if (targets == null) {
            return support.message(sender, texts.getLobbyLeaveUsage());
        }
        if (targets.isEmpty()) {
            return support.message(sender, commandTexts.getNoTargets());
        }
        for (Player target : targets) {
            Optional<Lobby> lobby = lobbies.lobbyOf(target.getUniqueId());
            if (lobby.isEmpty()) {
                support.message(sender, texts.getLobbyLeaveNotMember(), Map.of("player", target.getName()));
                continue;
            }
            if (game.instanceOf(target.getUniqueId()).isPresent()) {
                support.message(sender, texts.getLobbyLeaveInMatch(), Map.of("player", target.getName()));
                continue;
            }
            lobbies.remove(target.getUniqueId());
            lobbies.restoreCollisions(target);
            support.message(sender, texts.getLobbyLeaveSuccess(), Map.of("player", target.getName(),
                    "lobby", String.valueOf(lobby.get().id())));
        }
        support.neutralSound(sender);
        game.updateAutostartState();
        return true;
    }
}
