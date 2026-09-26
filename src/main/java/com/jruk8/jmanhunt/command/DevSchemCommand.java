package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.bounds.LobbyBounds;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyService;
import com.jruk8.jmanhunt.lobby.world.LobbySchematicService;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Developer schematic tools ({@code /manhunt dev schem ...}). Corners are
 * the executing player's feet block when pos1/pos2 runs. Saved files land
 * in the lobby-schematics dir so devs can author default lobby presets;
 * loads paste centered on the executing player's feet. Not for production
 * use: its only safety rail is the overwrite rerun on bundle loads.
 */
public final class DevSchemCommand {
    /** Rerun window for overwrite-confirming bundle loads. */
    static final long CONFIRM_WINDOW_MILLIS = 10_000L;

    /** One pending overwrite confirmation. */
    record PendingLoad(String name, long at) {
        boolean expired(long now) {
            return now - at > CONFIRM_WINDOW_MILLIS;
        }
    }

    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final LobbySchematicService schematics;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final Map<UUID, PendingLoad> pendingLoads = new HashMap<>();

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
        List<Location> selection = selection(sender, player);
        if (selection == null) {
            return true;
        }
        World world = selection.get(0).getWorld();
        List<BlockVector> corners =
                normalized(blockVector(selection.get(0)), blockVector(selection.get(1)));
        Structure structure = fillSelection(sender, name, world, corners);
        if (structure == null) {
            return true;
        }
        byte[] nbt;
        try {
            nbt = captureNbt(structure);
        } catch (IOException failed) {
            plugin.logger().warning("Dev schem save failed: " + failed.getMessage());
            messages.message(sender, "dev.save-failed", Map.of("name", name));
            return true;
        }
        JmhLobbyService.SavedCounts counts;
        try {
            counts = bundleLobby(name, nbt, corners);
        } catch (IOException failed) {
            plugin.logger().warning("Dev schem save failed: " + failed.getMessage());
            messages.message(sender, "dev.save-failed", Map.of("name", name));
            return true;
        }
        BlockVector size = structure.getSize();
        messages.message(sender, "dev.saved", Map.of("name", name, "size",
                size.getBlockX() + "x" + size.getBlockY() + "x" + size.getBlockZ(),
                "lobbies", lobbySummary(counts)));
        return true;
    }

    /** Validated pos1/pos2 in one world, or null after messaging the sender. */
    private List<Location> selection(CommandSender sender, Player player) {
        Location first = pos1.get(player.getUniqueId());
        Location second = pos2.get(player.getUniqueId());
        if (first == null || second == null) {
            messages.message(sender, "dev.need-selection");
            return null;
        }
        if (first.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            messages.message(sender, "dev.world-mismatch");
            return null;
        }
        return List.of(first, second);
    }

    /** Fills a fresh structure from the selection, or null after messaging on failure. */
    private Structure fillSelection(
            CommandSender sender, String name, World world, List<BlockVector> corners) {
        Structure structure = Bukkit.getStructureManager().createStructure();
        try {
            structure.fill(corners.get(0).toLocation(world), corners.get(1).toLocation(world), true);
        } catch (RuntimeException failed) {
            plugin.logger().warning("Dev schem fill failed: " + failed.getMessage());
            messages.message(sender, "dev.save-failed", Map.of("name", name));
            return null;
        }
        return structure;
    }

    /** Serializes a filled structure to NBT bytes via a cleaned-up temp file. */
    private static byte[] captureNbt(Structure structure) throws IOException {
        Path staging = null;
        try {
            staging = Files.createTempFile("jmh-schem-save", ".nbt");
            Bukkit.getStructureManager().saveStructure(staging.toFile(), structure);
            return Files.readAllBytes(staging);
        } finally {
            deleteQuietly(staging);
        }
    }

    /** Bundles NBT bytes with the region's lobby boxes and teleports. */
    private JmhLobbyService.SavedCounts bundleLobby(
            String name, byte[] nbt, List<BlockVector> corners) throws IOException {
        BlockVector min = corners.get(0);
        BlockVector max = corners.get(1);
        return new JmhLobbyService(plugin).saveBundle(
                new File(schematics.schematicDir(), name + JmhLobbyService.BUNDLE_SUFFIX),
                nbt, plugin.lobbyConfig().getLobbies(),
                new LobbyBounds.Bound(min.getBlockX(), min.getBlockY(), min.getBlockZ(),
                        max.getBlockX(), max.getBlockY(), max.getBlockZ()),
                new JmhLobbyBundle.Offset(min.getBlockX(), min.getBlockY(), min.getBlockZ()));
    }

    /** Saved-counts phrase for the confirmation: pluralized, or the empty note. Pure for tests. */
    static String lobbySummary(JmhLobbyService.SavedCounts counts) {
        if (counts.bounds() == 0 && counts.teleports() == 0) {
            return "no bounds or teleports";
        }
        return counts.bounds() + plural(counts.bounds(), " bound")
                + ", " + counts.teleports() + plural(counts.teleports(), " teleport");
    }

    private static String plural(int count, String singular) {
        return count == 1 ? singular : singular + "s";
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

    private boolean load(CommandSender sender, String name) {
        if (!(sender instanceof Player player)) {
            return playerOnly(sender);
        }
        if (name == null) {
            messages.message(sender, "dev.usage");
            return true;
        }
        File bundle = new File(schematics.schematicDir(), name + JmhLobbyService.BUNDLE_SUFFIX);
        File legacy = new File(schematics.schematicDir(), name + ".nbt");
        if (!bundle.isFile() && !legacy.isFile()) {
            messages.message(sender, "dev.load-missing", Map.of("name", name));
            return true;
        }
        if (bundle.isFile() && confirmRequired(player, name, bundle)) {
            return true;
        }
        // World agnostic: pastes into whatever world the player stands in,
        // centered on their feet.
        if (schematics.pasteNbt(player.getWorld(), name, player.getLocation())) {
            pendingLoads.remove(player.getUniqueId());
            messages.message(sender, "dev.pasted", Map.of("name", name));
        } else {
            messages.message(sender, "dev.load-failed", Map.of("name", name));
        }
        return true;
    }

    /**
     * True when the bundle load must wait for an explicit rerun: pasting
     * in the lobby world over stored entries asks once, then proceeds
     * when the same name reruns inside the window. Unreadable bundles
     * skip the gate; the paste reports them.
     */
    private boolean confirmRequired(Player player, String name, File bundleFile) {
        JmhLobbyService lobbies = new JmhLobbyService(plugin);
        if (!player.getWorld().getName().equals(lobbies.lobbyWorldName())) {
            return false;
        }
        JmhLobbyBundle bundle;
        try {
            bundle = lobbies.readBundle(bundleFile);
        } catch (IOException unreadable) {
            return false;
        }
        Set<Integer> overwritten = JmhLobbyService.overwrittenIds(
                plugin.lobbyConfig().getLobbies(), bundle,
                plugin.lobbyService().multiLobbyAllowed());
        if (overwritten.isEmpty()) {
            return false;
        }
        PendingLoad pending = pendingLoads.get(player.getUniqueId());
        if (pending != null && pending.name().equals(name)
                && !pending.expired(System.currentTimeMillis())) {
            pendingLoads.remove(player.getUniqueId());
            return false;
        }
        pendingLoads.put(player.getUniqueId(),
                new PendingLoad(name, System.currentTimeMillis()));
        messages.message(player, "dev.load-overwrite-confirm",
                Map.of("name", name, "ids", overwritten.stream().sorted()
                        .map(String::valueOf)
                        .collect(Collectors.joining(", "))));
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

    /** Saved schematic names without suffix, bundles and legacy .nbt alike, alphabetically. */
    public List<String> schematicNames() {
        String[] files = schematics.schematicDir().list((dir, file) ->
                file.endsWith(JmhLobbyService.BUNDLE_SUFFIX) || file.endsWith(".nbt"));
        Set<String> names = new HashSet<>();
        if (files != null) {
            for (String file : files) {
                if (file.endsWith(JmhLobbyService.BUNDLE_SUFFIX)) {
                    names.add(stripSuffix(file, JmhLobbyService.BUNDLE_SUFFIX));
                } else {
                    names.add(stripSuffix(file, ".nbt"));
                }
            }
        }
        List<String> sorted = new ArrayList<>(names);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    private static String stripSuffix(String file, String suffix) {
        return file.substring(0, file.length() - suffix.length());
    }

    private static String blockCoords(Location corner) {
        return corner.getBlockX() + ", " + corner.getBlockY() + ", " + corner.getBlockZ();
    }

    /** Feet location to its block vector. Pure for tests. */
    /** Live pending corners for the debug particle draft boxes. */
    public Map<UUID, Location> pos1View() {
        return Collections.unmodifiableMap(pos1);
    }

    /** Live pending corners for the debug particle draft boxes. */
    public Map<UUID, Location> pos2View() {
        return Collections.unmodifiableMap(pos2);
    }

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
