package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WorldEdit detection and version checking that never references the WorldEdit
 * API directly, so it can be safely loaded even when WorldEdit is not
 * installed.
 *
 * <p>WorldEdit is a soft dependency used only by the schematic commands and
 * Lucky Block {@code structure} outcomes. Other classes must call these
 * methods (never {@link WorldEditSchematicService}) when checking availability,
 * because executing a method on the WorldEdit-dependent service loads its
 * classes and would fail with {@link NoClassDefFoundError} otherwise.</p>
 */
public final class WorldEditAvailability {
    /** The minimum WorldEdit version required for schematic features. */
    public static final String MINIMUM_VERSION = "7.3.0";
    private static final int MINIMUM_MAJOR = 7;
    private static final int MINIMUM_MINOR = 3;
    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)(?:\\.(\\d+))?");

    private WorldEditAvailability() {
    }

    /**
     * Checks whether the WorldEdit plugin is installed and its API is visible
     * to the plugin's class loader. Catches every throwable so that running
     * without WorldEdit never crashes the caller.
     */
    public static boolean isAvailable() {
        try {
            return Bukkit.getPluginManager().getPlugin("WorldEdit") != null
                    && Class.forName("com.sk89q.worldedit.WorldEdit") != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Pure minimum-version checker used for unit tests and for the runtime
     * minimum WorldEdit check. Accepts forms such as {@code 7.4.5},
     * {@code 7.3.0-SNAPSHOT} and {@code 8.0.0}.
     *
     * @param version the WorldEdit version string
     * @return true when the version is at least MINIMUM_VERSION
     */
    public static boolean isSupportedVersion(String version) {
        if (version == null) {
            return false;
        }
        Matcher matcher = VERSION_PATTERN.matcher(version.trim());
        if (!matcher.find()) {
            return false;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return major > MINIMUM_MAJOR || (major == MINIMUM_MAJOR && minor >= MINIMUM_MINOR);
    }
}
