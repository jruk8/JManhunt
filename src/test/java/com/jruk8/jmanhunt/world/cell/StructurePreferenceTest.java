package com.jruk8.jmanhunt.world.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** StructurePreference: vocabulary words to vanilla structure keys. */
class StructurePreferenceTest {

    @Test
    void everyWordMapsToExpectedKeys() {
        assertEquals(List.of("minecraft:village_desert", "minecraft:village_plains",
                "minecraft:village_savanna", "minecraft:village_snowy", "minecraft:village_taiga"),
                StructurePreference.VILLAGE.keys());
        assertEquals(List.of("minecraft:desert_pyramid", "minecraft:jungle_pyramid"),
                StructurePreference.TEMPLE.keys());
        assertEquals(List.of("minecraft:shipwreck", "minecraft:shipwreck_beached"),
                StructurePreference.SHIPWRECK.keys());
        assertEquals(7, StructurePreference.RUINED_PORTAL.keys().size());
        assertTrue(StructurePreference.RUINED_PORTAL.keys().contains("minecraft:ruined_portal"));
        assertEquals(List.of("minecraft:buried_treasure"),
                StructurePreference.BURIED_TREASURE.keys());
        assertEquals(List.of("minecraft:mineshaft", "minecraft:mineshaft_mesa"),
                StructurePreference.MINESHAFT.keys());
        assertEquals(List.of("minecraft:monument"), StructurePreference.OCEAN_MONUMENT.keys());
        assertEquals(List.of("minecraft:mansion"), StructurePreference.MANSION.keys());
        assertEquals(List.of("minecraft:pillager_outpost"),
                StructurePreference.PILLAGER_OUTPOST.keys());
    }

    @Test
    void keysForIsCaseBlindDedupesAndSkipsUnknowns() {
        List<String> unknown = new ArrayList<>();
        List<String> keys = StructurePreference.keysFor(
                List.of("village", "VILLAGE", "temple", "  ", "stronghold", "MANSION"), unknown::add);

        assertEquals(List.of("minecraft:village_desert", "minecraft:village_plains",
                "minecraft:village_savanna", "minecraft:village_snowy", "minecraft:village_taiga",
                "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:mansion"), keys);
        assertEquals(List.of("stronghold"), unknown);
    }

    @Test
    void keysForSkipsNullsAndBlanksQuietly() {
        List<String> unknown = new ArrayList<>();
        List<String> keys = StructurePreference.keysFor(
                java.util.Arrays.asList(null, "   "), unknown::add);

        assertTrue(keys.isEmpty());
        assertTrue(unknown.isEmpty());
    }
}
