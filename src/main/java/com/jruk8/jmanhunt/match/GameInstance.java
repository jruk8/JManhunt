package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.lobby.SubLobby;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import java.util.Collections;
import java.util.HashSet;
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
    private long startedAtMillis;
    private SubLobby subLobby;
    private final Set<UUID> assigned = new HashSet<>();
    private final Set<UUID> activeParticipants = new HashSet<>();
    private boolean active = true;
    private boolean begun;
    private boolean ending;
    private boolean endPhaseDone;
    private boolean endStatsShown;
    private final HeadstartState hunterHeadstart = new HeadstartState();
    private final HeadstartState runnerHeadstart = new HeadstartState();
    private BukkitTask waitingReminderTask;
    private BukkitTask waitingExpiryTask;
    private int waitingDelayConfigured;
    private long waitingStartTime;
    private BukkitTask timeLimitTask;
    private final java.util.Set<Long> timeAnnounced = new java.util.HashSet<>();
    private boolean runnerUnlimitedAnnounced;
    private boolean hunterUnlimitedAnnounced;
    private Location startCenter;


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

    /** Re-anchors the match clock, used once when the game begins. */
    public void setStartedAtMillis(long startedAtMillis) {
        this.startedAtMillis = startedAtMillis;
    }

    /**
     * Sublobby this match runs as, or null when the parent lobby hosts
     * directly (engine off, or a non-SUBLOBBY policy).
     */
    public SubLobby subLobby() {
        return subLobby;
    }

    public void setSubLobby(SubLobby subLobby) {
        this.subLobby = subLobby;
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

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean begun() {
        return begun;
    }

    public void setBegun(boolean begun) {
        this.begun = begun;
    }

    public boolean ending() {
        return ending;
    }

    public void setEnding(boolean ending) {
        this.ending = ending;
    }

    public boolean endPhaseDone() {
        return endPhaseDone;
    }

    public void setEndPhaseDone(boolean endPhaseDone) {
        this.endPhaseDone = endPhaseDone;
    }

    public boolean endStatsShown() {
        return endStatsShown;
    }

    public void setEndStatsShown(boolean endStatsShown) {
        this.endStatsShown = endStatsShown;
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

    public BukkitTask waitingReminderTask() {
        return waitingReminderTask;
    }

    public void setWaitingReminderTask(BukkitTask waitingReminderTask) {
        this.waitingReminderTask = waitingReminderTask;
    }

    public BukkitTask waitingExpiryTask() {
        return waitingExpiryTask;
    }

    public void setWaitingExpiryTask(BukkitTask waitingExpiryTask) {
        this.waitingExpiryTask = waitingExpiryTask;
    }

    public int waitingDelayConfigured() {
        return waitingDelayConfigured;
    }

    public void setWaitingDelayConfigured(int waitingDelayConfigured) {
        this.waitingDelayConfigured = waitingDelayConfigured;
    }

    public long waitingStartTime() {
        return waitingStartTime;
    }

    public void setWaitingStartTime(long waitingStartTime) {
        this.waitingStartTime = waitingStartTime;
    }

    public BukkitTask timeLimitTask() {
        return timeLimitTask;
    }

    public void setTimeLimitTask(BukkitTask timeLimitTask) {
        this.timeLimitTask = timeLimitTask;
    }

    /** Announced countdown thresholds, in whole seconds remaining. */
    public java.util.Set<Long> timeAnnounced() {
        return timeAnnounced;
    }

    /** Whether the side's unlimited-lives line has fired this match. */
    public boolean runnerUnlimitedAnnounced() {
        return runnerUnlimitedAnnounced;
    }

    public void setRunnerUnlimitedAnnounced(boolean announced) {
        this.runnerUnlimitedAnnounced = announced;
    }

    /** Whether the side's unlimited-lives line has fired this match. */
    public boolean hunterUnlimitedAnnounced() {
        return hunterUnlimitedAnnounced;
    }

    public void setHunterUnlimitedAnnounced(boolean announced) {
        this.hunterUnlimitedAnnounced = announced;
    }

    /** Whole seconds between match start and the given moment. */
    public long elapsedSeconds(long nowMillis) {
        return Math.max(0L, (nowMillis - startedAtMillis) / 1000L);
    }

    /**
     * Engine-off start center: participants scatter around it at match
     * start and again at match end. Null with the engine on.
     */
    public Location startCenter() {
        return startCenter;
    }

    public void setStartCenter(Location startCenter) {
        this.startCenter = startCenter;
    }
}
