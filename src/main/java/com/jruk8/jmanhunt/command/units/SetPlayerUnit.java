package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CapSupport;
import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.ManhuntCommand;
import com.jruk8.jmanhunt.command.PendingConfirmations;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.command.UnitSyntaxException;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.MidMatchPolicy;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.lifecycle.MatchFinishService;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.player.CapLimits;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/** The setplayer verb: assign roles with caps, guards, and confirms. */
public final class SetPlayerUnit implements SubcommandUnit {
    public record SetPlayerDeps(PlayerStateStore playerStates, GameManager game,
            LobbyService lobbies, CapSupport caps, RoleTeamService teams, Logger logger,
            PendingConfirmations confirms) {
    }

    public record SetPlayerTexts(ManhuntMessages manhunt, CommandMessages command,
            CommandSupport support) {
    }

    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final LobbyService lobbies;
    private final CapSupport caps;
    private final RoleTeamService teams;
    private final Logger logger;
    private final PendingConfirmations confirms;
    private final ManhuntMessages texts;
    private final CommandMessages commandTexts;
    private final CommandSupport support;

    public SetPlayerUnit(SetPlayerDeps deps, SetPlayerTexts texts) {
        this.playerStates = deps.playerStates();
        this.game = deps.game();
        this.lobbies = deps.lobbies();
        this.caps = deps.caps();
        this.teams = deps.teams();
        this.logger = deps.logger();
        this.confirms = deps.confirms();
        this.texts = texts.manhunt();
        this.commandTexts = texts.command();
        this.support = texts.support();
    }

    /** Validated selector plus role plus trailing flags. */
    public record SetPlayerArgs(String selector, Role role, SetPlayerFlags flags) {
    }

    public static SetPlayerArgs parse(String[] args, ManhuntMessages texts)
            throws UnitSyntaxException {
        SetPlayerFlags flags = parseSetPlayerFlags(args);
        if (flags.end() != 3) {
            throw new UnitSyntaxException(texts.getSetplayerUsage(), Map.of());
        }
        Optional<Role> parsed = Role.parse(args[2]);
        if (parsed.isEmpty()) {
            throw new UnitSyntaxException(texts.getSetplayerUsage(), Map.of());
        }
        return new SetPlayerArgs(args[1], parsed.get(), flags);
    }

    /** Trailing -force/-silent flags plus the remaining argument count. Pure for tests. */
    public static SetPlayerFlags parseSetPlayerFlags(String[] args) {
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

    /** True for the -f and -force flags accepted by setplayer and lobby join. */
    public static boolean isForceFlag(String arg) {
        return arg.equalsIgnoreCase("-f") || arg.equalsIgnoreCase("-force");
    }

    /** True for the -s and -silent flags accepted by setplayer. */
    public static boolean isSilentFlag(String arg) {
        return arg.equalsIgnoreCase("-s") || arg.equalsIgnoreCase("-silent");
    }

    @Override public String primaryName() {
        return "setplayer";
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

    public boolean executeParsed(CommandSender sender, SetPlayerArgs args) {
        List<Entity> selected;
        try {
            selected = Bukkit.selectEntities(sender, args.selector());
        } catch (IllegalArgumentException exception) {
            return support.message(sender, texts.getSetplayerUsage());
        }
        // The full setplayer permission overrides every other check: any
        // selector and any role. Otherwise the sender needs the self node,
        // the selector must resolve to exactly the sender, and the sender
        // must hold the permission for the requested role.
        if (!sender.hasPermission("jmanhunt.command.setplayer")
                && (!ManhuntCommand.isSelfOnlySelection(sender, selected)
                || !sender.hasPermission(ManhuntCommand.rolePermissionNode(args.role())))) {
            return support.message(sender, commandTexts.getNoPermission());
        }
        if (selected.stream().noneMatch(entity -> entity instanceof Player)) {
            return support.message(sender, commandTexts.getNoTargets());
        }
        int afkWakes = countAfkWakes(sender, selected, args.role(), args.flags().force());
        if (afkWakes > 0 && !confirms.confirm("setplayer-afk:" + CommandSupport.senderKey(sender))) {
            support.message(sender, texts.getSetAfkConfirm(), Map.of("count", String.valueOf(afkWakes)));
            return true;
        }
        SetPlayerTally tally = new SetPlayerTally();
        for (Entity entity : selected) {
            if (entity instanceof Player player) {
                applySetPlayerToPlayer(sender, player, args.role(), args.flags().force(),
                        args.flags().silent(), tally);
            }
        }
        reportSetPlayerResults(sender, args.role(), tally);
        return true;
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("setplayer")) {
            if (args[1].startsWith("@a[")) {
                return CommandSupport.partial(args[1],
                        List.of("@a[distance=", "@a[limit=", "@a[name=", "@a[gamemode="));
            }
            return CommandSupport.partial(args[1], CommandSupport.selectorOptions());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setplayer")) {
            return CommandSupport.partial(args[2], List.of("hunter", "speedrunner", "spectator", "afk", "none"));
        }
        if ((args.length == 4 || args.length == 5) && args[0].equalsIgnoreCase("setplayer")) {
            return CommandSupport.partial(args[args.length - 1], List.of("-f", "-force", "-s", "-silent"));
        }
        return null;
    }

    /** Assigns one selected player their role, recording the outcome. */
    private void applySetPlayerToPlayer(CommandSender sender, Player player, Role role, boolean force,
            boolean silent, SetPlayerTally tally) {
        if (playerStates.role(player) == role) {
            tally.unchanged++;
            return;
        }
        if (!player.hasPermission(ManhuntCommand.rolePermissionNode(role))) {
            tally.skipped++;
            return;
        }
        Optional<Lobby> targetLobby = lobbies.lobbyOf(player.getUniqueId());
        Optional<GameInstance> live = targetLobby.flatMap(lobby -> game.instanceForLobby(lobby.id()));
        if (live.isPresent()) {
            applySetPlayerInMatch(sender, player, role, force, silent, targetLobby, live.get(), tally);
            return;
        }
        if (!caps.capAllows(lobbies.lobbyOf(player.getUniqueId()), role, force, tally.cappedIn)) {
            return;
        }
        assignSetPlayerRole(sender, player, role, silent, tally);
    }

    /** Assigns a player whose lobby has a live match: mid-match join or hold. */
    private void applySetPlayerInMatch(CommandSender sender, Player player, Role role, boolean force,
            boolean silent, Optional<Lobby> targetLobby, GameInstance live, SetPlayerTally tally) {
        if (!lobbies.multiLobbyAllowed()) {
            support.message(sender, texts.getSetInMatch(), Map.of("player", player.getName()));
            return;
        }
        boolean member = live.isActive(player.getUniqueId());
        if (member && !force) {
            support.message(sender, texts.getSetplayerNeedsForce(), Map.of("player", player.getName()));
            return;
        }
        if (member && role == Role.SPECTATOR) {
            leaveMatchForSetPlayer(player, silent, live, tally);
            return;
        }
        if (member && (role == Role.AFK || role == Role.NONE)) {
            leaveMatchToLobbyForSetPlayer(player, role, silent, live, tally);
            return;
        }
        MidMatchPolicy policy = lobbies.midMatchPolicy();
        GameInstance target = targetLobby.map(lobby ->
                game.midMatchJoinTarget(policy, lobby.id(), live, role)).orElse(live);
        if (policy.joinsMidMatch(role)
                && game.joinPlayers(target, List.of(player), role) == 1) {
            tally.joined++;
            tally.assigned.add(player.getUniqueId());
            return;
        }
        if (member) {
            switchMemberRoleForSetPlayer(sender, player, role, silent, live, tally);
            return;
        }
        if (!caps.capAllows(targetLobby, role, force, tally.cappedIn)) {
            return;
        }
        assignSetPlayerRole(sender, player, role, silent, tally);
        recordQueuedRole(player, role, silent, policy, tally);
    }

    /** Tallies a non-member queued under a live match and tells them where they wait. */
    private void recordQueuedRole(Player player, Role role, boolean silent,
            MidMatchPolicy policy, SetPlayerTally tally) {
        if (policy.usesSubLobbies()) {
            tally.queued++;
        } else {
            tally.held++;
        }
        if (!silent && role != Role.AFK) {
            support.message(player, policy.queueMessageTemplate(texts), Map.of("role", support.roleName(role)));
        }
    }

    /** Mid-match setplayer to spectator: the same leave /manhunt game leave performs. */
    private void leaveMatchForSetPlayer(Player player, boolean silent, GameInstance live,
            SetPlayerTally tally) {
        Role from = playerStates.role(player);
        int removed = game.leaveMatch(live, List.of(player), live.begun());
        if (removed == 0) {
            return;
        }
        tally.changed++;
        tally.assigned.add(player.getUniqueId());
        Role after = playerStates.role(player);
        if (!silent && after != from) {
            game.messaging().announceRoleChange(player, from, after);
        }
    }

    /** Mid-match setplayer to AFK or NONE: a full leave plus a lobby return. */
    private void leaveMatchToLobbyForSetPlayer(Player player, Role role, boolean silent,
            GameInstance live, SetPlayerTally tally) {
        Role from = playerStates.role(player);
        int removed = game.leaveMatchToLobby(live, player, role);
        if (removed == 0) {
            return;
        }
        tally.changed++;
        tally.assigned.add(player.getUniqueId());
        if (!silent) {
            game.messaging().announceRoleChange(player, from, role);
        }
    }

    /** Cancels a live match a forced role change left without both sides. */
    private void cancelMatchIfInvalid(CommandSender sender, GameInstance live) {
        int hunters = game.activeHunterCount(live);
        int runners = game.activeRunnerCount(live);
        if (MatchFinishService.canProgress(hunters, runners) || !live.active() || live.ending()) {
            return;
        }
        String reason = MatchFinishService.invalidReason(hunters, runners);
        support.message(sender, texts.getSetplayerInvalidCancel(),
                Map.of("id", live.lobbyTag(), "reason", reason));
        logger.warning("Match " + live.lobbyTag() + " cancelled by setplayer: " + reason + ".");
        game.cancel(live);
    }

    /**
     * Forced in-match member switch through the shared switch path:
     * lives refresh, compass handover, and modifier catch-up ride
     * along. Setplayer keeps its own messaging and cancel check.
     */
    private void switchMemberRoleForSetPlayer(CommandSender sender, Player player, Role role,
            boolean silent, GameInstance live, SetPlayerTally tally) {
        Role from = playerStates.role(player);
        game.switchMemberRole(live, player, role);
        tally.changed++;
        tally.assigned.add(player.getUniqueId());
        lobbies.applyLobbyCollisions(player);
        if (!silent) {
            game.messaging().announceRoleChange(player, from, role);
            support.message(player, texts.getRoleAssigned(), Map.of("role", support.roleName(role)));
            support.neutralSound(player);
        }
        cancelMatchIfInvalid(sender, live);
    }

    /** Sets one player's role with teams sync and announcements. */
    private void assignSetPlayerRole(CommandSender sender, Player player, Role role, boolean silent,
            SetPlayerTally tally) {
        Role from = playerStates.role(player);
        playerStates.setRole(player, role);
        tally.changed++;
        teams.sync(player);
        lobbies.applyLobbyCollisions(player);
        if (!silent) {
            game.messaging().announceRoleChange(player, from, role);
        }
        tally.assigned.add(player.getUniqueId());
        if (!silent) {
            support.message(player, texts.getRoleAssigned(), Map.of("role", support.roleName(role)));
            support.neutralSound(player);
        }
    }

    /** Reports one setplayer run: summary, skips, joins, holds, and caps. */
    private void reportSetPlayerResults(CommandSender sender, Role role, SetPlayerTally tally) {
        support.message(sender, tally.unchanged == 0 ? texts.getSetSuccess()
                : texts.getSetSuccessUnchanged(),
                Map.of("count", String.valueOf(tally.changed), "role", support.roleName(role),
                        "unchanged", String.valueOf(tally.unchanged)));
        if (tally.skipped > 0) {
            support.message(sender, texts.getSetSkipped(), Map.of("count", String.valueOf(tally.skipped)));
        }
        if (tally.joined > 0) {
            support.message(sender, texts.getSetplayerJoined(),
                    Map.of("count", String.valueOf(tally.joined), "role", support.roleName(role)));
        }
        if (tally.held > 0) {
            support.message(sender, texts.getSetplayerHeldSummary(), Map.of("count", String.valueOf(tally.held)));
        }
        if (tally.queued > 0) {
            support.message(sender, texts.getSetplayerQueuedSublobbySummary(),
                    Map.of("count", String.valueOf(tally.queued)));
        }
        for (Map.Entry<Integer, Set<Role>> entry : tally.cappedIn.entrySet()) {
            for (Role cappedRole : entry.getValue()) {
                support.message(sender, texts.getLobbyFull(), Map.of("lobby", String.valueOf(entry.getKey()),
                        "role", support.roleName(cappedRole)));
            }
        }
        if (sender instanceof Player player && !tally.assigned.contains(player.getUniqueId())) {
            support.neutralSound(player);
        }
        if (tally.changed > 0) {
            game.updateAutostartState();
        }
    }

    /** Trailing flags parsed from a setplayer invocation. */
    public record SetPlayerFlags(boolean force, boolean silent, int end) {
    }

    /** Mutable counters for one setplayer run. */
    private static final class SetPlayerTally {
        int changed;
        int unchanged;
        int skipped;
        int joined;
        int held;
        int queued;
        final Set<java.util.UUID> assigned = new HashSet<>();
        final Map<Integer, Set<Role>> cappedIn = new LinkedHashMap<>();
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
            if (!player.hasPermission(ManhuntCommand.rolePermissionNode(role))) {
                continue;
            }
            Optional<Lobby> lobby = lobbies.lobbyOf(player.getUniqueId());
            if (lobby.isPresent() && game.instanceForLobby(lobby.get().id()).isPresent()) {
                continue;
            }
            if (!force && role.isParticipant() && lobby.isPresent()
                    && !CapLimits.allows(caps.lobbyRoleCount(lobby.get(), role), caps.capFor(role))) {
                continue;
            }
            wakes++;
        }
        return wakes;
    }

    /** True when waking someone else's AFK role, which needs confirmation. */
    public static boolean needsAfkGuard(Role from, Role to, boolean self) {
        return from == Role.AFK && to != Role.AFK && !self;
    }

    private static boolean isSelfTarget(CommandSender sender, Player player) {
        return sender instanceof Player self && self.getUniqueId().equals(player.getUniqueId());
    }
}
