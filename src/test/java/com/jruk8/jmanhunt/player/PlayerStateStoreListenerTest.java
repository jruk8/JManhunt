package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerStateStoreListenerTest {

    private record Change(UUID playerId, Role from, Role to) {
    }

    @Test
    void setRoleNotifiesWithFromAndTo() {
        PlayerStateStore players = new PlayerStateStore();
        List<Change> changes = new ArrayList<>();
        players.addRoleListener((id, from, to) -> changes.add(new Change(id, from, to)));
        UUID id = UUID.randomUUID();

        players.setRole(id, Role.HUNTER);
        players.setRole(id, Role.SPECTATOR);

        assertEquals(List.of(new Change(id, Role.NONE, Role.HUNTER),
                new Change(id, Role.HUNTER, Role.SPECTATOR)), changes);
    }

    @Test
    void unchangedRoleNotifiesNobody() {
        PlayerStateStore players = new PlayerStateStore();
        List<Change> changes = new ArrayList<>();
        players.addRoleListener((id, from, to) -> changes.add(new Change(id, from, to)));
        UUID id = UUID.randomUUID();
        players.setRole(id, Role.HUNTER);

        players.setRole(id, Role.HUNTER);

        assertEquals(1, changes.size());
    }

    @Test
    void resetRolesNotifies() {
        PlayerStateStore players = new PlayerStateStore();
        List<Change> changes = new ArrayList<>();
        players.addRoleListener((id, from, to) -> changes.add(new Change(id, from, to)));
        UUID id = UUID.randomUUID();
        players.setRole(id, Role.SPECTATOR);

        players.resetRoles(List.of(id));

        assertEquals(List.of(new Change(id, Role.SPECTATOR, Role.NONE)),
                changes.subList(1, changes.size()));
    }
}
