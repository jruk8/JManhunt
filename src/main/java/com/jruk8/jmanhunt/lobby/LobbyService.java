package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.LobbiesConfig;
import com.jruk8.jmanhunt.config.WorldEngineConfig;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Owns lobby membership. Unknown ids are created on join and empty lobbies
 * are deleted on leave; nothing persists across restarts.
 */
public final class LobbyService {
    public static final int MAX_LOBBY_ID = Integer.MAX_VALUE;

    /** Collisions toggle path, shared with the change subscription. */
    public static final String COLLISIONS_PATH = "advanced.lobbies.disable-player-collisions";

    private final JManhuntPlugin plugin;
    private final LobbiesConfig lobbySettings;
    private final WorldEngineConfig engineSettings;
    private final ManhuntMessages manhunt;
    private final Map<Integer, Lobby> lobbies = new HashMap<>();
    private final Map<UUID, Integer> membership = new HashMap<>();
    private final Map<Integer, Integer> nextSubIds = new HashMap<>();

    public LobbyService(JManhuntPlugin plugin, LobbiesConfig lobbySettings,
            WorldEngineConfig engineSettings, ManhuntMessages manhunt) {
        this.plugin = plugin;
        this.lobbySettings = lobbySettings;
        this.engineSettings = engineSettings;
        this.manhunt = manhunt;
    }

    /**
     * Lobby ids run from 0 to 2,147,483,647.
     */
    public static boolean isValidId(long id) {
        return id >= 0 && id <= MAX_LOBBY_ID;
    }

    /**
     * Parses a lobby id command argument, or empty when out of range.
     */
    public static OptionalInt parseId(String raw) {
        if (raw == null) {
            return OptionalInt.empty();
        }
        try {
            long value = Long.parseLong(raw.trim());
            return isValidId(value) ? OptionalInt.of((int) value) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    public int defaultLobbyId() {
        return lobbySettings.getDefaultLobbyId();
    }

    /**
     * Multiple lobbies exist only with the world engine on.
     */
    public boolean multiLobbyAllowed() {
        return engineSettings.isEnabled();
    }

    /**
     * Mid-match setplayer policy, live-read so reloads apply. Single
     * reader of the path; every policy consumer calls this.
     */
    public MidMatchPolicy midMatchPolicy() {
        return lobbySettings.getMidMatchSetplayer();
    }

    /**
     * Assigns the default lobby; a negative default leaves the player
     * lobby-less. With the world engine off everyone is forced to lobby 0.
     */
    public void assignDefault(UUID playerId) {
        assignDefault(playerId, multiLobbyAllowed() ? defaultLobbyId() : 0);
    }

    /**
     * Testable core of {@link #assignDefault(UUID)}. Package-visible so unit
     * tests can exercise it without a running server.
     */
    public void assignDefault(UUID playerId, int defaultId) {
        if (defaultId < 0) {
            remove(playerId);
            return;
        }
        setLobby(playerId, defaultId);
    }

    /**
     * Moves a player to a lobby, creating it when needed.
     */
    public Lobby setLobby(UUID playerId, int lobbyId) {
        remove(playerId);
        Lobby lobby = lobbies.computeIfAbsent(lobbyId, Lobby::new);
        lobby.add(playerId);
        membership.put(playerId, lobbyId);
        return lobby;
    }

    /**
     * Removes a player from their lobby, deleting it when it becomes empty.
     */
    public Optional<Lobby> remove(UUID playerId) {
        Integer lobbyId = membership.remove(playerId);
        if (lobbyId == null) {
            return Optional.empty();
        }
        Lobby lobby = lobbies.get(lobbyId);
        if (lobby == null) {
            return Optional.empty();
        }
        lobby.remove(playerId);
        if (lobby.isEmpty()) {
            lobbies.remove(lobbyId);
        }
        return Optional.of(lobby);
    }

    public Optional<Lobby> lobbyOf(UUID playerId) {
        Integer lobbyId = membership.get(playerId);
        return lobbyId == null ? Optional.empty() : Optional.ofNullable(lobbies.get(lobbyId));
    }

    /**
     * Disables collisions for a lobby member when the toggle is on.
     * Match members and lobby outsiders are left untouched, as is
     * everyone when the toggle is off.
     */
    public void applyLobbyCollisions(Player player) {
        if (!lobbySettings.isDisablePlayerCollisions()) {
            return;
        }
        if (plugin.game().instanceOf(player.getUniqueId()).isPresent()) {
            return;
        }
        if (lobbyOf(player.getUniqueId()).isEmpty()) {
            return;
        }
        player.setCollidable(false);
    }

    /**
     * Restores collisions unless fake spectator mode owns them: its
     * disable restores them later and re-applies lobby state.
     */
    public void restoreCollisions(Player player) {
        if (plugin.fakeSpectators().isFakeSpectator(player)) {
            return;
        }
        player.setCollidable(true);
    }

    /**
     * Re-applies collision state to everyone online after a toggle
     * flip: fake spectators stay uncollidable, match members collide,
     * lobby members follow the toggle, outsiders are never touched.
     */
    public void reapplyCollisions() {
        reapplyCollisions(Bukkit.getOnlinePlayers());
    }

    /**
     * Testable core of {@link #reapplyCollisions()}. Package-visible so
     * unit tests can exercise it without a running server.
     */
    void reapplyCollisions(Collection<? extends Player> onlinePlayers) {
        boolean disabled = lobbySettings.isDisablePlayerCollisions();
        for (Player online : onlinePlayers) {
            if (plugin.fakeSpectators().isFakeSpectator(online)) {
                online.setCollidable(false);
            } else if (plugin.game().instanceOf(online.getUniqueId()).isPresent()) {
                online.setCollidable(true);
            } else if (lobbyOf(online.getUniqueId()).isPresent()) {
                online.setCollidable(!disabled);
            }
        }
    }

    /**
     * Announces an explicit lobby change after the move. Only positive
     * lobbies announce, and only to the subject plus members of the
     * affected lobbies, per {@code lobbies.announce-lobby-changes}.
     */
    public void announceLobbyChange(Player subject, OptionalInt oldId, OptionalInt newId) {
        if (oldId.equals(newId)) {
            return;
        }
        AnnounceMode mode = lobbySettings.getAnnounceLobbyChanges();
        boolean toSelf = mode.tellsSelf();
        boolean toMembers = mode.tellsMembers();
        if (!toSelf && !toMembers) {
            return;
        }
        MessageService messages = plugin.messages();
        if (oldId.isPresent() && oldId.getAsInt() > 0) {
            announceLeave(subject, oldId.getAsInt(), toSelf, toMembers, messages);
        }
        if (newId.isPresent() && newId.getAsInt() > 0) {
            announceJoin(subject, newId.getAsInt(), toSelf, toMembers, messages);
        }
    }

    private void announceLeave(Player subject, int lobbyId, boolean toSelf,
            boolean toMembers, MessageService messages) {
        if (toSelf) {
            messages.messageRaw(subject, manhunt.getLobbyLeft(),
                    Map.of("lobby", String.valueOf(lobbyId)));
        }
        if (toMembers) {
            announceToMembers(lobbyId, subject, manhunt.getLobbyLeftMember(), messages);
        }
    }

    private void announceJoin(Player subject, int lobbyId, boolean toSelf,
            boolean toMembers, MessageService messages) {
        if (toSelf) {
            messages.messageRaw(subject, manhunt.getLobbyJoined(),
                    Map.of("lobby", String.valueOf(lobbyId)));
        }
        if (toMembers) {
            announceToMembers(lobbyId, subject, manhunt.getLobbyJoinedMember(), messages);
        }
    }

    private void announceToMembers(int lobbyId, Player subject, String template,
            MessageService messages) {
        Lobby lobby = lobbies.get(lobbyId);
        if (lobby == null) {
            return;
        }
        for (UUID memberId : lobby.memberIds()) {
            if (memberId.equals(subject.getUniqueId())) {
                continue;
            }
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                messages.messageRaw(member, template, Map.of("player", subject.getName(),
                        "lobby", String.valueOf(lobbyId)));
            }
        }
    }

    public Optional<Lobby> get(int lobbyId) {
        return Optional.ofNullable(lobbies.get(lobbyId));
    }

    public Set<Integer> lobbyIds() {
        return Collections.unmodifiableSet(new HashSet<>(lobbies.keySet()));
    }

    /**
     * Next sublobby counter for a lobby, starting at 0. Monotonic per
     * server run; ids are never reused.
     */
    public int nextSubId(int lobbyId) {
        int sub = nextSubIds.getOrDefault(lobbyId, 0);
        nextSubIds.put(lobbyId, sub + 1);
        return sub;
    }
}
