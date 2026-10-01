package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.lobby.config.CompassSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.CompassMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Compass refresh and render: slot and match resolution, snapshot
 * cache writes, live and cache renders, and the click outcome sound.
 * Extracted from the manager; the manager keeps delegating entries.
 */
final class CompassRefreshService {
    /** Target resolution inputs. */
    record RefreshInputs(CompassTargetService targets, CompassSignalService signal,
            CompassItemService items, CompassInaccuracyService inaccuracy,
            CompassDeltaRenderer deltas) {
    }

    /** Live lock, session, and cache state. */
    record RefreshSession(CompassLockService locks, CompassAnalysisSessions sessions,
            CompassCache cache, Map<UUID, Component> compassActionbars) {
    }

    /** Render chat plus click sounds. */
    record RefreshTexts(MessageService messages, CompassMessages compass, SoundService sounds) {
    }

    /** Role plus fake-spectator reads. */
    record RefreshPlayers(PlayerStateStore states, FakeSpectatorService fakes) {
    }

    private final CompassSettingsFacade settings;
    private final RefreshInputs inputs;
    private final RefreshSession session;
    private final RefreshTexts texts;
    private final RefreshPlayers players;
    private GameManager game;

    CompassRefreshService(CompassSettingsFacade settings, RefreshInputs inputs,
            RefreshSession session, RefreshTexts texts, RefreshPlayers players) {
        this.settings = settings;
        this.inputs = inputs;
        this.session = session;
        this.texts = texts;
        this.players = players;
    }

    /** Wires the game after construction so refreshes resolve within one match. */
    void setGameManager(GameManager game) {
        this.game = game;
    }

    /** Origin lobby of the holder's match, or null outside matches. */
    private Integer lobbyOf(Player holder) {
        return game == null ? null : game.lobbyOfPlayer(holder.getUniqueId());
    }

    private Role role(Player player) {
        return players.states().role(player);
    }

    void refreshCompass(Player holder) {
        if (holder.isDead()) {
            return;
        }
        refreshCompassOutcome(holder);
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
        boolean analysis = session.sessions().hasAnalysisSnapshots(id);
        try {
            return refreshCompassResolved(holder);
        } finally {
            if (analysis) {
                session.sessions().cancelAnalysisSnapshots(id);
            }
        }
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
        List<CompassCandidate> opponents = inputs.targets().collectOpponents(holder,
                target.targetRole(), target.instance());
        List<CompassSighting> sightings = inputs.targets().collectSightings(holder,
                target.targetRole(), target.instance(), holder.getLocation());
        CompassLockService.LockedTargets narrowed = session.locks().narrowToLock(
                holder.getUniqueId(), opponents, sightings);
        CompassPick pick = CompassManager.resolveCompassPick(settings,
                target.instance().originLobbyId(), target.holderRole(), narrowed.opponents(),
                narrowed.sightings());
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
        inputs.items().deduplicateCompasses(holder);
        int slot = inputs.items().findCompassSlot(holder);
        if (slot < 0) {
            return Optional.empty();
        }
        ItemStack item = holder.getInventory().getItem(slot);
        if (!inputs.items().isCompass(item)) {
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
        int cap = CompassCache.clampMaxTargets(
                settings.targetCyclingMaxTargets(lobbyOf(holder)));
        session.cache().replace(holder.getUniqueId(), holder.getLocation().clone(),
                inputs.targets().collectSnapshots(holder, Role.HUNTER, instance, cap),
                inputs.targets().collectSnapshots(holder, Role.SPEEDRUNNER, instance, cap));
    }

    /** Resolves the match and roles for a refresh, rendering the spectator fallback. */
    private Optional<RefreshMatch> refreshMatch(Player holder, RefreshSlot slot) {
        if (CompassManager.isVanillaSpectator(holder)) {
            return Optional.empty();
        }
        Role holderRole = role(holder);
        if (!holderRole.isParticipant()) {
            session.locks().clearMatchState(holder.getUniqueId());
            return Optional.empty();
        }
        Role targetRole = session.locks().targetRole(holder);
        String targetRoleString = texts.messages().roleName(targetRole);
        if (players.fakes().isFakeSpectator(holder)) {
            showNoTarget(holder, slot.item(), slot.slot(), targetRoleString);
            return Optional.empty();
        }
        if (game == null) {
            return Optional.empty();
        }
        Optional<GameInstance> match = game.instanceOf(holder.getUniqueId());
        if (match.isEmpty()) {
            session.locks().clearMatchState(holder.getUniqueId());
            return Optional.empty();
        }
        return Optional.of(new RefreshMatch(match.get(), holderRole, targetRole, targetRoleString));
    }

    /**
     * Renders a resolved pick onto the compass item and actionbar. True
     * when the needle now tracks a live target or sighting.
     */
    private boolean renderCompassPick(Player holder, ItemStack item, int slot, CompassPick pick,
            String targetRoleString, boolean locked) {
        Location spot = session.sessions().resolutionSpot(holder);
        Location targetPress = session.sessions().targetPressSpot(holder.getUniqueId(), pick.id());
        double holderMoved = session.sessions().analysisMaxMoved(holder);
        Optional<SignalInterference.Reason> reason = pick.kind() == CompassPick.Kind.NONE
                ? Optional.empty()
                : inputs.signal().reasonForPick(holder, spot, targetPress, pick, holderMoved);
        if (reason.isPresent()) {
            showBadSignal(holder, item, slot, reason.get());
            return false;
        }
        return switch (pick.kind()) {
            case TRACK_PLAYER -> trackPlayer(holder, item, slot, pick, targetRoleString, locked);
            case TRACK_SIGHTING -> trackSighting(holder, item, slot, pick, targetRoleString, locked,
                    session.sessions().resolutionSpot(holder));
            case NEARBY -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                session.compassActionbars().put(holder.getUniqueId(),
                        texts.messages().componentRaw(texts.compass().getNearbyActionbar(),
                                Map.of("player", pick.name())));
                yield false;
            }
            case TOO_FAR -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                session.compassActionbars().put(holder.getUniqueId(),
                        texts.messages().componentRaw(texts.compass().getTooFarActionbar(),
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
        int maxTargets = CompassCache.clampMaxTargets(
                settings.targetCyclingMaxTargets(lobbyOf(holder)));
        CompassLockService.CachedCycle cycle = session.locks().buildCycle(holder,
                target.instance(), target.targetRole(), maxTargets);
        CompassLockService.LockedTargets narrowed = session.locks().narrowToLockCached(
                holder.getUniqueId(), cycle.cached(), cycle.sightings(), cycle.trackableIds());
        CompassPick pick = CompassManager.resolveCompassPick(settings,
                target.instance().originLobbyId(), target.holderRole(), narrowed.opponents(),
                narrowed.sightings());
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
                    CompassManager.effectiveSpot(
                            session.cache().holderSpotFor(holder.getUniqueId()),
                            session.sessions().resolutionSpot(holder)));
            case NEARBY -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                session.compassActionbars().put(holder.getUniqueId(),
                        texts.messages().componentRaw(texts.compass().getNearbyActionbar(),
                                Map.of("player", pick.name())));
            }
            case TOO_FAR -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                session.compassActionbars().put(holder.getUniqueId(),
                        texts.messages().componentRaw(texts.compass().getTooFarActionbar(),
                                Map.of("player", pick.name())));
            }
            case NONE -> showCacheBadSignal(holder, item, slot);
        }
    }

    /** Points the needle at a snapshotted location, or Bad Signals when gone. */
    private void trackCachedPlayer(Player holder, ItemStack item, int slot, CompassPick pick,
            boolean locked) {
        Location spot = session.cache().spotsFor(holder.getUniqueId()).get(pick.id());
        if (spot == null || spot.getWorld() == null
                || !spot.getWorld().getUID().equals(holder.getWorld().getUID())) {
            showCacheBadSignal(holder, item, slot);
            return;
        }
        Location origin = CompassManager.effectiveSpot(
                session.cache().holderSpotFor(holder.getUniqueId()),
                session.sessions().resolutionSpot(holder));
        CompassInaccuracyService.Result drifted =
                resolveInaccuracy(holder, origin, spot, pick.id());
        setLodestone(item, drifted.needleSpot());
        holder.getInventory().setItem(slot, item);
        String template = trackingTemplate(holder, locked, false);
        inputs.deltas().putTrackingBar(holder, role(holder), lobbyOf(holder), template,
                pick.name(), pick.id(), drifted.feedbackDistance(), Map.of(), drifted);
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
        return inputs.inaccuracy().resolve(lobbyOf(holder), origin, truth, targetId,
                random.nextDouble(), random.nextDouble());
    }

    /** Reasonless Bad Signal for uncached switch targets, by spec. */
    private void showCacheBadSignal(Player holder, ItemStack item, int slot) {
        spinNeedle(item, holder);
        holder.getInventory().setItem(slot, item);
        session.compassActionbars().put(holder.getUniqueId(), texts.messages()
                .componentRaw(texts.compass().getBadSignalActionbar()));
    }

    /** Tracking actionbar key for the holder's mode, lock, and sighting state. */
    private String trackingTemplate(Player holder, boolean locked, boolean lastSeen) {
        boolean teammate = session.locks().teammateMode(holder.getUniqueId());
        CompassMessages compass = texts.compass();
        if (lastSeen) {
            if (teammate) {
                return locked ? compass.getTeammateLastSeenLockedActionbar()
                        : compass.getTeammateLastSeenActionbar();
            }
            return locked ? compass.getCompassLastSeenLockedActionbar()
                    : compass.getCompassLastSeenActionbar();
        }
        if (teammate) {
            return locked ? compass.getTeammateLockedActionbar() : compass.getTeammateActionbar();
        }
        return locked ? compass.getCompassLockedActionbar() : compass.getCompassActionbar();
    }

    private boolean trackPlayer(Player holder, ItemStack item, int slot, CompassPick pick,
            String targetRoleString, boolean locked) {
        Player target = Bukkit.getPlayer(pick.id());
        if (target == null) {
            showNoTarget(holder, item, slot, targetRoleString);
            return false;
        }
        Location truth = target.getLocation();
        CompassInaccuracyService.Result drifted = resolveInaccuracy(holder,
                session.sessions().resolutionSpot(holder), truth, pick.id());
        setLodestone(item, drifted.needleSpot());
        holder.getInventory().setItem(slot, item);
        String template = trackingTemplate(holder, locked, false);
        inputs.deltas().putTrackingBar(holder, role(holder), lobbyOf(holder), template,
                target.getName(), pick.id(), drifted.feedbackDistance(), Map.of(), drifted);
        return true;
    }

    private boolean trackSighting(Player holder, ItemStack item, int slot, CompassPick pick,
            String targetRoleString, boolean locked, Location origin) {
        Location location = players.states().sightings().getOrDefault(pick.id(), Map.of())
                .get(holder.getWorld().getUID());
        Player seen = Bukkit.getPlayer(pick.id());
        if (location == null || location.getWorld() == null
                || CompassManager.skipLastSeen(seen != null,
                        seen != null && players.fakes().isFakeSpectator(seen))) {
            showNoTarget(holder, item, slot, targetRoleString);
            return false;
        }
        CompassInaccuracyService.Result drifted =
                resolveInaccuracy(holder, origin, location, pick.id());
        setLodestone(item, drifted.needleSpot());
        holder.getInventory().setItem(slot, item);
        String reason = seen != null ? "Another Dimension" : "Log-Out";
        String template = trackingTemplate(holder, locked, true);
        inputs.deltas().putTrackingBar(holder, role(holder), lobbyOf(holder), template,
                pick.name(), pick.id(), drifted.feedbackDistance(), Map.of("reason", reason),
                drifted);
        return true;
    }

    private void showNoTarget(Player holder, ItemStack item, int slot, String targetRoleString) {
        spinNeedle(item, holder);
        holder.getInventory().setItem(slot, item);
        session.compassActionbars().put(holder.getUniqueId(), texts.messages()
                .componentRaw(texts.compass().getNoTargetActionbar(),
                        Map.of("role", targetRoleString)));
    }

    private void showBadSignal(Player holder, ItemStack item, int slot,
            SignalInterference.Reason reason) {
        spinNeedle(item, holder);
        holder.getInventory().setItem(slot, item);
        if (settings.showReasonInActionbar(lobbyOf(holder))) {
            session.compassActionbars().put(holder.getUniqueId(), texts.messages()
                    .componentRaw(texts.compass().getBadSignalReasonActionbar(),
                            Map.of("reason", reasonText(reason))));
            return;
        }
        session.compassActionbars().put(holder.getUniqueId(), texts.messages()
                .componentRaw(texts.compass().getBadSignalActionbar()));
    }

    /**
     * Display text for one interference reason: the signal-reason
     * message for the option id, prefixed for target-side failures.
     */
    private String reasonText(SignalInterference.Reason reason) {
        String text = texts.compass().getSignalReason().getOrDefault(reason.id(), reason.id());
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
     * Click-initiated refresh with exactly one outcome sound: the
     * refresh click when the needle tracks, the failure sound when it
     * lands on nearby, too far, bad signal, or no target.
     */
    void resolveClickRefresh(Player holder) {
        if (refreshCompassOutcome(holder)) {
            texts.sounds().playSound(holder, "compass.right-click");
        } else {
            texts.sounds().playSound(holder, "compass.failure");
        }
    }
}
