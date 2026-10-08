package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ContextCalculator;
import net.luckperms.api.context.ContextConsumer;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.UUID;

/**
 * LuckPerms jmh-role context backed by live manhunt roles. Values are
 * none, afk, spectator, speedrunner, and hunter; unassigned subjects
 * resolve to none through the store default. There is deliberately no
 * null value: LuckPerms contexts have no null mechanism, so emitting a
 * literal "null" string would create a real matchable value.
 */
public final class RoleContexts implements ContextCalculator<Player> {
    public static final String KEY = "jmh-role";

    private final PlayerStateStore playerStates;
    private LuckPerms api;
    private boolean active;

    public RoleContexts(PlayerStateStore playerStates) {
        this.playerStates = playerStates;
    }

    /** Context slug for a role, null-safe. Pure for tests. */
    public static String valueFor(Role role) {
        if (role == null) {
            return "none";
        }
        return switch (role) {
            case HUNTER -> "hunter";
            case SPEEDRUNNER -> "speedrunner";
            case SPECTATOR -> "spectator";
            case AFK -> "afk";
            case NONE -> "none";
        };
    }

    @Override
    public void calculate(Player target, ContextConsumer consumer) {
        consumer.accept(KEY, valueFor(playerStates.role(target.getUniqueId())));
    }

    /** Registers the calculator and signals LuckPerms on every role change. */
    public void start() {
        api = LuckPermsProvider.get();
        active = true;
        api.getContextManager().registerCalculator(this);
        playerStates.addRoleListener(this::onRoleChange);
    }

    /** Stops signaling and unregisters; the store listener stays but no-ops. */
    public void stop() {
        active = false;
        if (api != null) {
            api.getContextManager().unregisterCalculator(this);
            api = null;
        }
    }

    private void onRoleChange(UUID playerId, Role from, Role to) {
        if (!active || api == null) {
            return;
        }
        Object subject = Bukkit.getPlayer(playerId);
        if (subject == null) {
            User user = api.getUserManager().getUser(playerId);
            if (user == null) {
                return;
            }
            subject = user;
        }
        try {
            api.getContextManager().signalContextUpdate(subject);
        } catch (IllegalStateException gone) {
            active = false;
        }
    }
}
