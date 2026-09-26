package com.jruk8.jmanhunt.world.cell;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Close-to-structure vocabulary: each word maps to the vanilla
 * structure keys it accepts. Bukkit-free so the mapping stays unit
 * testable; the lookup layer resolves keys to Structure handles.
 */
public enum StructurePreference {
    VILLAGE("minecraft:village_desert", "minecraft:village_plains",
            "minecraft:village_savanna", "minecraft:village_snowy",
            "minecraft:village_taiga"),
    TEMPLE("minecraft:desert_pyramid", "minecraft:jungle_pyramid"),
    SHIPWRECK("minecraft:shipwreck", "minecraft:shipwreck_beached"),
    RUINED_PORTAL("minecraft:ruined_portal", "minecraft:ruined_portal_desert",
            "minecraft:ruined_portal_jungle", "minecraft:ruined_portal_mountain",
            "minecraft:ruined_portal_nether", "minecraft:ruined_portal_ocean",
            "minecraft:ruined_portal_swamp"),
    BURIED_TREASURE("minecraft:buried_treasure"),
    MINESHAFT("minecraft:mineshaft", "minecraft:mineshaft_mesa"),
    OCEAN_MONUMENT("minecraft:monument"),
    MANSION("minecraft:mansion"),
    PILLAGER_OUTPOST("minecraft:pillager_outpost");

    private final List<String> keys;

    StructurePreference(String... keys) {
        this.keys = List.of(keys);
    }

    public List<String> keys() {
        return keys;
    }

    /**
     * Resolves user words to structure keys, case-blind, duplicates
     * removed, order kept. Unknown words are skipped and reported to
     * onUnknown so the caller can warn once per fetch.
     */
    public static List<String> keysFor(List<String> words, Consumer<String> onUnknown) {
        Set<String> keys = new LinkedHashSet<>();
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            String trimmed = word.strip();
            StructurePreference match = null;
            for (StructurePreference preference : values()) {
                if (preference.name().equalsIgnoreCase(trimmed)) {
                    match = preference;
                    break;
                }
            }
            if (match == null) {
                onUnknown.accept(trimmed);
                continue;
            }
            keys.addAll(match.keys());
        }
        return new ArrayList<>(keys);
    }
}
