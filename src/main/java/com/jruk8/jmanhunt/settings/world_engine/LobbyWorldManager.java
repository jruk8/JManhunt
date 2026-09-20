package com.jruk8.jmanhunt.settings.world_engine;

import com.jruk8.jmanhunt.JManhuntPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.LongSupplier;

/**
 * Owns the native lobby world: a void dimension with a small stone platform
 * that admins build their lobby on, generated on confirmed request without
 * any world-management plugin. Also guards generation behind a run-twice
 * confirmation and resolves void-rescue destinations.
 */
public final class LobbyWorldManager {
    /** Platform extends this far from the origin each way: 9x9 blocks. */
    static final int PLATFORM_RADIUS = 4;
    /** Platform top surface height; spawn sits one block above. */
    static final int PLATFORM_Y = 64;
    /** Window in which a second tpto run confirms generation. */
    static final long CONFIRM_TIMEOUT_MILLIS = 10_000L;

    private final JManhuntPlugin plugin;
    private final LongSupplier clock;
    /** Sender keys with an armed generation confirmation. */
    private final Map<String, Pending> pending = new HashMap<>();

    /** A lobby world plus how it came to be. */
    public record LobbyWorld(World world, boolean created, boolean lobbyZeroSet) {
    }

    private record Pending(String worldName, long expiresAt) {
    }

    public LobbyWorldManager(JManhuntPlugin plugin) {
        this(plugin, System::currentTimeMillis);
    }

    LobbyWorldManager(JManhuntPlugin plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    LobbyWorldManager(LongSupplier clock) {
        this.plugin = null;
        this.clock = clock;
    }

    /** Configured lobby world name, live-read so renames apply on reload. */
    public String lobbyWorldName() {
        return plugin.getConfig().getString("world-engine.lobby-world-name", "jmh-lobby");
    }

    /** True when the lobby world name collides with the game world name. Pure for tests. */
    public static boolean namesClash(String lobbyWorldName, String gameWorldName) {
        return lobbyWorldName != null && gameWorldName != null
                && lobbyWorldName.equalsIgnoreCase(gameWorldName);
    }

    /** True when a world with the lobby name is loaded or has a folder waiting. */
    public boolean lobbyWorldExists() {
        String name = lobbyWorldName();
        if (Bukkit.getWorld(name) != null) {
            return true;
        }
        return new File(plugin.getServer().getWorldContainer(), name).isDirectory();
    }

    /**
     * Arms (first call) or confirms (matching second call within the timeout)
     * lobby-world generation for one sender. Sending keys are player uuid
     * strings, or "console". Returns true only when confirmed. A config
     * rename between runs voids the pending arm and starts over. The world
     * name is a parameter so this stays plugin-free for tests.
     */
    public boolean confirmGeneration(String senderKey, String worldName) {
        pruneExpired();
        Pending existing = pending.get(senderKey);
        if (existing != null && existing.worldName().equals(worldName)) {
            pending.remove(senderKey);
            return true;
        }
        pending.put(senderKey, new Pending(worldName, clock.getAsLong() + CONFIRM_TIMEOUT_MILLIS));
        return false;
    }

    /**
     * Loads or generates the lobby world for any configured name. Fresh
     * worlds get the void generator, a stone platform, a spawn on top of
     * it, and a lobby 0 location when none is configured. Empty when
     * creation fails.
     */
    public Optional<LobbyWorld> ensureLobbyWorld() {
        String name = lobbyWorldName();
        if (namesClash(name, plugin.getConfig().getString("world-engine.world-name", "world"))) {
            plugin.logger().warning("Refusing to load lobby world '" + name
                    + "': it matches the game world. Rename world-engine.lobby-world-name.");
            return Optional.empty();
        }
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            return Optional.of(new LobbyWorld(loaded, false, false));
        }
        boolean fresh = !new File(plugin.getServer().getWorldContainer(), name).isDirectory();
        WorldCreator creator = new WorldCreator(name);
        creator.generator(new VoidChunkGenerator());
        creator.generateStructures(false);
        World world;
        try {
            world = creator.createWorld();
        } catch (Exception exception) {
            plugin.logger().warning("Could not create lobby world " + name + ": " + exception.getMessage());
            return Optional.empty();
        }
        if (world == null) {
            return Optional.empty();
        }
        if (!fresh) {
            // Our leftover from before a restart: platform and spawn persist.
            return Optional.of(new LobbyWorld(world, false, false));
        }
        buildPlatform(world);
        Location spawn = new Location(world, 0.5, PLATFORM_Y + 1, 0.5, 0.0f, 0.0f);
        world.setSpawnLocation(spawn);
        boolean lobbyZeroSet = autoSetLobbyZero(spawn);
        return Optional.of(new LobbyWorld(world, true, lobbyZeroSet));
    }

    /**
     * Rescue destination for a void fall in the lobby world: the member
     * lobby's location, else lobby 0's, else empty. Pure for tests.
     */
    static Optional<Location> selectRescueLocation(Map<Integer, Location> locations, OptionalInt memberLobby) {
        if (memberLobby.isPresent()) {
            Location own = locations.get(memberLobby.getAsInt());
            if (own != null) {
                return Optional.of(own);
            }
        }
        return Optional.ofNullable(locations.get(0));
    }

    /** True when no lobby 0 location is configured. Pure for tests. */
    static boolean missingLobbyZero(FileConfiguration config) {
        return !config.contains("world-engine.lobby-locations.0");
    }

    /**
     * Keeps a rescue target only when it sits in the lobby world, so void
     * rescue never strands a player in the game world. Pure for tests.
     */
    static Optional<Location> inLobbyWorld(Optional<Location> target, String lobbyWorldName) {
        return target.filter(location -> location.getWorld() != null
                && location.getWorld().getName().equals(lobbyWorldName));
    }

    private void pruneExpired() {
        long now = clock.getAsLong();
        pending.values().removeIf(candidate -> candidate.expiresAt() <= now);
    }

    private void buildPlatform(World world) {
        for (int x = -PLATFORM_RADIUS; x <= PLATFORM_RADIUS; x++) {
            for (int z = -PLATFORM_RADIUS; z <= PLATFORM_RADIUS; z++) {
                world.getBlockAt(x, PLATFORM_Y, z).setType(Material.STONE, false);
            }
        }
    }

    /**
     * Points lobby 0 at freshly generated spawn, unless admins already set
     * it. Returns true when it wrote.
     */
    private boolean autoSetLobbyZero(Location spawn) {
        FileConfiguration config = plugin.getConfig();
        if (!missingLobbyZero(config)) {
            return false;
        }
        String base = "world-engine.lobby-locations.0.";
        config.set(base + "world", spawn.getWorld().getName());
        config.set(base + "x", spawn.getX());
        config.set(base + "y", spawn.getY());
        config.set(base + "z", spawn.getZ());
        config.set(base + "yaw", spawn.getYaw());
        config.set(base + "pitch", spawn.getPitch());
        plugin.saveConfig();
        return true;
    }
}
