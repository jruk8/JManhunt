package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.lobby.SubLobby;
import com.jruk8.jmanhunt.player.Role;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import com.jruk8.jmanhunt.match.prestart.HeadstartState;

/**
 * Identity, roster, and mutable execution state for one running match.
 * Per-player data (roles, lives, sightings) stays in
 * {@link PlayerStateStore}; everything match-shaped lives here so matches
 * run concurrently without sharing timers or flags.
 */
public final class GameInstance {
    private final long matchId;
    private final int originLobbyId;
    private final OptionalLong cellIndex;
    /** Re-anchors the match clock, used once when the game begins. */
    @Setter
    private long startedAtMillis;
    @Setter
    private SubLobby subLobby;
    private final Set<UUID> assigned = new HashSet<>();
    private final Set<UUID> activeParticipants = new HashSet<>();
    @Setter
    private boolean active = true;
    @Setter
    private boolean begun;
    @Setter
    private boolean ending;
    @Setter
    private boolean endPhaseDone;
    @Setter
    private boolean endStatsShown;
    private final HeadstartState hunterHeadstart = new HeadstartState();
    private final HeadstartState runnerHeadstart = new HeadstartState();
    @Setter
    private BukkitTask waitingReminderTask;
    @Setter
    private BukkitTask waitingExpiryTask;
    @Setter
    private int waitingDelayConfigured;
    @Setter
    private long waitingStartTime;
    @Setter
    private BukkitTask timeLimitTask;
    private final java.util.Set<Long> timeAnnounced = new java.util.HashSet<>();
    @Setter
    private boolean runnerUnlimitedAnnounced;
    @Setter
    private boolean hunterUnlimitedAnnounced;
    @Setter
    private Location startCenter;
    private long cachedElapsedMillis;
    /** Modifiers already ON_START-fired by mid-match toggles this match. */
    private final Set<String> toggleStartedModifiers = new HashSet<>();
    /** Modifiers already cleaned by mid-match toggles this match. */
    private final Set<String> toggleCleanedModifiers = new HashSet<>();
    /** Permanent deaths in death order (oldest first). */
    private final List<DeadPlayer> deadPlayers = new ArrayList<>();
    /** Players whose ON_START sequence already ran this match. */
    private final Set<UUID> startFired = new HashSet<>();
    /** Per-player death count: the current life index, 0 before any death. */
    private final Map<UUID, Integer> lifeSequence = new HashMap<>();
    /** Life index ON_RESPAWN last fired for, per player. */
    private final Map<UUID, Integer> respawnFiredLife = new HashMap<>();

    public GameInstance(long matchId, int originLobbyId, OptionalLong cellIndex, long startedAtMillis) {
        this.matchId = matchId;
        this.originLobbyId = originLobbyId;
        this.cellIndex = cellIndex;
        this.startedAtMillis = startedAtMillis;
    }

    /** Incrementing match number, unique per server run. */
    public long matchId() {
        return matchId;
    }

    /** Lobby the match was started from; players return to its location. */
    public int originLobbyId() {
        return originLobbyId;
    }

    /** World-engine cell backing the match, accepted as an alias for the match id; empty when the engine is off. */
    public OptionalLong cellIndex() {
        return cellIndex;
    }

    public long startedAtMillis() {
        return startedAtMillis;
    }

    /**
     * Sublobby this match runs as, or null when the parent lobby hosts
     * directly (engine off, or a non-SUBLOBBY policy).
     */
    public SubLobby subLobby() {
        return subLobby;
    }

    /** Lobby tag for status output: L{id}, or L{id}-{sub} when sublobbed. */
    public String lobbyTag() {
        return subLobby == null ? "L" + originLobbyId : subLobby.format();
    }

    /** Records players assigned to this instance (grows, never shrinks). */
    public void assignAll(Iterable<UUID> playerIds) {
        for (UUID playerId : playerIds) {
            assigned.add(playerId);
        }
    }

    /** Every player ever assigned, including the eliminated and disconnected. */
    public Set<UUID> assignedPlayerIds() {
        return Collections.unmodifiableSet(new HashSet<>(assigned));
    }

    public int assignedCount() {
        return assigned.size();
    }

    /** One permanent death: who, under which name, as which role. */
    public record DeadPlayer(UUID playerId, String name, Role formerRole) {
    }

    /**
     * Records a permanent death in death order; repeats for the same
     * player are ignored. Kept for the status ghost line and
     * end-of-match statistics.
     */
    public void recordDeath(UUID playerId, String name, Role formerRole) {
        for (DeadPlayer dead : deadPlayers) {
            if (dead.playerId().equals(playerId)) {
                return;
            }
        }
        deadPlayers.add(new DeadPlayer(playerId, name, formerRole));
    }

    /** Permanent deaths in death order (oldest first). */
    public List<DeadPlayer> deadPlayers() {
        return List.copyOf(deadPlayers);
    }

    /** Marks a player as an active participant (also records the assignment). */
    public void activate(UUID playerId) {
        assigned.add(playerId);
        activeParticipants.add(playerId);
    }

    /** Drops a player from the active roster; the assignment record stays. */
    public void deactivate(UUID playerId) {
        activeParticipants.remove(playerId);
    }

    public boolean isActive(UUID playerId) {
        return activeParticipants.contains(playerId);
    }

    public Set<UUID> activeIds() {
        return Collections.unmodifiableSet(new HashSet<>(activeParticipants));
    }

    /** True while the match runs, including its end-delay phase. */
    public boolean active() {
        return active;
    }

    public boolean begun() {
        return begun;
    }

    public boolean ending() {
        return ending;
    }

    /**
     * Marks one modifier ON_START-fired by a mid-match toggle. Returns
     * false when it already fired this match (repeat toggle to skip).
     */
    public boolean markModifierStarted(String name) {
        return toggleStartedModifiers.add(name);
    }

    /**
     * Marks one modifier cleaned by a mid-match toggle. Returns false
     * when it already cleaned this match (repeat toggle to skip).
     */
    public boolean markModifierCleaned(String name) {
        return toggleCleanedModifiers.add(name);
    }

    /**
     * Marks one player ON_START-fired. Returns false when it already
     * fired for them this match (role-switch catch-up to skip).
     */
    public boolean markStartFired(UUID playerId) {
        return startFired.add(playerId);
    }

    /** Advances one player to their next life after a death. */
    public void noteDeath(UUID playerId) {
        lifeSequence.merge(playerId, 1, Integer::sum);
    }

    /** Current life index: death count, 0 before any death. */
    public int lifeOf(UUID playerId) {
        return lifeSequence.getOrDefault(playerId, 0);
    }

    /**
     * Marks ON_RESPAWN fired for one life. Returns false when it
     * already fired for that life (role-switch catch-up to skip).
     */
    public boolean markRespawnFired(UUID playerId, int life) {
        if (respawnFiredLife.getOrDefault(playerId, -1) == life) {
            return false;
        }
        respawnFiredLife.put(playerId, life);
        return true;
    }

    public boolean endPhaseDone() {
        return endPhaseDone;
    }

    public boolean endStatsShown() {
        return endStatsShown;
    }

    public HeadstartState hunterHeadstart() {
        return hunterHeadstart;
    }

    public HeadstartState runnerHeadstart() {
        return runnerHeadstart;
    }

    public HeadstartState headstart(Role role) {
        return role == Role.SPEEDRUNNER ? runnerHeadstart : hunterHeadstart;
    }

    /** True while either headstart holds the player at a return point. */
    public boolean isHeadstartHeld(UUID playerId) {
        return hunterHeadstart.returnPoints().containsKey(playerId)
                || runnerHeadstart.returnPoints().containsKey(playerId);
    }

    public BukkitTask waitingReminderTask() {
        return waitingReminderTask;
    }

    public BukkitTask waitingExpiryTask() {
        return waitingExpiryTask;
    }

    public int waitingDelayConfigured() {
        return waitingDelayConfigured;
    }

    public long waitingStartTime() {
        return waitingStartTime;
    }

    public BukkitTask timeLimitTask() {
        return timeLimitTask;
    }

    /** Announced countdown thresholds, in whole seconds remaining. */
    public java.util.Set<Long> timeAnnounced() {
        return timeAnnounced;
    }

    /** Whether the side's unlimited-lives line has fired this match. */
    public boolean runnerUnlimitedAnnounced() {
        return runnerUnlimitedAnnounced;
    }

    /** Whether the side's unlimited-lives line has fired this match. */
    public boolean hunterUnlimitedAnnounced() {
        return hunterUnlimitedAnnounced;
    }

    /** Elapsed millis between match start and the given moment; frozen once the match ends. */
    public long elapsedMillis(long nowMillis) {
        if (ending) {
            return cachedElapsedMillis;
        }
        return Math.max(0L, nowMillis - startedAtMillis);
    }

    /** Whole seconds between match start and the given moment; frozen once the match ends. */
    public long elapsedSeconds(long nowMillis) {
        return elapsedMillis(nowMillis) / 1000L;
    }

    /**
     * Caches the current elapsed millis so the end intermission reads a
     * frozen clock. Ignored once the match ends; the end paths stamp
     * the exact value right before setting the flag.
     */
    public void refreshElapsedCache(long nowMillis) {
        if (!ending) {
            cachedElapsedMillis = Math.max(0L, nowMillis - startedAtMillis);
        }
    }

    /**
     * Engine-off start center: participants scatter around it at match
     * start and again at match end. Null with the engine on.
     */
    public Location startCenter() {
        return startCenter;
    }

}
