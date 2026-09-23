package com.jruk8.jmanhunt.world.teleport;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyPreset;
import com.jruk8.jmanhunt.lobby.world.LobbyWorld;
import com.jruk8.jmanhunt.lobby.world.LobbyWorldManager;
import com.jruk8.jmanhunt.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import com.jruk8.jmanhunt.world.WorldEngineConfig;

/** Lobby world loading, lobby teleports, care sweeps, and void rescue. */
public final class LobbyWorldService {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final LobbyWorldManager lobbyWorlds;
    /** Last lobby-care sweep, for the configured repeat interval. */
    private long lastCareMillis;

    public LobbyWorldService(JManhuntPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.lobbyWorlds = new LobbyWorldManager(plugin);
    }

    /** Configured lobby world name. */
    public String lobbyWorldName() {
        return lobbyWorlds.lobbyWorldName();
    }

    /** True when the lobby world is loaded or has a folder waiting. */
    public boolean lobbyWorldExists() {
        return lobbyWorlds.lobbyWorldExists();
    }

    /**
     * Arms or confirms lobby-world generation for one sender key. True only
     * on a matching second call within the timeout.
     */
    public boolean confirmLobbyGeneration(String senderKey) {
        return lobbyWorlds.confirmGeneration(senderKey, lobbyWorlds.lobbyWorldName());
    }

    /** Loads or generates the lobby world. Empty when creation fails. */
    public Optional<LobbyWorld> ensureLobbyWorld() {
        return ensureLobbyWorld(Optional.empty());
    }

    /** Same, with a one-shot preset override for fresh generation. */
    public Optional<LobbyWorld> ensureLobbyWorld(Optional<LobbyPreset> presetOverride) {
        return lobbyWorlds.ensureLobbyWorld(presetOverride);
    }

    /**
     * True when void rescue applies in a world: the lobby world, never the
     * game world, even if an admin points both names at the same world.
     */
    public boolean rescuesVoidIn(World world) {
        return isLobbyWorld(world);
    }

    /**
     * True when a world is the lobby world: name match, never the game
     * world, even if an admin points both names at the same world.
     */
    public boolean isLobbyWorld(World world) {
        if (world == null) {
            return false;
        }
        String name = world.getName();
        return name.equals(lobbyWorldName())
                && !name.equals(plugin.getConfig().getString("world-engine.world-name", "world"));
    }

    /**
     * Heals and feeds one lobby-world occupant per the lobby care toggles.
     * No-op anywhere else.
     */
    public void careFor(Player player) {
        if (player == null || !isLobbyWorld(player.getWorld())) {
            return;
        }
        LobbyConfig.CareData care = careConfig();
        if (care == null) {
            return;
        }
        if (care.getHeal() != null && care.getHeal().isEnabled()) {
            player.setHealth(player.getMaxHealth());
        }
        if (care.getSaturate() != null && care.getSaturate().isEnabled()) {
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }

    /**
     * Repeating upkeep sweep over every lobby-world occupant. Runs every
     * second from the plugin scheduler; the configured interval gates
     * the actual sweep so reloads apply without rescheduling.
     */
    public void careTick() {
        LobbyConfig.CareData care = careConfig();
        if (care == null) {
            return;
        }
        int intervalSeconds = Math.max(1, care.getInterval());
        long now = System.currentTimeMillis();
        if (!careDue(now, lastCareMillis, intervalSeconds)) {
            return;
        }
        lastCareMillis = now;
        for (Player player : Bukkit.getOnlinePlayers()) {
            careFor(player);
        }
    }

    /**
     * True when the upkeep sweep may run: never ran, or a full interval
     * elapsed since the last one. Pure for tests.
     */
    public static boolean careDue(long now, long lastMillis, int intervalSeconds) {
        return lastMillis <= 0 || now - lastMillis >= intervalSeconds * 1000L;
    }

    private LobbyConfig.CareData careConfig() {
        LobbyConfig config = plugin.lobbyConfig();
        return config == null ? null : config.getCare();
    }

    /**
     * Rescue destination for a void fall in the lobby world: the member
     * lobby's location when it sits in the lobby world, else lobby 0's
     * when it does, else the lobby world's own spawn. Never a game-world
     * location. Empty only when the lobby world is not loaded.
     */
    public Optional<Location> lobbyRescueLocation(OptionalInt memberLobby) {
        String lobbyWorld = lobbyWorlds.lobbyWorldName();
        World world = Bukkit.getWorld(lobbyWorld);
        Map<Integer, Location> locations = new HashMap<>();
        if (world != null) {
            for (Map.Entry<Integer, LobbyConfig.LobbyTp> entry : lobbyTps().entrySet()) {
                locations.put(entry.getKey(), toLobbyLocation(world, entry.getValue()));
            }
        }
        Optional<Location> configured = LobbyWorldManager.inLobbyWorld(
                LobbyWorldManager.selectRescueLocation(locations, memberLobby), lobbyWorld);
        if (configured.isPresent()) {
            return configured;
        }
        if (world != null) {
            return Optional.of(world.getSpawnLocation());
        }
        return Optional.empty();
    }

    /** True when lobby-world-name collides with the game world name. */
    public boolean lobbyWorldNameClashes() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        return LobbyWorldManager.namesClash(lobbyWorlds.lobbyWorldName(), config.worldName());
    }

    /**
     * Warns when lobby-world-name matches the game world name, in which
     * case lobby world loading stays refused until it is renamed. True
     * when clean. Runs on enable and reload.
     */
    public boolean validateLobbyWorldName() {
        if (!lobbyWorldNameClashes()) {
            return true;
        }
        WorldEngineConfig config = WorldEngineConfig.fromConfig(plugin.getConfig());
        plugin.logger().warning("world-engine.lobby-world-name '" + lobbyWorlds.lobbyWorldName()
                + "' matches the game world '" + config.worldName()
                + "'. Lobby world loading stays disabled until it is renamed.");
        return false;
    }

    /**
     * Resolves a lobby teleport: the lobby's own lobbytp in the lobby
     * world, else the lowest lobby id with a valid lobbytp as a
     * fallback (debug-logged). A missing or unloaded lobby world
     * resolves like a missing lobby entirely.
     */
    public Location resolveLobby(int lobbyId, boolean logFallback) {
        World lobbyWorld = Bukkit.getWorld(lobbyWorlds.lobbyWorldName());
        Map<Integer, LobbyConfig.LobbyTp> tps = lobbyTps();
        LobbyConfig.LobbyTp own = tps.get(lobbyId);
        if (lobbyWorld != null && own != null) {
            return toLobbyLocation(lobbyWorld, own);
        }
        if (lobbyWorld == null || tps.isEmpty()) {
            return null;
        }
        int fallback = tps.keySet().stream().min(Integer::compare).orElseThrow();
        if (logFallback) {
            plugin.logger().debug("debug.lobby-fallback", Map.of(
                    "lobby", String.valueOf(lobbyId), "fallback", String.valueOf(fallback)));
        }
        return toLobbyLocation(lobbyWorld, tps.get(fallback));
    }

    /**
     * resolveLobby plus the nothing-anywhere announcement: when no
     * lobbytp exists anywhere (or the lobby world is missing), every
     * target is told that no lobby exists and to contact an
     * administrator, and the miss is debug-logged.
     */
    public Location resolveLobbyTeleport(int lobbyId, List<Player> targets) {
        return resolveLobbyTeleport(lobbyId, targets, true);
    }

    /**
     * Resolves a lobby teleport, optionally silent on chat for the second
     * half of a paired teleport-plus-spawn call. The debug log always fires.
     */
    public Location resolveLobbyTeleport(int lobbyId, List<Player> targets, boolean announce) {
        Location lobby = resolveLobby(lobbyId, true);
        if (lobby != null) {
            return lobby;
        }
        plugin.logger().debug("debug.lobby-missing", Map.of("lobby", String.valueOf(lobbyId)));
        if (!announce) {
            return null;
        }
        for (Player target : targets) {
            messages.message(target, "manhunt.lobby-no-location-anywhere",
                    Map.of("lobby", String.valueOf(lobbyId)));
        }
        return null;
    }

    /** Valid lobbytps keyed by lobby id: integer keys with a stored lobbytp. */
    private Map<Integer, LobbyConfig.LobbyTp> lobbyTps() {
        Map<Integer, LobbyConfig.LobbyTp> tps = new HashMap<>();
        LobbyConfig lobbyConfig = plugin.lobbyConfig();
        if (lobbyConfig == null || lobbyConfig.getLobbies() == null) {
            return tps;
        }
        for (Map.Entry<String, LobbyConfig.LobbyEntry> entry : lobbyConfig.getLobbies().entrySet()) {
            int id;
            try {
                id = Integer.parseInt(entry.getKey().trim());
            } catch (NumberFormatException expected) {
                continue;
            }
            if (id < 0 || entry.getValue() == null || entry.getValue().getLobbytp() == null) {
                continue;
            }
            tps.put(id, entry.getValue().getLobbytp());
        }
        return tps;
    }

    /** Lobbytp coordinates as a location in the given lobby world. */
    private static Location toLobbyLocation(World lobbyWorld, LobbyConfig.LobbyTp point) {
        return new Location(lobbyWorld, point.getX(), point.getY(), point.getZ(),
                point.getYaw(), point.getPitch());
    }
}
