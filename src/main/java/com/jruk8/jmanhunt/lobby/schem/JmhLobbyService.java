package com.jruk8.jmanhunt.lobby.schem;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.bounds.LobbyBounds;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.BoundEntry;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.Offset;
import com.jruk8.jmanhunt.lobby.schem.JmhLobbyBundle.TeleportEntry;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * .jmhlobby Bukkit wiring: save-time collection of the bounds and
 * teleports inside a schem region, and load-time building of bundled
 * entries back into the live lobby config. The zip and JSON mechanics
 * live in {@link JmhLobbyCodec}; the geometry here is static and pure.
 */
public final class JmhLobbyService {

    /** Suffix of the bundled lobby schematic format. */
    public static final String BUNDLE_SUFFIX = ".jmhlobby";

    private final JManhuntPlugin plugin;

    public JmhLobbyService(JManhuntPlugin plugin) {
        this.plugin = plugin;
    }

    /** Save counts for the bundled confirmation. */
    public record SavedCounts(int bounds, int teleports) {
    }

    /** Load counts for the built confirmation. */
    public record BuiltCounts(int bounds, int teleports) {
    }

    /** Kept teleports plus dropped orphans of one collection. */
    public record CollectedTeleports(List<TeleportEntry> kept, List<OrphanTeleport> dropped) {
    }

    /** A teleport dropped for want of a collected boundary. */
    public record OrphanTeleport(int lobby, double x, double y, double z) {
    }

    /**
     * Collects the bounds boxes at least partly inside the region,
     * stored relative to the region's minimum corner. Non-numeric
     * lobby ids and partial boxes are skipped. Pure for tests.
     */
    public static List<BoundEntry> collectBounds(Map<String, LobbyConfig.LobbyEntry> lobbies,
            LobbyBounds.Bound region, Offset origin) {
        List<BoundEntry> collected = new ArrayList<>();
        for (Map.Entry<String, LobbyConfig.LobbyEntry> entry : lobbies.entrySet()) {
            OptionalInt id = LobbyService.parseId(entry.getKey());
            if (id.isEmpty() || entry.getValue() == null
                    || entry.getValue().getBounds() == null) {
                continue;
            }
            LobbyConfig.Position pos1 = entry.getValue().getBounds().getPos1();
            LobbyConfig.Position pos2 = entry.getValue().getBounds().getPos2();
            if (pos1 == null || pos2 == null) {
                continue;
            }
            LobbyBounds.Bound box = new LobbyBounds.Bound(
                    pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());
            if (!LobbyBounds.intersects(region, box)) {
                continue;
            }
            int minX = (int) Math.floor(Math.min(pos1.getX(), pos2.getX()));
            int minY = (int) Math.floor(Math.min(pos1.getY(), pos2.getY()));
            int minZ = (int) Math.floor(Math.min(pos1.getZ(), pos2.getZ()));
            int maxX = (int) Math.floor(Math.max(pos1.getX(), pos2.getX()));
            int maxY = (int) Math.floor(Math.max(pos1.getY(), pos2.getY()));
            int maxZ = (int) Math.floor(Math.max(pos1.getZ(), pos2.getZ()));
            collected.add(new BoundEntry(id.getAsInt(),
                    new Offset(minX - origin.x(), minY - origin.y(), minZ - origin.z()),
                    new Offset(maxX - origin.x(), maxY - origin.y(), maxZ - origin.z())));
        }
        return collected;
    }

    /**
     * Collects the teleports standing inside the region. A teleport
     * is a point, so inside means its block is contained; teleports
     * whose lobby id collected no boundary are reported as orphans
     * instead of kept. Pure for tests.
     */
    public static CollectedTeleports collectTeleports(Map<String, LobbyConfig.LobbyEntry> lobbies,
            LobbyBounds.Bound region, Offset origin, Set<Integer> boundedIds) {
        List<TeleportEntry> kept = new ArrayList<>();
        List<OrphanTeleport> dropped = new ArrayList<>();
        for (Map.Entry<String, LobbyConfig.LobbyEntry> entry : lobbies.entrySet()) {
            OptionalInt id = LobbyService.parseId(entry.getKey());
            if (id.isEmpty() || entry.getValue() == null
                    || entry.getValue().getLobbytp() == null) {
                continue;
            }
            LobbyConfig.LobbyTp tp = entry.getValue().getLobbytp();
            long blockX = (long) Math.floor(tp.getX());
            long blockY = (long) Math.floor(tp.getY());
            long blockZ = (long) Math.floor(tp.getZ());
            if (!LobbyBounds.contains(region, blockX, blockY, blockZ)) {
                continue;
            }
            if (!boundedIds.contains(id.getAsInt())) {
                dropped.add(new OrphanTeleport(id.getAsInt(), tp.getX(), tp.getY(), tp.getZ()));
                continue;
            }
            kept.add(new TeleportEntry(id.getAsInt(),
                    (int) blockX - origin.x(), (int) blockY - origin.y(),
                    (int) blockZ - origin.z(), tp.getYaw(), tp.getPitch()));
        }
        return new CollectedTeleports(kept, dropped);
    }

    /**
     * Writes a bundle file: collects the region's bounds and teleports,
     * warns for orphans, and moves the zip into place atomically.
     */
    public SavedCounts saveBundle(File target, byte[] nbt,
            Map<String, LobbyConfig.LobbyEntry> lobbies, LobbyBounds.Bound region,
            Offset origin) throws IOException {
        List<BoundEntry> bounds = collectBounds(lobbies, region, origin);
        Set<Integer> boundedIds = new HashSet<>();
        for (BoundEntry entry : bounds) {
            boundedIds.add(entry.lobby());
        }
        CollectedTeleports teleports = collectTeleports(lobbies, region, origin, boundedIds);
        for (OrphanTeleport orphan : teleports.dropped()) {
            plugin.logger().warning("Dev schem save '" + target.getName() + "': lobby "
                    + orphan.lobby() + " teleport at (" + orphan.x() + ", " + orphan.y() + ", "
                    + orphan.z() + ") has no collected boundary; dropping it.");
        }
        byte[] zip = JmhLobbyCodec.write(
                new JmhLobbyBundle(nbt, origin, bounds, teleports.kept()));
        File staging = new File(target.getParentFile(), "." + target.getName() + ".tmp");
        Files.write(staging.toPath(), zip);
        try {
            Files.move(staging.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException fallback) {
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return new SavedCounts(bounds.size(), teleports.kept().size());
    }

    /** Reads a bundle file back. */
    public JmhLobbyBundle readBundle(File file) throws IOException {
        return JmhLobbyCodec.read(Files.readAllBytes(file.toPath()));
    }

    /**
     * Builds bundled bounds and teleports into the live lobby config at
     * the paste corner, creating missing entries and overwriting the
     * bounds and teleports of existing ones (overrides and upkeep are
     * preserved). Nonzero lobbies are skipped with a warning when the
     * world engine is off, mirroring the lobbyconfig commands.
     */
    public BuiltCounts buildIntoLobbyConfig(JmhLobbyBundle bundle,
            int pasteX, int pasteY, int pasteZ) {
        LobbyConfig lobbyConfig = plugin.lobbyConfig();
        boolean multiLobby = plugin.configService().getBoolean("world-engine.enabled", false);
        int bounds = 0;
        int teleports = 0;
        for (BoundEntry entry : bundle.bounds()) {
            if (!multiLobby && entry.lobby() != 0) {
                plugin.logger().warning("Skipping bundled lobby " + entry.lobby()
                        + " bounds: the world engine is off, so only lobby 0 applies.");
                continue;
            }
            LobbyConfig.LobbyEntry target = entryFor(lobbyConfig, entry.lobby());
            LobbyConfig.BoundsData data = new LobbyConfig.BoundsData();
            data.setPos1(LobbyConfig.Position.of(
                    pasteX + entry.min().x(), pasteY + entry.min().y(), pasteZ + entry.min().z()));
            data.setPos2(LobbyConfig.Position.of(
                    pasteX + entry.max().x(), pasteY + entry.max().y(), pasteZ + entry.max().z()));
            target.setBounds(data);
            bounds++;
        }
        for (TeleportEntry entry : bundle.teleports()) {
            if (!multiLobby && entry.lobby() != 0) {
                plugin.logger().warning("Skipping bundled lobby " + entry.lobby()
                        + " teleport: the world engine is off, so only lobby 0 applies.");
                continue;
            }
            LobbyConfig.LobbyEntry target = entryFor(lobbyConfig, entry.lobby());
            target.setLobbytp(LobbyConfig.LobbyTp.of(
                    pasteX + entry.x(), pasteY + entry.y(), pasteZ + entry.z(),
                    entry.yaw(), entry.pitch()));
            teleports++;
        }
        lobbyConfig.save();
        return new BuiltCounts(bounds, teleports);
    }

    /** Configured lobby world name for the paste gate. */
    public String lobbyWorldName() {
        return plugin.configService().getString("world-engine.lobby-world-name", "jmh_lobby");
    }

    private static LobbyConfig.LobbyEntry entryFor(LobbyConfig lobbyConfig, int lobbyId) {
        String key = String.valueOf(lobbyId);
        LobbyConfig.LobbyEntry entry = lobbyConfig.getLobbies().get(key);
        if (entry == null) {
            entry = new LobbyConfig.LobbyEntry();
            lobbyConfig.getLobbies().put(key, entry);
        }
        return entry;
    }
}
