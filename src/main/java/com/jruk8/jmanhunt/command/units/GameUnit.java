package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.PendingConfirmations;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.command.UnitSyntaxException;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.GameMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** The game verb: join players to or remove them from a running match. */
public final class GameUnit implements SubcommandUnit {
    public record GameDeps(GameManager game, PlayerStateStore playerStates,
            PendingConfirmations confirms) {
    }

    public record GameTexts(ManhuntMessages manhunt, GameMessages game,
            CommandMessages command, CommandSupport support) {
    }

    private final GameManager game;
    private final PlayerStateStore playerStates;
    private final PendingConfirmations confirms;
    private final ManhuntMessages texts;
    private final GameMessages gameTexts;
    private final CommandMessages commandTexts;
    private final CommandSupport support;

    public GameUnit(GameDeps deps, GameTexts texts) {
        this.game = deps.game();
        this.playerStates = deps.playerStates();
        this.confirms = deps.confirms();
        this.texts = texts.manhunt();
        this.gameTexts = texts.game();
        this.commandTexts = texts.command();
        this.support = texts.support();
    }

    /**
     * Validated verb plus the raw line: join and leave keep their own
     * shape checks because their arities diverge.
     */
    public record GameArgs(String action, String[] args) {
    }

    public static GameArgs parse(String[] args, ManhuntMessages texts)
            throws UnitSyntaxException {
        if (args.length < 2 || args[1].isBlank()) {
            throw new UnitSyntaxException(texts.getGameUsage(), Map.of());
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("join") || action.equals("leave")) {
            return new GameArgs(action, args);
        }
        throw new UnitSyntaxException(texts.getGameUsage(), Map.of());
    }

    @Override public String primaryName() {
        return "game";
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

    public boolean executeParsed(CommandSender sender, GameArgs args) {
        if (args.action().equals("join")) {
            return gameJoin(sender, args.args());
        }
        return gameLeave(sender, args.args());
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("game")) {
            return CommandSupport.partial(args[1], List.of("join", "leave"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("game")
                && (args[1].equalsIgnoreCase("join") || args[1].equalsIgnoreCase("leave"))) {
            return CommandSupport.partial(args[2], CommandSupport.instanceIdOptions(game));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("join")) {
            return CommandSupport.partial(args[3], List.of("hunter", "speedrunner", "spectator", "none"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("leave")) {
            return CommandSupport.partial(args[3], CommandSupport.selectorOptions());
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("game") && args[1].equalsIgnoreCase("join")) {
            return CommandSupport.partial(args[4], CommandSupport.selectorOptions());
        }
        return null;
    }

    /** Adds players to a running match: game join <id> [role] [selector]. */
    private boolean gameJoin(CommandSender sender, String[] args) {
        if (args.length < 3 || args.length > 5) {
            return support.message(sender, texts.getGameJoinUsage());
        }
        Optional<GameInstance> instance = game.resolveInstance(args[2]);
        if (instance.isEmpty() || instance.get().ending()) {
            return support.message(sender, texts.getInvalidInstanceId());
        }
        Role role = Role.SPECTATOR;
        if (args.length >= 4) {
            Optional<Role> parsed = Role.parse(args[3]);
            if (parsed.isEmpty() || parsed.get() == Role.AFK) {
                return support.message(sender, gameTexts.getJoinInvalidRole());
            }
            role = parsed.get();
        }
        List<Player> targets = CommandSupport.selectPlayers(sender, args.length == 5 ? args[4] : "@s");
        if (targets == null) {
            return support.message(sender, texts.getGameJoinUsage());
        }
        if (targets.isEmpty()) {
            return support.message(sender, commandTexts.getNoTargets());
        }
        Map<UUID, Role> before = new HashMap<>();
        for (Player target : targets) {
            before.put(target.getUniqueId(), playerStates.role(target));
        }
        int added = game.joinPlayers(instance.get(), targets, role);
        if (added == 0) {
            return support.message(sender, gameTexts.getJoinNoChange());
        }
        for (Player target : targets) {
            Role from = before.get(target.getUniqueId());
            Role after = playerStates.role(target);
            if (after != from) {
                game.messaging().announceRoleChange(target, from, after);
            }
        }
        support.message(sender, gameTexts.getJoinSuccess(), Map.of("count", String.valueOf(added),
                "id", String.valueOf(instance.get().matchId()), "role", support.roleName(role)));
        support.neutralSound(sender);
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
            return support.message(sender, texts.getGameLeaveUsage());
        }
        List<Player> targets = CommandSupport.selectPlayers(sender, args.length == 4 ? args[3] : "@s");
        if (targets == null) {
            return support.message(sender, texts.getGameLeaveUsage());
        }
        if (targets.isEmpty()) {
            return support.message(sender, commandTexts.getNoTargets());
        }
        Map<GameInstance, List<Player>> byMatch = groupLeaversByMatch(sender, targets, args);
        if (byMatch == null) {
            return true;
        }
        if (byMatch.isEmpty()) {
            return support.message(sender, gameTexts.getLeaveNotInMatch());
        }
        boolean needsConfirm = byMatch.values().stream().flatMap(List::stream)
                .anyMatch(player -> playerStates.role(player).isParticipant());
        if (needsConfirm && !confirms.confirm("gameleave:" + CommandSupport.senderKey(sender))) {
            return support.message(sender, gameTexts.getLeaveConfirm());
        }
        int removed = removeLeavers(byMatch);
        Set<java.util.UUID> leaverIds = byMatch.values().stream().flatMap(List::stream)
                .map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
        if (!(sender instanceof Player self) || !leaverIds.contains(self.getUniqueId())) {
            support.message(sender, gameTexts.getLeaveRemoved(), Map.of("count", String.valueOf(removed)));
        }
        support.neutralSound(sender);
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
                support.message(sender, texts.getInvalidInstanceId());
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
                    game.messaging().announceRoleChange(leaver, from, after);
                }
            }
        }
        return removed;
    }
}
