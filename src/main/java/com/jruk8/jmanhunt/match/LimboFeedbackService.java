package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.config.DurationFormat;
import com.jruk8.jmanhunt.command.TagCooldownStore;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.config.MatchSettingsFacade;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.match.lifecycle.MatchStore;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Limbo feedback for players waiting to spawn: headstart-held plus
 * join and respawn holds. Ticks ride the shared countdown (every
 * hold and headstart tick calls in); the multi broadcast gates on
 * the cooldown store, so no extra scheduler runs.
 */
public final class LimboFeedbackService {
    /** Store, states, settings, cooldowns, and the flush scheduler. */
    public record LimboReads(MatchStore store, PlayerStateStore states, MatchSettingsFacade match,
            TagCooldownStore cooldowns, TaskScheduler tasks) {
    }

    /** Messages plus match messaging. */
    public record LimboTexts(MessageService messages, ManhuntMessages manhunt,
            MatchMessaging messaging) {
    }

    private static final String MULTI_KEY = "limbo-multi";

    /** One queued spawn notice, keyed by match plus player for dedupe. */
    private record SpawnKey(long matchId, UUID playerId) {
    }

    /** Queued spawn notice: identity plus the role grouping key. */
    private record SpawnNotice(long matchId, UUID playerId, String playerName, Role role) {
    }

    private final LimboReads reads;
    private final LimboTexts texts;
    private final Set<UUID> pendingHolds = new HashSet<>();
    private final Map<SpawnKey, SpawnNotice> pendingSpawns = new LinkedHashMap<>();
    private boolean spawnFlushScheduled;

    public LimboFeedbackService(LimboReads reads, LimboTexts texts) {
        this.reads = reads;
        this.texts = texts;
    }

    /** Tracks a join hold or respawn delay for the limbo census. */
    public void trackHold(UUID playerId) {
        pendingHolds.add(playerId);
    }

    /** Drops a finished hold from the census. */
    public void untrackHold(UUID playerId) {
        pendingHolds.remove(playerId);
    }

    /** Drops every tracked hold. */
    public void clearHolds() {
        pendingHolds.clear();
    }

    /** Tracked hold ids, for bulk cancels. */
    public Set<UUID> pendingHoldIds() {
        return Set.copyOf(pendingHolds);
    }

    /**
     * One limbo tick for the match: the source countdown passes its
     * mark decision, so marks send every waiting player their personal
     * return note plus the solo broadcast when exactly one waits; two
     * or more waiting broadcast on the multi interval through the
     * cooldown store. Waiting players hear only their personal note:
     * broadcasts skip them (sounds still play match-wide as the cue).
     */
    public void limboTick(GameInstance instance, int remaining, boolean mark) {
        if (!instance.active()) {
            return;
        }
        List<Player> waiting = waitingPlayers(instance);
        if (waiting.isEmpty()) {
            return;
        }
        if (mark) {
            String time = DurationFormat.format(remaining);
            for (Player player : waiting) {
                texts.messages().sendToRaw(List.of(player), texts.manhunt().getLimboSelf(),
                        Map.of("time", time));
            }
            if (waiting.size() == 1) {
                Player player = waiting.get(0);
                texts.messaging().sendToInstanceExcept(instance, texts.manhunt().getLimboSingle(),
                        Map.of("rolecolor",
                                texts.messages().roleColor(reads.states().role(player)),
                                "player", player.getName(), "time", time),
                        Set.of(player.getUniqueId()));
                texts.messaging().playInstanceSound(instance, "game.autostart-countdown");
            }
        }
        if (waiting.size() >= 2) {
            int interval = reads.match().limboMultiBroadcastInterval(instance.originLobbyId());
            if (reads.cooldowns().tryAcquire(instance.matchId(), MULTI_KEY, interval)) {
                texts.messaging().sendToInstanceExcept(instance, texts.manhunt().getLimboMulti(),
                        Map.of("total", Integer.toString(waiting.size())), waitingIds(waiting));
                texts.messaging().playInstanceSound(instance, "game.autostart-countdown");
            }
        }
    }

    /** Ids of every player waiting to spawn, for broadcast exclusion. */
    public Set<UUID> waitingIds(GameInstance instance) {
        Set<UUID> held = new HashSet<>();
        held.addAll(instance.headstart(Role.HUNTER).returnPoints().keySet());
        held.addAll(instance.headstart(Role.SPEEDRUNNER).returnPoints().keySet());
        held.addAll(pendingHolds);
        return held;
    }

    /**
     * Queues one player's spawn notice for the next-tick flush, so
     * same-tick spawns collapse into one line per role instead of
     * naming everyone. The flush re-checks liveness, so a player
     * leaving within the tick stays silent.
     */
    public void announceSpawned(GameInstance instance, Player player) {
        pendingSpawns.put(new SpawnKey(instance.matchId(), player.getUniqueId()),
                new SpawnNotice(instance.matchId(), player.getUniqueId(), player.getName(),
                        reads.states().role(player)));
        if (!spawnFlushScheduled) {
            spawnFlushScheduled = true;
            reads.tasks().run(this::flushSpawns);
        }
    }

    /**
     * Flushes queued spawn notices: one line per role per match, the
     * name line for solo spawns and the count line once two or more
     * of a role spawn on the same tick. Skips dead matches and
     * players no longer active.
     */
    private void flushSpawns() {
        spawnFlushScheduled = false;
        if (pendingSpawns.isEmpty()) {
            return;
        }
        Map<Long, List<SpawnNotice>> byMatch = new LinkedHashMap<>();
        for (SpawnNotice notice : pendingSpawns.values()) {
            byMatch.computeIfAbsent(notice.matchId(), match -> new ArrayList<>()).add(notice);
        }
        pendingSpawns.clear();
        for (Map.Entry<Long, List<SpawnNotice>> entry : byMatch.entrySet()) {
            GameInstance instance = reads.store().instance(entry.getKey()).orElse(null);
            if (instance == null || !instance.active()) {
                continue;
            }
            Map<Role, List<SpawnNotice>> byRole = new LinkedHashMap<>();
            for (SpawnNotice notice : entry.getValue()) {
                if (instance.isActive(notice.playerId())) {
                    byRole.computeIfAbsent(notice.role(), role -> new ArrayList<>()).add(notice);
                }
            }
            for (Map.Entry<Role, List<SpawnNotice>> group : byRole.entrySet()) {
                announceSpawnGroup(instance, group.getKey(), group.getValue());
            }
        }
    }

    /** One spawn line for a role group: named solo, counted multi. */
    private void announceSpawnGroup(GameInstance instance, Role role, List<SpawnNotice> group) {
        String color = texts.messages().roleColor(role);
        if (group.size() == 1) {
            texts.messaging().sendToInstance(instance, texts.manhunt().getLimboSpawned(),
                    Map.of("rolecolor", color, "player", group.get(0).playerName()));
            return;
        }
        texts.messaging().sendToInstance(instance, texts.manhunt().getLimboSpawnedMulti(),
                Map.of("rolecolor", color, "count", Integer.toString(group.size()),
                        "role", role.displayName().toLowerCase(Locale.ROOT)));
    }

    /** Ids of the given waiting players, for broadcast exclusion. */
    private static Set<UUID> waitingIds(List<Player> waiting) {
        Set<UUID> ids = new HashSet<>();
        for (Player player : waiting) {
            ids.add(player.getUniqueId());
        }
        return ids;
    }

    /** Online, active players waiting to spawn: headstart-held plus holds. */
    private List<Player> waitingPlayers(GameInstance instance) {
        Set<UUID> held = waitingIds(instance);
        List<Player> waiting = new ArrayList<>();
        for (Player player : reads.store().onlineAssignedPlayers(instance)) {
            if (held.contains(player.getUniqueId()) && instance.isActive(player.getUniqueId())) {
                waiting.add(player);
            }
        }
        return waiting;
    }
}
