package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
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
import java.util.concurrent.ThreadLocalRandom;

public final class CompassManager {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final SoundService sounds;
    private final PlayerStateStore playerStates;
    private final CompassTargetService targets;
    private final CompassSignalService signal;
    private final HotspotService hotspots;
    private final CompassInaccuracyService inaccuracy;
    private final CompassLockService locks;
    private final CompassItemService items;
    private final CompassCache cache = new CompassCache();
    /** Last automatic refresh per holder; clicks also stamp this. */
    private final Map<UUID, Long> lastAutoRefresh = new HashMap<>();
    /** Last accepted refresh click per holder; right-clicks only. */
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, Component> compassActionbars = new HashMap<>();
    private final CompassDeltaRenderer deltas;
    private final CompassAnalysisSessions sessions;
    private GameManager game;

    public CompassManager(JManhuntPlugin plugin, MessageService messages, SoundService sounds,
                          PlayerStateStore playerStates, NamespacedKey compassKey) {
        this.plugin = plugin;
        this.messages = messages;
        this.sounds = sounds;
        this.playerStates = playerStates;
        this.targets = new CompassTargetService(playerStates, plugin.fakeSpectators());
        this.signal = new CompassSignalService(plugin, playerStates);
        this.hotspots = new HotspotService(plugin, playerStates);
        this.inaccuracy = new CompassInaccuracyService(plugin, hotspots);
        this.items = new CompassItemService(plugin, messages, playerStates, compassKey);
        this.sessions = new CompassAnalysisSessions(plugin, messages, playerStates, targets,
                signal, items, compassActionbars);
        this.locks = new CompassLockService(plugin, playerStates, sounds, messages, targets,
                compassActionbars, this::refreshCompass, this::resolveClickRefresh,
                this::renderFromCache, sessions::beginAnalysisSpot, cache, lastClick, sessions);
        sessions.setLockService(locks);
        this.deltas = new CompassDeltaRenderer(plugin, messages, compassActionbars);
    }

    /** Wires the game after construction so targets resolve within one match. */
    public void setGameManager(GameManager game) {
        this.game = game;
        locks.setGameManager(game);
        items.setGameManager(game);
        sessions.setGameManager(game);
        hotspots.setGameManager(game);
    }

    /** Origin lobby of the holder's match, or null outside matches. */
    private Integer lobbyOf(Player holder) {
        return game == null ? null : game.lobbyOfPlayer(holder.getUniqueId());
    }

    public void refreshAllCompasses(boolean active) {
        if (active) {
            // The automatic clock only: holders refreshed less than an
            // interval ago keep their fresh target. Right-clicks stamp
            // this clock too, so each click restarts the interval. The
            // caller ticks fast (every few ticks); per-holder gating
            // keeps each interval strict instead of quantizing everyone
            // to one shared beat.
            long now = System.currentTimeMillis();
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> role(p).isParticipant())
                    .filter(p -> !isVanillaSpectator(p))
                    .filter(p -> !p.isDead())
                    .filter(p -> inLiveInstance(p))
                    .filter(items::hasCompass)
                    .forEach(holder -> {
                        UUID id = holder.getUniqueId();
                        // A running analysis owns the needle: the auto clock
                        // restarts anyway so no refresh fires mid-analysis.
                        if (locks.isAnalyzing(id)) {
                            lastAutoRefresh.put(id, now);
                            return;
                        }
                        Integer lobby = lobbyOf(holder);
                        var overrides = plugin.overrides();
                        boolean autoEnabled = overrides.getBoolean(lobby,
                                "settings.compass.actions.auto.enabled", true);
                        double intervalSeconds = overrides.getDouble(lobby,
                                "settings.compass.actions.auto.interval", 35.0);
                        double deviationSeconds = overrides.getDouble(lobby,
                                "settings.compass.actions.auto.deviation", 0.0);
                        if (!autoRefreshDue(now, lastAutoRefresh.getOrDefault(id, 0L),
                                autoEnabled, intervalSeconds, deviationSeconds,
                                ThreadLocalRandom.current().nextDouble())) {
                            return;
                        }
                        lastAutoRefresh.put(id, now);
                        refreshCompass(holder);
                    });
        }
    }

    /** True when the cooldown has elapsed since the last refresh. Pure for tests. */
    static boolean shouldRefresh(long nowMillis, long lastMillis, long cooldownMs) {
        return nowMillis - lastMillis >= cooldownMs;
    }

    /**
     * True when a holder is due for an automatic refresh: a disabled
     * clock never fires, otherwise the jittered interval must have
     * elapsed since the last automatic or click refresh. The roll
     * stretches the interval by plus-or-minus the deviation, capped at
     * the interval itself. Pure for tests.
     */
    static boolean autoRefreshDue(long nowMillis, long lastMillis, boolean enabled,
            double intervalSeconds, double deviationSeconds, double roll) {
        if (!enabled) {
            return false;
        }
        double interval = Math.max(0.0, intervalSeconds);
        double jitter = Math.min(Math.max(0.0, deviationSeconds), interval);
        double effective = Math.max(0.0, interval + (roll * 2.0 - 1.0) * jitter);
        return shouldRefresh(nowMillis, lastMillis, (long) (effective * 1000));
    }

    public void showHeldActionbars(boolean active) {
        if (!active) {
            return;
        }
        Bukkit.getOnlinePlayers().stream().filter(p -> role(p).isParticipant())
                .filter(p -> inLiveInstance(p))
                .filter(p -> items.isCompass(p.getInventory().getItemInMainHand())
                        || items.isCompass(p.getInventory().getItemInOffHand()))
                .forEach(p -> {
                    if (isVanillaSpectator(p)) {
                        compassActionbars.remove(p.getUniqueId());
                        deltas.forget(p.getUniqueId());
                        return;
                    }
                    p.sendActionBar(compassActionbars.getOrDefault(p.getUniqueId(),
                            component("compass.no-target-actionbar",
                                    Map.of("role", messages.roleName(locks.targetRole(p))))));
                });
    }

    /** Vanilla spectators (for example admins) get no compass behavior at all. */
    static boolean isVanillaSpectator(Player player) {
        return player.getGameMode() == GameMode.SPECTATOR;
    }

    /** True when the holder actively participates in a live match. */
    private boolean inLiveInstance(Player player) {
        return game != null && game.instanceOf(player.getUniqueId()).isPresent();
    }

    public void refreshCompass(Player holder) {
        if (holder.isDead()) {
            return;
        }
        refreshCompassOutcome(holder);
    }

    /** Flips emptied teammate modes in one match back to opponents. */
    public void reconcileTeammateModes(GameInstance instance) {
        locks.reconcileTeammateModes(instance);
    }

    /** Clears every manual lock on a dead target, notifying holders when enabled. */
    public void clearLocksOnTargetDeath(UUID victimId) {
        locks.clearLocksOnTargetDeath(victimId, Bukkit::getPlayer);
    }

    /**
     * Immediate compass refresh for one match's holders, bypassing the
     * automatic interval: deaths must move needles at once, not on the
     * next tick. Skips analysis routing on purpose: this re-resolves
     * targets, it never starts a click tracking session.
     */
    public void refreshInstance(GameInstance instance) {
        if (game == null) {
            return;
        }
        long matchId = instance.matchId();
        Bukkit.getOnlinePlayers().stream()
                .filter(holder -> role(holder).isParticipant())
                .filter(holder -> game.instanceOf(holder.getUniqueId())
                        .map(match -> match.matchId() == matchId).orElse(false))
                .filter(items::hasCompass)
                .forEach(this::refreshCompass);
    }

    /**
     * Refreshes the compass, reporting whether it now tracks a target.
     * NEARBY, TOO_FAR, bad signal, and no-target outcomes all report
     * false so click callers can play the failure sound instead.
     */
    boolean refreshCompassOutcome(Player holder) {
        UUID id = holder.getUniqueId();
        // Analysis resolutions consume the press-time snapshots; every
        // other path renders from live positions.
        boolean analysis = sessions.hasAnalysisSnapshots(id);
        try {
            return refreshCompassResolved(holder);
        } finally {
            if (analysis) {
                sessions.cancelAnalysisSnapshots(id);
            }
        }
    }


    /**
     * Cached holder spot when one is pending for this resolution and
     * still in the holder's world, else the live location. A world
     * change mid-analysis voids the cache. Pure for tests.
     */
    static Location effectiveSpot(Location cached, Location live) {
        if (cached != null && cached.getWorld() != null && cached.getWorld().equals(live.getWorld())) {
            return cached;
        }
        return live;
    }


    private boolean refreshCompassResolved(Player holder) {
        Optional<RefreshSlot> slot = refreshSlot(holder);
        if (slot.isEmpty()) {
            return false;
        }
        Optional<RefreshMatch> match = refreshMatch(holder, slot.get());
        if (match.isEmpty()) {
            return false;
        }
        RefreshMatch target = match.get();
        writeCache(holder, target.instance());
        List<CompassCandidate> opponents = targets.collectOpponents(holder, target.targetRole(),
                target.instance());
        List<CompassSighting> sightings = targets.collectSightings(holder, target.targetRole(),
                target.instance(), holder.getLocation());
        CompassLockService.LockedTargets narrowed = locks.narrowToLock(holder.getUniqueId(),
                opponents, sightings);
        CompassPick pick = resolveCompassPick(plugin.overrides(), target.instance().originLobbyId(),
                target.holderRole(), narrowed.opponents(), narrowed.sightings());
        return renderCompassPick(holder, slot.get().item(), slot.get().slot(), pick,
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

    /**
     * Snapshots the closest hunters plus the closest speedrunners for one
     * holder, capped at the clamped max-targets each. Every refresh event
     * funnels through here, so clicks always read a fresh cache.
     */
    private void writeCache(Player holder, GameInstance instance) {
        int cap = CompassCache.clampMaxTargets(plugin.overrides()
                .getInt(lobbyOf(holder), "settings.compass.actions.target-cycling.max-targets", 5));
        cache.replace(holder.getUniqueId(), holder.getLocation().clone(),
                targets.collectSnapshots(holder, Role.HUNTER, instance, cap),
                targets.collectSnapshots(holder, Role.SPEEDRUNNER, instance, cap));
    }

    /** Resolves the match and roles for a refresh, rendering the spectator fallback. */
    private Optional<RefreshMatch> refreshMatch(Player holder, RefreshSlot slot) {
        if (isVanillaSpectator(holder)) {
            return Optional.empty();
        }
        Role holderRole = role(holder);
        if (!holderRole.isParticipant()) {
            locks.clearMatchState(holder.getUniqueId());
            return Optional.empty();
        }
        Role targetRole = locks.targetRole(holder);
        String targetRoleString = messages.roleName(targetRole);
        if (plugin.fakeSpectators().isFakeSpectator(holder)) {
            showNoTarget(holder, slot.item(), slot.slot(), targetRoleString);
            return Optional.empty();
        }
        if (game == null) {
            return Optional.empty();
        }
        Optional<GameInstance> match = game.instanceOf(holder.getUniqueId());
        if (match.isEmpty()) {
            locks.clearMatchState(holder.getUniqueId());
            return Optional.empty();
        }
        return Optional.of(new RefreshMatch(match.get(), holderRole, targetRole, targetRoleString));
    }

    /** Resolves the compass pick for the narrowed targets. */
    static CompassPick resolveCompassPick(OverrideService overrides, Integer lobby, Role holderRole,
            List<CompassCandidate> opponents, List<CompassSighting> sightings) {
        String roleBase = "settings.compass.distance-limits."
                + holderRole.name().toLowerCase(Locale.ROOT) + ".";
        boolean nearbyEnabled = overrides.getBoolean(lobby, roleBase + "min-distance.enabled", true);
        double nearbyThreshold = overrides.getDouble(lobby, roleBase + "min-distance.distance", 25.0);
        double trackingDistance = overrides.getBoolean(lobby, roleBase + "max-distance.enabled", true)
                ? overrides.getDouble(lobby, roleBase + "max-distance.distance", -1.0)
                : -1.0;
        return CompassPick.resolve(opponents, sightings, nearbyEnabled, nearbyThreshold,
                trackingDistance);
    }

    /**
     * Renders a resolved pick onto the compass item and actionbar. True
     * when the needle now tracks a live target or sighting.
     */
    private boolean renderCompassPick(Player holder, ItemStack item, int slot, CompassPick pick,
            String targetRoleString, boolean locked) {
        Location spot = sessions.resolutionSpot(holder);
        Location targetPress = sessions.targetPressSpot(holder.getUniqueId(), pick.id());
        double holderMoved = sessions.analysisMaxMoved(holder);
        Optional<SignalInterference.Reason> reason = pick.kind() == CompassPick.Kind.NONE
                ? Optional.empty()
                : signal.reasonForPick(holder, spot, targetPress, pick, holderMoved);
        if (reason.isPresent()) {
            showBadSignal(holder, item, slot, reason.get());
            return false;
        }
        return switch (pick.kind()) {
            case TRACK_PLAYER -> trackPlayer(holder, item, slot, pick, targetRoleString, locked);
            case TRACK_SIGHTING -> trackSighting(holder, item, slot, pick, targetRoleString, locked,
                    sessions.resolutionSpot(holder));
            case NEARBY -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                compassActionbars.put(holder.getUniqueId(), component("compass.nearby-actionbar",
                        Map.of("player", pick.name())));
                yield false;
            }
            case TOO_FAR -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                compassActionbars.put(holder.getUniqueId(), component("compass.too-far-actionbar",
                        Map.of("player", pick.name())));
                yield false;
            }
            case NONE -> {
                showNoTarget(holder, item, slot, targetRoleString);
                yield false;
            }
        };
    }

    /**
     * Renders the holder's current lock and mode from the snapshot cache
     * only. Accepted clicks call this instead of refreshing, so browsing
     * never fetches a live position and never touches the refresh
     * cooldown. Uncached targets show a reasonless Bad Signal.
     */
    void renderFromCache(Player holder) {
        Optional<RefreshSlot> slot = refreshSlot(holder);
        if (slot.isEmpty()) {
            return;
        }
        Optional<RefreshMatch> match = refreshMatch(holder, slot.get());
        if (match.isEmpty()) {
            return;
        }
        RefreshMatch target = match.get();
        int maxTargets = CompassCache.clampMaxTargets(plugin.overrides()
                .getInt(lobbyOf(holder), "settings.compass.actions.target-cycling.max-targets", 5));
        CompassLockService.CachedCycle cycle =
                locks.buildCycle(holder, target.instance(), target.targetRole(), maxTargets);
        CompassLockService.LockedTargets narrowed = locks.narrowToLockCached(holder.getUniqueId(),
                cycle.cached(), cycle.sightings(), cycle.trackableIds());
        CompassPick pick = resolveCompassPick(plugin.overrides(), target.instance().originLobbyId(),
                target.holderRole(), narrowed.opponents(), narrowed.sightings());
        renderCachedPick(holder, slot.get().item(), slot.get().slot(), pick,
                target.targetRoleString(), narrowed.locked());
    }

    /**
     * Renders a cache-resolved pick. Signal interference is deliberately
     * not consulted: evaluating it would read live positions, and the
     * switch must serve whatever is currently cached.
     */
    private void renderCachedPick(Player holder, ItemStack item, int slot, CompassPick pick,
            String targetRoleString, boolean locked) {
        switch (pick.kind()) {
            case TRACK_PLAYER -> trackCachedPlayer(holder, item, slot, pick, locked);
            case TRACK_SIGHTING -> trackSighting(holder, item, slot, pick, targetRoleString, locked,
                    effectiveSpot(cache.holderSpotFor(holder.getUniqueId()),
                            sessions.resolutionSpot(holder)));
            case NEARBY -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                compassActionbars.put(holder.getUniqueId(), component("compass.nearby-actionbar",
                        Map.of("player", pick.name())));
            }
            case TOO_FAR -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                compassActionbars.put(holder.getUniqueId(), component("compass.too-far-actionbar",
                        Map.of("player", pick.name())));
            }
            case NONE -> showCacheBadSignal(holder, item, slot);
        }
    }

    /** Points the needle at a snapshotted location, or Bad Signals when gone. */
    private void trackCachedPlayer(Player holder, ItemStack item, int slot, CompassPick pick,
            boolean locked) {
        Location spot = cache.spotsFor(holder.getUniqueId()).get(pick.id());
        if (spot == null || spot.getWorld() == null
                || !spot.getWorld().getUID().equals(holder.getWorld().getUID())) {
            showCacheBadSignal(holder, item, slot);
            return;
        }
        Location origin = effectiveSpot(cache.holderSpotFor(holder.getUniqueId()),
                sessions.resolutionSpot(holder));
        CompassInaccuracyService.Result drifted =
                resolveInaccuracy(holder, origin, spot, pick.id());
        setLodestone(item, drifted.needleSpot());
        holder.getInventory().setItem(slot, item);
        String key = trackingKey(holder, locked, false);
        deltas.putTrackingBar(holder, role(holder), lobbyOf(holder), key, pick.name(), pick.id(),
                drifted.feedbackDistance(), Map.of());
    }

    /**
     * Drifts one true spot through the inaccuracy service: the shared
     * helper behind live, sighting, and cache renders, so scrolled
     * targets drift exactly like refreshed ones. The target id feeds
     * the hotspot reduction (null for unknown owners).
     */
    private CompassInaccuracyService.Result resolveInaccuracy(Player holder, Location origin,
            Location truth, UUID targetId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return inaccuracy.resolve(lobbyOf(holder), origin, truth, targetId, random.nextDouble(),
                random.nextDouble());
    }

    /** One hotspot sampling tick, driven by the plugin scheduler. */
    public void sampleHotspots() {
        hotspots.tick(System.currentTimeMillis());
    }

    /** Reasonless Bad Signal for uncached switch targets, by spec. */
    private void showCacheBadSignal(Player holder, ItemStack item, int slot) {
        spinNeedle(item, holder);
        holder.getInventory().setItem(slot, item);
        compassActionbars.put(holder.getUniqueId(), component("compass.bad-signal-actionbar"));
    }

    /** Tracking actionbar key for the holder's mode, lock, and sighting state. */
    private String trackingKey(Player holder, boolean locked, boolean lastSeen) {
        boolean teammate = locks.teammateMode(holder.getUniqueId());
        if (lastSeen) {
            if (teammate) {
                return locked ? "compass.teammate-last-seen-locked-actionbar"
                        : "compass.teammate-last-seen-actionbar";
            }
            return locked ? "compass.compass-last-seen-locked-actionbar"
                    : "compass.compass-last-seen-actionbar";
        }
        if (teammate) {
            return locked ? "compass.teammate-locked-actionbar" : "compass.teammate-actionbar";
        }
        return locked ? "compass.compass-locked-actionbar" : "compass.compass-actionbar";
    }

    private boolean trackPlayer(Player holder, ItemStack item, int slot, CompassPick pick, String targetRoleString,
            boolean locked) {
        Player target = Bukkit.getPlayer(pick.id());
        if (target == null) {
            showNoTarget(holder, item, slot, targetRoleString);
            return false;
        }
        Location truth = target.getLocation();
        CompassInaccuracyService.Result drifted = resolveInaccuracy(holder,
                sessions.resolutionSpot(holder), truth, pick.id());
        setLodestone(item, drifted.needleSpot());
        holder.getInventory().setItem(slot, item);
        String key = trackingKey(holder, locked, false);
        deltas.putTrackingBar(holder, role(holder), lobbyOf(holder), key, target.getName(), pick.id(),
                drifted.feedbackDistance(), Map.of());
        return true;
    }

    private boolean trackSighting(Player holder, ItemStack item, int slot, CompassPick pick, String targetRoleString,
            boolean locked, Location origin) {
        Location location = playerStates.sightings().getOrDefault(pick.id(), Map.of())
                .get(holder.getWorld().getUID());
        Player seen = Bukkit.getPlayer(pick.id());
        if (location == null || location.getWorld() == null
                || skipLastSeen(seen != null,
                        seen != null && plugin.fakeSpectators().isFakeSpectator(seen))) {
            showNoTarget(holder, item, slot, targetRoleString);
            return false;
        }
        CompassInaccuracyService.Result drifted =
                resolveInaccuracy(holder, origin, location, pick.id());
        setLodestone(item, drifted.needleSpot());
        holder.getInventory().setItem(slot, item);
        String reason = seen != null ? "Another Dimension" : "Log-Out";
        String key = trackingKey(holder, locked, true);
        deltas.putTrackingBar(holder, role(holder), lobbyOf(holder), key, pick.name(), pick.id(),
                drifted.feedbackDistance(), Map.of("reason", reason));
        return true;
    }

    private void showNoTarget(Player holder, ItemStack item, int slot, String targetRoleString) {
        spinNeedle(item, holder);
        holder.getInventory().setItem(slot, item);
        compassActionbars.put(holder.getUniqueId(), component("compass.no-target-actionbar",
                Map.of("role", targetRoleString)));
    }

    private void showBadSignal(Player holder, ItemStack item, int slot,
            SignalInterference.Reason reason) {
        spinNeedle(item, holder);
        holder.getInventory().setItem(slot, item);
        if (plugin.overrides().getBoolean(lobbyOf(holder),
                "settings.compass.signal.interference.show-reason-in-actionbar", true)) {
            compassActionbars.put(holder.getUniqueId(), component("compass.bad-signal-reason-actionbar",
                    Map.of("reason", reasonText(reason))));
            return;
        }
        compassActionbars.put(holder.getUniqueId(), component("compass.bad-signal-actionbar"));
    }

    /**
     * Display text for one interference reason: the signal-reason
     * message for the option id, prefixed for target-side failures.
     */
    private String reasonText(SignalInterference.Reason reason) {
        String text = messages.string("compass.signal-reason." + reason.id(), reason.id());
        return reason.targetSide() ? "target " + text : text;
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
     * Online fake spectators are mid-respawn (or otherwise out of play):
     * never a last-seen target. Offline players still report their log-out
     * spot. Pure for tests.
     */
    static boolean skipLastSeen(boolean online, boolean fakeSpectator) {
        return online && fakeSpectator;
    }

    public boolean shouldReceiveCompass(Integer lobby, Role role) {
        return items.shouldReceiveCompass(lobby, role);
    }

    public void giveCompass(Player player) {
        items.giveCompass(player);
    }

    public void deduplicateCompasses(Player player) {
        items.deduplicateCompasses(player);
    }

    public void removeCompasses(Player player) {
        items.removeCompasses(player);
        deltas.forget(player.getUniqueId());
        sessions.cancelAnalysisSnapshots(player.getUniqueId());
        hotspots.clear(player.getUniqueId());
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

    public boolean mustBeInventory(Integer lobby) {
        return items.mustBeInventory(lobby);
    }

    public void handleRightClick(Player player) {
        if (isVanillaSpectator(player)) {
            return;
        }
        Integer lobby = lobbyOf(player);
        if (!plugin.overrides()
                .getBoolean(lobby, "settings.compass.actions.manual.enabled", false)) {
            return;
        }
        if (plugin.fakeSpectators().isFakeSpectator(player)) {
            return;
        }
        if (locks.isAnalyzing(player.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (plugin.overrides()
                .getDouble(lobby, "settings.compass.actions.manual.cooldown", 3.0) * 1000);
        if (!shouldRefresh(now, lastClick.getOrDefault(player.getUniqueId(), 0L), cooldownMs)) {
            return;
        }
        // Right-clicks run on the refresh click cooldown, so a fresh
        // automatic refresh never blocks them; each click also stamps the
        // automatic clock at initiation, restarting the interval from here.
        // With analysis, the stamp lands at resolution instead, so the full
        // cooldown runs after the refresh. Left and shift-left clicks use
        // their own throttles and never touch this cooldown.
        if (locks.analyzeEnabled(lobby)) {
            // Poor holders never start: no snapshot, no debuffs, no stamps.
            if (!sessions.tryInitiateCost(player)) {
                return;
            }
            lastAutoRefresh.put(player.getUniqueId(), now);
            locks.startAnalysis(player);
            return;
        }
        lastAutoRefresh.put(player.getUniqueId(), now);
        lastClick.put(player.getUniqueId(), now);
        resolveClickRefresh(player);
    }

    /**
     * Click-initiated refresh with exactly one outcome sound: the
     * refresh click when the needle tracks, the failure sound when it
     * lands on nearby, too far, bad signal, or no target.
     */
    void resolveClickRefresh(Player holder) {
        if (refreshCompassOutcome(holder)) {
            sounds.playSound(holder, "compass.right-click");
        } else {
            sounds.playSound(holder, "compass.failure");
        }
    }

    /**
     * Cycles the holder's manual target lock one step: automatic locks
     * the nearest candidate, further clicks advance through the rest,
     * and cycling past the last candidate returns to automatic. No-op
     * unless left-click cycling is enabled and the holder participates
     * in a live match. Clicks inside the scroll cooldown are ignored,
     * which also stops held clicks from scrolling; scrolling is refused
     * during analysis. Cycling reads only the snapshot cache, never
     * fetches a live position, and never touches the refresh cooldown;
     * uncached targets render a reasonless Bad Signal. With one or
     * fewer candidates the click quits silently. Locks survive
     * automatic refreshes; the cached render after each click applies
     * the new lock immediately.
     */
    public void handleLeftClick(Player player) {
        locks.handleLeftClick(player);
    }

    /**
     * Shift-left-click: toggles teammate tracking when enabled, else
     * locks exactly like a left-click. The toggle has its own
     * switch-cooldown throttle and, like left-clicks, renders only
     * from cache without touching the refresh cooldown. See the lock
     * service docs.
     */
    public void handleShiftLeft(Player player) {
        locks.handleShiftLeft(player);
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
