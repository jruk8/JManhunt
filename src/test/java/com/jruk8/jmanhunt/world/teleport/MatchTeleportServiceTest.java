package com.jruk8.jmanhunt.world.teleport;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void fluidRejectsWaterLavaAndPowderSnow() {
        assertTrue(MatchTeleportService.isFluid(Material.WATER));
        assertTrue(MatchTeleportService.isFluid(Material.LAVA));
        assertTrue(MatchTeleportService.isFluid(Material.POWDER_SNOW));
        assertFalse(MatchTeleportService.isFluid(Material.AIR));
        assertFalse(MatchTeleportService.isFluid(Material.SNOW));
    }

    @Test
    void medianYPicksMiddleAndHigherMiddleWhenEven() {
        assertEquals(64, MatchTeleportService.medianY(List.of(64)));
        assertEquals(70, MatchTeleportService.medianY(List.of(100, 70, 40)));
        assertEquals(70, MatchTeleportService.medianY(List.of(40, 100, 70, 60)));
        assertEquals(120, MatchTeleportService.medianY(List.of(120, 64)));
    }

    @Test
    void fitsYHonorsToleranceBand() {
        assertTrue(MatchTeleportService.fitsY(64, 64, 7));
        assertTrue(MatchTeleportService.fitsY(71, 64, 7));
        assertTrue(MatchTeleportService.fitsY(57, 64, 7));
        assertFalse(MatchTeleportService.fitsY(72, 64, 7));
        assertFalse(MatchTeleportService.fitsY(56, 64, 7));
    }

    @Test
    void closestIndexPrefersFirstSeenOnTies() {
        assertEquals(1, MatchTeleportService.closestIndex(List.of(40, 60, 100), 64));
        assertEquals(0, MatchTeleportService.closestIndex(List.of(60, 68), 64));
    }

    @Test
    void selectCandidatePrefersFirstFitElseClosest() {
        assertEquals(2, MatchTeleportService.selectCandidate(List.of(40, 100, 66), 64, 7));
        assertEquals(0, MatchTeleportService.selectCandidate(List.of(60, 100), 64, 7));
        assertEquals(1, MatchTeleportService.selectCandidate(List.of(40, 50, 100), 64, 7));
    }
}
