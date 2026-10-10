package com.jruk8.jmanhunt.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/** Shared random draws behind {@link CommandPlaceholders}. */
public final class SharedRandomRolls {
    // Lazily initialized to avoid IllegalStateException when the class is
    // loaded in a unit test without a running Bukkit server.
    private static volatile List<EntityType> spawnableLiving;
    private static volatile List<Material> items;

    private SharedRandomRolls() {
    }

    public static String rollSharedRandom(String name) {
        return "random-mob".equals(name) ? randomMob() : randomItem();
    }

    static boolean containsSharedRandom(String line) {
        Matcher matcher = CommandPlaceholders.INNER_TAG.matcher(line);
        while (matcher.find()) {
            if (isSharedRandom(matcher.group(1))) {
                return true;
            }
        }
        return false;
    }

    static boolean isSharedRandom(String body) {
        String name = CommandPlaceholders.tagName(body);
        return "random-mob".equals(name) || "random-item".equals(name);
    }

    static long nextLong(ModifierTagScope scope, long bound) {
        long drawn = scope.random().nextLong() >>> 1;
        return drawn % bound;
    }

    static String randomMob() {
        List<EntityType> mobs = spawnableLivingEntities();
        return mobs.get(ThreadLocalRandom.current().nextInt(mobs.size())).name().toLowerCase(Locale.ROOT);
    }

    static String randomItem() {
        List<Material> itemList = items();
        return itemList.get(ThreadLocalRandom.current().nextInt(itemList.size())).name().toLowerCase(Locale.ROOT);
    }

    /** Cached spawnable living entity types behind {@code <random-mob>}. */
    static List<EntityType> spawnableLivingEntities() {
        if (spawnableLiving == null) {
            synchronized (SharedRandomRolls.class) {
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

    /** Cached item materials behind {@code <random-item>}. */
    static List<Material> items() {
        if (items == null) {
            synchronized (SharedRandomRolls.class) {
                if (items == null) {
                    items = Arrays.stream(Material.values())
                            .filter(Material::isItem)
                            .toList();
                }
            }
        }
        return items;
    }
}
