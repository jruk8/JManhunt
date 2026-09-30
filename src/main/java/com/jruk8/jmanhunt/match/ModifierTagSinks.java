package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.command.CommandSyntax;
import com.jruk8.jmanhunt.command.EngineEscapes;
import com.jruk8.jmanhunt.command.ModifierTagScope;
import com.jruk8.jmanhunt.command.QuietConsoleDispatch;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.message.MessageService;
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
    /** Config path of the modifier command blacklist. */
    private static final String BLACKLISTED_COMMANDS_PATH =
            "advanced.misc.interop.blacklisted-modifier-commands";

    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final SoundService sounds;
    private final GameManager game;
    private final PlayerStateStore playerStates;
    private final ConfigService configService;

    public ModifierTagSinks(JManhuntPlugin plugin, MessageService messages, SoundService sounds,
            GameManager game, PlayerStateStore playerStates, ConfigService configService) {
        this.plugin = plugin;
        this.messages = messages;
        this.sounds = sounds;
        this.game = game;
        this.playerStates = playerStates;
        this.configService = configService;
    }

    String formatEngineMessage(String text) {
        return NamedPlayerSinks.formatEngineMessage(messages, text);
    }

    /**
     * {@code <run>} sink: dispatches one evaluated line from the
     * console with the blacklist enforced, like a command list
     * entry. A hit only skips that line, never the outer list.
     */
    void runTagCommand(String line, String provenance) {
        String restored = EngineEscapes.restore(line);
        Collection<String> blocked = configService.getStringList(BLACKLISTED_COMMANDS_PATH);
        if (CommandSyntax.isBlockedCommand(restored, blocked)) {
            plugin.logger().severe("Blocked blacklisted modifier command '"
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
                messages.sendText(member, formatted);
            }
        });
    }

    /**
     * {@code <rsound>} sink: plays for every online assigned player
     * of the named role. Unknown ids skip like engine sounds.
     */
    void playRoleSound(String name, long matchId, ModifierTagScope scope,
            String role, String soundId, float pitch, float volume) {
        Optional<List<Player>> members = roleMembers("rsound", name, matchId, scope, role);
        if (members.isEmpty()) {
            return;
        }
        if (!sounds.isValidSound(soundId)) {
            plugin.logger().warning("modifier \"" + name
                    + "\" tried playing invalid sound \"" + soundId + "\"");
            return;
        }
        for (Player member : members.get()) {
            sounds.playCustomSound(member, soundId, pitch, volume);
        }
    }

    /**
     * Online assigned players of the named role behind role tags,
     * else empty with the reason warned. The tag layer validates the
     * role, like {@code <win>}.
     */
    private Optional<List<Player>> roleMembers(String tag, String name, long matchId,
            ModifierTagScope scope, String role) {
        Role target = Role.valueOf(role);
        Optional<GameInstance> instance = game.instance(matchId);
        if (instance.isEmpty()) {
            scope.warn("Tag <" + tag + "> needs a live match: skipped in '" + name + "'.");
            return Optional.empty();
        }
        List<Player> members = new ArrayList<>();
        for (Player member : game.onlineAssignedPlayers(instance.get())) {
            if (playerStates.role(member) == target) {
                members.add(member);
            }
        }
        return Optional.of(members);
    }

    /**
     * Loop-limit sink: a {@code <while>} or {@code <for>} passed 1000
     * steps. Logs the source line, tells the match to contact an
     * administrator, and cancels the match.
     */
    void loopLimitExceeded(String detail, long matchId) {
        plugin.logger().severe("JMHScript loop exceeded 1000 steps at " + detail);
        Optional<GameInstance> instance = game.instance(matchId);
        if (instance.isEmpty()) {
            return;
        }
        String text = messages.string("modifiers.loop-limit",
                "{prefix}<red>A modifier loop exceeded its step limit and the match was cancelled. "
                        + "Please tell an administrator.");
        for (Player player : game.onlineParticipants(matchId)) {
            messages.sendText(player, text);
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
        if (!sounds.isValidSound(soundId)) {
            plugin.logger().warning("modifier \"" + containerId
                    + "\" tried playing invalid sound \"" + soundId + "\"");
            return;
        }
        if (target != null) {
            sounds.playCustomSound(target, soundId, pitch, volume);
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            sounds.playCustomSound(online, soundId, pitch, volume);
        }
    }
}
