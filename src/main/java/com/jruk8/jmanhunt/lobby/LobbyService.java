package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.JManhuntPlugin;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * Owns lobby membership. Unknown ids are created on join and empty lobbies
 * are deleted on leave; nothing persists across restarts.
 */
public final class LobbyService {
    public static final int MAX_LOBBY_ID = Integer.MAX_VALUE;

    private final JManhuntPlugin plugin;
    private final Map<Integer, Lobby> lobbies = new HashMap<>();
    private final Map<UUID, Integer> membership = new HashMap<>();
    private final Map<Integer, Integer> nextSubIds = new HashMap<>();

    public LobbyService(JManhuntPlugin plugin) {
        this.plugin = plugin;
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
        return plugin.getConfig().getInt("lobbies.default-lobby-id", 0);
    }

    /**
     * Multiple lobbies exist only with the world engine on.
     */
    public boolean multiLobbyAllowed() {
        return plugin.getConfig().getBoolean("world-engine.enabled", false);
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
    void assignDefault(UUID playerId, int defaultId) {
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
