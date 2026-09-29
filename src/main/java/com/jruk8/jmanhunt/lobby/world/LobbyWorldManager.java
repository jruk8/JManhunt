package com.jruk8.jmanhunt.lobby.world;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.world.DimensionWorlds;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.LongSupplier;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyPreset;

/**
 * Owns the native lobby world: a void dimension filled by the configured
 * lobby preset, generated on confirmed request without any world-management
 * plugin. Also guards generation behind a run-twice confirmation and
 * resolves void-rescue destinations.
 */
public final class LobbyWorldManager {
    /** Lowest spawn height; with no pasted blocks this is the spawn. */
    static final int PLATFORM_Y = 64;
    /** Window in which a second tpto run confirms generation. */
    public static final long CONFIRM_TIMEOUT_MILLIS = 10_000L;

    private final JManhuntPlugin plugin;
    private final LongSupplier clock;
    /** Sender keys with an armed generation confirmation. */
    private final Map<String, Pending> pending = new HashMap<>();


    private record Pending(String worldName, long expiresAt) {
    }

    public LobbyWorldManager(JManhuntPlugin plugin) {
        this(plugin, System::currentTimeMillis);
    }

    public LobbyWorldManager(JManhuntPlugin plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    public LobbyWorldManager(LongSupplier clock) {
        this.plugin = null;
        this.clock = clock;
    }

    /** Configured lobby world name, live-read so renames apply on reload. */
    public String lobbyWorldName() {
        return plugin.configService().getString("advanced.lobbies.lobby-world-name", "jmh_lobby");
    }

    /** True when the lobby world name collides with the game world name. Pure for tests. */
    public static boolean namesClash(String lobbyWorldName, String gameWorldName) {
        return lobbyWorldName != null && gameWorldName != null
                && lobbyWorldName.equalsIgnoreCase(gameWorldName);
    }

    /** True when a world with the lobby name is loaded or has a folder waiting. */
    public boolean lobbyWorldExists() {
        return DimensionWorlds.exists(plugin.getServer().getWorldContainer(),
                plugin.configService().getString("world-engine.world-name", "world"),
                lobbyWorldName());
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
     * worlds get the void generator, the preset paste plus its
     * commands, a safe spawn, and a lobby 0 location when none is
     * configured. Empty when creation fails.
     */
    public Optional<LobbyWorld> ensureLobbyWorld() {
        return ensureLobbyWorld(Optional.empty());
    }

    /**
     * Same, but a present override replaces DEFAULT for this
     * fresh generation only. Already generated worlds ignore it entirely.
     */
    public Optional<LobbyWorld> ensureLobbyWorld(Optional<LobbyPreset> presetOverride) {
        String name = lobbyWorldName();
        if (namesClash(name, plugin.configService().getString("world-engine.world-name", "world"))) {
            plugin.logger().warning("Refusing to load lobby world '" + name
                    + "': it matches the game world. Rename advanced.lobbies.lobby-world-name.");
            return Optional.empty();
        }
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            return Optional.of(new LobbyWorld(loaded, false, false));
        }
        File container = plugin.getServer().getWorldContainer();
        String gameWorld = plugin.configService().getString("world-engine.world-name", "world");
        boolean fresh = !DimensionWorlds.folderExists(container, gameWorld, name);
        World world;
        try {
            world = DimensionWorlds.loadOrCreate(name, creator -> {
                creator.generator(new VoidChunkGenerator());
                creator.generateStructures(false);
            });
        } catch (Exception exception) {
            plugin.logger().warning("Could not create lobby world " + name + ": " + exception.getMessage());
            return Optional.empty();
        }
        if (world == null) {
            return Optional.empty();
        }
        if (!fresh) {
            // Our leftover from before a restart: paste and spawn persist.
            return Optional.of(new LobbyWorld(world, false, false));
        }
        LobbyPreset preset = presetOverride.orElse(LobbyPreset.DEFAULT);
        new LobbySchematicService(plugin).applyPreset(world, preset);
        applyLobbyDefaults(world);
        Location spawn = safeSpawn(world);
        world.setSpawnLocation(spawn);
        boolean lobbyZeroSet = ensureLobbyZero(spawn);
        lowestLobbyTp(plugin.lobbyConfig().getLobbies()).ifPresent(lowest ->
                world.setSpawnLocation(toSpawn(world, lowest.getValue())));
        return Optional.of(new LobbyWorld(world, true, lobbyZeroSet));
    }

    /**
     * Safe defaults for a fresh lobby world: peaceful difficulty, frozen
     * time and weather, no locator bar, and no mob, trader, phantom,
     * patrol, or griefing activity. Generation-only: later loads never
     * touch these again. Refuses game-world names outright, so a lobby
     * misconfiguration can never freeze time and weather where matches run.
     */
    private void applyLobbyDefaults(World world) {
        String gameWorldName = plugin.configService().getString("world-engine.world-name", "world");
        if (namesClash(world.getName(), gameWorldName)) {
            plugin.logger().warning("Refusing to apply lobby defaults to '" + world.getName()
                    + "': it matches the game world. Rename advanced.lobbies.lobby-world-name.");
            return;
        }
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
        world.setGameRule(GameRules.LOCATOR_BAR, false);
        world.setGameRule(GameRules.SPAWN_MOBS, false);
        world.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        world.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        world.setGameRule(GameRules.SPAWN_PATROLS, false);
        world.setGameRule(GameRules.MOB_GRIEFING, false);
    }

    /**
     * Highest solid block at the origin plus one, never below y=65, so a
     * missing schematic still yields a sane spawn instead of the void.
     */
    private Location safeSpawn(World world) {
        int top = world.getHighestBlockYAt(0, 0);
        return new Location(world, 0.5, Math.max(top + 1, PLATFORM_Y + 1), 0.5, 0.0f, 0.0f);
    }

    /**
     * Rescue destination for a void fall in the lobby world: the member
     * lobby's location, else lobby 0's, else empty. Pure for tests.
     */
    public static Optional<Location> selectRescueLocation(Map<Integer, Location> locations, OptionalInt memberLobby) {
        if (memberLobby.isPresent()) {
            Location own = locations.get(memberLobby.getAsInt());
            if (own != null) {
                return Optional.of(own);
            }
        }
        return Optional.ofNullable(locations.get(0));
    }

    /** True when no lobby 0 lobbytp is configured. Pure for tests. */
    public static boolean missingLobbyZero(LobbyConfig lobbyConfig) {
        return lobbyConfig == null || lobbyConfig.getLobbies() == null
                || lobbyConfig.getLobbies().get("0") == null
                || lobbyConfig.getLobbies().get("0").getLobbytp() == null;
    }

    /**
     * Keeps a rescue target only when it sits in the lobby world, so void
     * rescue never strands a player in the game world. Pure for tests.
     */
    public static Optional<Location> inLobbyWorld(Optional<Location> target, String lobbyWorldName) {
        return target.filter(location -> location.getWorld() != null
                && location.getWorld().getName().equals(lobbyWorldName));
    }

    private void pruneExpired() {
        long now = clock.getAsLong();
        pending.values().removeIf(candidate -> candidate.expiresAt() <= now);
    }

    /**
     * Points lobby 0 at freshly generated spawn, unless admins already set
     * it. Returns true when it wrote.
     */
    /**
     * Points lobby 0 at the spawn when none is configured. True when
     * written.
     */
    public boolean ensureLobbyZero(Location spawn) {
        LobbyConfig lobbyConfig = plugin.lobbyConfig();
        if (!missingLobbyZero(lobbyConfig)) {
            return false;
        }
        LobbyConfig.LobbyEntry entry = new LobbyConfig.LobbyEntry();
        entry.setLobbytp(LobbyConfig.LobbyTp.of(
                spawn.getX(), spawn.getY(), spawn.getZ(), spawn.getYaw(), spawn.getPitch()));
        lobbyConfig.getLobbies().put("0", entry);
        lobbyConfig.save();
        return true;
    }

    /**
     * Lowest lobby id holding a teleport, with its point. Pure for tests:
     * skips non-integer keys, negative ids, and entries without a lobbytp.
     */
    public static Optional<Map.Entry<Integer, LobbyConfig.LobbyTp>> lowestLobbyTp(
            Map<String, LobbyConfig.LobbyEntry> lobbies) {
        if (lobbies == null) {
            return Optional.empty();
        }
        Integer lowestId = null;
        LobbyConfig.LobbyTp lowestTp = null;
        for (Map.Entry<String, LobbyConfig.LobbyEntry> candidate : lobbies.entrySet()) {
            if (candidate.getValue() == null || candidate.getValue().getLobbytp() == null) {
                continue;
            }
            int id;
            try {
                id = Integer.parseInt(candidate.getKey());
            } catch (NumberFormatException invalid) {
                continue;
            }
            if (id < 0 || (lowestId != null && id >= lowestId)) {
                continue;
            }
            lowestId = id;
            lowestTp = candidate.getValue().getLobbytp();
        }
        if (lowestId == null) {
            return Optional.empty();
        }
        return Optional.of(Map.entry(lowestId, lowestTp));
    }

    private static Location toSpawn(World world, LobbyConfig.LobbyTp point) {
        return new Location(world, point.getX(), point.getY(), point.getZ(),
                point.getYaw(), point.getPitch());
    }
}
