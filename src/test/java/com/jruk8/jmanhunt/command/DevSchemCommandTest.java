package com.jruk8.jmanhunt.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

class DevSchemCommandTest {
    @Test
    void normalizedOrdersCornersPerAxis() {
        List<BlockVector> corners = DevSchemCommand.normalized(
                new BlockVector(5, 70, -3), new BlockVector(-2, 64, 9));

        assertEquals(new BlockVector(-2, 64, -3), corners.get(0));
        assertEquals(new BlockVector(5, 70, 9), corners.get(1));
    }

    @Test
    void blockVectorFloorsFeetToBlock() {
        assertEquals(new BlockVector(10, 64, -21),
                DevSchemCommand.blockVector(new Location(null, 10.5, 64.0, -20.2)));
    }

    @Test
    void validNameRejectsSeparatorsAndParentRefs() {
        assertTrue(DevSchemCommand.validName("arena"));
        assertTrue(DevSchemCommand.validName("my-arena_2"));
        assertFalse(DevSchemCommand.validName("a/b"));
        assertFalse(DevSchemCommand.validName("a\\b"));
        assertFalse(DevSchemCommand.validName("../arena"));
        assertFalse(DevSchemCommand.validName("  "));
        assertFalse(DevSchemCommand.validName(null));
    }
}
