package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.LobbySchematicService;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Developer schematic tools ({@code /manhunt dev schem ...}). Corners are
 * the executing player's feet block when pos1/pos2 runs. Saved files land
 * in the lobby-schematics dir so devs can author default lobby presets;
 * loads paste centered on the executing player's feet. Not for production
 * use: it offers no safety rails beyond the lobby presets themselves.
 */
public final class DevSchemCommand {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final LobbySchematicService schematics;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public DevSchemCommand(JManhuntPlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.schematics = new LobbySchematicService(plugin);
    }

    /** Runs one schem action; args[0] is the action, args[1] the name if any. */
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            messages.message(sender, "dev.usage");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        String name = args.length > 1 ? args[1] : null;
        return switch (action) {
            case "pos1" -> pos(sender, true);
            case "pos2" -> pos(sender, false);
            case "save" -> save(sender, name);
            case "load" -> load(sender, name);
            case "list" -> list(sender);
            default -> {
                messages.message(sender, "dev.usage");
                yield true;
            }
        };
    }

    private boolean pos(CommandSender sender, boolean first) {
        if (!(sender instanceof Player player)) {
            return playerOnly(sender);
        }
        // Feet block at execution time: Location is already feet-based.
        Location corner = player.getLocation().clone();
        (first ? pos1 : pos2).put(player.getUniqueId(), corner);
        messages.message(sender, first ? "dev.pos1" : "dev.pos2",
                Map.of("pos", blockCoords(corner)));
        return true;
    }

    private boolean save(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            return playerOnly(sender);
        }
        if (name == null || !validName(name)) {
            messages.message(sender, name == null ? "dev.usage" : "dev.invalid-name");
            return true;
        }
        Location first = pos1.get(player.getUniqueId());
        Location second = pos2.get(player.getUniqueId());
        if (first == null || second == null) {
            messages.message(sender, "dev.need-selection");
            return true;
        }
        World world = first.getWorld();
        if (world == null || !world.equals(second.getWorld())) {
            messages.message(sender, "dev.world-mismatch");
            return true;
        }
        List<BlockVector> corners = normalized(blockVector(first), blockVector(second));
        Location from = corners.get(0).toLocation(world);
        Location to = corners.get(1).toLocation(world);
        Structure structure = Bukkit.getStructureManager().createStructure();
        try {
            structure.fill(from, to, true);
        } catch (RuntimeException failed) {
            plugin.logger().warning("Dev schem fill failed: " + failed.getMessage());
            messages.message(sender, "dev.save-failed", Map.of("name", name));
            return true;
        }
        try {
            Bukkit.getStructureManager().saveStructure(new File(schematics.schematicDir(), name + ".nbt"),
                    structure);
        } catch (IOException failed) {
            plugin.logger().warning("Dev schem save failed: " + failed.getMessage());
            messages.message(sender, "dev.save-failed", Map.of("name", name));
            return true;
        }
        BlockVector size = structure.getSize();
        messages.message(sender, "dev.saved", Map.of("name", name, "size",
                size.getBlockX() + "x" + size.getBlockY() + "x" + size.getBlockZ()));
        return true;
    }

    private boolean load(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            return playerOnly(sender);
        }
        if (name == null) {
            messages.message(sender, "dev.usage");
            return true;
        }
        if (!new File(schematics.schematicDir(), name + ".nbt").isFile()) {
            messages.message(sender, "dev.load-missing", Map.of("name", name));
            return true;
        }
        // World agnostic: pastes into whatever world the player stands in,
        // centered on their feet.
        if (schematics.pasteNbt(player.getWorld(), name, player.getLocation())) {
            messages.message(sender, "dev.pasted", Map.of("name", name));
        } else {
            messages.message(sender, "dev.load-failed", Map.of("name", name));
        }
        return true;
    }

    private boolean list(CommandSender sender) {
        List<String> names = schematicNames();
        if (names.isEmpty()) {
            messages.message(sender, "dev.list-empty");
        } else {
            messages.message(sender, "dev.list", Map.of("value", ListFormatter.joinOxford(names)));
        }
        return true;
    }

    private boolean playerOnly(CommandSender sender) {
        messages.message(sender, "command.player-only");
        return true;
    }

    /** Saved schematic names without the .nbt suffix, alphabetically. */
    public List<String> schematicNames() {
        String[] files = schematics.schematicDir().list((dir, file) -> file.endsWith(".nbt"));
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (String file : files) {
                names.add(file.substring(0, file.length() - ".nbt".length()));
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private static String blockCoords(Location corner) {
        return corner.getBlockX() + ", " + corner.getBlockY() + ", " + corner.getBlockZ();
    }

    /** Feet location to its block vector. Pure for tests. */
    static BlockVector blockVector(Location location) {
        return new BlockVector(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** Normalized [min, max] corners. Pure for tests. */
    static List<BlockVector> normalized(BlockVector a, BlockVector b) {
        return List.of(
                new BlockVector(Math.min(a.getBlockX(), b.getBlockX()),
                        Math.min(a.getBlockY(), b.getBlockY()),
                        Math.min(a.getBlockZ(), b.getBlockZ())),
                new BlockVector(Math.max(a.getBlockX(), b.getBlockX()),
                        Math.max(a.getBlockY(), b.getBlockY()),
                        Math.max(a.getBlockZ(), b.getBlockZ())));
    }

    /** Schematic names stay flat files: no separators or parent refs. Pure for tests. */
    static boolean validName(String name) {
        return name != null && !name.isBlank()
                && !name.contains("/") && !name.contains("\\") && !name.contains("..");
    }
}
