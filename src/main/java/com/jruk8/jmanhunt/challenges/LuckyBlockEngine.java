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
 */
public final class LuckyBlockEngine {
    public enum OutcomeType { ITEM, NONE, COMMAND, STRUCTURE }

    public record Sound(boolean enabled, String sound, float pitch, float volume) {}

    public record Feedback(Sound sound, String message, String broadcast) {}

    public record StructureSettings(String name, boolean randomRotation) {}

    public record Outcome(String name, double weight, OutcomeType type, String itemName, int quantity,
                          String relativeTo, List<String> commands, StructureSettings structureSettings,
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
            String typeStr = entry.getString("type", "NONE").toUpperCase();
            OutcomeType type;
            try {
                type = OutcomeType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Entry '" + name + "' has invalid type '" + typeStr + "'");
            }
            double weight = entry.getDouble("weight", 1.0);
            if (weight <= 0) {
                throw new IllegalArgumentException("Entry '" + name + "' has non-positive weight " + weight);
            }
            String itemName = null;
            int quantity = 1;
            String relativeTo = "BLOCK";
            List<String> commands = List.of();
            StructureSettings structureSettings = null;
            if (type == OutcomeType.ITEM) {
                ConfigurationSection item = entry.getConfigurationSection("item-settings");
                if (item == null) {
                    throw new IllegalArgumentException("Entry '" + name + "' is ITEM but missing item-settings");
                }
                itemName = item.getString("name");
                if (itemName == null || itemName.isBlank()) {
                    throw new IllegalArgumentException("Entry '" + name + "' is ITEM but missing item-settings.name");
                }
                quantity = Math.max(1, item.getInt("quantity", 1));
            } else if (type == OutcomeType.COMMAND) {
                ConfigurationSection cmd = entry.getConfigurationSection("command-settings");
                if (cmd == null) {
                    throw new IllegalArgumentException("Entry '" + name + "' is COMMAND but missing command-settings");
                }
                relativeTo = cmd.getString("relative-to", "BLOCK").toUpperCase();
                if (!relativeTo.equals("BLOCK") && !relativeTo.equals("PLAYER")) {
                    throw new IllegalArgumentException("Entry '" + name + "' has invalid relative-to '" + relativeTo + "'");
                }
                commands = cmd.getStringList("commands");
                if (commands.isEmpty()) {
                    throw new IllegalArgumentException("Entry '" + name + "' is COMMAND but has no commands");
                }
            } else if (type == OutcomeType.STRUCTURE) {
                ConfigurationSection struct = entry.getConfigurationSection("structure-settings");
                if (struct == null) {
                    throw new IllegalArgumentException("Entry '" + name + "' is STRUCTURE but missing structure-settings");
                }
                String structName = struct.getString("name");
                if (structName == null || structName.isBlank()) {
                    throw new IllegalArgumentException(
                            "Entry '" + name + "' is STRUCTURE but missing structure-settings.name");
                }
                boolean randomRotation = struct.getBoolean("random-rotation", false);
                structureSettings = new StructureSettings(structName, randomRotation);
            }
            Feedback feedback = parseFeedback(entry);
            outcomes.add(new Outcome(name, weight, type, itemName, quantity, relativeTo, commands,
                    structureSettings, feedback));
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