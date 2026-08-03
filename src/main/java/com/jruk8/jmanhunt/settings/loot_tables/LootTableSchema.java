package com.jruk8.jmanhunt.settings.loot_tables;

import com.google.gson.JsonElement;

import java.util.List;

public final class LootTableSchema {
    public record LootTable(List<Pool> pools) {}

    public record Pool(List<Entry> entries) {}

    public record Entry(String type, String name, int weight, List<Function> functions) {}

    public record Function(String function, JsonElement count, String id) {}
}
