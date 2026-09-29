package com.jruk8.jmanhunt.match.autostart;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.NumberWords;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.world.WorldEngineService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.jruk8.jmanhunt.match.lifecycle.MatchControl;
import com.jruk8.jmanhunt.match.lifecycle.MatchMessaging;
import com.jruk8.jmanhunt.match.lifecycle.MatchStore;

/**
 * Watches lobby queues and starts matches on their own: per-lobby countdowns
 * while a queue stays eligible, plus the periodic shortfall nag that tells
 * queued players which roles are still missing.
 */
public final class AutostartService {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final PlayerStateStore playerStates;
    private final LobbyService lobbies;
    private final WorldEngineService worldEngine;
    private final MatchStore store;
    private final MatchMessaging messaging;
    private final MatchControl control;
    /** Per-lobby autostart countdowns, keyed by lobby id. */
    private final Map<Integer, AutostartCountdown> autostartCountdowns = new HashMap<>();
    /** Last shortfall broadcast per lobby, for the needs-more interval. */
    private final Map<Integer, Long> lastShortfallBroadcast = new HashMap<>();
    /** Last-seen hunter/speedrunner sets per lobby, for nag seeding. */
    private final Map<Integer, Set<UUID>> lastNagTeams = new HashMap<>();

    /** Mutable per-lobby countdown state; the task ticks in GameManager. */
    private static final class AutostartCountdown {
        BukkitTask task;
        int remaining;
        int configured;
    }

    public AutostartService(JManhuntPlugin plugin, MessageService messages, PlayerStateStore playerStates,
            LobbyService lobbies, WorldEngineService worldEngine, MatchStore store,
            MatchMessaging messaging, MatchControl control) {
        this.plugin = plugin;
        this.messages = messages;
        this.playerStates = playerStates;
        this.lobbies = lobbies;
        this.worldEngine = worldEngine;
        this.store = store;
        this.messaging = messaging;
        this.control = control;
    }

    public void updateAutostartState() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> updateAutostartState());
            return;
        }
        pruneAutostartCountdowns();
        for (int lobbyId : lobbies.lobbyIds()) {
            if (!lobbies.multiLobbyAllowed() && lobbyId != 0) {
                continue;
            }
            updateAutostartState(lobbyId);
        }
    }

    /** Drops countdowns for deleted lobbies (and non-zero lobbies with the engine off). */
    private void pruneAutostartCountdowns() {
        for (int lobbyId : List.copyOf(autostartCountdowns.keySet())) {
            if (lobbies.get(lobbyId).isEmpty() || (!lobbies.multiLobbyAllowed() && lobbyId != 0)) {
                cancelAutostartCountdown(lobbyId, false);
            }
        }
    }

    private void updateAutostartState(int lobbyId) {
        Optional<Lobby> lobby = lobbies.get(lobbyId);
        if (lobby.isEmpty() || store.instanceForLobby(lobbyId).isPresent()
                || !isEligibleToStart(lobby.get())) {
            cancelAutostartCountdown(lobbyId, true);
            return;
        }
        if (!plugin.overrides().getBoolean(lobbyId, "settings.match.autostart.enabled", false)) {
            cancelAutostartCountdown(lobbyId, true);
            return;
        }
        if (autostartCountdowns.containsKey(lobbyId)) {
            return;
        }
        int configured = Math.max(0, plugin.overrides()
                .getInt(lobbyId, "settings.match.autostart.countdown-seconds", 60));
        if (configured == 0) {
            control.start(lobbyId);
            return;
        }
        worldEngine.prepareNextCell();
        AutostartCountdown countdown = new AutostartCountdown();
        countdown.configured = configured;
        countdown.remaining = configured;
        autostartCountdowns.put(lobbyId, countdown);
        messaging.sendToLobby(lobbyId, "manhunt.autostart-eligible",
                Map.of("seconds", String.valueOf(configured)));
        messaging.playLobbySound(lobbyId, "game.autostart-countdown");
        // Eligible covered these seconds already; ticks announce the rest.
        countdown.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Optional<Lobby> tickLobby = lobbies.get(lobbyId);
            if (store.instanceForLobby(lobbyId).isPresent() || tickLobby.isEmpty()
                    || !isEligibleToStart(tickLobby.get())) {
                cancelAutostartCountdown(lobbyId, true);
                return;
            }
            countdown.remaining--;
            if (countdown.remaining <= 0) {
                cancelAutostartCountdown(lobbyId, false);
                control.start(lobbyId);
                return;
            }
            announceAutostartCheckpoint(lobbyId, countdown, countdown.remaining);
        }, 20L, 20L);
    }

    private void announceAutostartCheckpoint(int lobbyId, AutostartCountdown countdown, int remainingSeconds) {
        if (!AutostartCountdownMessages.shouldAnnounce(remainingSeconds, countdown.configured)) {
            return;
        }
        messaging.sendToLobby(lobbyId, "manhunt.autostart-countdown",
                Map.of("seconds", String.valueOf(remainingSeconds)));
        messaging.playLobbySound(lobbyId, "game.autostart-countdown");
    }

    public void cancelAutostartCountdown(int lobbyId, boolean announce) {
        AutostartCountdown countdown = autostartCountdowns.remove(lobbyId);
        if (countdown != null) {
            if (countdown.task != null) {
                countdown.task.cancel();
            }
            if (announce) {
                messaging.sendToLobby(lobbyId, "manhunt.autostart-cancelled", Map.of());
                messaging.playLobbySound(lobbyId, "game.autostart-cancelled");
            }
        }
    }

    public void cancelAllAutostartCountdowns(boolean announce) {
        for (int lobbyId : List.copyOf(autostartCountdowns.keySet())) {
            cancelAutostartCountdown(lobbyId, announce);
        }
    }

    private boolean isEligibleToStart(Lobby lobby) {
        return autostartEligible(shortfallFor(lobby), overfillFor(lobby));
    }

    /**
     * True when a lobby may autostart: no role below its minimum and
     * no role above its maximum. Pure for tests.
     */
    public static boolean autostartEligible(Map<Role, Integer> missing, Map<Role, Integer> excess) {
        return missing.isEmpty() && excess.isEmpty();
    }

    /** Online hunters then speedrunners queued in a lobby, as {hunters, speedrunners}. */
    private int[] countQueuedRoles(Lobby lobby) {
        int hunters = 0;
        int speedrunners = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!lobby.contains(player.getUniqueId())) {
                continue;
            }
            if (playerStates.role(player) == Role.HUNTER) {
                hunters++;
            } else if (playerStates.role(player) == Role.SPEEDRUNNER) {
                speedrunners++;
            }
        }
        return new int[] {hunters, speedrunners};
    }

    /** Per-role shortfall of a lobby's online members against the autostart minimums. */
    private Map<Role, Integer> shortfallFor(Lobby lobby) {
        int[] counts = countQueuedRoles(lobby);
        return autostartShortfall(counts[0], counts[1],
                plugin.overrides().getInt(lobby.id(),
                        "settings.match.autostart.minimums.hunter", 1),
                plugin.overrides().getInt(lobby.id(),
                        "settings.match.autostart.minimums.speedrunner", 1));
    }

    /** Per-role overfill of a lobby's online members against the autostart maximums. */
    private Map<Role, Integer> overfillFor(Lobby lobby) {
        int[] counts = countQueuedRoles(lobby);
        return autostartOverfill(counts[0], counts[1],
                plugin.overrides().getInt(lobby.id(),
                        "settings.match.autostart.maximums.hunter", -1),
                plugin.overrides().getInt(lobby.id(),
                        "settings.match.autostart.maximums.speedrunner", -1));
    }

    /**
     * Roles still missing queued players against the autostart minimums,
     * mapped to how many more each needs. Empty means eligible to start.
     * Minimums clamp to a hard minimum of 1 per role. Pure for tests.
     */
    public static Map<Role, Integer> autostartShortfall(int hunters, int speedrunners,
            int minHunters, int minSpeedrunners) {
        int needHunters = Math.max(1, minHunters);
        int needSpeedrunners = Math.max(1, minSpeedrunners);
        Map<Role, Integer> missing = new EnumMap<>(Role.class);
        if (hunters < needHunters) {
            missing.put(Role.HUNTER, needHunters - hunters);
        }
        if (speedrunners < needSpeedrunners) {
            missing.put(Role.SPEEDRUNNER, needSpeedrunners - speedrunners);
        }
        return missing;
    }

    /**
     * Roles with more queued players than the autostart maximums,
     * mapped to the excess count. A negative maximum disables that
     * role. Empty means no role is over its maximum. Pure for tests.
     */
    public static Map<Role, Integer> autostartOverfill(int hunters, int speedrunners,
            int maxHunters, int maxSpeedrunners) {
        Map<Role, Integer> excess = new EnumMap<>(Role.class);
        if (maxHunters >= 0 && hunters > maxHunters) {
            excess.put(Role.HUNTER, hunters - maxHunters);
        }
        if (maxSpeedrunners >= 0 && speedrunners > maxSpeedrunners) {
            excess.put(Role.SPEEDRUNNER, speedrunners - maxSpeedrunners);
        }
        return excess;
    }

    /**
     * Tells queued hunters and speedrunners of ineligible lobbies how
     * many more of each role autostart needs, at most once per
     * configured interval. Runs every second from the plugin scheduler;
     * eligible, counting-down, and in-match lobbies are skipped and
     * reset so the next shortfall announces immediately. Players only:
     * the console is spared the nag, as are none, afk, and spectator
     * members.
     */
    public void broadcastAutostartShortfalls() {
        long now = System.currentTimeMillis();
        for (int lobbyId : lobbies.lobbyIds()) {
            broadcastLobbyShortfall(lobbyId, now);
        }
        lastShortfallBroadcast.keySet().removeIf(id -> lobbies.get(id).isEmpty());
        lastNagTeams.keySet().removeIf(id -> lobbies.get(id).isEmpty());
    }

    /** Nags one lobby about unmet autostart requirements when the interval is due. */
    private void broadcastLobbyShortfall(int lobbyId, long now) {
        if (!lobbies.multiLobbyAllowed() && lobbyId != 0) {
            return;
        }
        if (!plugin.overrides().getBoolean(lobbyId, "settings.match.autostart.enabled", false)) {
            return;
        }
        if (!plugin.overrides().getBoolean(lobbyId,
                "settings.match.autostart.broadcast-requirements.enabled", false)) {
            return;
        }
        int intervalSeconds = Math.max(1, plugin.overrides().getInt(lobbyId,
                "settings.match.autostart.broadcast-requirements.interval-seconds", 60));
        Optional<Lobby> lobby = lobbies.get(lobbyId);
        if (lobby.isEmpty() || store.instanceForLobby(lobbyId).isPresent()
                || autostartCountdowns.containsKey(lobbyId)) {
            lastShortfallBroadcast.remove(lobbyId);
            return;
        }
        Set<UUID> teams = teamComposition(lobby.get());
        Set<UUID> previous = lastNagTeams.put(lobbyId, teams);
        if (teamGrew(previous, teams)) {
            // A player joined the teams: the nag waits a full
            // interval from the assignment instead of firing now.
            lastShortfallBroadcast.put(lobbyId, now);
            return;
        }
        Map<Role, Integer> missing = shortfallFor(lobby.get());
        Map<Role, Integer> excess = overfillFor(lobby.get());
        List<Player> recipients = messaging.lobbyRecipients(lobbyId).stream()
                .filter(player -> receivesShortfall(playerStates.role(player)))
                .toList();
        if ((missing.isEmpty() && excess.isEmpty()) || recipients.isEmpty()) {
            lastShortfallBroadcast.remove(lobbyId);
            return;
        }
        Long last = lastShortfallBroadcast.get(lobbyId);
        if (!nagDue(now, last, intervalSeconds)) {
            return;
        }
        lastShortfallBroadcast.put(lobbyId, now);
        sendRequirementNag(recipients, missing, excess);
    }

    /** Sends the too-many nag when overfilled, else the needs-more nag. */
    private void sendRequirementNag(List<Player> recipients,
            Map<Role, Integer> missing, Map<Role, Integer> excess) {
        if (!excess.isEmpty()) {
            messages.sendTo(recipients, "manhunt.autostart-too-many",
                    Map.of("details", countDetails(excess, "extra")));
            return;
        }
        messages.sendTo(recipients, "manhunt.autostart-needs-more",
                Map.of("details", countDetails(missing, "more")));
    }

    /** Online hunters and speedrunners of a lobby, for nag seeding. */
    private Set<UUID> teamComposition(Lobby lobby) {
        Set<UUID> teams = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!lobby.contains(player.getUniqueId())) {
                continue;
            }
            if (receivesShortfall(playerStates.role(player))) {
                teams.add(player.getUniqueId());
            }
        }
        return teams;
    }

    /**
     * True when the team set gained members since the last sighting. A
     * null previous set (first sighting, e.g. after a restart) counts
     * as no growth, so the nag still fires immediately. Pure for tests.
     */
    public static boolean teamGrew(Set<UUID> previous, Set<UUID> current) {
        return previous != null && current.stream().anyMatch(id -> !previous.contains(id));
    }

    /**
     * True when the shortfall nag may fire: never broadcast, or a full
     * interval elapsed since the last one. Pure for tests.
     */
    public static boolean nagDue(long now, Long lastBroadcast, int intervalSeconds) {
        return lastBroadcast == null || now - lastBroadcast >= intervalSeconds * 1000L;
    }

    /** "two more Hunters and one more Speedrunner" for a role count map, role-colored. */
    private String countDetails(Map<Role, Integer> counts, String middle) {
        List<String> parts = new ArrayList<>();
        for (Role role : List.of(Role.HUNTER, Role.SPEEDRUNNER)) {
            Integer count = counts.get(role);
            if (count == null) {
                continue;
            }
            parts.add(countPart(NumberWords.word(count), messages.roleName(role), count, middle));
        }
        return String.join(" and ", parts);
    }

    /**
     * True for the roles the autostart shortfall nag goes to: assigned
     * hunters and speedrunners only. Pure for tests.
     */
    public static boolean receivesShortfall(Role role) {
        return role == Role.HUNTER || role == Role.SPEEDRUNNER;
    }

    /**
     * One "two more Hunters" shortfall fragment. The role color is
     * closed back to the message's yellow so it cannot bleed into the
     * separator or the sentence tail. Pure for tests.
     */
    public static String shortfallPart(String countWord, String coloredRoleName, int need) {
        return countPart(countWord, coloredRoleName, need, "more");
    }

    /**
     * One "two extra Hunters" overfill fragment, same coloring rules
     * as the shortfall fragment. Pure for tests.
     */
    public static String overfillPart(String countWord, String coloredRoleName, int excess) {
        return countPart(countWord, coloredRoleName, excess, "extra");
    }

    /**
     * One role-count fragment with a caller-chosen middle word ("more"
     * for shortfalls, "extra" for overfills). Pure for tests.
     */
    public static String countPart(String countWord, String coloredRoleName, int count, String middle) {
        return "<white>" + countWord + "</white> " + middle + " " + coloredRoleName
                + (count == 1 ? "" : "s") + "<yellow>";
    }
}
