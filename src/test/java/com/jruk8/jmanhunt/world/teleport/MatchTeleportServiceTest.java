package com.jruk8.jmanhunt.world.teleport;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchTeleportServiceTest {

    @Test
    void airLikeAcceptsTransparentPassableBlocks() {
        // Grass, torches, and air itself.
        assertTrue(MatchTeleportService.isAirLike(false, true, false));
    }

    @Test
    void airLikeRejectsSolidBlocks() {
        // Stone: occluding and colliding.
        assertFalse(MatchTeleportService.isAirLike(true, false, false));
    }

    @Test
    void airLikeRejectsCollidableTransparentBlocks() {
        // Leaves: see-through but solid.
        assertFalse(MatchTeleportService.isAirLike(false, false, false));
    }

    @Test
    void airLikeRejectsPressurePlates() {
        // Passable but explicitly excluded.
        assertFalse(MatchTeleportService.isAirLike(false, true, true));
    }

    @Test
    void airLikeRejectsOccludingPassableBlocks() {
        assertFalse(MatchTeleportService.isAirLike(true, true, false));
    }
}
