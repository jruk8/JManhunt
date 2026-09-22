package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CompassManager {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final PlayerStateStore playerStates;
    private final CompassTargetService targets;
    private final CompassSignalService signal;
    private final CompassLockService locks;
    private final CompassItemService items;
    /** Last automatic refresh per holder; right-clicks also stamp this. */
    private final Map<UUID, Long> lastAutoRefresh = new HashMap<>();
    /** Last right-click refresh per holder; the click's own cooldown. */
    private final Map<UUID, Long> lastClickRefresh = new HashMap<>();
    private final Map<UUID, Component> compassActionbars = new HashMap<>();
    private GameManager game;

    public CompassManager(JManhuntPlugin plugin, MessageService messages, SoundService sounds,
                          PlayerStateStore playerStates, NamespacedKey compassKey) {
        this.plugin = plugin;
        this.messages = messages;
        this.playerStates = playerStates;
        this.targets = new CompassTargetService(playerStates);
        this.signal = new CompassSignalService(plugin, playerStates);
        this.locks = new CompassLockService(plugin, playerStates, sounds, messages, targets, signal,
                compassActionbars, this::refreshCompass);
        this.items = new CompassItemService(plugin, messages, playerStates, compassKey);
    }

    /** Wires the game after construction so targets resolve within one match. */
    public void setGameManager(GameManager game) {
        this.game = game;
        locks.setGameManager(game);
        items.setGameManager(game);
    }

    public void refreshAllCompasses(boolean active) {
        if (active) {
            // The automatic clock only: holders refreshed less than an
            // interval ago keep their fresh target. Right-clicks stamp
            // this clock too, so each click restarts the interval.
            long intervalMs = (long) (plugin.getConfig()
                    .getDouble("settings.compass.refresh-interval", 10.0) * 1000);
            long now = System.currentTimeMillis();
            boolean analyze = locks.analyzeEnabled(true);
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> role(p).isParticipant())
                    .filter(p -> inLiveInstance(p))
                    .filter(items::hasCompass)
                    .forEach(holder -> {
                        UUID id = holder.getUniqueId();
                        if (!shouldRefresh(now, lastAutoRefresh.getOrDefault(id, 0L), intervalMs)) {
                            return;
                        }
                        lastAutoRefresh.put(id, now);
                        if (analyze) {
                            locks.startAnalysis(holder);
                        } else {
                            refreshCompass(holder);
                        }
                    });
        }
    }

    /** True when the cooldown has elapsed since the last refresh. Pure for tests. */
    static boolean shouldRefresh(long nowMillis, long lastMillis, long cooldownMs) {
        return nowMillis - lastMillis >= cooldownMs;
    }

    public void showHeldActionbars(boolean active) {
        if (!active) {
            return;
        }
        Bukkit.getOnlinePlayers().stream().filter(p -> role(p).isParticipant())
                .filter(p -> inLiveInstance(p))
                .filter(p -> items.isCompass(p.getInventory().getItemInMainHand())
                        || items.isCompass(p.getInventory().getItemInOffHand()))
                .forEach(p -> p.sendActionBar(compassActionbars.getOrDefault(p.getUniqueId(),
                        component("compass.no-target-actionbar",
                                Map.of("role", messages.roleName(role(p) == Role.HUNTER
                                        ? Role.SPEEDRUNNER : Role.HUNTER))))));
    }

    /** True when the holder actively participates in a live match. */
    private boolean inLiveInstance(Player player) {
        return game != null && game.instanceOf(player.getUniqueId()).isPresent();
    }

    public void refreshCompass(Player holder) {
        Optional<RefreshSlot> slot = refreshSlot(holder);
        if (slot.isEmpty()) {
            return;
        }
        Optional<RefreshMatch> match = refreshMatch(holder, slot.get());
        if (match.isEmpty()) {
            return;
        }
        RefreshMatch target = match.get();
        List<CompassCandidate> opponents = targets.collectOpponents(holder, target.targetRole(),
                target.instance());
        List<CompassSighting> sightings = targets.collectSightings(holder, target.targetRole(),
                target.instance());
        CompassLockService.LockedTargets narrowed = locks.narrowToLock(holder.getUniqueId(),
                opponents, sightings);
        CompassPick pick = resolveCompassPick(target.holderRole(), narrowed.opponents(),
                narrowed.sightings());
        renderCompassPick(holder, slot.get().item(), slot.get().slot(), pick,
                target.targetRoleString(), narrowed.locked());
    }

    /** Compass slot and item that are eligible for a refresh. */
    private record RefreshSlot(int slot, ItemStack item) {
    }

    /** Match context for a compass refresh. */
    private record RefreshMatch(GameInstance instance, Role holderRole, Role targetRole,
            String targetRoleString) {
    }

    /** Finds the compass slot and item to refresh, if any. */
    private Optional<RefreshSlot> refreshSlot(Player holder) {
        items.deduplicateCompasses(holder);
        int slot = items.findCompassSlot(holder);
        if (slot < 0) {
            return Optional.empty();
        }
        ItemStack item = holder.getInventory().getItem(slot);
        if (!items.isCompass(item)) {
            return Optional.empty();
        }
        return Optional.of(new RefreshSlot(slot, item));
    }

    /** Resolves the match and roles for a refresh, rendering the spectator fallback. */
    private Optional<RefreshMatch> refreshMatch(Player holder, RefreshSlot slot) {
        Role holderRole = role(holder);
        if (!holderRole.isParticipant()) {
            locks.clearLock(holder.getUniqueId());
            return Optional.empty();
        }
        Role targetRole = holderRole == Role.HUNTER ? Role.SPEEDRUNNER : Role.HUNTER;
        String targetRoleString = messages.roleName(targetRole);
        if (holder.getGameMode() == GameMode.SPECTATOR) {
            showNoTarget(holder, slot.item(), slot.slot(), targetRoleString);
            return Optional.empty();
        }
        if (game == null) {
            return Optional.empty();
        }
        Optional<GameInstance> match = game.instanceOf(holder.getUniqueId());
        if (match.isEmpty()) {
            locks.clearLock(holder.getUniqueId());
            return Optional.empty();
        }
        return Optional.of(new RefreshMatch(match.get(), holderRole, targetRole, targetRoleString));
    }

    /** Resolves the compass pick for the narrowed targets. */
    private CompassPick resolveCompassPick(Role holderRole, List<CompassCandidate> opponents,
            List<CompassSighting> sightings) {
        String roleBase = "settings.compass." + holderRole.name().toLowerCase(Locale.ROOT) + ".";
        boolean nearbyEnabled = plugin.getConfig()
                .getBoolean(roleBase + "min-distance.enabled", true);
        double nearbyThreshold = plugin.getConfig()
                .getDouble(roleBase + "min-distance.distance", 25.0);
        double trackingDistance = plugin.getConfig()
                        .getBoolean(roleBase + "max-distance.enabled", true)
                ? plugin.getConfig().getDouble(roleBase + "max-distance.distance", -1.0)
                : -1.0;
        return CompassPick.resolve(opponents, sightings, nearbyEnabled, nearbyThreshold,
                trackingDistance);
    }

    /** Renders a resolved pick onto the compass item and actionbar. */
    private void renderCompassPick(Player holder, ItemStack item, int slot, CompassPick pick,
            String targetRoleString, boolean locked) {
        if (pick.kind() != CompassPick.Kind.NONE && signal.badSignalForPick(holder, pick)) {
            showBadSignal(holder, item, slot);
            return;
        }
        switch (pick.kind()) {
            case TRACK_PLAYER -> trackPlayer(holder, item, slot, pick, targetRoleString, locked);
            case NEARBY -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                compassActionbars.put(holder.getUniqueId(), component("compass.nearby-actionbar",
                        Map.of("player", pick.name())));
            }
            case TRACK_SIGHTING -> trackSighting(holder, item, slot, pick, targetRoleString, locked);
            case TOO_FAR -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                compassActionbars.put(holder.getUniqueId(), component("compass.too-far-actionbar",
                        Map.of("player", pick.name())));
            }
            case NONE -> showNoTarget(holder, item, slot, targetRoleString);
        }
    }

    private void trackPlayer(Player holder, ItemStack item, int slot, CompassPick pick, String targetRoleString,
            boolean locked) {
        Player target = Bukkit.getPlayer(pick.id());
        if (target == null) {
            showNoTarget(holder, item, slot, targetRoleString);
            return;
        }
        setLodestone(item, target.getLocation());
        holder.getInventory().setItem(slot, item);
        compassActionbars.put(holder.getUniqueId(), component(
                locked ? "compass.compass-locked-actionbar" : "compass.compass-actionbar",
                Map.of("player", target.getName(),
                        "distance",
                        String.valueOf(Math.round(holder.getLocation().distance(target.getLocation()))))));
    }

    private void trackSighting(Player holder, ItemStack item, int slot, CompassPick pick, String targetRoleString,
            boolean locked) {
        Location location = playerStates.sightings().getOrDefault(pick.id(), Map.of())
                .get(holder.getWorld().getUID());
        Player seen = Bukkit.getPlayer(pick.id());
        if (location == null || location.getWorld() == null
                || skipLastSeen(seen != null, seen == null ? null : seen.getGameMode())) {
            showNoTarget(holder, item, slot, targetRoleString);
            return;
        }
        setLodestone(item, location);
        holder.getInventory().setItem(slot, item);
        String reason = seen != null ? "Another Dimension" : "Log-Out";
        compassActionbars.put(holder.getUniqueId(), component(
                locked ? "compass.compass-last-seen-locked-actionbar" : "compass.compass-last-seen-actionbar",
                Map.of("player", pick.name(),
                        "distance",
                        String.valueOf(Math.round(holder.getLocation().distance(location))),
                        "reason", reason)));
    }

    private void showNoTarget(Player holder, ItemStack item, int slot, String targetRoleString) {
        spinNeedle(item, holder);
        holder.getInventory().setItem(slot, item);
        compassActionbars.put(holder.getUniqueId(), component("compass.no-target-actionbar",
                Map.of("role", targetRoleString)));
    }

    private void showBadSignal(Player holder, ItemStack item, int slot) {
        spinNeedle(item, holder);
        holder.getInventory().setItem(slot, item);
        compassActionbars.put(holder.getUniqueId(), component("compass.bad-signal-actionbar"));
    }

    /**
     * Spins the needle by pointing at spawn of a dimension the holder is
     * not in, which the client cannot resolve to a direction. With no
     * other dimension loaded, the lodestone is cleared instead, so the
     * compass falls back to vanilla behavior.
     */
    private void spinNeedle(ItemStack item, Player holder) {
        World.Environment here = holder.getWorld().getEnvironment();
        World other = Bukkit.getWorlds().stream()
                .filter(world -> world.getEnvironment() != here)
                .findFirst()
                .orElse(null);
        if (other == null) {
            clearLodestone(item);
            return;
        }
        setLodestone(item, new Location(other, 0.0, 64.0, 0.0));
    }

    private void setLodestone(ItemStack item, Location location) {
        if (item != null && item.getItemMeta() instanceof CompassMeta meta) {
            meta.setLodestone(location);
            meta.setLodestoneTracked(false);
            item.setItemMeta(meta);
        }
    }

    private void clearLodestone(ItemStack item) {
        if (item != null && item.getItemMeta() instanceof CompassMeta meta) {
            meta.clearLodestone();
            item.setItemMeta(meta);
        }
    }

    /**
     * Online spectators are mid-respawn (or otherwise out of play): never a
     * last-seen target. Offline players still report their log-out spot.
     * Pure for tests.
     */
    static boolean skipLastSeen(boolean online, GameMode mode) {
        return online && mode == GameMode.SPECTATOR;
    }

    public boolean shouldReceiveCompass(Role role) {
        return items.shouldReceiveCompass(role);
    }

    public void giveCompass(Player player) {
        items.giveCompass(player);
    }

    public void deduplicateCompasses(Player player) {
        items.deduplicateCompasses(player);
    }

    public void removeCompasses(Player player) {
        items.removeCompasses(player);
    }

    public boolean isCompass(ItemStack item) {
        return items.isCompass(item);
    }

    public boolean mayHoldCompass(Player player) {
        return items.mayHoldCompass(player);
    }

    public void refreshCompassIdentity(Player player) {
        items.refreshCompassIdentity(player);
    }

    public boolean mustBeInventory() {
        return items.mustBeInventory();
    }

    public void handleRightClick(Player player) {
        if (!plugin.getConfig()
                .getBoolean("settings.compass.right-click.refresh-on-right-click", false)) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (plugin.getConfig()
                .getDouble("settings.compass.right-click.right-click-cooldown", 3.0) * 1000);
        if (!shouldRefresh(now, lastClickRefresh.getOrDefault(player.getUniqueId(), 0L), cooldownMs)) {
            return;
        }
        // Clicks run on their own cooldown, so a fresh automatic refresh
        // never blocks them; each click also stamps the automatic clock
        // at initiation, restarting the interval from here.
        lastClickRefresh.put(player.getUniqueId(), now);
        lastAutoRefresh.put(player.getUniqueId(), now);
        if (locks.analyzeEnabled(false)) {
            locks.startAnalysis(player);
            return;
        }
        refreshCompass(player);
    }

    /**
     * Cycles the holder's manual target lock one step: automatic locks
     * the nearest candidate, further clicks advance through the rest,
     * and cycling past the last candidate returns to automatic. No-op
     * unless left-click cycling is enabled and the holder participates
     * in a live match. Clicks inside the scroll cooldown are ignored,
     * which also stops held clicks from scrolling; scrolling is refused
     * with one or fewer candidates, during bad signal, and during
     * analysis. Locks survive automatic refreshes; the refresh after
     * each click applies the new lock immediately.
     */
    public void handleLeftClick(Player player) {
        locks.handleLeftClick(player);
    }

    private Role role(Player player) {
        return playerStates.role(player);
    }

    private Component component(String key) {
        return messages.component(key);
    }

    private Component component(String key, Map<String, String> values) {
        return messages.component(key, values);
    }
}
