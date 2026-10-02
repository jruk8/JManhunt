package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.JManhuntPlaceholders;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.config.CompassSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.CompassMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CompassManager {
    /** Chat, item, debuff, and click-sound halves. */
    public record ManagerTexts(MessageService messages, CompassMessages compass,
            ModifiersMessages modifiers, SoundService sounds) {
    }

    /** Role plus fake-spectator reads. */
    public record ManagerPlayers(PlayerStateStore playerStates, FakeSpectatorService fakes) {
    }

    /** Scheduler, logger, placeholder, and item-key edge. */
    public record ManagerEdge(TaskScheduler tasks, JManhuntLogger log,
            java.util.function.Supplier<JManhuntPlaceholders> placeholders,
            NamespacedKey compassKey) {
    }

    private final CompassSettingsFacade settings;
    private final ManagerTexts texts;
    private final ManagerPlayers players;
    private final CompassSignalService signal;
    private final HotspotService hotspots;
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
    private final CompassRefreshService refresh;
    private GameManager game;

    public CompassManager(CompassSettingsFacade settings, ManagerTexts texts,
            ManagerPlayers players, ManagerEdge edge) {
        this.settings = settings;
        this.texts = texts;
        this.players = players;
        PlayerStateStore playerStates = players.playerStates();
        FakeSpectatorService fakes = players.fakes();
        MessageService messages = texts.messages();
        CompassMessages compass = texts.compass();
        ModifiersMessages modifiers = texts.modifiers();
        SoundService sounds = texts.sounds();
        CompassTargetService targets = new CompassTargetService(playerStates, fakes);
        this.signal = new CompassSignalService(settings, playerStates);
        this.hotspots = new HotspotService(settings, playerStates, fakes);
        CompassInaccuracyService inaccuracy = new CompassInaccuracyService(settings, hotspots);
        this.items = new CompassItemService(settings,
                new CompassItemService.ItemPlayers(playerStates, fakes),
                new CompassItemService.ItemTexts(messages, compass), edge.log(),
                edge.compassKey());
        this.sessions = new CompassAnalysisSessions(settings,
                new CompassAnalysisSessions.SessionTexts(messages, compass, sounds),
                new CompassAnalysisSessions.SessionServices(targets, signal, items),
                new CompassAnalysisSessions.SessionPlayers(playerStates, fakes),
                compassActionbars);
        CompassAnalysisRunner runner = newRunner(settings, texts, players, edge, sessions);
        this.locks = new CompassLockService(settings,
                new CompassLockService.LockCycle(targets, cache, this::renderFromCache,
                        this::refreshCompass),
                runner, new CompassLockService.LockTexts(messages, compass, sounds),
                new CompassLockService.LockPlayers(playerStates, fakes));
        sessions.setLockService(locks);
        this.deltas = new CompassDeltaRenderer(edge.tasks(), settings, messages,
                compassActionbars);
        this.refresh = new CompassRefreshService(settings,
                new CompassRefreshService.RefreshInputs(targets, signal, items, inaccuracy,
                        deltas),
                new CompassRefreshService.RefreshSession(locks, sessions, cache,
                        compassActionbars),
                new CompassRefreshService.RefreshTexts(messages, compass, sounds),
                new CompassRefreshService.RefreshPlayers(playerStates, fakes));
    }

    /** Builds the analysis runner behind compass clicks and debuffs. */
    private CompassAnalysisRunner newRunner(CompassSettingsFacade settings, ManagerTexts texts,
            ManagerPlayers players, ManagerEdge edge, CompassAnalysisSessions sessions) {
        return new CompassAnalysisRunner(settings,
                new CompassAnalysisRunner.RunnerFeedback(texts.messages(), texts.compass(),
                        texts.modifiers(), texts.sounds(), edge.log()),
                new CompassAnalysisRunner.RunnerCallbacks(this::resolveClickRefresh,
                        this::refreshCompass, sessions::beginAnalysisSpot, sessions,
                        edge.tasks()),
                new CompassAnalysisRunner.RunnerShared(compassActionbars, lastClick),
                new CompassAnalysisRunner.RunnerData(players.playerStates(), players.fakes(),
                        edge.placeholders()));
    }

    /** Wires the game after construction so targets resolve within one match. */
    public void setGameManager(GameManager game) {
        this.game = game;
        locks.setGameManager(game);
        items.setGameManager(game);
        sessions.setGameManager(game);
        hotspots.setGameManager(game);
        signal.setGameManager(game);
        refresh.setGameManager(game);
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
                        boolean autoEnabled = settings.autoEnabled(lobby);
                        double intervalSeconds =
                                settings.autoIntervalSeconds(lobby);
                        double deviationSeconds =
                                settings.autoDeviationSeconds(lobby);
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
        MessageService messages = texts.messages();
        CompassMessages compass = texts.compass();
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
                            messages.componentRaw(compass.getNoTargetActionbar(),
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
        refresh.refreshCompass(holder);
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


    /** Resolves the compass pick for the narrowed targets. */
    static CompassPick resolveCompassPick(CompassSettingsFacade settings, Integer lobby,
            Role holderRole, List<CompassCandidate> opponents,
            List<CompassSighting> sightings) {
        boolean hunter = holderRole == Role.HUNTER;
        boolean minOn = hunter ? settings.hunterMinDistanceEnabled(lobby)
                : settings.speedrunnerMinDistanceEnabled(lobby);
        double min = hunter ? settings.hunterMinDistance(lobby)
                : settings.speedrunnerMinDistance(lobby);
        boolean maxOn = hunter ? settings.hunterMaxDistanceEnabled(lobby)
                : settings.speedrunnerMaxDistanceEnabled(lobby);
        double max = maxOn ? (hunter ? settings.hunterMaxDistance(lobby)
                : settings.speedrunnerMaxDistance(lobby)) : -1.0;
        return CompassPick.resolve(opponents, sightings, minOn, min, max);
    }

    /** One hotspot sampling tick, driven by the plugin scheduler. */
    public void sampleHotspots() {
        hotspots.tick(System.currentTimeMillis());
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
    }

    /** Drops one player's hotspot history: permanent-elimination cleanup. */
    public void clearHotspotHistory(UUID playerId) {
        hotspots.clear(playerId);
    }

    /** Drops every listed hotspot history: game-end cleanup for one match. */
    public void clearHotspotHistories(Collection<UUID> playerIds) {
        hotspots.clearAll(playerIds);
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
        if (!settings.manualEnabled(lobby)) {
            return;
        }
        if (players.fakes().isFakeSpectator(player)) {
            return;
        }
        if (locks.isAnalyzing(player.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (settings.manualCooldownSeconds(lobby) * 1000);
        if (!shouldRefresh(now, lastClick.getOrDefault(player.getUniqueId(), 0L), cooldownMs)) {
            return;
        }
        if (sessions.failureBlocked(player.getUniqueId(), lobby, now)) {
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

    /** Renders the holder's lock and mode from the snapshot cache only. */
    void renderFromCache(Player holder) {
        refresh.renderFromCache(holder);
    }

    /** Click-initiated refresh with exactly one outcome sound. */
    void resolveClickRefresh(Player holder) {
        refresh.resolveClickRefresh(holder);
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
        return players.playerStates().role(player);
    }
}
