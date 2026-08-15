package com.jruk8.jmanhunt;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Location;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * WorldEdit-backed schematic loader used by the schematic commands and the
 * Lucky Block {@code structure} outcomes.
 *
 * <p>WorldEdit is a soft dependency: this class is the only place in the code
 * base that references the WorldEdit API, so its removal keeps the rest of
 * the plugin working. Every public operation first verifies WorldEdit is
 * present and supported and fails cleanly otherwise.</p>
 *
 * <p>The pivot point is the block position where the schematic was saved:
 * when saving, the clipboard's origin is set to the saver's block position,
 * and when loading the clipboard is pasted so that origin lands on the
 * requested location (WorldEdit's {@code PasteBuilder.to()} places the
 * clipboard origin at the target point).</p>
 */
public final class WorldEditSchematicService {
    /** The minimum WorldEdit version required for schematic features. */
    public static final String MINIMUM_VERSION = WorldEditAvailability.MINIMUM_VERSION;

    private WorldEditSchematicService() {
    }

    /**
     * Checks whether the WorldEdit plugin is installed and its API is visible
     * to the plugin's class loader.
     *
     * <p>Note: callers that may be executed when WorldEdit is absent should
     * use {@link WorldEditAvailability#isAvailable()} instead of this method,
     * because executing any method on this class loads the WorldEdit API.</p>
     */
    public static boolean isWorldEditAvailable() {
        return WorldEditAvailability.isAvailable();
    }

    /**
     * Checks whether the installed WorldEdit meets the minimum version.
     */
    public static boolean isWorldEditSupported() {
        if (!WorldEditAvailability.isAvailable()) {
            return false;
        }
        try {
            return WorldEditAvailability.isSupportedVersion(WorldEdit.getVersion());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Returns the installed WorldEdit version string, or {@code "unknown"}. */
    public static String installedVersion() {
        try {
            String version = WorldEdit.getVersion();
            return version == null || version.isBlank() ? "unknown" : version;
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    /**
     * Saves the block selection between {@code pos1} and {@code pos2}
     * (inclusive) to {@code file} as a WorldEdit {@code .schem} file. The
     * pivot point (the clipboard origin) is set to the saver's position.
     *
     * @throws IOException when saving fails or WorldEdit is unavailable
     */
    public static void saveSchematic(Location pos1, Location pos2, Location pivot, File file) throws IOException {
        requireWorldEdit("save schematics");
        CuboidRegion region = new CuboidRegion(
                BukkitAdapter.adapt(pos1.getWorld()),
                BukkitAdapter.asBlockVector(pos1),
                BukkitAdapter.asBlockVector(pos2));
        BlockArrayClipboard clipboard = new BlockArrayClipboard(region);
        try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(pos1.getWorld()))
                .build()) {
            ForwardExtentCopy copy = new ForwardExtentCopy(session, region, clipboard, region.getMinimumPoint());
            try {
                Operations.complete(copy);
            } catch (WorldEditException e) {
                throw new IOException("Failed to copy the selected region: " + e.getMessage(), e);
            }
        }
        // The pivot point: the saver's block position.
        clipboard.setOrigin(BukkitAdapter.asBlockVector(pivot));
        ClipboardFormat format = ClipboardFormats.findByAlias("schem");
        if (format == null) {
            throw new IOException("No WorldEdit .schem clipboard format is available.");
        }
        try (OutputStream output = new FileOutputStream(file);
             ClipboardWriter writer = format.getWriter(output)) {
            writer.write(clipboard);
        }
    }

    /**
     * Loads the {@code .schem} file at {@code file} and pastes it so its saved
     * pivot (origin) lands exactly on {@code target}.
     *
     * @throws IOException when loading or pasting fails, or WorldEdit is
     *         unavailable
     */
    public static void loadSchematic(File file, Location target) throws IOException {
        requireWorldEdit("load schematics");
        if (file == null || !file.isFile()) {
            throw new FileNotFoundException("Schematic file not found: "
                    + (file == null ? "null" : file.getAbsolutePath()));
        }
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            throw new IOException("Unsupported or corrupt schematic file: " + file.getName());
        }
        Clipboard clipboard;
        try (InputStream input = new FileInputStream(file)) {
            try (ClipboardReader reader = format.getReader(input)) {
                clipboard = reader.read();
            }
        }
        try (EditSession session = WorldEdit.getInstance().newEditSessionBuilder()
                .world(BukkitAdapter.adapt(target.getWorld()))
                .build()) {
            Operation paste = new ClipboardHolder(clipboard).createPaste(session)
                    .to(BukkitAdapter.asBlockVector(target))
                    .ignoreAirBlocks(false)
                    .build();
            try {
                Operations.complete(paste);
            } catch (WorldEditException e) {
                throw new IOException("Failed to paste schematic '" + file.getName() + "': " + e.getMessage(), e);
            }
        }
    }

    /**
     * Returns a human-readable message describing that WorldEdit is missing
     * or too old, including the minimum version to install.
     */
    public static String missingWorldEditMessage() {
        if (!isWorldEditAvailable()) {
            return "WorldEdit is not installed. JManhunt schematics require WorldEdit "
                    + MINIMUM_VERSION + " or newer - install it and restart the server.";
        }
        return "WorldEdit " + installedVersion() + " is too old; JManhunt schematics require "
                + MINIMUM_VERSION + " or newer.";
    }

    private static void requireWorldEdit(String action) throws IOException {
        if (!isWorldEditAvailable()) {
            throw new IOException("WorldEdit " + MINIMUM_VERSION + " or newer is required to "
                    + action + "; it is not installed.");
        }
        if (!isWorldEditSupported()) {
            throw new IOException("WorldEdit " + MINIMUM_VERSION + " or newer is required to "
                    + action + "; installed version is " + installedVersion() + ".");
        }
    }
}
