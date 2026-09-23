package com.jruk8.jmanhunt.lobby.bounds;

import org.bukkit.Color;

/**
 * Debug-only lobby-bounds particle tuning. Internal visual aid, so the
 * knobs live here as static fields and never in config.yml.
 */
public final class LobbyBoundsPalette {
    /** Only boxes within this many blocks of the player are drawn. */
    public static final double CHECK_RADIUS_BLOCKS = 25.0;
    /** Seconds between particle refreshes. */
    public static final double REFRESH_SECONDS = 0.5;
    /** Spacing between particles along a box edge, in blocks. */
    public static final double EDGE_STEP_BLOCKS = 1.0;
    /** Dust particle size. */
    public static final float PARTICLE_SIZE = 1.0f;
    /** Pending-selection boxes are always white. */
    public static final Color PENDING_COLOR = Color.WHITE;

    private static final int COLORS_PER_LEVEL = 6;
    private static final int LEVELS = 4;

    private LobbyBoundsPalette() {
    }

    /** Ticks between refreshes for the scheduler. */
    public static long refreshTicks() {
        return Math.max(1L, Math.round(REFRESH_SECONDS * 20.0));
    }

    /**
     * Maximum-contrast color for the nth box: full red, green, blue,
     * yellow, cyan, magenta, then the same six with each channel halved
     * per level, wrapping to full brightness when levels run out.
     */
    public static Color colorForIndex(int index) {
        int slot = Math.floorMod(index, COLORS_PER_LEVEL * LEVELS);
        int channel = 255 >> (slot / COLORS_PER_LEVEL);
        return switch (slot % COLORS_PER_LEVEL) {
            case 0 -> Color.fromRGB(channel, 0, 0);
            case 1 -> Color.fromRGB(0, channel, 0);
            case 2 -> Color.fromRGB(0, 0, channel);
            case 3 -> Color.fromRGB(channel, channel, 0);
            case 4 -> Color.fromRGB(0, channel, channel);
            default -> Color.fromRGB(channel, 0, channel);
        };
    }
}
