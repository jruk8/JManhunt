package com.jruk8.jmanhunt.world.cell;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.world.WorldEngineConfig;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.World;

/**
 * One fetch worth of close-to-structure state: the proximity lookup
 * plus the selection policy. Inactive unless the block is enabled
 * with at least one known structure; unknown words warn once here.
 */
final class CloseToStructureFetch {
    private final StructureProximity proximity;
    private final StructureSelectionPolicy policy;

    private CloseToStructureFetch(StructureProximity proximity, StructureSelectionPolicy policy) {
        this.proximity = proximity;
        this.policy = policy;
    }

    /** Inactive fetch: the loop keeps legacy first-pass behavior. */
    static CloseToStructureFetch inactive() {
        return new CloseToStructureFetch(null, null);
    }

    /** Arms the fetch from config, warning once about unknown words. */
    static CloseToStructureFetch armed(WorldEngineConfig config, JManhuntPlugin plugin) {
        Set<String> unknown = new LinkedHashSet<>();
        List<String> keys =
                StructurePreference.keysFor(config.spawnCloseToStructureWords(), unknown::add);
        if (!unknown.isEmpty()) {
            plugin.logger().warning("Unknown spawn-close-to-structure entries skipped: "
                    + String.join(", ", unknown));
        }
        if (keys.isEmpty()) {
            return inactive();
        }
        return new CloseToStructureFetch(
                new StructureProximity(keys, config.spawnCloseToStructureMaxDistance()),
                new StructureSelectionPolicy(config.spawnCloseToStructureAttempts()));
    }

    boolean active() {
        return proximity != null;
    }

    /**
     * Runs the proximity lookup for one raw-passing origin and feeds
     * the outcome to the policy. Returns the origin to use, or null
     * to fetch the next cell.
     */
    CellOrigin consider(World world, CellOrigin origin, int surfaceY) {
        boolean hit = proximity.nearStructure(world, origin.x(), surfaceY, origin.z());
        return policy.record(origin, hit);
    }

    /** Latest raw-passing origin, for raw-limit exhaustion. */
    CellOrigin lastRawPass() {
        return policy.lastRawPass();
    }
}
