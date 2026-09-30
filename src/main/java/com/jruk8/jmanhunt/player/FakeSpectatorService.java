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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Fake spectator mode: adventure plus flight plus no collision plus
 * infinite invisibility, hidden from alive players but visible to
 * other fake spectators. This keeps terrain collision (noclip is a
 * vanilla spectator property, not a flight property) while hiding held
 * items and armor client-side. Role-spectator and fake-spectator-mode
 * are not the same thing: queuing as a spectator never enters this
 * mode by itself. Only explicit join and watch paths enable it;
 * death waits, headstart holds, and NONE watchers also use it with
 * other roles.
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
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION, 0, false, false, false));
        for (Player viewer : onlinePlayers.get()) {
            if (viewer.getUniqueId().equals(id)) {
                continue;
            }
            // The new fake sees everyone; only fakes see them back.
            player.showPlayer(plugin, viewer);
            if (seesPlayer(isFakeSpectator(viewer), true)) {
                viewer.showPlayer(plugin, player);
            } else {
                viewer.hidePlayer(plugin, player);
            }
        }
        notifyMode(player, true);
    }

    /**
     * Disables fake spectator mode and restores normal state. Always
     * restores (survival, collidable, visible) so call sites can swap
     * their survival restores for this unconditionally; flight is
     * grounded unless the player is in creative mode.
     */
    public void disable(Player player) {
        UUID id = player.getUniqueId();
        actives.remove(id);
        stopFlightUnlessCreative(player);
        player.setCollidable(true);
        // Falling distance gathered while flying must not survive the
        // exit: it would land as fall damage on the next touchdown.
        player.setFallDistance(0F);
        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        for (Player viewer : onlinePlayers.get()) {
            if (viewer.getUniqueId().equals(id)) {
                continue;
            }
            viewer.showPlayer(plugin, player);
            if (seesPlayer(false, isFakeSpectator(viewer))) {
                player.showPlayer(plugin, viewer);
            } else {
                player.hidePlayer(plugin, viewer);
            }
        }
        notifyMode(player, false);
    }

    /**
     * True when a viewer sees a target player: anyone sees the alive,
     * but only fake spectators see fakes. Pure for tests.
     */
    static boolean seesPlayer(boolean viewerFake, boolean targetFake) {
        return !targetFake || viewerFake;
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
     * Creative players keep their flight.
     */
    public void clearDanglingState(Player joiner) {
        stopFlightUnlessCreative(joiner);
        for (Player viewer : onlinePlayers.get()) {
            if (!viewer.getUniqueId().equals(joiner.getUniqueId())) {
                viewer.showPlayer(plugin, joiner);
            }
        }
    }

    /**
     * Grounds the player unless they are in creative mode, where
     * flight is inherent and must never be stripped by cleanup.
     */
    private static void stopFlightUnlessCreative(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
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
        // Queuing as a spectator never enters fake mode by itself: only
        // explicit join and watch paths enable it. Leaving the role
        // unwinds fake mode, but only for players who actually have it.
        if (to == Role.SPECTATOR || from != Role.SPECTATOR) {
            return;
        }
        if (!isFakeSpectator(playerId)) {
            actives.remove(playerId);
            return;
        }
        for (Player online : onlinePlayers.get()) {
            if (online.getUniqueId().equals(playerId)) {
                disable(online);
                return;
            }
        }
        actives.remove(playerId);
    }
}
