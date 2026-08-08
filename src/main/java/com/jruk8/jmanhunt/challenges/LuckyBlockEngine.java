package com.jruk8.jmanhunt.challenges;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure-logic engine for parsing the lucky-blocks.yml outcome table and
 * rolling a random outcome. Does not depend on a running server.
 *
 * <p>Outcomes are composable: each outcome may contain any combination of
 * {@code items}, {@code commands}, {@code structure}, and {@code feedback}
 * sections. An outcome with none of these sections is a valid empty outcome
 * that does nothing (unless feedback is configured).</p>
 *
 * <p>Selection is two-stage: first a rarity is rolled using the top-level
 * {@code rarities} weights, then an outcome within that rarity is rolled
 * using each outcome's own {@code weight} (default 1.0). Outcomes without an
 * explicit {@code rarity} default to {@code common}.</p>
 */
public final class LuckyBlockEngine {
    public record Sound(boolean enabled, String sound, float pitch, float volume) {}

    public record Feedback(Sound sound, String message, String broadcast) {}

    public record StructureSettings(String name, boolean randomRotation) {}

    /**
     * A single item entry parsed from the {@code items} section. The item
     * string may be a plain material name or the modern data-component form
     * used by the {@code /give} command, e.g.
     * {@code minecraft:golden_sword[enchantments={sharpness:10},damage=29]}.
     */
    public record ItemEntry(String item, int quantity) {}

    /** A single step in a command sequence: either an actual command or a delay in ticks. */
    public sealed interface CommandStep permits CommandStep.Command, CommandStep.Delay {
        /** A console command to dispatch. */
        record Command(String command) implements CommandStep {}
        /** A pause in ticks before the next command step runs. */
        record Delay(int ticks) implements CommandStep {}
    }

    /**
     * A composable lucky block outcome. Any of the action fields may be null
     * or empty; an outcome with no actions is a valid empty outcome.
     */
    public record Outcome(String name, String rarity, double weight,
                          List<ItemEntry> items,
                          List<CommandStep> commands,
                          String relativeTo,
                          StructureSettings structure,
                          Feedback feedback) {}

    private static final Map<String, Double> DEFAULT_RARITIES = Map.of(
            "common", 60.0,
            "rare", 22.0,
            "epic", 12.0,
            "legendary", 6.0);
    private static final String DEFAULT_RARITY = "common";

    private final Map<String, Double> rarities = new LinkedHashMap<>();
    private final List<Outcome> outcomes = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private double totalRarityWeight = 0.0;

    /** Clears all loaded rarities, outcomes and warnings. */
    public void clear() {
        rarities.clear();
        outcomes.clear();
        warnings.clear();
        totalRarityWeight = 0.0;
    }

    /**
     * Loads rarities and outcomes from the given YAML file.
     * Returns false on parse failure.
     */
    public boolean load(File file) {
        clear();
        if (file == null || !file.exists()) return false;
        Map<String, Object> root;
        try (InputStream in = new FileInputStream(file)) {
            Object loaded = new Yaml().load(in);
            if (loaded == null) return false;
            if (!(loaded instanceof Map<?, ?> map)) return false;
            root = castMap(map);
        } catch (IOException e) {
            return false;
        }

        loadRarities(asSection(root.get("rarities")));
        if (rarities.isEmpty()) {
            rarities.putAll(DEFAULT_RARITIES);
            totalRarityWeight = DEFAULT_RARITIES.values().stream().mapToDouble(Double::doubleValue).sum();
        }

        Map<String, Object> section = asSection(root.get("outcomes"));
        if (section == null) return false;
        for (String name : section.keySet()) {
            Map<String, Object> entry = asSection(section.get(name));
            if (entry == null) {
                throw new IllegalArgumentException("Entry '" + name + "' is not a section");
            }
            double weight = getDouble(entry.get("weight"), 1.0);
            if (weight <= 0) {
                throw new IllegalArgumentException("Entry '" + name + "' has non-positive weight " + weight);
            }

            String rarity = getString(entry.get("rarity"), DEFAULT_RARITY).toLowerCase();
            if (!rarities.containsKey(rarity)) {
                warnings.add("Entry '" + name + "' uses unknown rarity '" + rarity
                        + "'; defaulting to '" + DEFAULT_RARITY + "'.");
                rarity = DEFAULT_RARITY;
            }

            List<ItemEntry> items = parseItems(entry, name);
            List<CommandStep> commands = List.of();
            String relativeTo = "BLOCK";
            Map<String, Object> cmdSection = asSection(entry.get("commands"));
            if (cmdSection != null) {
                relativeTo = getString(cmdSection.get("relative-to"), "BLOCK").toUpperCase();
                if (!relativeTo.equals("BLOCK") && !relativeTo.equals("PLAYER")) {
                    throw new IllegalArgumentException("Entry '" + name + "' has invalid relative-to '" + relativeTo + "'");
                }
                commands = parseCommands(getStringList(cmdSection.get("commands")), name);
            }

            StructureSettings structure = parseStructure(entry, name);
            Feedback feedback = parseFeedback(entry, name);

            outcomes.add(new Outcome(name, rarity, weight, items, commands, relativeTo, structure, feedback));
        }
        return !outcomes.isEmpty();
    }

    /**
     * Rolls a random outcome using two-stage weighted selection: first pick a
     * rarity by the top-level {@code rarities} weights, then pick an outcome
     * within that rarity by the outcome's own weight. Returns null if no
     * outcomes are loaded.
     */
    public Outcome roll() {
        if (outcomes.isEmpty() || totalRarityWeight <= 0) return null;
        Outcome outcome = pickOutcome(pickRarity());
        if (outcome != null) return outcome;
        // The chosen rarity had no outcomes; fall back to any outcome.
        return pickOutcome(null);
    }

    /** Returns an immutable copy of the loaded outcomes. */
    public List<Outcome> outcomes() {
        return List.copyOf(outcomes);
    }

    /** Returns non-fatal warnings collected during the last load, e.g. unknown rarities. */
    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    private void loadRarities(Map<String, Object> section) {
        if (section == null) return;
        for (String key : section.keySet()) {
            String rarity = key.toLowerCase();
            double weight = getDouble(section.get(key), 0.0);
            if (weight <= 0) {
                throw new IllegalArgumentException("Rarity '" + rarity + "' has non-positive weight " + weight);
            }
            rarities.put(rarity, weight);
            totalRarityWeight += weight;
        }
    }

    private String pickRarity() {
        double roll = ThreadLocalRandom.current().nextDouble() * totalRarityWeight;
        double current = 0.0;
        for (Map.Entry<String, Double> entry : rarities.entrySet()) {
            current += entry.getValue();
            if (roll < current) return entry.getKey();
        }
        return rarities.keySet().iterator().next();
    }

    private Outcome pickOutcome(String rarity) {
        double poolWeight = 0.0;
        for (Outcome outcome : outcomes) {
            if (rarity == null || rarity.equals(outcome.rarity())) {
                poolWeight += outcome.weight();
            }
        }
        if (poolWeight <= 0) return null;
        double roll = ThreadLocalRandom.current().nextDouble() * poolWeight;
        double current = 0.0;
        for (Outcome outcome : outcomes) {
            if (rarity == null || rarity.equals(outcome.rarity())) {
                current += outcome.weight();
                if (roll < current) return outcome;
            }
        }
        return outcomes.get(outcomes.size() - 1);
    }

    /**
     * Parses {@code items} list entries. Each line is either:
     * <ul>
     *   <li>a plain material with optional quantity: {@code diamond 4}</li>
     *   <li>the modern give-command component form with optional quantity:
     *       {@code golden_sword[enchantments={sharpness:3},damage=29] 1}</li>
     *   <li>a material, quantity, and trailing component spec:
     *       {@code golden_sword 1 enchantments={sharpness:3},damage=29}</li>
     * </ul>
     */
    private List<ItemEntry> parseItems(Map<String, Object> entry, String name) {
        List<String> raw = getStringList(entry.get("items"));
        if (raw.isEmpty()) return List.of();
        List<ItemEntry> parsed = new ArrayList<>();
        for (String line : raw) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("\\s+");
            String itemName = parts[0];
            int quantity = 1;
            int index = 1;
            if (parts.length > index && parts[index].matches("\\d+")) {
                quantity = Math.max(1, Integer.parseInt(parts[index]));
                index++;
            }
            if (itemName.isBlank()) {
                throw new IllegalArgumentException("Entry '" + name + "' has an empty item name in '" + line + "'");
            }
            // Any trailing tokens are data-component syntax, appended as
            // material[component,component,...] exactly like the /give command.
            if (index < parts.length) {
                StringBuilder components = new StringBuilder();
                for (int i = index; i < parts.length; i++) {
                    if (components.length() > 0) components.append(',');
                    components.append(parts[i]);
                }
                String comps = components.toString();
                if (comps.startsWith("[") && comps.endsWith("]")) {
                    comps = comps.substring(1, comps.length() - 1);
                }
                itemName = itemName + "[" + comps + "]";
            }
            parsed.add(new ItemEntry(itemName, quantity));
        }
        return parsed;
    }

    /**
     * Parses the {@code commands} list. Each line is either a console command
     * or a {@code delay: <ticks>} entry that pauses the sequence.
     */
    private List<CommandStep> parseCommands(List<String> raw, String name) {
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Entry '" + name + "' has a commands section but no commands");
        }
        List<CommandStep> parsed = new ArrayList<>();
        for (String line : raw) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (trimmed.toLowerCase().startsWith("delay:")) {
                String ticksStr = trimmed.substring("delay:".length()).trim();
                int ticks;
                try {
                    ticks = Integer.parseInt(ticksStr);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Entry '" + name + "' has invalid delay '" + trimmed + "'");
                }
                if (ticks <= 0) {
                    throw new IllegalArgumentException("Entry '" + name + "' has non-positive delay '" + trimmed + "'");
                }
                parsed.add(new CommandStep.Delay(ticks));
            } else {
                parsed.add(new CommandStep.Command(trimmed));
            }
        }
        return parsed;
    }

    private StructureSettings parseStructure(Map<String, Object> entry, String name) {
        Map<String, Object> struct = asSection(entry.get("structure"));
        if (struct == null) return null;
        String structName = getString(struct.get("name"), null);
        if (structName == null || structName.isBlank()) {
            throw new IllegalArgumentException("Entry '" + name + "' has a structure section but is missing structure.name");
        }
        boolean randomRotation = getBoolean(struct.get("random-rotation"), false);
        return new StructureSettings(structName, randomRotation);
    }

    private Feedback parseFeedback(Map<String, Object> entry, String name) {
        Map<String, Object> feedbackSection = asSection(entry.get("feedback"));
        if (feedbackSection == null) return null;

        Sound sound = null;
        Map<String, Object> soundSection = asSection(feedbackSection.get("sound"));
        if (soundSection != null) {
            if (!soundSection.containsKey("enabled")) {
                throw new IllegalArgumentException("Entry '" + name + "' feedback.sound is missing 'enabled'");
            }
            String soundName = getString(soundSection.get("sound"), null);
            if (soundName == null || soundName.isBlank()) {
                throw new IllegalArgumentException("Entry '" + name + "' feedback.sound is missing 'sound'");
            }
            boolean enabled = getBoolean(soundSection.get("enabled"), false);
            float pitch = (float) getDouble(soundSection.get("pitch"), 1.0);
            float volume = (float) getDouble(soundSection.get("volume"), 1.0);
            sound = new Sound(enabled, soundName, pitch, volume);
        }

        String message = getString(feedbackSection.get("message"), null);
        String broadcast = getString(feedbackSection.get("broadcast"), null);

        return new Feedback(sound, message, broadcast);
    }

    // ===================== YAML helper methods =====================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            result.put(String.valueOf(e.getKey()), e.getValue());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asSection(Object value) {
        if (value instanceof Map<?, ?> map) {
            return castMap(map);
        }
        return null;
    }

    private static String getString(Object value, String defaultValue) {
        if (value == null) return defaultValue;
        return String.valueOf(value);
    }

    private static double getDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean getBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    @SuppressWarnings("unchecked")
    private static List<String> getStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        return List.of();
    }

}
