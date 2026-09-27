package com.jruk8.jmanhunt.lobby.world;

import com.jruk8.jmanhunt.JManhuntPlugin;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import com.jruk8.jmanhunt.lobby.config.LobbyPreset;

/**
 * Applies lobby presets during fresh lobby-world generation: pastes the
 * preset's vanilla .nbt with its midpoint at 0,64,0, then runs the
 * preset's console commands. A missing or unreadable schematic warns
 * and leaves void; commands still run.
 */
public final class LobbySchematicService {
    /** Structure midpoint height; X/Z center on the origin. */
    static final int PASTE_MID_Y = 64;

    private final JManhuntPlugin plugin;

    public LobbySchematicService(JManhuntPlugin plugin) {
        this.plugin = plugin;
    }

    /** Pastes the schematic, then runs preset commands. Always in this order. */
    public void applyPreset(World world, LobbyPreset preset) {
        pasteSchematic(world, preset);
        runCommands(world, preset);
    }

    private void pasteSchematic(World world, LobbyPreset preset) {
        String name = plugin.devConfig().schematicFor(preset.name());
        if (name == null || name.isBlank()) {
            return;
        }
        pasteNbt(world, name);
    }

    /** Directory holding lobby schematics, bundles and legacy .nbt alike, created on demand. */
    public File schematicDir() {
        File dir = new File(plugin.getDataFolder(), "settings/world-engine/lobby-schematics");
        dir.mkdirs();
        return dir;
    }

    /**
     * Pastes a named .nbt from the schematics dir with its midpoint at
     * 0,64,0. Warns and returns false when the file is missing or the
     * paste fails.
     */
    public boolean pasteNbt(World world, String name) {
        return pasteNbt(world, name, new Location(world, 0, PASTE_MID_Y, 0));
    }

    /**
     * Pastes a named schematic from the schematics dir centered on the
     * given midpoint's block, in the given world. A .jmhlobby bundle
     * wins over a same-named legacy .nbt; bundles also build their
     * bounds and teleports into the lobby config when the paste lands
     * in the lobby world. Warns and returns false when the file is
     * missing or the paste fails.
     */
    public boolean pasteNbt(World world, String name, Location midpoint) {
        File bundle = new File(schematicDir(), name + JmhLobbyService.BUNDLE_SUFFIX);
        if (bundle.isFile()) {
            return pasteBundle(world, name, midpoint, bundle);
        }
        File file = new File(schematicDir(), name + ".nbt");
        if (!file.isFile()) {
            plugin.logger().warning("Lobby schematic '" + name
                    + ".nbt' is missing from settings/world-engine/lobby-schematics/; leaving void.");
            return false;
        }
        Structure structure;
        try {
            structure = Bukkit.getStructureManager().loadStructure(file);
        } catch (IOException unreadable) {
            plugin.logger().warning(
                    "Could not load lobby schematic '" + file.getName() + "': " + unreadable.getMessage());
            return false;
        }
        BlockVector size = structure.getSize();
        Location corner = cornerFor(world, midpoint, size);
        try {
            structure.place(corner, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f,
                    ThreadLocalRandom.current());
        } catch (RuntimeException failed) {
            plugin.logger().warning(
                    "Could not paste lobby schematic '" + file.getName() + "': " + failed.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Pastes a .jmhlobby bundle: the structure first, then its bounds
     * and teleports into the lobby config when the paste lands in the
     * lobby world. Pastes elsewhere place blocks only, since lobby
     * entries are lobby-world coordinates.
     */
    private boolean pasteBundle(World world, String name, Location midpoint, File bundle) {
        JmhLobbyService lobbies = new JmhLobbyService(plugin);
        JmhLobbyBundle unbundled;
        try {
            unbundled = lobbies.readBundle(bundle);
        } catch (IOException unreadable) {
            plugin.logger().warning(
                    "Could not read lobby bundle '" + bundle.getName() + "': " + unreadable.getMessage());
            return false;
        }
        Path staging = null;
        Structure structure;
        try {
            staging = Files.createTempFile("jmh-schem-load", ".nbt");
            Files.write(staging, unbundled.nbt());
            structure = Bukkit.getStructureManager().loadStructure(staging.toFile());
        } catch (IOException unreadable) {
            plugin.logger().warning("Could not load lobby schematic '" + bundle.getName()
                    + "': " + unreadable.getMessage());
            return false;
        } finally {
            deleteQuietly(staging);
        }
        Location corner = cornerFor(world, midpoint, structure.getSize());
        try {
            structure.place(corner, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f,
                    ThreadLocalRandom.current());
        } catch (RuntimeException failed) {
            plugin.logger().warning(
                    "Could not paste lobby schematic '" + bundle.getName() + "': " + failed.getMessage());
            return false;
        }
        if (!world.getName().equals(lobbies.lobbyWorldName())) {
            plugin.logger().info("Pasted lobby bundle '" + bundle.getName()
                    + "' outside the lobby world; skipping its bounds and teleports.");
            return true;
        }
        JmhLobbyService.BuiltCounts built = lobbies.buildIntoLobbyConfig(unbundled,
                corner.getBlockX(), corner.getBlockY(), corner.getBlockZ());
        plugin.logger().info("Built " + built.bounds() + " bounds and " + built.teleports()
                + " teleports into the lobby config from '" + bundle.getName() + "'.");
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
        List<String> commands = plugin.devConfig().commandsFor(preset.name());
        for (String command : commands) {
            if (command.isBlank()) {
                continue;
            }
            String parsed = command.replace("{world}", world.getName()).replace("{preset}", preset.name());
            if (parsed.startsWith("/")) {
                parsed = parsed.substring(1);
            }
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            } catch (Exception exception) {
                plugin.logger().severe(
                        "Failed to run lobby preset command '" + command + "'. Skipping..");
                exception.printStackTrace();
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
