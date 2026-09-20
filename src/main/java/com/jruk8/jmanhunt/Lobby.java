package com.jruk8.jmanhunt;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One lobby queue: member ids plus, from Phase 2, its own autostart state. */
public final class Lobby {
    private final int id;
    private final Set<UUID> members = new HashSet<>();

    public Lobby(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public boolean add(UUID playerId) {
        return members.add(playerId);
    }

    public boolean remove(UUID playerId) {
        return members.remove(playerId);
    }

    public boolean contains(UUID playerId) {
        return members.contains(playerId);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public int size() {
        return members.size();
    }

    public Set<UUID> memberIds() {
        return Collections.unmodifiableSet(new HashSet<>(members));
    }
}
