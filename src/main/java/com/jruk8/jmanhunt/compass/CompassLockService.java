package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.lobby.config.CompassSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.CompassMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Manual target locks, left-click scroll cycling, and teammate
 * tracking. Analysis runs live in the runner; this keeps the lock,
 * scroll, and cycle logic plus analysis delegates.
 */
final class CompassLockService {
    /** Cycle inputs, renders, and the reconcile refresh. */
    record LockCycle(CompassTargetService targets, CompassCache cache,
            Consumer<Player> cacheRenderer, Consumer<Player> refresher) {
    }

    /** Lock chat plus click sounds. */
    record LockTexts(MessageService messages, CompassMessages compass, SoundService sounds) {
    }

    /** Role plus fake-spectator reads. */
    record LockPlayers(PlayerStateStore states, FakeSpectatorService fakes) {
    }

    private final CompassSettingsFacade settings;
    private final LockCycle cycle;
    private final CompassAnalysisRunner runner;
    private final LockTexts texts;
    private final LockPlayers players;
    /** Manual left-click target locks: holder id -> locked target id. */
    private final Map<UUID, UUID> locks = new HashMap<>();
    /** Holders tracking teammates instead of enemies, toggled by shift-left. */
    private final Set<UUID> teammates = new HashSet<>();
    /** Last accepted left-click scroll per holder; throttles held clicks. */
    private final Map<UUID, Long> lastScroll = new HashMap<>();
    /** Last accepted shift-left switch per holder; a pure feel throttle. */
    private final Map<UUID, Long> lastSwitch = new HashMap<>();
    private GameManager game;

    CompassLockService(CompassSettingsFacade settings, LockCycle cycle,
            CompassAnalysisRunner runner, LockTexts texts, LockPlayers players) {
        this.settings = settings;
        this.cycle = cycle;
        this.runner = runner;
        this.texts = texts;
        this.players = players;
    }

    /** Wires the game after construction; scroll and analysis need matches. */
    void setGameManager(GameManager game) {
        this.game = game;
        runner.setGameManager(game);
    }

    /** Origin lobby of the holder's match, or null outside matches. */
    private Integer lobbyOf(Player holder) {
        return game == null ? null : game.lobbyOfPlayer(holder.getUniqueId());
    }

    /** Opponents and sightings narrowed to the holder's manual lock. */
    record LockedTargets(List<CompassCandidate> opponents, List<CompassSighting> sightings,
            boolean locked) {
    }

    /** Narrows targets to the manual lock, clearing stale locks. */
    LockedTargets narrowToLock(UUID holderId, List<CompassCandidate> opponents,
            List<CompassSighting> sightings) {
        UUID lockedId = locks.get(holderId);
        if (lockedId == null) {
            return new LockedTargets(opponents, sightings, false);
        }
        List<CompassCandidate> lockedOpponents = opponents.stream()
                .filter(candidate -> candidate.id().equals(lockedId)).toList();
        List<CompassSighting> lockedSightings = sightings.stream()
                .filter(sighting -> sighting.ownerId().equals(lockedId)).toList();
        if (lockedOpponents.isEmpty() && lockedSightings.isEmpty()) {
            locks.remove(holderId);
            return new LockedTargets(opponents, sightings, false);
        }
        return new LockedTargets(lockedOpponents, lockedSightings, true);
    }

    /**
     * Narrows cached spots and stored sightings to the manual lock. A
     * lock on a still-trackable but uncached target is kept with empty
     * inputs so the render shows Bad Signal; only truly stale locks
     * clear back to automatic tracking.
     */
    LockedTargets narrowToLockCached(UUID holderId, List<CompassCandidate> cached,
            List<CompassSighting> sightings, Set<UUID> trackableIds) {
        UUID lockedId = locks.get(holderId);
        if (lockedId == null) {
            return new LockedTargets(cached, sightings, false);
        }
        List<CompassCandidate> lockedCached = cached.stream()
                .filter(candidate -> candidate.id().equals(lockedId)).toList();
        List<CompassSighting> lockedSightings = sightings.stream()
                .filter(sighting -> sighting.ownerId().equals(lockedId)).toList();
        if (!lockedCached.isEmpty() || !lockedSightings.isEmpty()) {
            return new LockedTargets(lockedCached, lockedSightings, true);
        }
        if (trackableIds.contains(lockedId)) {
            return new LockedTargets(List.of(), List.of(), true);
        }
        locks.remove(holderId);
        return new LockedTargets(cached, sightings, false);
    }

    /** Cache-only inputs for one holder: spots, sightings, names, and order. */
    record CachedCycle(List<CompassCandidate> cached, List<CompassSighting> sightings,
            Set<UUID> trackableIds, List<UUID> ordered, Map<UUID, String> names) {
    }

    /**
     * Builds one holder's cache-only inputs: snapshots filtered to
     * currently trackable same-world targets, stored sightings, and
     * the capped cycle order. Distances measure from the holder's
     * refresh-time snapshot, so browsing never recomputes them from
     * the live position.
     */
    CachedCycle buildCycle(Player holder, GameInstance instance, Role targetRole, int maxTargets) {
        List<CompassIdentity> identities =
                cycle.targets().collectIdentities(holder, targetRole, instance);
        Map<UUID, String> names = new HashMap<>();
        for (CompassIdentity identity : identities) {
            names.put(identity.id(), identity.name());
        }
        List<CompassCandidate> cached = new ArrayList<>();
        Map<UUID, Location> spots = cycle.cache().spotsFor(holder.getUniqueId());
        Location origin = CompassManager.effectiveSpot(
                cycle.cache().holderSpotFor(holder.getUniqueId()), holder.getLocation());
        if (!spots.isEmpty()) {
            UUID worldId = holder.getWorld().getUID();
            for (Map.Entry<UUID, Location> entry : spots.entrySet()) {
                Location spot = entry.getValue();
                if (!names.containsKey(entry.getKey()) || spot == null
                        || spot.getWorld() == null
                        || !spot.getWorld().getUID().equals(worldId)) {
                    continue;
                }
                cached.add(new CompassCandidate(entry.getKey(), names.get(entry.getKey()),
                        origin.distance(spot),
                        CompassTargetService.flatDistance(origin, spot)));
            }
        }
        List<CompassSighting> sightings =
                cycle.targets().collectSightings(holder, targetRole, instance, origin);
        List<UUID> ordered =
                CompassPick.orderedCachedCandidates(cached, sightings, identities, maxTargets);
        return new CachedCycle(cached, sightings, names.keySet(), ordered, names);
    }

    /** Drops the holder's manual lock, if any. */
    void clearLock(UUID holderId) {
        locks.remove(holderId);
    }

    /** True while the holder tracks teammates instead of enemies. */
    boolean teammateMode(UUID holderId) {
        return teammates.contains(holderId);
    }

    /** Drops the holder's teammate tracking, if any. */
    void clearTeammateMode(UUID holderId) {
        teammates.remove(holderId);
    }

    /** Drops lock, teammate mode, and snapshots: the per-holder match state. */
    void clearMatchState(UUID holderId) {
        locks.remove(holderId);
        teammates.remove(holderId);
        cycle.cache().clear(holderId);
    }

    /**
     * Flips holders in the given match back to opponent tracking when
     * their last teammate is gone, refreshing each flipped compass at
     * once. Called after eliminations and leaves that can empty a team.
     */
    public void reconcileTeammateModes(GameInstance instance) {
        if (game == null) {
            return;
        }
        long matchId = instance.matchId();
        for (Player holder : Bukkit.getOnlinePlayers()) {
            if (!teammateMode(holder.getUniqueId())) {
                continue;
            }
            Optional<GameInstance> match = game.instanceOf(holder.getUniqueId());
            if (match.isEmpty() || match.get().matchId() != matchId) {
                continue;
            }
            Role holderRole = players.states().role(holder);
            if (!holderRole.isParticipant()) {
                continue;
            }
            if (cycle.targets().collectOpponents(holder, holderRole, match.get()).isEmpty()) {
                clearTeammateMode(holder.getUniqueId());
                cycle.refresher().accept(holder);
            }
        }
    }

    /**
     * Effective target role: the holder's own role in teammate mode,
     * else the enemy role.
     */
    Role targetRole(Player holder) {
        Role holderRole = players.states().role(holder);
        return teammateMode(holder.getUniqueId()) ? holderRole : holderRole.opposite();
    }

    /** True while the holder's analysis runs. */
    boolean isAnalyzing(UUID holderId) {
        return runner.isAnalyzing(holderId);
    }

    /** Starts one holder's analysis run. */
    void startAnalysis(Player holder) {
        runner.startAnalysis(holder);
    }

    /** Cycles the holder's manual target lock one step; see the facade docs. */
    void handleLeftClick(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        long now = System.currentTimeMillis();
        Optional<GameInstance> match = scrollMatch(player, now);
        if (match.isEmpty()) {
            return;
        }
        // Stamped before target checks so failed clicks throttle too.
        lastScroll.put(player.getUniqueId(), now);
        int maxTargets = CompassCache.clampMaxTargets(
                settings.targetCyclingMaxTargets(lobbyOf(player)));
        CachedCycle cycle =
                buildCycle(player, match.get(), targetRole(player), maxTargets);
        if (cycle.ordered().size() <= 1) {
            return;
        }
        applyCachedCycle(player, cycle);
        this.cycle.cacheRenderer().accept(player);
    }

    /** Resolves the scroll match, applying the enabled, throttle, and membership gates. */
    private Optional<GameInstance> scrollMatch(Player player, long now) {
        Integer lobby = lobbyOf(player);
        if (!settings.targetCyclingEnabled(lobby)) {
            return Optional.empty();
        }
        if (players.fakes().isFakeSpectator(player)) {
            return Optional.empty();
        }
        if (runner.isAnalyzing(player.getUniqueId())) {
            return Optional.empty();
        }
        long cooldownMs = (long) (Math.max(0.0,
                settings.targetCyclingScrollCooldownSeconds(lobby)) * 1000);
        // Shared pure helper lives on the facade.
        if (!CompassManager.shouldRefresh(now, lastScroll.getOrDefault(player.getUniqueId(), 0L),
                cooldownMs)) {
            return Optional.empty();
        }
        if (game == null || !players.states().role(player).isParticipant()) {
            return Optional.empty();
        }
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty()) {
            clearMatchState(player.getUniqueId());
            return Optional.empty();
        }
        return match;
    }

    /**
     * Shift-left-click: with teammate tracking enabled, toggles the
     * holder between enemies and teammates, drops the manual lock,
     * and renders the current cache at once. With it disabled, behaves
     * exactly like a normal left-click lock. Vanilla and fake
     * spectators, analyses, the switch throttle, and non-participants
     * refuse the toggle. The refresh cooldown is never touched.
     */
    void handleShiftLeft(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Integer lobby = lobbyOf(player);
        if (!settings.teammatesEnabled(lobby)) {
            handleLeftClick(player);
            return;
        }
        if (players.fakes().isFakeSpectator(player)
                || runner.isAnalyzing(player.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        long switchMs = (long) (Math.max(0.0,
                settings.teammateSwitchCooldownSeconds(lobby)) * 1000);
        // Shared pure helper lives on the facade.
        if (!CompassManager.shouldRefresh(now,
                lastSwitch.getOrDefault(player.getUniqueId(), 0L), switchMs)) {
            return;
        }
        if (game == null || !players.states().role(player).isParticipant()) {
            return;
        }
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty()) {
            clearMatchState(player.getUniqueId());
            return;
        }
        UUID holderId = player.getUniqueId();
        // Stamped before the toggle so failed clicks throttle too.
        lastSwitch.put(holderId, now);
        if (!applyShiftToggle(player, match.get(), holderId)) {
            return;
        }
        locks.remove(holderId);
        texts.sounds().playSound(player, "compass.left-click");
        cycle.cacheRenderer().accept(player);
    }

    /**
     * Flips one holder's teammate mode, refusing with a message when
     * entering it with nobody to track. True when the mode flipped.
     */
    private boolean applyShiftToggle(Player player, GameInstance match, UUID holderId) {
        boolean entering;
        if (teammates.contains(holderId)) {
            teammates.remove(holderId);
            entering = false;
        } else {
            Role holderRole = players.states().role(player);
            if (cycle.targets().collectIdentities(player, holderRole, match).isEmpty()) {
                texts.messages().messageRaw(player, texts.compass().getNoTeammates());
                texts.sounds().playAngrySound(player);
                return false;
            }
            teammates.add(holderId);
            entering = true;
        }
        if (chatMessagesEnabled(lobbyOf(player))) {
            texts.messages().messageRaw(player,
                    entering ? texts.compass().getTeammateOnChat()
                            : texts.compass().getTeammateOffChat());
        }
        return true;
    }

    /** True when compass chat messages are enabled for one lobby. */
    private boolean chatMessagesEnabled(Integer lobby) {
        return settings.chatMessagesEnabled(lobby);
    }

    /**
     * Clears every manual lock pointing at a dead target, notifying each
     * online holder once when chat messages are enabled for their lobby.
     * The lookup is injected so tests never touch the server.
     */
    void clearLocksOnTargetDeath(UUID victimId, Function<UUID, Player> onlineLookup) {
        for (UUID holderId : List.copyOf(locks.keySet())) {
            if (!victimId.equals(locks.get(holderId))) {
                continue;
            }
            locks.remove(holderId);
            Player holder = onlineLookup.apply(holderId);
            if (holder == null || !chatMessagesEnabled(lobbyOf(holder))) {
                continue;
            }
            texts.messages().messageRaw(holder, texts.compass().getLockedTargetDiedChat());
        }
    }

    /** Advances the manual lock to the next cached cycle target. */
    private void applyCachedCycle(Player player, CachedCycle cycle) {
        UUID current = locks.get(player.getUniqueId());
        UUID next = CompassPick.cycleOrdered(cycle.ordered(), current);
        if (!Objects.equals(next, current)) {
            texts.sounds().playSound(player, "compass.left-click");
        }
        if (next == null) {
            locks.remove(player.getUniqueId());
            return;
        }
        locks.put(player.getUniqueId(), next);
    }

    /** Sound interval clamped to its registry bounds, for stale files. */
    static double clampedSoundInterval(double value) {
        SettingDescriptor descriptor =
                SettingRegistry.byPath("settings.compass.actions.manual.analysis.sound-interval-seconds");
        if (descriptor == null) {
            return value;
        }
        if (descriptor.min() != null) {
            value = Math.max(descriptor.min(), value);
        }
        if (descriptor.max() != null) {
            value = Math.min(descriptor.max(), value);
        }
        return value;
    }

    boolean analyzeEnabled(Integer lobby) {
        return settings.analysisEnabled(lobby);
    }
}
