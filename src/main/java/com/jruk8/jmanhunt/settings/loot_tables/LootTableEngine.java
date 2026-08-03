package com.jruk8.jmanhunt.settings.loot_tables;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class LootTableEngine {
    private static final Gson GSON = new Gson();
    private static final Random RANDOM = new Random();

    private record ParsedEntry(int weight, Material material, int minCount, int maxCount) {}

    private final List<ParsedEntry> parsedTable = new ArrayList<>();
    private int totalWeight = 0;

    static int[] parseCountRange(JsonElement countElement) {
        int min = 1;
        int max = 1;

        if (countElement == null || countElement.isJsonNull()) {
            return new int[]{min, max};
        }

        if (countElement.isJsonPrimitive() && countElement.getAsJsonPrimitive().isNumber()) {
            int count = countElement.getAsInt();
            return new int[]{count, count};
        }

        if (countElement.isJsonObject()) {
            JsonObject countObject = countElement.getAsJsonObject();
            JsonElement minElement = countObject.get("min");
            JsonElement maxElement = countObject.get("max");

            if (minElement != null && minElement.isJsonPrimitive() && minElement.getAsJsonPrimitive().isNumber()) {
                min = minElement.getAsInt();
            }
            if (maxElement != null && maxElement.isJsonPrimitive() && maxElement.getAsJsonPrimitive().isNumber()) {
                max = maxElement.getAsInt();
            }
        }

        return new int[]{min, max};
    }

    public boolean loadFromFile(File jsonFile) {
        parsedTable.clear();
        totalWeight = 0;

        if (!jsonFile.exists()) return false;

        try (FileReader reader = new FileReader(jsonFile)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return false;

            LootTableSchema.LootTable table = GSON.fromJson(root, LootTableSchema.LootTable.class);

            if (table == null || table.pools() == null) return false;

            for (LootTableSchema.Pool pool : table.pools()) {
                if (pool.entries() == null) continue;

                for (LootTableSchema.Entry entry : pool.entries()) {
                    if (entry.name() == null) continue;

                    String rawMat = entry.name().replace("minecraft:", "").toUpperCase();
                    Material mat = Material.matchMaterial(rawMat);

                    if (mat == null) continue;

                    int weight = entry.weight() > 0 ? entry.weight() : 1;
                    int min = 1;
                    int max = 1;

                    if (entry.functions() != null) {
                        for (LootTableSchema.Function func : entry.functions()) {
                            if (!"minecraft:set_count".equals(func.function()) || func.count() == null) continue;

                            int[] countRange = parseCountRange(func.count());
                            min = countRange[0];
                            max = countRange[1];
                        }
                    }

                    parsedTable.add(new ParsedEntry(weight, mat, min, max));
                    totalWeight += weight;
                }
            }
            return true;

        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<ItemStack> getRandomLoot() {
        if (parsedTable.isEmpty() || totalWeight <= 0) {
            return List.of(new ItemStack(Material.GRAVEL, 8)); // Fallback if file is corrupt
        }

        int roll = RANDOM.nextInt(totalWeight);
        int current = 0;

        for (ParsedEntry entry : parsedTable) {
            current += entry.weight();
            if (roll < current) {
                int amount = entry.minCount() == entry.maxCount()
                        ? entry.minCount()
                        : RANDOM.nextInt((entry.maxCount() - entry.minCount()) + 1) + entry.minCount();

                return List.of(new ItemStack(entry.material(), amount));
            }
        }

        return List.of(new ItemStack(Material.GRAVEL, 8));

    }
}
