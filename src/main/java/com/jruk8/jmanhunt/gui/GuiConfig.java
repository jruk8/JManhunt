package com.jruk8.jmanhunt.gui;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;

/**
 * Typed root of the internal Core/gui.yml: category icons plus one-line
 * setting descriptions for setting-button lore. Jar-loaded and never
 * user editable; a unit test pins every settings path present.
 */
@SuppressWarnings("FieldMayBeFinal")
public class GuiConfig extends OkaeriConfig {

    @Comment("Category name to icon material. Subsections use slash paths.")
    private Map<String, String> categories = new LinkedHashMap<>(Map.of(
            "match", "CLOCK",
            "compass", "COMPASS",
            "players", "PLAYER_HEAD",
            "server", "BELL"));

    @Comment("Full setting path to its one-line lore description.")
    private Map<String, String> descriptions = new LinkedHashMap<>();

    public Map<String, String> getCategories() {
        return categories;
    }

    public void setCategories(Map<String, String> categories) {
        this.categories = categories;
    }

    public Map<String, String> getDescriptions() {
        return descriptions;
    }

    public void setDescriptions(Map<String, String> descriptions) {
        this.descriptions = descriptions;
    }

    /** Icon for a settings category, paper when unknown. */
    public Material categoryItem(String category) {
        return sectionItem(category);
    }

    /**
     * Icon for any settings path: longest match wins, so a subsection
     * inherits its nearest annotated parent and unknown paths get paper.
     * Accepts full paths (settings.match.win-conditions) or bare suffixes
     * (match.win-conditions, match). Dots become slashes because the map
     * keys use slash paths, which YAML config loading keeps literal.
     */
    public Material sectionItem(String path) {
        String candidate = path == null ? "" : path;
        if (candidate.regionMatches(true, 0, "settings.", 0, 9)) {
            candidate = candidate.substring(9);
        }
        candidate = candidate.replace('.', '/');
        while (!candidate.isEmpty()) {
            String raw = categories.get(candidate);
            if (raw != null) {
                try {
                    return Material.valueOf(raw);
                } catch (IllegalArgumentException expected) {
                    return Material.PAPER;
                }
            }
            int slash = candidate.lastIndexOf('/');
            candidate = slash == -1 ? "" : candidate.substring(0, slash);
        }
        return Material.PAPER;
    }

    /** One-line lore description, empty when the path has none. */
    public String description(String path) {
        return descriptions.getOrDefault(path, "");
    }
}
