package com.jruk8.jmanhunt.world.cell;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.structure.Structure;

/**
 * Thin structure proximity lookup: true when any configured structure
 * sits within radius of a cell origin. Keys come from
 * StructurePreference; this only resolves and queries them. Main
 * thread only, like every other cell fetch step.
 */
public final class StructureProximity {
    private final List<Structure> structures;
    private final int radius;

    public StructureProximity(List<String> keys, int radius) {
        List<Structure> structures = new ArrayList<>();
        for (String key : keys) {
            structures.add(structureFor(key));
        }
        this.structures = List.copyOf(structures);
        this.radius = radius;
    }

    public boolean isEmpty() {
        return structures.isEmpty();
    }

    /** True when any configured structure is within radius of the origin. */
    public boolean nearStructure(World world, int x, int y, int z) {
        Location origin = new Location(world, x, y, z);
        for (Structure structure : structures) {
            if (world.locateNearestStructure(origin, structure, radius, false) != null) {
                return true;
            }
        }
        return false;
    }

    /** Resolves a preference key to its Structure handle. */
    private static Structure structureFor(String key) {
        return switch (key) {
            case "minecraft:village_desert" -> Structure.VILLAGE_DESERT;
            case "minecraft:village_plains" -> Structure.VILLAGE_PLAINS;
            case "minecraft:village_savanna" -> Structure.VILLAGE_SAVANNA;
            case "minecraft:village_snowy" -> Structure.VILLAGE_SNOWY;
            case "minecraft:village_taiga" -> Structure.VILLAGE_TAIGA;
            case "minecraft:desert_pyramid" -> Structure.DESERT_PYRAMID;
            case "minecraft:jungle_pyramid" -> Structure.JUNGLE_PYRAMID;
            case "minecraft:shipwreck" -> Structure.SHIPWRECK;
            case "minecraft:shipwreck_beached" -> Structure.SHIPWRECK_BEACHED;
            case "minecraft:ruined_portal" -> Structure.RUINED_PORTAL;
            case "minecraft:ruined_portal_desert" -> Structure.RUINED_PORTAL_DESERT;
            case "minecraft:ruined_portal_jungle" -> Structure.RUINED_PORTAL_JUNGLE;
            case "minecraft:ruined_portal_mountain" -> Structure.RUINED_PORTAL_MOUNTAIN;
            case "minecraft:ruined_portal_nether" -> Structure.RUINED_PORTAL_NETHER;
            case "minecraft:ruined_portal_ocean" -> Structure.RUINED_PORTAL_OCEAN;
            case "minecraft:ruined_portal_swamp" -> Structure.RUINED_PORTAL_SWAMP;
            case "minecraft:buried_treasure" -> Structure.BURIED_TREASURE;
            case "minecraft:mineshaft" -> Structure.MINESHAFT;
            case "minecraft:mineshaft_mesa" -> Structure.MINESHAFT_MESA;
            case "minecraft:monument" -> Structure.MONUMENT;
            case "minecraft:mansion" -> Structure.MANSION;
            case "minecraft:pillager_outpost" -> Structure.PILLAGER_OUTPOST;
            default -> throw new IllegalArgumentException("Unknown structure key: " + key);
        };
    }
}
