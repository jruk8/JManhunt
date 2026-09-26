package com.jruk8.jmanhunt.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Fake spectator mode: adventure plus flight plus no collision plus
 * hidden from other players. This keeps terrain collision (noclip is a
 * vanilla spectator property, not a flight property) while hiding held
 * items and armor client-side. Role-spectator and fake-spectator-mode
 * are not the same thing: entering the SPECTATOR role enables this
 * mode, but death waits, headstart holds, and NONE watchers also use
 * it with other roles.
 *
 * <p>State is memory only: a crash or restart always starts clean, and
 * quit plus join handlers reset any dangling flight.
 */
public final class FakeSpectatorService {
    /** Notified after fake spectator mode turns on or off for a player. */
    public interface ModeListener {
        void onModeChange(Player player, boolean enabled);
    }

    private final Plugin plugin;
    private final Supplier<Collection<? extends Player>> onlinePlayers;
    private final Set<UUID> actives = new HashSet<>();
    private final List<ModeListener> modeListeners = new ArrayList<>();

    public FakeSpectatorService(Plugin plugin, PlayerStateStore playerStates) {
        this(plugin, playerStates, Bukkit::getOnlinePlayers);
    }

    FakeSpectatorService(Plugin plugin, PlayerStateStore playerStates,
            Supplier<Collection<? extends Player>> onlinePlayers) {
        this.plugin = plugin;
        this.onlinePlayers = onlinePlayers;
        playerStates.addRoleListener(this::onRoleChange);
    }

    /** Subscribes to fake spectator mode changes. */
    public void addModeListener(ModeListener listener) {
        modeListeners.add(listener);
    }

    /** Enables fake spectator mode. Idempotent. */
    public void enable(Player player) {
        UUID id = player.getUniqueId();
        actives.add(id);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        for (Player viewer : onlinePlayers.get()) {
            if (!viewer.getUniqueId().equals(id)) {
                viewer.hidePlayer(plugin, player);
            }
        }
        notifyMode(player, true);
    }

    /**
     * Disables fake spectator mode and restores normal state. Always
     * restores (survival, grounded, collidable, visible) so call sites
     * can swap their survival restores for this unconditionally.
     */
    public void disable(Player player) {
        UUID id = player.getUniqueId();
        actives.remove(id);
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setCollidable(true);
        player.setGameMode(GameMode.SURVIVAL);
        for (Player viewer : onlinePlayers.get()) {
            if (!viewer.getUniqueId().equals(id)) {
                viewer.showPlayer(plugin, player);
            }
        }
        notifyMode(player, false);
    }

    private void notifyMode(Player player, boolean enabled) {
        for (ModeListener listener : List.copyOf(modeListeners)) {
            listener.onModeChange(player, enabled);
        }
    }

    /**
     * Quit entry: flight and visibility must never survive a disconnect.
     * Non-fakes are untouched (a creative admin keeps their gamemode).
     */
    public void handleQuit(Player player) {
        if (isFakeSpectator(player)) {
            disable(player);
        }
    }

    /** Hides every active fake spectator from a late joiner. */
    public void hideFrom(Player joiner) {
        UUID joinerId = joiner.getUniqueId();
        for (Player online : onlinePlayers.get()) {
            if (actives.contains(online.getUniqueId())
                    && !online.getUniqueId().equals(joinerId)) {
                joiner.hidePlayer(plugin, online);
            }
        }
    }

    /**
     * Clears joiner-side dangling state after a disconnect or crash:
     * persisted flight flags plus visibility to everyone online.
     */
    public void clearDanglingState(Player joiner) {
        joiner.setFlying(false);
        joiner.setAllowFlight(false);
        for (Player viewer : onlinePlayers.get()) {
            if (!viewer.getUniqueId().equals(joiner.getUniqueId())) {
                viewer.showPlayer(plugin, joiner);
            }
        }
    }

    /** True when the player is in fake spectator mode. Null-safe. */
    public boolean isFakeSpectator(Player player) {
        return player != null && actives.contains(player.getUniqueId());
    }

    /** True when the id is in fake spectator mode. Null-safe. */
    public boolean isFakeSpectator(UUID playerId) {
        return playerId != null && actives.contains(playerId);
    }

    private void onRoleChange(UUID playerId, Role from, Role to) {
        if (to != Role.SPECTATOR && from != Role.SPECTATOR) {
            return;
        }
        Player player = null;
        for (Player online : onlinePlayers.get()) {
            if (online.getUniqueId().equals(playerId)) {
                player = online;
                break;
            }
        }
        if (player == null) {
            if (from == Role.SPECTATOR) {
                actives.remove(playerId);
            }
            return;
        }
        if (to == Role.SPECTATOR) {
            enable(player);
        } else {
            disable(player);
        }
    }
}
