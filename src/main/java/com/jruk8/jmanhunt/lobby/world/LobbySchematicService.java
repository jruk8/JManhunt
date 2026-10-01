package com.jruk8.jmanhunt.lobby.world;

import com.jruk8.jmanhunt.command.QuietConsoleDispatch;
import com.jruk8.jmanhunt.config.DevConfig;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import lombok.Getter;
import com.jruk8.jmanhunt.lobby.config.LobbyPreset;

/**
 * Applies lobby presets during fresh lobby-world generation: pastes the
 * preset's bundled schematic with its midpoint at 0,64,0, then runs the
 * preset's console commands. A missing or unreadable schematic warns
 * and leaves void; commands still run. Preset pastes read only the
 * bundled resources; the data-folder schematics dir is the dev
 * authoring workspace.
 */
public final class LobbySchematicService {
    /** Structure midpoint height; X/Z center on the origin. */
    static final int PASTE_MID_Y = 64;

    /** Bundled schematic resources, below the jar root. */
    static final String BUNDLED_DIR = "dev/lobby-schematics";

    @Getter
    private final JmhLobbyService lobbies;
    private final DevConfig devConfig;
    private final Path dataFolder;
    private final JManhuntLogger log;
    private final Function<String, InputStream> resources;

    public LobbySchematicService(JmhLobbyService lobbies, DevConfig devConfig, Path dataFolder,
            JManhuntLogger log, Function<String, InputStream> resources) {
        this.lobbies = lobbies;
        this.devConfig = devConfig;
        this.dataFolder = dataFolder;
        this.log = log;
        this.resources = resources;
    }

    /** Pastes the schematic, then runs preset commands. Always in this order. */
    public void applyPreset(World world, LobbyPreset preset) {
        pasteSchematic(world, preset);
        runCommands(world, preset);
    }

    private void pasteSchematic(World world, LobbyPreset preset) {
        String name = devConfig.schematicFor(preset.name());
        if (name == null || name.isBlank()) {
            return;
        }
        pasteNbt(world, name);
    }

    /** Dev authoring dir for saved schematics, created on demand. */
    public File schematicDir() {
        File dir = dataFolder.resolve("settings/world-engine/lobby-schematics").toFile();
        dir.mkdirs();
        return dir;
    }

    /**
     * Pastes a named bundled schematic with its midpoint at 0,64,0.
     * Warns and returns false when the resource is missing or the
     * paste fails.
     */
    public boolean pasteNbt(World world, String name) {
        return pasteNbt(world, name, new Location(world, 0, PASTE_MID_Y, 0));
    }

    /**
     * Pastes a named bundled schematic centered on the given
     * midpoint's block, in the given world. A .jmhlobby bundle wins
     * over a same-named legacy .nbt; bundles also build their bounds
     * and teleports into the lobby config when the paste lands in the
     * lobby world. Warns and returns false when the resource is
     * missing or the paste fails.
     */
    public boolean pasteNbt(World world, String name, Location midpoint) {
        String bundleName = name + JmhLobbyService.BUNDLE_SUFFIX;
        byte[] bundle = bundledBytes(bundleName);
        if (bundle != null) {
            return pasteBundleBytes(world, bundleName, midpoint, bundle);
        }
        String nbtName = name + ".nbt";
        byte[] nbt = bundledBytes(nbtName);
        if (nbt == null) {
            log.warning("Lobby schematic '" + name
                    + "' is missing from the bundled dev/lobby-schematics/; leaving void.");
            return false;
        }
        return pasteNbtBytes(world, nbtName, midpoint, nbt);
    }

    /**
     * Dev paste of one explicit schematic file from the authoring dir:
     * bundles build their bounds and teleports like preset pastes.
     * Warns and returns false when the file is missing or the paste
     * fails.
     */
    public boolean pasteFile(World world, File file, Location midpoint) {
        if (!file.isFile()) {
            log.warning("Lobby schematic '" + file.getName()
                    + "' is missing from settings/world-engine/lobby-schematics/.");
            return false;
        }
        if (file.getName().endsWith(JmhLobbyService.BUNDLE_SUFFIX)) {
            JmhLobbyBundle unbundled;
            try {
                unbundled = lobbies.readBundle(file);
            } catch (IOException unreadable) {
                log.warning("Could not read lobby bundle '" + file.getName()
                        + "': " + unreadable.getMessage());
                return false;
            }
            return pasteParsedBundle(world, file.getName(), midpoint, unbundled);
        }
        Structure structure;
        try {
            structure = Bukkit.getStructureManager().loadStructure(file);
        } catch (IOException unreadable) {
            log.warning("Could not load lobby schematic '" + file.getName()
                    + "': " + unreadable.getMessage());
            return false;
        }
        return placeStructure(world, file.getName(), midpoint, structure);
    }

    /** One bundled resource's bytes, or null when it is missing or unreadable. */
    private byte[] bundledBytes(String file) {
        try (InputStream in = resources.apply(BUNDLED_DIR + "/" + file)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException unreadable) {
            log.warning("Could not read bundled lobby schematic '" + file
                    + "': " + unreadable.getMessage());
            return null;
        }
    }

    /** Pastes bundled bundle bytes under their resource name. */
    private boolean pasteBundleBytes(World world, String fileName, Location midpoint,
            byte[] bytes) {
        JmhLobbyBundle unbundled;
        try {
            unbundled = lobbies.readBundle(bytes);
        } catch (IOException unreadable) {
            log.warning("Could not read lobby bundle '" + fileName
                    + "': " + unreadable.getMessage());
            return false;
        }
        return pasteParsedBundle(world, fileName, midpoint, unbundled);
    }

    /** Pastes bundled .nbt bytes under their resource name. */
    private boolean pasteNbtBytes(World world, String fileName, Location midpoint,
            byte[] bytes) {
        Path staging = null;
        Structure structure;
        try {
            staging = Files.createTempFile("jmh-schem-load", ".nbt");
            Files.write(staging, bytes);
            structure = Bukkit.getStructureManager().loadStructure(staging.toFile());
        } catch (IOException unreadable) {
            log.warning("Could not load lobby schematic '" + fileName
                    + "': " + unreadable.getMessage());
            return false;
        } finally {
            deleteQuietly(staging);
        }
        return placeStructure(world, fileName, midpoint, structure);
    }

    /**
     * Pastes a parsed .jmhlobby bundle: the structure first, then its
     * bounds and teleports into the lobby config when the paste lands
     * in the lobby world. Pastes elsewhere place blocks only, since
     * lobby entries are lobby-world coordinates.
     */
    private boolean pasteParsedBundle(World world, String fileName, Location midpoint,
            JmhLobbyBundle unbundled) {
        Path staging = null;
        Structure structure;
        try {
            staging = Files.createTempFile("jmh-schem-load", ".nbt");
            Files.write(staging, unbundled.nbt());
            structure = Bukkit.getStructureManager().loadStructure(staging.toFile());
        } catch (IOException unreadable) {
            log.warning("Could not load lobby schematic '" + fileName
                    + "': " + unreadable.getMessage());
            return false;
        } finally {
            deleteQuietly(staging);
        }
        Location corner = cornerFor(world, midpoint, structure.getSize());
        if (!placeStructure(world, fileName, midpoint, structure)) {
            return false;
        }
        if (!world.getName().equals(lobbies.lobbyWorldName())) {
            log.info("Pasted lobby bundle '" + fileName
                    + "' outside the lobby world; skipping its bounds and teleports.");
            return true;
        }
        JmhLobbyService.BuiltCounts built = lobbies.buildIntoLobbyConfig(unbundled,
                corner.getBlockX(), corner.getBlockY(), corner.getBlockZ());
        log.info("Built " + built.bounds() + " bounds and " + built.teleports()
                + " teleports into the lobby config from '" + fileName + "'.");
        return true;
    }

    /** Places one loaded structure centered on the midpoint's block. */
    private boolean placeStructure(World world, String fileName, Location midpoint,
            Structure structure) {
        Location corner = cornerFor(world, midpoint, structure.getSize());
        try {
            structure.place(corner, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f,
                    ThreadLocalRandom.current());
        } catch (RuntimeException failed) {
            log.warning("Could not paste lobby schematic '" + fileName
                    + "': " + failed.getMessage());
            return false;
        }
        return true;
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // System temp cleans strays on its own.
        }
    }

    private void runCommands(World world, LobbyPreset preset) {
        List<String> commands = devConfig.commandsFor(preset.name());
        for (String command : commands) {
            if (command.isBlank()) {
                continue;
            }
            String parsed = command.replace("{world}", world.getName()).replace("{preset}", preset.name());
            if (parsed.startsWith("/")) {
                parsed = parsed.substring(1);
            }
            try {
                QuietConsoleDispatch.dispatch(parsed);
            } catch (Exception exception) {
                log.severe(
                        "Failed to run lobby preset command '" + command + "'. Skipping..",
                        exception);
            }
        }
    }

    /**
     * Paste corner centering the structure on the midpoint's block. Pure
     * for tests.
     */
    public static Location cornerFor(World world, Location midpoint, BlockVector size) {
        return new Location(world,
                cornerAxis(midpoint.getBlockX(), size.getBlockX()),
                cornerAxis(midpoint.getBlockY(), size.getBlockY()),
                cornerAxis(midpoint.getBlockZ(), size.getBlockZ()));
    }

    /**
     * Minimum corner for one axis so a structure of the given size lands
     * centered on the origin point. Integer division; even sizes lean one
     * block toward negative. Pure for tests.
     */
    public static int cornerAxis(int origin, int size) {
        return origin - size / 2;
    }
}
