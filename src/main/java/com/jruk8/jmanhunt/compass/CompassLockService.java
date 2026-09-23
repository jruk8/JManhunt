package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.command.CommandPlaceholders;
import com.jruk8.jmanhunt.command.ModifierTagScope;
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
    private GameManager game;

    CompassLockService(JManhuntPlugin plugin, PlayerStateStore playerStates, SoundService sounds,
            MessageService messages, CompassTargetService targets, CompassSignalService signal,
            Map<UUID, Component> actionbars, Consumer<Player> refresher) {
        this.plugin = plugin;
        this.playerStates = playerStates;
        this.sounds = sounds;
        this.messages = messages;
        this.targets = targets;
        this.signal = signal;
        this.actionbars = actionbars;
        this.refresher = refresher;
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
        int maxTargets = plugin.getConfig()
                .getInt("settings.compass.left-click.max-targets", 5);
        if (CompassPick.orderedCandidates(opponents, sightings, maxTargets).size() <= 1) {
            locks.remove(player.getUniqueId());
            refresher.accept(player);
            return;
        }
        if (signal.badSignalForScroll(player, opponents, sightings)) {
            refresher.accept(player);
            return;
        }
        applyScrollCycle(player, opponents, sightings, maxTargets);
        refresher.accept(player);
    }

    /** Resolves the scroll match, applying the enabled, cooldown, and membership gates. */
    private Optional<GameInstance> scrollMatch(Player player, long now) {
        if (!plugin.getConfig()
                .getBoolean("settings.compass.left-click.enabled", false)) {
            return Optional.empty();
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return Optional.empty();
        }
        if (analyzing.contains(player.getUniqueId())) {
            return Optional.empty();
        }
        long cooldownMs = (long) (Math.max(0.0, plugin.getConfig()
                .getDouble("settings.compass.left-click.scroll-cooldown", 0.5)) * 1000);
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
     * "Analyzing...", waits out the configured delay, then refreshes.
     * No second analysis starts while one runs. The caller stamps the
     * universal refresh clock at analysis start, so cooldowns run from
     * the click (or auto fire), not from resolution.
     */
    void startAnalysis(Player holder) {
        UUID id = holder.getUniqueId();
        if (!analyzing.add(id)) {
            return;
        }
        runAnalysisDebuffs(holder);
        actionbars.put(id, messages.component("compass.analyzing-actionbar"));
        long delayTicks = analyzeDelayTicks(
                plugin.getConfig().getDouble("settings.compass.analyze.delay-seconds", 1.0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            analyzing.remove(id);
            refresher.accept(holder);
            boolean live = game != null && game.instanceOf(holder.getUniqueId()).isPresent();
            if (!live || !playerStates.role(holder).isParticipant()) {
                actionbars.remove(id);
            }
        }, delayTicks);
    }

    /** Analysis delay in ticks, at least one. Pure for tests. */
    static long analyzeDelayTicks(double delaySeconds) {
        return Math.max(1L, Math.round(delaySeconds * 20.0));
    }

    /**
     * Runs the configured analysis debuff commands for a participant
     * holder: the shared player list plus their own role list, resolved
     * modifier-style and dispatched as console.
     */
    private void runAnalysisDebuffs(Player holder) {
        if (!plugin.getConfig().getBoolean("settings.compass.analyze.debuffs.enabled", false)) {
            return;
        }
        Role holderRole = playerStates.role(holder);
        if (!holderRole.isParticipant()) {
            return;
        }
        double delaySeconds = plugin.getConfig()
                .getDouble("settings.compass.analyze.delay-seconds", 1.0);
        List<String> commands = new ArrayList<>(plugin.getConfig()
                .getStringList("settings.compass.analyze.debuffs.commands.player"));
        commands.addAll(plugin.getConfig().getStringList(
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
        return plugin.getConfig().getBoolean(auto
                ? "settings.compass.analyze.auto" : "settings.compass.analyze.right-click", false);
    }
}
