package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.CommandSyntax;
import com.jruk8.jmanhunt.command.EngineEscapes;
import com.jruk8.jmanhunt.command.ModifierTagScope;
import com.jruk8.jmanhunt.command.QuietConsoleDispatch;
import com.jruk8.jmanhunt.command.TagLocations;
import com.jruk8.jmanhunt.config.MiscConfig;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Modifier tag side effects: role messages and sounds, win and
 * elimination sinks, loop-limit handling, and the {@code <run>}
 * console dispatch with the command blacklist enforced.
 */
public final class ModifierTagSinks {
    /** Message bus, modifier texts, and sounds. */
    public record SinkBus(MessageService messages, ModifiersMessages modifiers,
            SoundService sounds) {
    }

    private final JManhuntLogger log;
    private final SinkBus bus;
    private final GameManager game;
    private final PlayerStateStore playerStates;
    private final MiscConfig.Interop interop;

    public ModifierTagSinks(JManhuntLogger log, SinkBus bus, GameManager game,
            PlayerStateStore playerStates, MiscConfig.Interop interop) {
        this.log = log;
        this.bus = bus;
        this.game = game;
        this.playerStates = playerStates;
        this.interop = interop;
    }

    String formatEngineMessage(String text) {
        return NamedPlayerSinks.formatEngineMessage(bus.modifiers(), bus.messages(), text);
    }

    /**
     * {@code <run>} sink: dispatches one evaluated line from the
     * console with the blacklist enforced, like a command list
     * entry. A hit only skips that line, never the outer list.
     */
    void runTagCommand(String line, String provenance) {
        String restored = EngineEscapes.restore(line);
        Collection<String> blocked = interop.getBlacklistedModifierCommands();
        if (CommandSyntax.isBlockedCommand(restored, blocked)) {
            log.severe("Blocked blacklisted modifier command '"
                    + restored + "' at " + provenance + ".");
            return;
        }
        QuietConsoleDispatch.dispatch(restored);
    }

    /**
     * {@code <rmessage>} sink: tells every online assigned player of
     * the named role, formatted like {@code <pmessage>}.
     */
    void sendRoleMessage(String name, long matchId, ModifierTagScope scope,
            String role, String text) {
        roleMembers("rmessage", name, matchId, scope, role).ifPresent(members -> {
            String formatted = formatEngineMessage(text);
            for (Player member : members) {
                bus.messages().sendText(member, formatted);
            }
        });
    }

    /**
     * {@code <rsound>} sink: plays for every online assigned player
     * of the named role. Unknown ids skip like engine bus.sounds().
     */
    void playRoleSound(String name, long matchId, ModifierTagScope scope,
            String role, String soundId, float pitch, float volume) {
        Optional<List<Player>> members = roleMembers("rsound", name, matchId, scope, role);
        if (members.isEmpty()) {
            return;
        }
        if (!bus.sounds().isValidSound(soundId)) {
            log.warning("modifier \"" + name
                    + "\" tried playing invalid sound \"" + soundId + "\"");
            return;
        }
        for (Player member : members.get()) {
            bus.sounds().playCustomSound(member, soundId, pitch, volume);
        }
    }

    /**
     * {@code <rteleport>} sink: teleports every online assigned
     * player of the named role. Unknown worlds warn once and move
     * nothing.
     */
    void teleportRole(String name, long matchId, ModifierTagScope scope,
            String role, TagLocations.TeleportRequest target) {
        roleMembers("rteleport", name, matchId, scope, role).ifPresent(members -> {
            if (!teleportWorldKnown(name, target)) {
                return;
            }
            for (Player member : members) {
                TeleportService.teleport(member, target, Bukkit.getWorlds(), detail -> { });
            }
        });
    }

    /**
     * {@code <gteleport>} sink: teleports every online hunter and
     * speedrunner of the match. Spectators stay where they are so
     * holds and watch spawns never break; {@code <pteleport>} names
     * them directly when wanted.
     */
    void teleportGlobal(String name, long matchId, ModifierTagScope scope,
            TagLocations.TeleportRequest target) {
        roleMembers("gteleport", name, matchId, scope, "ALL").ifPresent(members -> {
            if (!teleportWorldKnown(name, target)) {
                return;
            }
            for (Player member : members) {
                TeleportService.teleport(member, target, Bukkit.getWorlds(), detail -> { });
            }
        });
    }

    /**
     * True when the target world resolves, warning once naming the
     * modifier when it does not, so one bad world never spams per
     * member.
     */
    private boolean teleportWorldKnown(String name, TagLocations.TeleportRequest target) {
        if (TeleportService.resolveWorld(target.worldRef(), Bukkit.getWorlds()).isEmpty()) {
            log.warning("modifier \"" + name + "\" teleport: unknown world '"
                    + target.worldRef() + "'.");
            return false;
        }
        return true;
    }

    /**
     * Online assigned players of the named role behind role tags,
     * else empty with the reason warned. ALL covers both teams. The
     * tag layer validates the role, like {@code <win>}.
     */
    private Optional<List<Player>> roleMembers(String tag, String name, long matchId,
            ModifierTagScope scope, String role) {
        Optional<GameInstance> instance = game.instance(matchId);
        if (instance.isEmpty()) {
            scope.warn("Tag <" + tag + "> needs a live match: skipped in '" + name + "'.");
            return Optional.empty();
        }
        List<Player> members = new ArrayList<>();
        for (Player member : game.onlineAssignedPlayers(instance.get())) {
            if (roleMatches(role, playerStates.role(member))) {
                members.add(member);
            }
        }
        return Optional.of(members);
    }

    /**
     * True when a member role falls under a role tag filter: ALL
     * covers hunters and speedrunners, anything else matches one
     * side. Pure for tests.
     */
    static boolean roleMatches(String filter, Role member) {
        if ("ALL".equals(filter)) {
            return member == Role.HUNTER || member == Role.SPEEDRUNNER;
        }
        return member == Role.valueOf(filter);
    }

    /**
     * Loop-limit sink: a {@code <while>} or {@code <for>} passed 1000
     * steps. Logs the source line, tells the match to contact an
     * administrator, and cancels the match.
     */
    void loopLimitExceeded(String detail, long matchId) {
        log.severe("JMHScript loop exceeded 1000 steps at " + detail);
        Optional<GameInstance> instance = game.instance(matchId);
        if (instance.isEmpty()) {
            return;
        }
        String text = bus.modifiers().getLoopLimit();
        for (Player player : game.onlineParticipants(matchId)) {
            bus.messages().sendText(player, text);
        }
        game.cancel(instance.get());
    }

    /**
     * {@code <loseplayer>} sink: eliminates one player by name with
     * the tag reason; failures skip with a warning naming the tag.
     */
    void losePlayerByName(String name, String target, String reason,
            ModifierTagScope scope, long matchId) {
        if (!game.losePlayer(matchId, target, reason)) {
            scope.warn("Tag <loseplayer:" + target + "> skipped: '" + target
                    + "' is not an active runner or hunter in '" + name + "'.");
        }
    }

    /**
     * {@code <pswitch:player,ROLE>} sink: switches one match
     * assignee to the named role with lives refreshed.
     */
    void switchPlayerRoleByName(String name, String target, String role,
            ModifierTagScope scope, long matchId) {
        if (!game.switchPlayerRole(matchId, target, role)) {
            scope.warn("Tag <pswitch:" + target + "," + role + "> skipped: '" + target
                    + "' is not an online match assignee in '" + name + "'.");
        }
    }

    /**
     * {@code <win:ROLE>} sink: ends the match for one role next
     * tick, with the tag reason on the win screen.
     */
    void winForRole(String name, String role, String reason,
            ModifierTagScope scope, long matchId) {
        Optional<GameInstance> instance = game.instance(matchId);
        if (instance.isEmpty() || !instance.get().begun() || instance.get().ending()) {
            scope.warn("Tag <win> needs a live match: skipped in '" + name + "'.");
            return;
        }
        game.finishLater(instance.get(), Role.valueOf(role), reason);
    }

    /**
     * Plays one engine sound for every online player (global) or one
     * executor. Unknown ids skip with the modifier named in the log.
     */
    void playEngineSound(String containerId, Player target, String soundId,
            float pitch, float volume) {
        if (!bus.sounds().isValidSound(soundId)) {
            log.warning("modifier \"" + containerId
                    + "\" tried playing invalid sound \"" + soundId + "\"");
            return;
        }
        if (target != null) {
            bus.sounds().playCustomSound(target, soundId, pitch, volume);
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            bus.sounds().playCustomSound(online, soundId, pitch, volume);
        }
    }
}
