package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.core.JManhuntPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
        String name = plugin.getConfig()
                .getString("world-engine.lobby-presets." + preset.name() + ".schematic", "");
        if (name == null || name.isBlank()) {
            return;
        }
        File dir = new File(plugin.getDataFolder(), "settings/world-engine/lobby-schematics");
        dir.mkdirs();
        File file = new File(dir, name + ".nbt");
        if (!file.isFile()) {
            plugin.logger().warning("Lobby preset " + preset.name() + " schematic '" + name
                    + ".nbt' is missing from settings/world-engine/lobby-schematics/; leaving void.");
            return;
        }
        Structure structure;
        try {
            structure = Bukkit.getStructureManager().loadStructure(file);
        } catch (IOException unreadable) {
            plugin.logger().warning(
                    "Could not load lobby schematic '" + file.getName() + "': " + unreadable.getMessage());
            return;
        }
        BlockVector size = structure.getSize();
        Location corner = new Location(world, cornerAxis(0, size.getBlockX()),
                cornerAxis(PASTE_MID_Y, size.getBlockY()), cornerAxis(0, size.getBlockZ()));
        try {
            structure.place(corner, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f,
                    ThreadLocalRandom.current());
        } catch (RuntimeException failed) {
            plugin.logger().warning(
                    "Could not paste lobby schematic '" + file.getName() + "': " + failed.getMessage());
        }
    }

    private void runCommands(World world, LobbyPreset preset) {
        List<String> commands = plugin.getConfig()
                .getStringList("world-engine.lobby-presets." + preset.name() + ".commands");
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
     * Minimum corner for one axis so a structure of the given size lands
     * centered on the origin point. Integer division; even sizes lean one
     * block toward negative. Pure for tests.
     */
    static int cornerAxis(int origin, int size) {
        return origin - size / 2;
    }
}
