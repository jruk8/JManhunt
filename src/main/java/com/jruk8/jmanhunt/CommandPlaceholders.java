package com.jruk8.jmanhunt;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces placeholders and resolves relative coordinates (~) in custom
 * modifier commands. All command dispatch stays through the console sender,
 * so tilde resolution is done here rather than relying on the sender's
 * location.
 */
public final class CommandPlaceholders {
    private static final Pattern TILDE_PATTERN = Pattern.compile("~([+-]?\\d+(?:\\.\\d+)?)?");

    // Lazily initialized to avoid IllegalStateException when the class is
    // loaded in a unit test without a running Bukkit server.
    private static volatile List<EntityType> spawnableLiving;
    private static volatile List<Material> items;

    private CommandPlaceholders() {
    }

    /**
     * Replaces all placeholders in a command string.
     *
     * @param command    the raw command from config
     * @param playerName the participating player's name, or null for console commands
     * @param x          the player's x coordinate for tilde resolution, or 0 if no player
     * @param y          the player's y coordinate for tilde resolution, or 0 if no player
     * @param z          the player's z coordinate for tilde resolution, or 0 if no player
     * @return the parsed command ready for console dispatch
     */
    public static String replace(String command, String playerName, double x, double y, double z) {
        String parsed = command;
        if (playerName != null) {
            parsed = parsed.replace("<p>", playerName);
        }
        if (parsed.contains("<random-mob>")) {
            parsed = parsed.replace("<random-mob>", randomMob());
        }
        if (parsed.contains("<random-item>")) {
            parsed = parsed.replace("<random-item>", randomItem());
        }
        if (playerName != null) {
            parsed = resolveTildes(parsed, x, y, z);
        }
        return parsed;
    }

    /**
     * Resolves relative coordinates (~, ~5, ~-3) to absolute coordinates using
     * the given reference position. Tildes cycle through x, y, z in order.
     * This is package-private for testing.
     */
    static String resolveTildes(String command, double x, double y, double z) {
        Matcher matcher = TILDE_PATTERN.matcher(command);
        StringBuilder result = new StringBuilder();
        int[] index = {0};
        double[] coords = {x, y, z};
        while (matcher.find()) {
            String offsetStr = matcher.group(1);
            double offset = offsetStr == null ? 0.0 : Double.parseDouble(offsetStr);
            double base = coords[index[0] % 3];
            double resolved = base + offset;
            // Use integer string when the value is a whole number, otherwise
            // keep the decimal to avoid ".0" in coordinates.
            String replacement = resolved == Math.floor(resolved)
                    ? String.valueOf((long) resolved)
                    : String.valueOf(resolved);
            matcher.appendReplacement(result, replacement);
            index[0]++;
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Returns the list of spawnable living entity types. Package-private for testing. */
    static List<EntityType> spawnableLivingEntities() {
        if (spawnableLiving == null) {
            synchronized (CommandPlaceholders.class) {
                if (spawnableLiving == null) {
                    spawnableLiving = Arrays.stream(EntityType.values())
                            .filter(EntityType::isSpawnable)
                            .filter(EntityType::isAlive)
                            .toList();
                }
            }
        }
        return spawnableLiving;
    }

    /** Returns the list of item materials. Package-private for testing. */
    static List<Material> items() {
        if (items == null) {
            synchronized (CommandPlaceholders.class) {
                if (items == null) {
                    items = Arrays.stream(Material.values())
                            .filter(Material::isItem)
                            .toList();
                }
            }
        }
        return items;
    }

    private static String randomMob() {
        List<EntityType> mobs = spawnableLivingEntities();
        return mobs.get(ThreadLocalRandom.current().nextInt(mobs.size())).name().toLowerCase(Locale.ROOT);
    }

    private static String randomItem() {
        List<Material> itemList = items();
        return itemList.get(ThreadLocalRandom.current().nextInt(itemList.size())).name().toLowerCase(Locale.ROOT);
    }
}