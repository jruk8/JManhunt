package com.jruk8.jmanhunt.gui;

import com.jruk8.jmanhunt.config.SettingRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Bundled Core/gui.yml pins: every settings path has a one-line
 * description and every category maps to a real material.
 */
class GuiDescriptionsTest {

    @Test
    void everySettingsPathHasADescription() throws Exception {
        YamlConfiguration yaml = bundledGui();
        ConfigurationSection descriptions = yaml.getConfigurationSection("descriptions");

        List<String> missing = new ArrayList<>();
        for (String path : SettingRegistry.settingNames()) {
            if (!path.startsWith("settings.")) {
                continue;
            }
            String text = descriptions == null ? null : descriptions.getString(path);
            if (text == null || text.isBlank()) {
                missing.add(path);
            }
        }
        for (String list : SettingRegistry.listPaths()) {
            if (!list.startsWith("settings.")) {
                continue;
            }
            String text = descriptions == null ? null : descriptions.getString(list);
            if (text == null || text.isBlank()) {
                missing.add(list);
            }
        }
        for (String section : sectionPaths()) {
            String text = descriptions == null ? null : descriptions.getString(section);
            if (text == null || text.isBlank()) {
                missing.add(section);
            }
        }
        assertTrue(missing.isEmpty(), "missing descriptions: " + missing);
    }

    @Test
    void categoriesMapToRealMaterials() throws Exception {
        YamlConfiguration yaml = bundledGui();
        ConfigurationSection categories = yaml.getConfigurationSection("categories");

        assertFalse(categories == null || categories.getKeys(false).isEmpty(),
                "no categories defined");
        for (String category : List.of("match", "compass", "players", "server")) {
            assertFalse(categories.getString(category) == null
                    || categories.getString(category).isBlank(),
                    "missing category: " + category);
        }
        for (String key : categories.getKeys(false)) {
            String raw = categories.getString(key);
            try {
                assertTrue(Material.valueOf(raw).isItem(), "not an item: " + raw);
            } catch (IllegalArgumentException expected) {
                fail("bad material: " + raw);
            }
        }
    }

    @Test
    void loreLinesStayTooltipShort() throws Exception {
        YamlConfiguration yaml = bundledGui();
        ConfigurationSection descriptions = yaml.getConfigurationSection("descriptions");

        List<String> longLines = new ArrayList<>();
        for (Map.Entry<String, Object> entry :
                Objects.requireNonNull(descriptions).getValues(false).entrySet()) {
            if (String.valueOf(entry.getValue()).length() > 80) {
                longLines.add((String) entry.getKey());
            }
        }
        assertTrue(longLines.isEmpty(), "descriptions over 80 chars: " + longLines);
    }

    /** Every intermediate section path implied by the registry. */
    private static List<String> sectionPaths() {
        List<String> sections = new ArrayList<>();
        List<String> leaves = new ArrayList<>(SettingRegistry.settingNames());
        leaves.addAll(SettingRegistry.listPaths());
        for (String path : leaves) {
            if (!path.startsWith("settings.")) {
                continue;
            }
            String[] parts = path.split("\\.");
            StringBuilder prefix = new StringBuilder();
            for (int index = 0; index < parts.length - 1; index++) {
                if (index > 0) {
                    prefix.append('.');
                }
                prefix.append(parts[index]);
                String section = prefix.toString();
                if (section.split("\\.").length > 1 && !sections.contains(section)) {
                    sections.add(section);
                }
            }
        }
        return sections;
    }

    private static YamlConfiguration bundledGui() throws Exception {
        try (InputStream stream = Objects.requireNonNull(
                GuiDescriptionsTest.class.getClassLoader().getResourceAsStream("Core/gui.yml"))) {
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
