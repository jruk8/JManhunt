package com.jruk8.jmanhunt.challenges;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure-logic engine for parsing the lucky-blocks.yml outcome table and
 * rolling a random outcome. Does not depend on a running server.
 *
 * <p>Outcomes are composable: each outcome may contain any combination of
 * {@code items}, {@code commands}, {@code structure}, and {@code feedback}
 * sections. An outcome with none of these sections is a valid empty outcome
 * that does nothing (unless feedback is configured).</p>
 */
public final class LuckyBlockEngine {
    public record Sound(boolean enabled, String sound, float pitch, float volume) {}

    public record Feedback(Sound sound, String message, String broadcast) {}

    public record StructureSettings(String name, boolean randomRotation) {}

    /** A single item entry parsed from the {@code items} section. */
    public record ItemEntry(String name, int quantity) {}

    /**
     * A composable lucky block outcome. Any of the action fields may be null
     * or empty; an outcome with no actions is a valid empty outcome.
     */
    public record Outcome(String name, double weight,
                          List<ItemEntry> items,
                          List<String> commands,
                          String relativeTo,
                          StructureSettings structure,
                          Feedback feedback) {}

    private final List<Outcome> outcomes = new ArrayList<>();
    private double totalWeight = 0.0;

    /** Clears all loaded outcomes and resets total weight. */
    public void clear() {
        outcomes.clear();
        totalWeight = 0.0;
    }

    /** Loads outcomes from the given YAML file. Returns false on parse failure. */
    public boolean load(File file) {
        outcomes.clear();
        totalWeight = 0.0;
        if (file == null || !file.exists()) return false;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("outcomes");
        if (section == null) return false;
        for (String name : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(name);
            if (entry == null) {
                throw new IllegalArgumentException("Entry '" + name + "' is not a section");
            }
            double weight = entry.getDouble("weight", 1.0);
            if (weight <= 0) {
                throw new IllegalArgumentException("Entry '" + name + "' has non-positive weight " + weight);
            }

            List<ItemEntry> items = parseItems(entry, name);
            List<String> commands = List.of();
            String relativeTo = "BLOCK";
            ConfigurationSection cmdSection = entry.getConfigurationSection("commands");
            if (cmdSection != null) {
                relativeTo = cmdSection.getString("relative-to", "BLOCK").toUpperCase();
                if (!relativeTo.equals("BLOCK") && !relativeTo.equals("PLAYER")) {
                    throw new IllegalArgumentException("Entry '" + name + "' has invalid relative-to '" + relativeTo + "'");
                }
                commands = cmdSection.getStringList("commands");
                if (commands.isEmpty()) {
                    throw new IllegalArgumentException("Entry '" + name + "' has a commands section but no commands");
                }
            }

            StructureSettings structure = parseStructure(entry, name);
            Feedback feedback = parseFeedback(entry);

            outcomes.add(new Outcome(name, weight, items, commands, relativeTo, structure, feedback));
            totalWeight += weight;
        }
        return !outcomes.isEmpty();
    }

    /** Rolls a random outcome based on weights. Returns null if no outcomes loaded. */
    public Outcome roll() {
        if (outcomes.isEmpty() || totalWeight <= 0) return null;
        double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
        double current = 0.0;
        for (Outcome outcome : outcomes) {
            current += outcome.weight();
            if (roll < current) return outcome;
        }
        return outcomes.get(outcomes.size() - 1);
    }

    public List<Outcome> outcomes() {
        return List.copyOf(outcomes);
    }

    private List<ItemEntry> parseItems(ConfigurationSection entry, String name) {
        List<String> raw = entry.getStringList("items");
        if (raw.isEmpty()) return List.of();
        List<ItemEntry> parsed = new ArrayList<>();
        for (String line : raw) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("\\s+");
            String itemName = parts[0];
            int quantity = 1;
            if (parts.length > 1) {
                try {
                    quantity = Math.max(1, Integer.parseInt(parts[1]));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Entry '" + name + "' has invalid item quantity in '" + line + "'");
                }
            }
            parsed.add(new ItemEntry(itemName, quantity));
        }
        return parsed;
    }

    private StructureSettings parseStructure(ConfigurationSection entry, String name) {
        ConfigurationSection struct = entry.getConfigurationSection("structure");
        if (struct == null) return null;
        String structName = struct.getString("name");
        if (structName == null || structName.isBlank()) {
            throw new IllegalArgumentException("Entry '" + name + "' has a structure section but is missing structure.name");
        }
        boolean randomRotation = struct.getBoolean("random-rotation", false);
        return new StructureSettings(structName, randomRotation);
    }

    private Feedback parseFeedback(ConfigurationSection entry) {
        ConfigurationSection feedbackSection = entry.getConfigurationSection("feedback");
        if (feedbackSection == null) return null;

        Sound sound = null;
        ConfigurationSection soundSection = feedbackSection.getConfigurationSection("sound");
        if (soundSection != null) {
            if (!soundSection.contains("enabled")) {
                throw new IllegalArgumentException("Entry '" + entry.getName() + "' feedback.sound is missing 'enabled'");
            }
            String soundName = soundSection.getString("sound");
            if (soundName == null || soundName.isBlank()) {
                throw new IllegalArgumentException("Entry '" + entry.getName() + "' feedback.sound is missing 'sound'");
            }
            boolean enabled = soundSection.getBoolean("enabled");
            float pitch = (float) soundSection.getDouble("pitch", 1.0);
            float volume = (float) soundSection.getDouble("volume", 1.0);
            sound = new Sound(enabled, soundName, pitch, volume);
        }

        String message = feedbackSection.getString("message");
        String broadcast = feedbackSection.getString("broadcast");

        return new Feedback(sound, message, broadcast);
    }
}
