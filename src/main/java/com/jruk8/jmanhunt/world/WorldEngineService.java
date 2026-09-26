package com.jruk8.jmanhunt.world;

import com.jruk8.jmanhunt.lobby.config.LobbyPreset;
import com.jruk8.jmanhunt.lobby.world.LobbyWorld;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.world.end.EndCellManager;
import com.jruk8.jmanhunt.world.end.EndResetManager;
import com.jruk8.jmanhunt.world.structure.NetherStructuresDatapackManager;
import com.jruk8.jmanhunt.world.structure.OverworldStructuresDatapackManager;
import com.jruk8.jmanhunt.world.structure.StrongholdDatapackManager;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.EngineStateRepository;
import com.jruk8.jmanhunt.config.SettingsListener;
import com.jruk8.jmanhunt.JManhuntPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.BooleanSupplier;
import com.jruk8.jmanhunt.world.border.WorldBorderService;
import com.jruk8.jmanhunt.world.cell.WorldCellService;
import com.jruk8.jmanhunt.world.teleport.LobbyWorldService;
import com.jruk8.jmanhunt.world.teleport.MatchTeleportService;

public final class WorldEngineService implements SettingsListener {
    private final JManhuntPlugin plugin;
    private final ConfigService configService;
    private final StrongholdDatapackManager strongholdDatapackManager;
    private final NetherStructuresDatapackManager netherStructuresDatapackManager;
    private final OverworldStructuresDatapackManager overworldStructuresDatapackManager;
    private final EndResetManager endResetManager;
    private final EndCellManager endCells;
    private final WorldCellService cells;
    private final WorldBorderService borders;
    private final MatchTeleportService teleport;
    private final LobbyWorldService lobbyWorlds;

    public WorldEngineService(JManhuntPlugin plugin, MessageService messages, ConfigService configService,
            EngineStateRepository engineState) {
        this.plugin = plugin;
        this.configService = configService;
        this.strongholdDatapackManager = new StrongholdDatapackManager(plugin);
        this.netherStructuresDatapackManager = new NetherStructuresDatapackManager(plugin);
        this.overworldStructuresDatapackManager = new OverworldStructuresDatapackManager(plugin);
        this.endResetManager = new EndResetManager(plugin);
        this.endCells = new EndCellManager(plugin, engineState);
        this.borders = new WorldBorderService(plugin, configService);
        this.cells = new WorldCellService(plugin, engineState, endCells, borders);
        this.lobbyWorlds = new LobbyWorldService(plugin, messages);
        this.teleport = new MatchTeleportService(plugin, lobbyWorlds);
    }

    /** Wires the match-running check behind the NO_MATCH_RUNNING refill policy. */
    public void setMatchRunningSupplier(BooleanSupplier matchRunning) {
        cells.setMatchRunningSupplier(matchRunning);
    }

    /** Teleport service, for wiring as the lobby teleporter. */
    public MatchTeleportService teleportService() {
        return teleport;
    }

    /**
     * Teleports participants to the next match cell. Returns the used cell
     * index, or empty when the engine is off, the world is missing, or no
     * valid cell could be allocated. The real border is only applied for a
     * lone match; concurrent matches use pseudo-borders instead.
     */
    public OptionalLong onMatchStart(List<Player> participants, List<Player> spectators,
                                     boolean applyBorder, int lobbyId, long matchId) {
        return cells.onMatchStart(participants, spectators, applyBorder, lobbyId, matchId);
    }

    /**
     * Teleports mid-match joiners to random spawns inside a match cell and
     * pins their respawn to the cell center. No-op when the engine is off or
     * the world is missing.
     */
    public void teleportJoinersToCell(List<Player> joiners, long cellIndex) {
        cells.teleportJoinersToCell(joiners, cellIndex);
    }

    /**
     * Tops up the ready-cell buffer. Called when a match ends and when an
     * autostart countdown begins; a periodic task covers matches started
     * while others run.
     */
    public void prepareNextCell() {
        cells.refillBuffer();
    }

    /** Buffered cell indexes, oldest first. */
    public List<Long> bufferedCellIndexes() {
        return cells.bufferedCellIndexes();
    }

    /**
     * Called when the game actually begins (via speedrunner damage or force start).
     * If the start-border is active, expands it to the full cell size.
     */
    public void onBeginGame() {
        borders.onBeginGame();
    }

    public void onMatchEnd(List<Player> participants, List<Player> spectators, int lobbyId, long matchId) {
        borders.clearInstanceBorders();
        WorldEngineConfig config = WorldEngineConfig.fromConfig(configService);
        if (!config.enabled()) {
            return;
        }

        List<Player> returning = new ArrayList<>(participants);
        returning.addAll(spectators);
        Location lobby = lobbyWorlds.resolveLobbyTeleport(lobbyId, returning);
        if (lobby == null) {
            return;
        }

        endCells.endWorldFor(matchId).ifPresentOrElse(endWorld -> {
            // Everyone in the dedicated end rides to the lobby, including
            // players outside the participant lists.
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getWorld().equals(endWorld)) {
                    player.teleport(lobby);
                    player.setRespawnLocation(lobby, true);
                }
            }
            endCells.release(matchId);
        }, () -> endResetManager.reset(config, lobby));

        for (Player player : participants) {
            player.teleport(lobby);
            player.setRespawnLocation(lobby, true);
        }
        // Spectators placed at the cell center at match start return to the
        // lobby with everyone else. When NONE spectator handling is disabled
        // they were never moved, so they are left alone.
        if (configService.getBoolean("settings.players.roles.turn-nones-spectator.enabled", false)) {
            for (Player spectator : spectators) {
                spectator.teleport(lobby);
                spectator.setRespawnLocation(lobby, true);
            }
        }
        borders.clearWorldBorder(lobby.getWorld());
    }

    /** Clears real borders and start-border state, e.g. when matches go concurrent. */
    public void clearInstanceBorders() {
        borders.clearInstanceBorders();
    }

    /**
     * Applies the real world border for one cell, e.g. when concurrency
     * drops back to a single match. Honors the start-border phase for
     * matches that have not begun yet.
     */
    public void applyInstanceBorder(long cellIndex, boolean begun) {
        borders.applyInstanceBorder(cellIndex, begun);
    }

    public boolean teleportToLobby(List<Player> targets, int lobbyId) {
        return teleport.teleportToLobby(targets, lobbyId);
    }

    public boolean setSpawnToLobby(List<Player> targets, int lobbyId) {
        return teleport.setSpawnToLobby(targets, lobbyId);
    }

    public boolean teleportToLobbyQuiet(List<Player> targets, int lobbyId) {
        return teleport.teleportToLobbyQuiet(targets, lobbyId);
    }

    public boolean setSpawnToLobbyQuiet(List<Player> targets, int lobbyId) {
        return teleport.setSpawnToLobbyQuiet(targets, lobbyId);
    }

    /**
     * True when newcomers have a lobby to wait in: the engine is on and
     * the lobby (or a fallback lobby) has a valid teleport.
     */
    public boolean hasLobbyLocation(int lobbyId) {
        return teleport.hasLobbyLocation(lobbyId);
    }

    @Override
    public void onStart() {
        refreshDatapacks();
        cells.refillBuffer();
        // Periodic top-up for matches consumed while others run. Plugin
        // tasks are cancelled automatically on disable.
        Bukkit.getScheduler().runTaskTimer(plugin, cells::refillBuffer, 1200L, 1200L);
    }

    /** Dedicated end world of a live match, if it has one. */
    public Optional<World> matchEndWorld(long matchId) {
        return endCells.endWorldFor(matchId);
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
        return lobbyWorlds.confirmLobbyGeneration(senderKey);
    }

    /** Loads or generates the lobby world. Empty when creation fails. */
    public Optional<LobbyWorld> ensureLobbyWorld() {
        return lobbyWorlds.ensureLobbyWorld();
    }

    /** Same, with a one-shot preset override for fresh generation. */
    public Optional<LobbyWorld> ensureLobbyWorld(Optional<LobbyPreset> presetOverride) {
        return lobbyWorlds.ensureLobbyWorld(presetOverride);
    }

    /** Points lobby 0 at the spawn when none is configured. True when written. */
    public boolean ensureLobbyZero(Location spawn) {
        return lobbyWorlds.ensureLobbyZero(spawn);
    }

    /**
     * True when void rescue applies in a world: the lobby world, never the
     * game world, even if an admin points both names at the same world.
     */
    public boolean rescuesVoidIn(World world) {
        return lobbyWorlds.rescuesVoidIn(world);
    }

    /**
     * True when a world is the lobby world: name match, never the game
     * world, even if an admin points both names at the same world.
     */
    public boolean isLobbyWorld(World world) {
        return lobbyWorlds.isLobbyWorld(world);
    }

    /**
     * Heals and feeds one lobby-world occupant per the lobby care toggles.
     * No-op anywhere else.
     */
    public void careFor(Player player) {
        lobbyWorlds.careFor(player);
    }

    /**
     * Repeating upkeep sweep over every lobby-world occupant. Runs every
     * second from the plugin scheduler; the configured interval gates
     * the actual sweep so reloads apply without rescheduling.
     */
    public void careTick() {
        lobbyWorlds.careTick();
    }

    /**
     * Rescue destination for a void fall in the lobby world: the member
     * lobby's location when it sits in the lobby world, else lobby 0's
     * when it does, else the lobby world's own spawn. Never a game-world
     * location. Empty only when the lobby world is not loaded.
     */
    public Optional<Location> lobbyRescueLocation(OptionalInt memberLobby) {
        return lobbyWorlds.lobbyRescueLocation(memberLobby);
    }

    /**
     * Startup sweep for orphaned end dimensions: reservations left by
     * restarts or crashes plus stray folders from older versions.
     */
    public void deleteOrphanedEndCells() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(configService);
        int deleted = endCells.deleteOrphans(config.worldName());
        if (deleted > 0) {
            plugin.logger().info("Deleted " + deleted + " orphaned end dimension(s).");
        }
    }

    /** True when lobby-world-name collides with the game world name. */
    public boolean lobbyWorldNameClashes() {
        return lobbyWorlds.lobbyWorldNameClashes();
    }

    /**
     * Warns when lobby-world-name matches the game world name, in which
     * case lobby world loading stays refused until it is renamed. True
     * when clean. Runs on enable and reload.
     */
    public boolean validateLobbyWorldName() {
        return lobbyWorlds.validateLobbyWorldName();
    }

    /** Surface center of a match cell, for end-exit routing. */
    public Optional<Location> cellRoot(long cellIndex) {
        return cells.cellRoot(cellIndex);
    }

    @Override
    public void onReload() {
        refreshDatapacks();
    }

    /**
     * Applies or removes every datapack from its own flag. Structure boosts
     * are independent of the world engine: each pack follows only its own
     * enabled state.
     */
    private void refreshDatapacks() {
        WorldEngineConfig config = WorldEngineConfig.fromConfig(configService);
        boolean worldEnabled = configService.getBoolean("world-engine.enabled", false);
        strongholdDatapackManager.apply(config.worldName(), worldEnabled);
        if (!worldEnabled) {
            strongholdDatapackManager.remove(config.worldName(), false);
        }
        boolean netherEnabled = configService.getBoolean("settings.match.game-boosts.nether-structures.enabled", false);
        netherStructuresDatapackManager.apply(config.worldName(), netherEnabled);
        if (!netherEnabled) {
            netherStructuresDatapackManager.remove(config.worldName(), false);
        }
        boolean overworldEnabled = configService.getBoolean(
                "settings.match.game-boosts.overworld-structures.enabled", false);
        overworldStructuresDatapackManager.apply(config.worldName(), overworldEnabled);
        if (!overworldEnabled) {
            overworldStructuresDatapackManager.remove(config.worldName(), false);
        }
    }

    @Override
    public String getDataPath() {
        return "settings/world-engine/strongholds.json";
    }

    /** Current cell index cap for the live configuration. */
    public long cellIndexCap() {
        return cells.cellIndexCap();
    }

    public static long clampCellIndex(long value, long max) {
        return WorldCellService.clampCellIndex(value, max);
    }

    /** Current cell index, or empty when the engine store is unavailable. */
    public OptionalLong cellIndex() {
        return cells.cellIndex();
    }

    /** Overwrites the cell index. Returns false when the store is unavailable. */
    public boolean cellIndex(long value) {
        return cells.cellIndex(value);
    }
}
