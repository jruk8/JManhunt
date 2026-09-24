package com.jruk8.jmanhunt.gui;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Longest-match section icons with paper fallback. */
class GuiConfigTest {

    private GuiConfig gui;

    @BeforeEach
    void setUp() {
        gui = new GuiConfig();
        gui.setCategories(new LinkedHashMap<>(Map.of(
                "match", "CLOCK",
                "match/win-conditions", "NETHER_STAR",
                "bogus", "BOGUS_ITEM")));
    }

    @Test
    void topLevelCategoryResolves() {
        assertEquals(Material.CLOCK, gui.sectionItem("settings.match"));
        assertEquals(Material.CLOCK, gui.categoryItem("match"));
    }

    @Test
    void longestMatchWinsOverParent() {
        assertEquals(Material.NETHER_STAR,
                gui.sectionItem("settings.match.win-conditions.hunter"));
    }

    @Test
    void bareSuffixFallsBackToTopLevel() {
        assertEquals(Material.CLOCK, gui.sectionItem("match.autostart"));
    }

    @Test
    void unknownPathsGetPaper() {
        assertEquals(Material.PAPER, gui.sectionItem("settings.nope.deep"));
        assertEquals(Material.PAPER, gui.sectionItem(null));
        assertEquals(Material.PAPER, gui.categoryItem("nope"));
    }

    @Test
    void badMaterialGetsPaper() {
        assertEquals(Material.PAPER, gui.sectionItem("settings.bogus"));
    }

    @Test
    void bundledGuiLoadsSubsectionIconsAndDescriptions() throws Exception {
        GuiConfig loaded = ConfigManager.create(GuiConfig.class,
                it -> it.withConfigurer(new YamlBukkitConfigurer()));
        try (InputStream stream = Objects.requireNonNull(
                GuiConfigTest.class.getClassLoader()
                        .getResourceAsStream("Core/gui.yml"))) {
            loaded.load(stream);
        }

        assertEquals(Material.CLOCK, loaded.categoryItem("match"));
        assertEquals(Material.NETHER_STAR,
                loaded.sectionItem("settings.match.win-conditions.hunter"));
        assertEquals(Material.BARRIER,
                loaded.sectionItem("settings.match.win-conditions.cancel"));
        assertEquals(Material.OAK_DOOR,
                loaded.sectionItem("settings.match.game-leave.destination"));
        assertEquals(Material.BELL,
                loaded.sectionItem("settings.server.announce-config-changes"));
        assertEquals("Pick which lines status commands show.",
                loaded.description("settings.server.status"));
        assertEquals("Cancel the match instead of crowning a winner.",
                loaded.description("settings.match.win-conditions.cancel"));
    }
}
