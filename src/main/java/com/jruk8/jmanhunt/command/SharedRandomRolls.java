package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

/** Shared random draws behind {@link CommandPlaceholders}. */
public final class SharedRandomRolls {
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
        List<EntityType> mobs = CommandPlaceholders.spawnableLivingEntities();
        return mobs.get(ThreadLocalRandom.current().nextInt(mobs.size())).name().toLowerCase(Locale.ROOT);
    }

    static String randomItem() {
        List<Material> itemList = CommandPlaceholders.items();
        return itemList.get(ThreadLocalRandom.current().nextInt(itemList.size())).name().toLowerCase(Locale.ROOT);
    }
}
