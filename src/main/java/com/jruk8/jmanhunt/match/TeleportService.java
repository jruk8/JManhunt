package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.TagLocations;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * World resolution and execution behind the teleport tags. Worlds
 * resolve by exact name first, then by the {@code nether} and
 * {@code end} environment aliases, so {@code <plocation>} output
 * round-trips back through {@code <pteleport>}.
 */
public final class TeleportService {

    private TeleportService() {
    }

    /**
     * Resolves one world ref against the given worlds: exact name
     * first (covering raw names and any literal {@code nether} or
     * {@code end} world), then the first world of the matching
     * environment for the bare aliases. Empty when unknown.
     */
    public static Optional<World> resolveWorld(String ref, Collection<World> worlds) {
        for (World world : worlds) {
            if (world.getName().equals(ref)) {
                return Optional.of(world);
            }
        }
        World.Environment environment = null;
        if ("nether".equalsIgnoreCase(ref)) {
            environment = World.Environment.NETHER;
        } else if ("end".equalsIgnoreCase(ref)) {
            environment = World.Environment.THE_END;
        }
        if (environment == null) {
            return Optional.empty();
        }
        for (World world : worlds) {
            if (world.getEnvironment() == environment) {
                return Optional.of(world);
            }
        }
        return Optional.empty();
    }

    /**
     * Teleports one player to the target. Unknown worlds warn and
     * move nothing; targets without angles keep the player's
     * current view. Returns true once the teleport was attempted.
     */
    public static boolean teleport(Player player, TagLocations.TeleportRequest target,
            Collection<World> worlds, Consumer<String> warn) {
        Optional<World> world = resolveWorld(target.worldRef(), worlds);
        if (world.isEmpty()) {
            warn.accept("Unknown world '" + target.worldRef() + "': teleport skipped.");
            return false;
        }
        float pitch = target.pitch() != null ? target.pitch()
                : player.getLocation().getPitch();
        float yaw = target.yaw() != null ? target.yaw()
                : player.getLocation().getYaw();
        Location destination = new Location(world.get(), target.x(), target.y(),
                target.z(), yaw, pitch);
        player.teleport(destination);
        return true;
    }
}
