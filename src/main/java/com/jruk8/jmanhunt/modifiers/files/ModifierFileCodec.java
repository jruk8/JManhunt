package com.jruk8.jmanhunt.modifiers.files;

import com.jruk8.jmanhunt.modifiers.config.ModifierCommandsPack;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses, validates, and serializes single mod files. Each file body
 * is one {@link ModifierEntry} or {@link ModifierPreset} mapping with
 * no wrapper key. Shape validation runs before Okaeri binding so
 * failures carry short single-line messages; Okaeri owns typed
 * binding and canonical serialization, reusing the same configurer
 * and commands serdes the old single-file store used. No SerdesBukkit:
 * it probes Bukkit statics that need a server, and this model uses
 * no Bukkit types. Bukkit-free except for the headless-safe YAML
 * parser also used by unit tests.
 */
public final class ModifierFileCodec {

    private static final Logger SILENT = silentLogger();

    private ModifierFileCodec() {
    }

    /** Parses one modifier file. Fails with a single-line message. */
    public static ModifierEntry readModifier(Path file) throws ModFileException {
        String text = readText(file);
        requireModifierShape(parseYaml(text));
        return bind(ModifierEntry.class, file, text);
    }

    /** Parses one preset file. Fails with a single-line message. */
    public static ModifierPreset readPreset(Path file) throws ModFileException {
        String text = readText(file);
        requirePresetShape(parseYaml(text));
        return bind(ModifierPreset.class, file, text);
    }

    /** Canonical YAML text for one modifier entry. */
    public static String serializeModifier(ModifierEntry entry) throws ModFileException {
        return serialize(entry);
    }

    /** Canonical YAML text for one preset. */
    public static String serializePreset(ModifierPreset preset) throws ModFileException {
        return serialize(preset);
    }

    /** Hex SHA-256 over canonical YAML text. Pure. */
    public static String fingerprint(String canonicalYaml) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalYaml.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("SHA-256 unavailable", impossible);
        }
    }

    private static String serialize(OkaeriConfig config) throws ModFileException {
        if (config.getConfigurer() == null) {
            config.withConfigurer(new YamlBukkitConfigurer(), new ModifierCommandsPack());
            config.updateDeclaration();
        }
        try {
            return config.saveToString();
        } catch (RuntimeException exception) {
            throw new ModFileException(firstLine(exception.getMessage(), "could not serialize file"));
        }
    }

    private static <T extends OkaeriConfig> T bind(Class<T> type, Path file, String text)
            throws ModFileException {
        try {
            T config = ConfigManager.create(type, it -> {
                it.withConfigurer(new YamlBukkitConfigurer(), new ModifierCommandsPack());
                it.withBindFile(file.toFile());
                it.withRemoveOrphans(true);
                it.withLogger(SILENT);
            });
            // Self-managed stream: Okaeri never closes file loads, which
            // locks files on Windows and breaks temp-dir cleanup in tests.
            try (InputStream stream = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))) {
                config.load(stream);
            }
            return config;
        } catch (RuntimeException | IOException exception) {
            throw new ModFileException(firstLine(exception.getMessage(), "could not parse file"));
        }
    }

    private static String readText(Path file) throws ModFileException {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ModFileException(firstLine(exception.getMessage(), "could not read file"));
        }
    }

    private static YamlConfiguration parseYaml(String text) throws ModFileException {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(new StringReader(text));
        } catch (IOException | InvalidConfigurationException | RuntimeException exception) {
            // RuntimeException covers non-map roots, which Bukkit may
            // surface as a cast failure instead of a checked error.
            throw new ModFileException(firstLine(exception.getMessage(), "could not parse file"));
        }
        return yaml;
    }

    private static void requireModifierShape(YamlConfiguration yaml) throws ModFileException {
        if (yaml.contains("enabled") && !yaml.isBoolean("enabled")) {
            throw new ModFileException("enabled must be true or false");
        }
        if (yaml.contains("meta") && !yaml.isConfigurationSection("meta")) {
            throw new ModFileException("meta must be a mapping");
        }
        requireBehaviorShape(yaml);
    }

    private static void requireBehaviorShape(YamlConfiguration yaml) throws ModFileException {
        if (!yaml.contains("behavior")) {
            return;
        }
        if (!yaml.isConfigurationSection("behavior")) {
            throw new ModFileException("behavior must be a mapping");
        }
        var section = yaml.getConfigurationSection("behavior");
        for (String key : section.getKeys(false)) {
            if (section.get(key) != null && !section.isConfigurationSection(key)) {
                throw new ModFileException("behavior '" + key + "' must be a mapping");
            }
            if (section.contains(key + ".runs-on") && !section.isList(key + ".runs-on")) {
                throw new ModFileException("behavior '" + key + "' runs-on must be a list");
            }
        }
    }

    private static void requirePresetShape(YamlConfiguration yaml) throws ModFileException {
        if (yaml.contains("meta") && !yaml.isConfigurationSection("meta")) {
            throw new ModFileException("meta must be a mapping");
        }
        if (yaml.contains("modifiers") && !yaml.isList("modifiers")) {
            throw new ModFileException("modifiers must be a list of ids");
        }
    }

    private static String firstLine(String message, String fallback) {
        if (message == null) {
            return fallback;
        }
        String line = message.strip();
        int end = line.indexOf('\n');
        if (end != -1) {
            line = line.substring(0, end).strip();
        }
        return line.isEmpty() ? fallback : line;
    }

    private static Logger silentLogger() {
        Logger logger = Logger.getLogger(ModifierFileCodec.class.getName() + ".silent");
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.OFF);
        return logger;
    }
}
