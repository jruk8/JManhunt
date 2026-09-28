package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.command.CommandPlaceholders;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.command.ModifierTagScope;
import com.jruk8.jmanhunt.command.PlaceholderResolver;
import com.jruk8.jmanhunt.command.RosterValues;
import com.jruk8.jmanhunt.command.StatValues;
import com.jruk8.jmanhunt.command.TagBackends;
import com.jruk8.jmanhunt.core.PlaceholderPass;
import com.jruk8.jmanhunt.command.TagContext;
import com.jruk8.jmanhunt.command.TagExpressions;
import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.MatchRosterValues;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Manual target locks, left-click scroll cycling, and the analysis lag
 * before a refresh resolves.
 */
final class CompassLockService {
    private final JManhuntPlugin plugin;
    private final PlayerStateStore playerStates;
    private final SoundService sounds;
    private final MessageService messages;
    private final CompassTargetService targets;
    private final Map<UUID, Component> actionbars;
    private final Consumer<Player> refresher;
    /** Click-initiated refresh: refreshes plus the outcome click sound. */
    private final Consumer<Player> clickResolver;
    /** Cache-only render for accepted clicks; never fetches or refreshes. */
    private final Consumer<Player> cacheRenderer;
    /** Press-time snapshot hook, run once when an analysis starts. */
    private final Consumer<Player> analysisStarter;
    /** Location snapshots written by refresh events, read by clicks. */
    private final CompassCache cache;
    /** Manual left-click target locks: holder id -> locked target id. */
    private final Map<UUID, UUID> locks = new HashMap<>();
    /** Holders tracking teammates instead of enemies, toggled by shift-left. */
    private final Set<UUID> teammates = new HashSet<>();
    /** Last accepted left-click scroll per holder; throttles held clicks. */
    private final Map<UUID, Long> lastScroll = new HashMap<>();
    /** Last accepted shift-left switch per holder; a pure feel throttle. */
    private final Map<UUID, Long> lastSwitch = new HashMap<>();
    private final Set<UUID> analyzing = new HashSet<>();
    /** Analysis generation per holder; stale tick tasks cancel themselves. */
    private final Map<UUID, Long> generations = new HashMap<>();
    /** Click cooldown stamps shared with right-clicks, owned by the facade. */
    private final Map<UUID, Long> sharedClicks;
    private GameManager game;

    CompassLockService(JManhuntPlugin plugin, PlayerStateStore playerStates, SoundService sounds,
            MessageService messages, CompassTargetService targets,
            Map<UUID, Component> actionbars, Consumer<Player> refresher,
            Consumer<Player> clickResolver, Consumer<Player> cacheRenderer,
            Consumer<Player> analysisStarter, CompassCache cache,
            Map<UUID, Long> sharedClicks) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.sounds = sounds;
        this.messages = messages;
        this.targets = targets;
        this.actionbars = actionbars;
        this.refresher = refresher;
        this.clickResolver = clickResolver;
        this.cacheRenderer = cacheRenderer;
        this.analysisStarter = analysisStarter;
        this.cache = cache;
        this.sharedClicks = sharedClicks;
    }

    /** Wires the game after construction; scroll and analysis need matches. */
    void setGameManager(GameManager game) {
        this.game = game;
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
     * the capped cycle order. Reads no live positions.
     */
    CachedCycle buildCycle(Player holder, GameInstance instance, Role targetRole, int maxTargets) {
        List<CompassIdentity> identities =
                targets.collectIdentities(holder, targetRole, instance);
        Map<UUID, String> names = new HashMap<>();
        for (CompassIdentity identity : identities) {
            names.put(identity.id(), identity.name());
        }
        List<CompassCandidate> cached = new ArrayList<>();
        Map<UUID, Location> spots = cache.spotsFor(holder.getUniqueId());
        if (!spots.isEmpty()) {
            Location origin = holder.getLocation();
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
                targets.collectSightings(holder, targetRole, instance);
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
        cache.clear(holderId);
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
            Role holderRole = playerStates.role(holder);
            if (!holderRole.isParticipant()) {
                continue;
            }
            if (targets.collectOpponents(holder, holderRole, match.get()).isEmpty()) {
                clearTeammateMode(holder.getUniqueId());
                refresher.accept(holder);
            }
        }
    }

    /**
     * Effective target role: the holder's own role in teammate mode,
     * else the enemy role.
     */
    Role targetRole(Player holder) {
        Role holderRole = playerStates.role(holder);
        return teammateMode(holder.getUniqueId()) ? holderRole : holderRole.opposite();
    }

    /** True while the holder's analysis runs. */
    boolean isAnalyzing(UUID holderId) {
        return analyzing.contains(holderId);
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
        int maxTargets = CompassCache.clampMaxTargets(plugin.overrides()
                .getInt(lobbyOf(player), "settings.compass.left-click.max-targets", 5));
        CachedCycle cycle =
                buildCycle(player, match.get(), targetRole(player), maxTargets);
        if (cycle.ordered().size() <= 1) {
            return;
        }
        applyCachedCycle(player, cycle);
        cacheRenderer.accept(player);
    }

    /** Resolves the scroll match, applying the enabled, throttle, and membership gates. */
    private Optional<GameInstance> scrollMatch(Player player, long now) {
        Integer lobby = lobbyOf(player);
        if (!plugin.overrides()
                .getBoolean(lobby, "settings.compass.left-click.enabled", false)) {
            return Optional.empty();
        }
        if (plugin.fakeSpectators().isFakeSpectator(player)) {
            return Optional.empty();
        }
        if (analyzing.contains(player.getUniqueId())) {
            return Optional.empty();
        }
        long cooldownMs = (long) (Math.max(0.0, plugin.overrides()
                .getDouble(lobby, "settings.compass.left-click.scroll-cooldown", 0.5)) * 1000);
        // Shared pure helper lives on the facade.
        if (!CompassManager.shouldRefresh(now, lastScroll.getOrDefault(player.getUniqueId(), 0L),
                cooldownMs)) {
            return Optional.empty();
        }
        if (game == null || !playerStates.role(player).isParticipant()) {
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
        if (!plugin.overrides()
                .getBoolean(lobby, "settings.compass.teammates.enabled", true)) {
            handleLeftClick(player);
            return;
        }
        if (plugin.fakeSpectators().isFakeSpectator(player)
                || analyzing.contains(player.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        long switchMs = (long) (Math.max(0.0, plugin.overrides().getDouble(lobby,
                "settings.compass.teammates.switch-cooldown", 0.5)) * 1000);
        // Shared pure helper lives on the facade.
        if (!CompassManager.shouldRefresh(now,
                lastSwitch.getOrDefault(player.getUniqueId(), 0L), switchMs)) {
            return;
        }
        if (game == null || !playerStates.role(player).isParticipant()) {
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
        sounds.playSound(player, "compass.left-click");
        cacheRenderer.accept(player);
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
            Role holderRole = playerStates.role(player);
            if (targets.collectIdentities(player, holderRole, match).isEmpty()) {
                messages.message(player, "compass.no-teammates");
                sounds.playAngrySound(player);
                return false;
            }
            teammates.add(holderId);
            entering = true;
        }
        if (chatMessagesEnabled(lobbyOf(player))) {
            messages.message(player,
                    entering ? "compass.teammate-on-chat" : "compass.teammate-off-chat");
        }
        return true;
    }

    /** True when compass chat messages are enabled for one lobby. */
    private boolean chatMessagesEnabled(Integer lobby) {
        return plugin.overrides()
                .getBoolean(lobby, "settings.compass.chat-messages.enabled", true);
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
            messages.message(holder, "compass.locked-target-died-chat");
        }
    }

    /** Advances the manual lock to the next cached cycle target. */
    private void applyCachedCycle(Player player, CachedCycle cycle) {
        UUID current = locks.get(player.getUniqueId());
        UUID next = CompassPick.cycleOrdered(cycle.ordered(), current);
        if (!Objects.equals(next, current)) {
            sounds.playSound(player, "compass.left-click");
        }
        if (next == null) {
            locks.remove(player.getUniqueId());
            return;
        }
        locks.put(player.getUniqueId(), next);
        if (chatMessagesEnabled(lobbyOf(player))) {
            String name = cycle.names().getOrDefault(next, playerStates.playerName(next));
            messages.message(player, "compass.locked-chat", Map.of("player", name));
        }
    }

    /**
     * Purposeful analysis lag before a right-click refresh resolves:
     * shows "Analyzing...", ticks the analysis sound on the configured
     * interval, waits out the jittered delay, then refreshes. No second
     * analysis starts while one runs. Runs stamp the shared click
     * cooldown at resolution, so the full cooldown runs after the
     * refresh, and close with the outcome click sound.
     */
    void startAnalysis(Player holder) {
        UUID id = holder.getUniqueId();
        if (!analyzing.add(id)) {
            return;
        }
        analysisStarter.accept(holder);
        long generation = generations.merge(id, 1L, Long::sum);
        Integer lobby = lobbyOf(holder);
        double effectiveDelay = jitteredDelay(
                plugin.overrides().getDouble(lobby, "settings.compass.analysis.delay-seconds", 1.0),
                plugin.overrides()
                        .getDouble(lobby, "settings.compass.analysis.delay-deviation-seconds", 0.0),
                ThreadLocalRandom.current().nextDouble());
        runAnalysisDebuffs(holder, effectiveDelay);
        actionbars.put(id, messages.component("compass.analyzing-actionbar"));
        sounds.playSound(holder, "compass.analysis");
        long intervalTicks = analysisTickInterval(clampedSoundInterval(plugin.overrides()
                .getDouble(lobby, "settings.compass.analysis.sound-interval-seconds", 0.5)));
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!analyzing.contains(id) || generations.getOrDefault(id, 0L) != generation) {
                task.cancel();
                return;
            }
            if (holder.isOnline()) {
                sounds.playSound(holder, "compass.analysis");
            }
        }, intervalTicks, intervalTicks);
        long delayTicks = analyzeDelayTicks(effectiveDelay);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            analyzing.remove(id);
            sharedClicks.put(id, System.currentTimeMillis());
            if (holder.isOnline()) {
                clickResolver.accept(holder);
            } else {
                refresher.accept(holder);
            }
            boolean live = game != null && game.instanceOf(holder.getUniqueId()).isPresent();
            if (!live || !playerStates.role(holder).isParticipant()) {
                actionbars.remove(id);
            }
        }, delayTicks);
    }

    /**
     * Jittered analysis delay: the deviation is clamped to the delay,
     * then a uniform sample in delay +- deviation, never negative.
     * Pure for tests; roll is a [0, 1) sample.
     */
    static double jitteredDelay(double delaySeconds, double deviationSeconds, double roll) {
        double delay = Math.max(0.0, delaySeconds);
        double deviation = Math.min(Math.max(0.0, deviationSeconds), delay);
        return Math.max(0.0, delay + (roll * 2.0 - 1.0) * deviation);
    }

    /** Sound interval clamped to its registry bounds, for stale files. */
    static double clampedSoundInterval(double value) {
        SettingDescriptor descriptor =
                SettingRegistry.byPath("settings.compass.analysis.sound-interval-seconds");
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

    /** Analysis delay in ticks, at least one. Pure for tests. */
    static long analyzeDelayTicks(double delaySeconds) {
        return Math.max(1L, Math.round(delaySeconds * 20.0));
    }

    /**
     * Analysis tick interval in ticks: seconds rounded to whole ticks
     * (the game runs 20 ticks per second), at least one. Pure for tests.
     */
    static long analysisTickInterval(double intervalSeconds) {
        return Math.max(1L, Math.round(intervalSeconds * 20.0));
    }

    /**
     * Runs the configured analysis debuff commands for a participant
     * holder: the shared player list plus their own role list, resolved
     * modifier-style and dispatched as console.
     */
    private void runAnalysisDebuffs(Player holder, double effectiveDelaySeconds) {
        Integer lobby = lobbyOf(holder);
        if (!plugin.overrides().getBoolean(lobby,
                "settings.compass.analysis.debuffs.enabled", false)) {
            return;
        }
        Role holderRole = playerStates.role(holder);
        if (!holderRole.isParticipant()) {
            return;
        }
        double delaySeconds = effectiveDelaySeconds;
        List<String> commands = debuffCommands(lobby, holderRole);
        Location location = holder.getLocation();
        TagContext context = debuffContext(holder);
        for (int lineIndex = 0; lineIndex < commands.size(); lineIndex++) {
            String command = commands.get(lineIndex);
            stampProvenance(context, lineIndex);
            if (command.isBlank()) {
                continue;
            }
            if (TagExpressions.isExitMisuse(command)) {
                context.scope().warn("'exit' must stand alone on its line, skipping: " + command);
                continue;
            }
            try {
                String parsed = CommandPlaceholders.replace(
                        CommandPlaceholders.withDuration(command, delaySeconds),
                        holder.getName(), location.getX(), location.getY(), location.getZ(), context);
                parsed = context.placeholders().resolve(parsed, holder.getName());
                if (TagExpressions.isExit(parsed)) {
                    return;
                }
                if (TagExpressions.isExitMisuse(parsed)) {
                    context.scope().warn("'exit' must stand alone on its line, skipping: " + command);
                    continue;
                }
                dispatchDebuffLine(parsed, context);
            } catch (Exception exception) {
                plugin.logger().severe(
                        "Failed to run analysis debuff command '" + command + "'. Skipping..");
                exception.printStackTrace();
            }
        }
    }

    /** Stamps the debuff line provenance for loop-limit diagnostics. */
    private static void stampProvenance(TagContext context, int lineIndex) {
        context.setProvenance(TagContext.Provenance.of("debuffs", -1, "debuffs").withLine(lineIndex));
    }

    /**
     * Dispatches one parsed debuff line as console. A line that
     * resolved to pure {@code "null"} warns with the source line and
     * never dispatches.
     */
    private void dispatchDebuffLine(String parsed, TagContext context) {
        Optional<String> dispatchable = TagExpressions.dispatchableLine(parsed);
        if (dispatchable.isPresent() && TagExpressions.isPureNull(dispatchable.get())) {
            plugin.logger().warning("Skipping command that resolved to pure \"null\" at "
                    + context.provenance().describe() + ".");
            return;
        }
        dispatchable.ifPresent(line ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line));
    }

    /** Shared player debuffs plus the holder's own role list. */
    private List<String> debuffCommands(Integer lobby, Role holderRole) {
        List<String> commands = new ArrayList<>(plugin.overrides()
                .getStringList(lobby, "settings.compass.analysis.debuffs.commands.player"));
        commands.addAll(plugin.overrides().getStringList(lobby,
                "settings.compass.analysis.debuffs.commands." + holderRole.name().toLowerCase(Locale.ROOT)));
        return commands;
    }

    /** Tag context for one debuff run: {@code <id>} is {@code debuffs}. */
    private TagContext debuffContext(Player holder) {
        ModifierTagScope scope = ModifierTagScope.executor(holder.getName(), plugin.logger()::warning);
        long matchId = game == null ? TagContext.NO_MATCH
                : game.instanceOf(holder.getUniqueId()).map(GameInstance::matchId)
                        .orElse(TagContext.NO_MATCH);
        StatValues stats = game == null ? StatValues.inert() : game.matchStatValues(matchId);
        FlagStore flags = game == null ? new FlagStore() : game.flagStore();
        PlaceholderResolver placeholderPass = plugin.placeholderValues() == null
                ? PlaceholderResolver.inert()
                : new PlaceholderPass(plugin.placeholderValues());
        RosterValues roster = game == null ? RosterValues.inert()
                : new MatchRosterValues(game, playerStates, plugin.fakeSpectators(), matchId);
        TagBackends backends = new TagBackends(stats, flags, placeholderPass, roster);
        return TagContext.run(scope, "debuffs",
                text -> messages.broadcastText(formatEngineMessage(text)),
                text -> messages.sendText(holder, formatEngineMessage(text)),
                (soundId, pitch, volume) -> playGlobalSound(soundId, pitch, volume),
                (soundId, pitch, volume) -> {
                    if (!sounds.isValidSound(soundId)) {
                        warnInvalidSound(soundId);
                        return;
                    }
                    sounds.playCustomSound(holder, soundId, pitch, volume);
                },
                (target, reason) -> scope.warn("Tag <loseplayer> only works in modifiers: skipped."),
                (role, reason) -> scope.warn("Tag <win> only works in modifiers: skipped."),
                matchId, backends, List.of(), detail -> loopLimitExceeded(detail, matchId),
                (role, text) -> scope.warn("Tag <rmessage> only works in modifiers: skipped."),
                (role, soundId, pitch, volume) ->
                        scope.warn("Tag <rsound> only works in modifiers: skipped."));
    }

    /**
     * Loop-limit sink for debuff lines: without a live match there is
     * nothing to cancel, so the source line is only logged.
     */
    private void loopLimitExceeded(String detail, long matchId) {
        plugin.logger().severe("JMHScript loop exceeded 1000 steps at " + detail);
        if (game == null || matchId == TagContext.NO_MATCH) {
            return;
        }
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

    private String formatEngineMessage(String text) {
        String format = messages.string("modifiers.message-format", "{prefix}{message}");
        return format.replace("{prefix}", messages.string("prefix", ""))
                .replace("{message}", text);
    }

    private void playGlobalSound(String soundId, float pitch, float volume) {
        if (!sounds.isValidSound(soundId)) {
            warnInvalidSound(soundId);
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            sounds.playCustomSound(online, soundId, pitch, volume);
        }
    }

    private void warnInvalidSound(String soundId) {
        plugin.logger().warning("modifier \"debuffs\" tried playing invalid sound \""
                + soundId + "\"");
    }

    boolean analyzeEnabled(Integer lobby) {
        return plugin.overrides().getBoolean(lobby, "settings.compass.analysis.enabled", false);
    }
}
