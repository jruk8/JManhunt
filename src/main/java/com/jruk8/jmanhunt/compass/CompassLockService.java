package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.command.CommandPlaceholders;
import com.jruk8.jmanhunt.command.ModifierTagScope;
import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingRegistry;
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
    private final CompassSignalService signal;
    private final Map<UUID, Component> actionbars;
    private final Consumer<Player> refresher;
    /** Manual left-click target locks: holder id -> locked target id. */
    private final Map<UUID, UUID> locks = new HashMap<>();
    /** Last accepted left-click scroll per holder; throttles held clicks. */
    private final Map<UUID, Long> lastScroll = new HashMap<>();
    private final Set<UUID> analyzing = new HashSet<>();
    /** Analysis generation per holder; stale tick tasks cancel themselves. */
    private final Map<UUID, Long> generations = new HashMap<>();
    /** Click cooldown stamps shared with right-clicks, owned by the facade. */
    private final Map<UUID, Long> sharedClicks;
    private GameManager game;

    CompassLockService(JManhuntPlugin plugin, PlayerStateStore playerStates, SoundService sounds,
            MessageService messages, CompassTargetService targets, CompassSignalService signal,
            Map<UUID, Component> actionbars, Consumer<Player> refresher,
            Map<UUID, Long> sharedClicks) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.sounds = sounds;
        this.messages = messages;
        this.targets = targets;
        this.signal = signal;
        this.actionbars = actionbars;
        this.refresher = refresher;
        this.sharedClicks = sharedClicks;
    }

    /** Wires the game after construction; scroll and analysis need matches. */
    void setGameManager(GameManager game) {
        this.game = game;
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

    /** Drops the holder's manual lock, if any. */
    void clearLock(UUID holderId) {
        locks.remove(holderId);
    }

    /** True while the holder's analysis runs. */
    boolean isAnalyzing(UUID holderId) {
        return analyzing.contains(holderId);
    }

    /** Cycles the holder's manual target lock one step; see the facade docs. */
    void handleLeftClick(Player player) {
        long now = System.currentTimeMillis();
        Optional<GameInstance> match = scrollMatch(player, now);
        if (match.isEmpty()) {
            return;
        }
        lastScroll.put(player.getUniqueId(), now);
        GameInstance instance = match.get();
        Role targetRole = playerStates.role(player) == Role.HUNTER ? Role.SPEEDRUNNER : Role.HUNTER;
        List<CompassCandidate> opponents = targets.collectOpponents(player, targetRole, instance);
        List<CompassSighting> sightings = targets.collectSightings(player, targetRole, instance);
        int maxTargets = plugin.configService()
                .getInt("settings.compass.left-click.max-targets", 5);
        if (CompassPick.orderedCandidates(opponents, sightings, maxTargets).size() <= 1) {
            locks.remove(player.getUniqueId());
            sharedClicks.put(player.getUniqueId(), now);
            return;
        }
        if (signal.badSignalForScroll(player, opponents, sightings)) {
            sharedClicks.put(player.getUniqueId(), now);
            refresher.accept(player);
            return;
        }
        applyScrollCycle(player, opponents, sightings, maxTargets);
        if (analyzeEnabled(false)) {
            startAnalysis(player, true);
            return;
        }
        sharedClicks.put(player.getUniqueId(), now);
        refresher.accept(player);
    }

    /** Resolves the scroll match, applying the enabled, cooldown, and membership gates. */
    private Optional<GameInstance> scrollMatch(Player player, long now) {
        if (!plugin.configService()
                .getBoolean("settings.compass.left-click.enabled", false)) {
            return Optional.empty();
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return Optional.empty();
        }
        if (analyzing.contains(player.getUniqueId())) {
            return Optional.empty();
        }
        long clickMs = (long) (plugin.configService()
                .getDouble("settings.compass.click.click-cooldown", 3.0) * 1000);
        // Shared pure helper lives on the facade.
        if (!CompassManager.shouldRefresh(now,
                sharedClicks.getOrDefault(player.getUniqueId(), 0L), clickMs)) {
            return Optional.empty();
        }
        long cooldownMs = (long) (Math.max(0.0, plugin.configService()
                .getDouble("settings.compass.left-click.scroll-cooldown", 0.5)) * 1000);
        if (!CompassManager.shouldRefresh(now, lastScroll.getOrDefault(player.getUniqueId(), 0L),
                cooldownMs)) {
            return Optional.empty();
        }
        if (game == null || !playerStates.role(player).isParticipant()) {
            return Optional.empty();
        }
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty()) {
            locks.remove(player.getUniqueId());
            return Optional.empty();
        }
        return match;
    }

    /** Advances the manual lock to the next scroll target. */
    private void applyScrollCycle(Player player, List<CompassCandidate> opponents,
            List<CompassSighting> sightings, int maxTargets) {
        UUID current = locks.get(player.getUniqueId());
        UUID next = CompassPick.cycleLock(opponents, sightings, current, maxTargets);
        if (!Objects.equals(next, current)) {
            sounds.playSound(player, "compass.left-click");
        }
        if (next == null) {
            locks.remove(player.getUniqueId());
        } else {
            locks.put(player.getUniqueId(), next);
        }
    }

    /**
     * Purposeful analysis lag before a refresh resolves: shows
     * "Analyzing...", ticks the analysis sound on the configured interval,
     * waits out the jittered delay, then refreshes. No second analysis
     * starts while one runs. Click-initiated runs stamp the shared click
     * cooldown at resolution, so the full cooldown runs after the refresh;
     * they close with the refresh click sound while automatic runs stay
     * silent at the end.
     */
    void startAnalysis(Player holder, boolean fromClick) {
        UUID id = holder.getUniqueId();
        if (!analyzing.add(id)) {
            return;
        }
        long generation = generations.merge(id, 1L, Long::sum);
        double effectiveDelay = jitteredDelay(
                plugin.configService().getDouble("settings.compass.analyze.delay-seconds", 1.0),
                plugin.configService()
                        .getDouble("settings.compass.analyze.delay-deviation-seconds", 0.0),
                ThreadLocalRandom.current().nextDouble());
        runAnalysisDebuffs(holder, effectiveDelay);
        actionbars.put(id, messages.component("compass.analyzing-actionbar"));
        sounds.playSound(holder, "compass.analysis");
        long intervalTicks = analysisTickInterval(clampedSoundInterval(plugin.configService()
                .getDouble("settings.compass.analyze.sound-interval-seconds", 0.5)));
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
            if (fromClick) {
                sharedClicks.put(id, System.currentTimeMillis());
            }
            refresher.accept(holder);
            if (fromClick && holder.isOnline()) {
                sounds.playSound(holder, "compass.right-click");
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
                SettingRegistry.byPath("settings.compass.analyze.sound-interval-seconds");
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
        if (!plugin.configService().getBoolean("settings.compass.analyze.debuffs.enabled", false)) {
            return;
        }
        Role holderRole = playerStates.role(holder);
        if (!holderRole.isParticipant()) {
            return;
        }
        double delaySeconds = effectiveDelaySeconds;
        List<String> commands = new ArrayList<>(plugin.configService()
                .getStringList("settings.compass.analyze.debuffs.commands.player"));
        commands.addAll(plugin.configService().getStringList(
                "settings.compass.analyze.debuffs.commands." + holderRole.name().toLowerCase(Locale.ROOT)));
        Location location = holder.getLocation();
        for (String command : commands) {
            if (command.isBlank()) {
                continue;
            }
            try {
                ModifierTagScope scope = ModifierTagScope.executor(holder.getName(), plugin.logger()::warning);
                String parsed = CommandPlaceholders.replace(
                        CommandPlaceholders.withDuration(command, delaySeconds),
                        holder.getName(), location.getX(), location.getY(), location.getZ(), scope);
                if (parsed.startsWith("/")) {
                    parsed = parsed.substring(1);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            } catch (Exception exception) {
                plugin.logger().severe(
                        "Failed to run analysis debuff command '" + command + "'. Skipping..");
                exception.printStackTrace();
            }
        }
    }

    boolean analyzeEnabled(boolean auto) {
        return plugin.configService().getBoolean(auto
                ? "settings.compass.analyze.auto" : "settings.compass.analyze.right-click", false);
    }
}
