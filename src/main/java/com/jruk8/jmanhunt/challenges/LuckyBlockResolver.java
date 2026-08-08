package com.jruk8.jmanhunt.challenges;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Resolves the lucky block definition, including random block selection. */
public final class LuckyBlockResolver {
    private static final Set<String> UNOBTAINABLE = Set.of(
            "air", "cave_air", "void_air", "barrier", "bedrock", "water", "lava", "fire", "command_block",
            "chain_command_block", "repeating_command_block", "structure_block", "structure_void", "jigsaw", "light",
            "spawner", "nether_portal", "end_portal", "end_portal_frame", "end_gateway", "moving_piston", "piston_head",
            "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "sugar_cane", "vine", "glow_lichen",
            "sculk_vein", "kelp", "seagrass", "tall_seagrass", "bamboo_sapling", "suspicious_sand", "suspicious_gravel",
            "big_dripleaf_stem", "small_dripleaf", "attached_pumpkin_stem", "attached_melon_stem", "pumpkin_stem",
            "melon_stem", "cocoa", "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant",
            "cave_vines", "cave_vines_plant", "tripwire", "tripwire_hook", "redstone_wire", "torch", "wall_torch",
            "soul_torch", "soul_wall_torch", "redstone_torch", "redstone_wall_torch", "ladder", "rail", "powered_rail",
            "detector_rail", "activator_rail", "lever", "stone_button", "oak_button", "spruce_button", "birch_button",
            "jungle_button", "acacia_button", "dark_oak_button", "mangrove_button", "cherry_button", "bamboo_button",
            "crimson_button", "warped_button", "polished_blackstone_button", "stone_pressure_plate", "oak_pressure_plate",
            "spruce_pressure_plate", "birch_pressure_plate", "jungle_pressure_plate", "acacia_pressure_plate",
            "dark_oak_pressure_plate", "mangrove_pressure_plate", "cherry_pressure_plate", "bamboo_pressure_plate",
            "crimson_pressure_plate", "warped_pressure_plate", "polished_blackstone_pressure_plate",
            "heavy_weighted_pressure_plate", "light_weighted_pressure_plate", "repeater", "comparator", "daylight_detector",
            "target", "decorated_pot", "player_head", "player_wall_head", "zombie_head", "zombie_wall_head",
            "skeleton_skull", "skeleton_wall_skull", "wither_skeleton_skull", "wither_skeleton_wall_skull", "creeper_head",
            "creeper_wall_head", "dragon_head", "dragon_wall_head", "piglin_head", "piglin_wall_head", "banner",
            "wall_banner", "standing_banner", "item_frame", "glow_item_frame", "painting", "armor_stand", "boat",
            "minecart", "tnt_minecart", "chest_minecart", "furnace_minecart", "hopper_minecart", "command_block_minecart",
            "spawn_egg", "enchanting_table", "ender_chest", "oak_sign", "spruce_sign", "birch_sign", "jungle_sign",
            "acacia_sign", "dark_oak_sign", "mangrove_sign", "cherry_sign", "bamboo_sign", "crimson_sign", "warped_sign",
            "oak_wall_sign", "spruce_wall_sign", "birch_wall_sign", "jungle_wall_sign", "acacia_wall_sign",
            "dark_oak_wall_sign", "mangrove_wall_sign", "cherry_wall_sign", "bamboo_wall_sign", "crimson_wall_sign",
            "warped_wall_sign", "hanging_sign", "oak_hanging_sign", "spruce_hanging_sign", "birch_hanging_sign",
            "jungle_hanging_sign", "acacia_hanging_sign", "dark_oak_hanging_sign", "mangrove_hanging_sign",
            "cherry_hanging_sign", "bamboo_hanging_sign", "crimson_hanging_sign", "warped_hanging_sign",
            "oak_wall_hanging_sign", "spruce_wall_hanging_sign", "birch_wall_hanging_sign", "jungle_wall_hanging_sign",
            "acacia_wall_hanging_sign", "dark_oak_wall_hanging_sign", "mangrove_wall_hanging_sign",
            "cherry_wall_hanging_sign", "bamboo_wall_hanging_sign", "crimson_wall_hanging_sign", "warped_wall_hanging_sign",
            "thrown_potion", "lingering_potion", "experience_bottle", "end_crystal", "firework_rocket", "firework_star",
            "fire_charge", "knowledge_book", "enchanted_book", "ink_sac", "glow_ink_sac", "honey_bottle", "potion",
            "splash_potion", "tipped_arrow", "arrow", "spectral_arrow", "bundle", "brush", "spyglass", "recovery_compass",
            "warped_fungus_on_a_stick", "debug_stick", "dragon_egg", "beacon", "conduit", "lodestone", "sculk_sensor",
            "sculk_shrieker", "sculk_catalyst", "trial_spawner", "vault", "coal_ore", "deepslate_coal_ore", "iron_ore",
            "deepslate_iron_ore", "copper_ore", "deepslate_copper_ore", "gold_ore", "deepslate_gold_ore", "redstone_ore",
            "deepslate_redstone_ore", "lapis_ore", "deepslate_lapis_ore", "diamond_ore", "deepslate_diamond_ore",
            "emerald_ore", "deepslate_emerald_ore", "nether_gold_ore", "nether_quartz_ore", "ancient_debris",
            "infested_stone", "infested_cobblestone", "infested_stone_bricks", "infested_mossy_stone_bricks",
            "infested_cracked_stone_bricks", "infested_chiseled_stone_bricks", "infested_deepslate",
            "infested_cobbled_deepslate", "reinforced_deepslate", "budding_amethyst", "candle", "white_candle",
            "orange_candle", "magenta_candle", "light_blue_candle", "yellow_candle", "lime_candle", "pink_candle",
            "gray_candle", "light_gray_candle", "cyan_candle", "purple_candle", "blue_candle", "brown_candle",
            "green_candle", "red_candle", "black_candle", "candle_cake", "white_candle_cake", "orange_candle_cake",
            "magenta_candle_cake", "light_blue_candle_cake", "yellow_candle_cake", "lime_candle_cake", "pink_candle_cake",
            "gray_candle_cake", "light_gray_candle_cake", "cyan_candle_cake", "purple_candle_cake", "blue_candle_cake",
            "brown_candle_cake", "green_candle_cake", "red_candle_cake", "black_candle_cake", "exposed_copper",
            "weathered_copper", "oxidized_copper", "waxed_copper_block", "waxed_exposed_copper", "waxed_weathered_copper",
            "waxed_oxidized_copper", "exposed_cut_copper", "weathered_cut_copper", "oxidized_cut_copper",
            "waxed_cut_copper", "waxed_exposed_cut_copper", "waxed_weathered_cut_copper", "waxed_oxidized_cut_copper",
            "exposed_cut_copper_stairs", "weathered_cut_copper_stairs", "oxidized_cut_copper_stairs",
            "waxed_cut_copper_stairs", "waxed_exposed_cut_copper_stairs", "waxed_weathered_cut_copper_stairs",
            "waxed_oxidized_cut_copper_stairs", "exposed_cut_copper_slab", "weathered_cut_copper_slab",
            "oxidized_cut_copper_slab", "waxed_cut_copper_slab", "waxed_exposed_cut_copper_slab",
            "waxed_weathered_cut_copper_slab", "waxed_oxidized_cut_copper_slab", "exposed_chiseled_copper",
            "weathered_chiseled_copper", "oxidized_chiseled_copper", "waxed_chiseled_copper",
            "waxed_exposed_chiseled_copper", "waxed_weathered_chiseled_copper", "waxed_oxidized_chiseled_copper",
            "exposed_copper_door", "weathered_copper_door", "oxidized_copper_door", "waxed_copper_door",
            "waxed_exposed_copper_door", "waxed_weathered_copper_door", "waxed_oxidized_copper_door",
            "exposed_copper_trapdoor", "weathered_copper_trapdoor", "oxidized_copper_trapdoor", "waxed_copper_trapdoor",
            "waxed_exposed_copper_trapdoor", "waxed_weathered_copper_trapdoor", "waxed_oxidized_copper_trapdoor",
            "exposed_copper_grate", "weathered_copper_grate", "oxidized_copper_grate", "waxed_copper_grate",
            "waxed_exposed_copper_grate", "waxed_weathered_copper_grate", "waxed_oxidized_copper_grate",
            "exposed_copper_bulb", "weathered_copper_bulb", "oxidized_copper_bulb", "waxed_copper_bulb",
            "waxed_exposed_copper_bulb", "waxed_weathered_copper_bulb", "waxed_oxidized_copper_bulb", "shulker_box",
            "white_shulker_box", "orange_shulker_box", "magenta_shulker_box", "light_blue_shulker_box",
            "yellow_shulker_box", "lime_shulker_box", "pink_shulker_box", "gray_shulker_box", "light_gray_shulker_box",
            "cyan_shulker_box", "purple_shulker_box", "blue_shulker_box", "brown_shulker_box", "green_shulker_box",
            "red_shulker_box", "black_shulker_box", "chorus_flower", "chorus_plant", "netherite_block",
            "diamond_block");

    private LuckyBlockResolver() {
    }

    /** Returns true if the configured definition is "random". */
    public static boolean isRandom(String definition) {
        return definition != null && definition.equalsIgnoreCase("random");
    }

    /**
     * Picks a random registered, solid block material excluding unobtainable
     * blocks (air, barriers, command blocks, structure voids, etc.).
     *
     * @return a random valid block material
     */
    public static Material randomBlock() {
        List<Material> candidates = new ArrayList<>();
        for (Material material : Registry.MATERIAL) {
            if (!material.isBlock() || !material.isSolid() || !material.isItem()) continue;
            if (UNOBTAINABLE.contains(material.getKey().getKey().toLowerCase())) continue;
            candidates.add(material);
        }
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }

    /** Formats a material as a human-readable display name, e.g. GOLD_BLOCK -> "Gold Block". */
    public static String displayName(Material material) {
        String key = material.getKey().getKey();
        String[] words = key.split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (builder.length() > 0) builder.append(' ');
            if (word.isEmpty()) continue;
            builder.append(Character.toUpperCase(word.charAt(0)));
            builder.append(word.substring(1));
        }
        return builder.toString();
    }

    /**
     * Resolves the configured definition to a material, or null if the
     * definition refers to an unknown material.
     *
     * @param definition the raw config value ("random", "minecraft:<name>", or "<name>")
     * @return the resolved material, or null if unresolvable
     */
    public static Material resolve(String definition) {
        if (definition == null || definition.isBlank()) return null;
        if (isRandom(definition)) return randomBlock();
        String normalized = definition.contains(":") ? definition : "minecraft:" + definition;
        NamespacedKey key = NamespacedKey.fromString(normalized);
        if (key == null) return null;
        return Registry.MATERIAL.get(key);
    }
}