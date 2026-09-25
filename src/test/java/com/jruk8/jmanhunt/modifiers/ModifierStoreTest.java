package com.jruk8.jmanhunt.modifiers;

import com.jruk8.jmanhunt.command.CommandSyntax;
import com.jruk8.jmanhunt.modifiers.config.ModifierCommandsPack;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierStoreTest {

    private static final String FIXTURE = """
            modifiers-version: 1
            modifiers:
              beef:
                enabled: true
                meta:
                  name: "Everyone Gets Beef"
                  description: "Steak at the start"
                  item: COOKED_BEEF
                  author: JManhunt
                behavior:
                  runs-on:
                    - ON_START
                  options:
                    interval-settings:
                      interval: 90
                      deviation: 5
                      behavior: PER_EXECUTOR
                    success-chance:
                      chance: 0.5
                      behavior: PER_EXECUTOR
                    execution:
                      selection: PICK_RANDOM
                      pick-random:
                        count: 2
                        behavior: PER_EXECUTOR
                    delay: 100
                  commands:
                    player:
                      - "give <p> beef 8"
                    custom-list:
                      - "say hi"
              bare:
                enabled: false
              thin:
                enabled: false
                behavior:
                  commands:
                    player:
                      - "say thin"
            presets:
              mixed:
                meta:
                  name: "Mixed"
                  description: "Beef plus nothing"
                  item: TNT
                modifiers:
                  - beef
                  - bare
            """;

    @TempDir
    private Path tempDir;

    private File file;
    private ModifiersConfig config;
    private ModifierStore store;
    private List<String> warnings;

    @BeforeEach
    void setup() throws Exception {
        file = tempDir.resolve("modifiers.yml").toFile();
        Files.writeString(file.toPath(), FIXTURE, StandardCharsets.UTF_8);
        config = load(file);

        warnings = new ArrayList<>();
        Logger log = Logger.getAnonymousLogger();
        log.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        store = new ModifierStore(config, log);
    }

    @Test
    void namesAndEnabledReadThrough() {
        assertEquals(3, store.modifierNames().size());
        assertTrue(store.isEnabled("beef"));
        assertFalse(store.isEnabled("bare"));
        assertFalse(store.isEnabled("missing"));
    }

    @Test
    void setEnabledRoundtripsAndPersists() {
        assertTrue(store.setEnabled("bare", true));
        assertTrue(store.isEnabled("bare"));

        YamlConfiguration reread = YamlConfiguration.loadConfiguration(file);
        assertTrue(reread.getBoolean("modifiers.bare.enabled"));
    }

    @Test
    void setEnabledUnknownReturnsFalse() {
        assertFalse(store.setEnabled("missing", true));
    }

    @Test
    void typedBehaviorReads() {
        assertEquals(List.of("ON_START"), store.runsOn("beef"));
        assertEquals(90.0, store.intervalSeconds("beef"));
        assertEquals(5.0, store.intervalDeviation("beef"));
        assertEquals("PER_EXECUTOR", store.intervalBehavior("beef"));
        assertEquals(0.5, store.chance("beef"));
        assertEquals("PER_EXECUTOR", store.chanceBehavior("beef"));
        assertEquals("PER_EXECUTOR", store.pickBehavior("beef"));
        assertEquals(100L, store.delayTicks("beef"));
        assertEquals("PICK_RANDOM", store.selection("beef"));
        assertEquals(2, store.pickCount("beef"));
        assertEquals(List.of("give <p> beef 8"), store.commandList("beef", "player"));
        assertEquals(List.of("say hi"), store.commandList("beef", "custom-list"));
        assertTrue(store.commandList("beef", "missing").isEmpty());
        assertNull(store.preStartOrder("beef"));
    }

    @Test
    void bareModifierUsesDefaults() {
        assertTrue(store.runsOn("bare").isEmpty());
        assertEquals(60.0, store.intervalSeconds("bare"));
        assertEquals(0.0, store.intervalDeviation("bare"));
        assertNull(store.intervalBehavior("bare"));
        assertEquals(1.0, store.chance("bare"));
        assertNull(store.chanceBehavior("bare"));
        assertNull(store.pickBehavior("bare"));
        assertEquals(0L, store.delayTicks("bare"));
        assertNull(store.selection("bare"));
        assertEquals(1, store.pickCount("bare"));
        assertTrue(store.commandList("bare", "player").isEmpty());
        assertNull(store.preStartOrder("bare"));
    }

    @Test
    void metaFallsBack() {
        assertEquals("Everyone Gets Beef", store.metaName("beef"));
        assertEquals("Steak at the start", store.metaDescription("beef"));
        assertEquals("JManhunt", store.metaAuthor("beef"));
        assertEquals(ModifierStore.DEFAULT_NAME, store.metaName("bare"));
        assertEquals(ModifierStore.DEFAULT_DESCRIPTION, store.metaDescription("bare"));
        assertNull(store.metaAuthor("bare"));

        config.getModifiers().get("beef").getMeta().setName("   ");
        config.getModifiers().get("beef").getMeta().setDescription("");
        assertEquals(ModifierStore.DEFAULT_NAME, store.metaName("beef"));
        assertEquals(ModifierStore.DEFAULT_DESCRIPTION, store.metaDescription("beef"));
    }

    @Test
    void itemsParseLeniently() {
        assertEquals(Material.COOKED_BEEF, store.metaItem("beef"));

        config.getModifiers().get("beef").getMeta().setItem("minecraft:diamond");
        assertEquals(Material.DIAMOND, store.metaItem("beef"));

        config.getModifiers().get("beef").getMeta().setItem("golden apple");
        assertEquals(Material.GOLDEN_APPLE, store.metaItem("beef"));
    }

    @Test
    void missingItemIsSilentStone() {
        assertEquals(Material.STONE, store.metaItem("bare"));
        assertTrue(warnings.isEmpty());
    }

    @Test
    void invalidItemWarnsOnceThenStone() {
        config.getModifiers().get("beef").getMeta().setItem("banana");

        assertEquals(Material.STONE, store.metaItem("beef"));
        assertEquals(Material.STONE, store.metaItem("beef"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("beef"));

        store.clearItemWarnings();
        assertEquals(Material.STONE, store.metaItem("beef"));
        assertEquals(2, warnings.size());
    }

    @Test
    void airItemFallsBackToStone() {
        config.getModifiers().get("beef").getMeta().setItem("AIR");

        assertEquals(Material.STONE, store.metaItem("beef"));
        assertEquals(1, warnings.size());
    }

    @Test
    void presetsReadThrough() {
        assertEquals(1, store.presetNames().size());
        assertEquals(List.of("beef", "bare"), store.presetMembers("mixed"));
        assertEquals("Mixed", store.presetName("mixed"));
        assertEquals("Beef plus nothing", store.presetDescription("mixed"));
        assertEquals(Material.TNT, store.presetItem("mixed"));

        assertTrue(store.presetMembers("missing").isEmpty());
        assertEquals(ModifierStore.DEFAULT_NAME, store.presetName("missing"));
        assertEquals(ModifierStore.DEFAULT_DESCRIPTION, store.presetDescription("missing"));
        assertEquals(Material.STONE, store.presetItem("missing"));
    }

    @Test
    void saveKeepsAbsentSectionsAbsent() {
        assertTrue(store.setEnabled("bare", true));

        YamlConfiguration reread = YamlConfiguration.loadConfiguration(file);
        var bare = reread.getConfigurationSection("modifiers.bare");
        assertTrue(bare.getBoolean("enabled"));
        assertFalse(bare.contains("meta"));
        assertFalse(bare.contains("behavior"));

        var thin = reread.getConfigurationSection("modifiers.thin.behavior");
        assertTrue(thin.contains("commands"));
        assertFalse(thin.contains("runs-on"));
        assertFalse(thin.contains("options"));
        assertFalse(thin.contains("on-start"));
    }

    @Test
    void saveKeepsCustomListsAndOptions() throws Exception {
        assertTrue(store.setEnabled("bare", true));

        ModifierStore reread = new ModifierStore(load(file), Logger.getAnonymousLogger());
        assertEquals(List.of("say hi"), reread.commandList("beef", "custom-list"));
        assertEquals("PICK_RANDOM", reread.selection("beef"));
        assertEquals(2, reread.pickCount("beef"));
        assertEquals("PER_EXECUTOR", reread.pickBehavior("beef"));
        assertEquals(List.of("say thin"), reread.commandList("thin", "player"));
    }

    @Test
    void bundledFileHasCompleteDefaults() throws Exception {
        YamlConfiguration bundled;
        try (InputStream stream = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("modifiers.yml"),
                "missing test resource: modifiers.yml")) {
            bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        var modifiers = bundled.getConfigurationSection("modifiers");
        assertEquals(20, modifiers.getKeys(false).size());
        for (String name : modifiers.getKeys(false)) {
            var section = modifiers.getConfigurationSection(name);
            assertTrue(section.contains("enabled"), name);
            assertTrue(section.getConfigurationSection("meta").contains("name"), name);
            assertTrue(section.getConfigurationSection("meta").contains("description"), name);
            assertTrue(section.getConfigurationSection("meta").contains("item"), name);
            assertTrue(section.getConfigurationSection("meta").contains("author"), name);
            List<String> behaviorKeys = new ArrayList<>(
                    section.getConfigurationSection("behavior").getKeys(false));
            assertEquals("commands", behaviorKeys.get(behaviorKeys.size() - 1), name);
        }

        var presets = bundled.getConfigurationSection("presets");
        assertEquals(3, presets.getKeys(false).size());
        for (String id : presets.getKeys(false)) {
            var preset = presets.getConfigurationSection(id);
            for (String member : preset.getStringList("modifiers")) {
                assertTrue(modifiers.contains(member),
                        "preset " + id + " references missing modifier " + member);
            }
        }
    }

    @Test
    void bundledFileLoadsThroughStore() throws Exception {
        File bundledFile = tempDir.resolve("bundled.yml").toFile();
        try (InputStream stream = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("modifiers.yml"),
                "missing test resource: modifiers.yml")) {
            Files.copy(stream, bundledFile.toPath());
        }
        ModifierStore bundled = new ModifierStore(load(bundledFile), Logger.getAnonymousLogger());

        assertEquals(20, bundled.modifierNames().size());
        assertEquals(3, bundled.presetNames().size());
        assertEquals("PICK_RANDOM", bundled.selection("gear-dice"));
        assertEquals(15.0, bundled.intervalSeconds("gear-dice"));
        assertEquals(0.5, bundled.chance("gear-dice"));
        assertEquals(1, bundled.pickCount("gear-dice"));
        assertEquals(100L, bundled.delayTicks("tpall-on-end"));
        assertEquals("AFTER", bundled.preStartOrder("hunter-start-debuffs"));
        assertEquals(3, bundled.presetMembers("chaos-mode").size());
        assertEquals(Material.TNT, bundled.presetItem("chaos-mode"));
    }

    @Test
    void bundledSwapModifierRunsConsoleBodyOnFiveMinutes() throws Exception {
        File bundledFile = new File("src/main/resources/modifiers.yml");
        ModifierStore bundled = new ModifierStore(load(bundledFile), Logger.getAnonymousLogger());

        assertEquals(List.of("INTERVAL"), bundled.runsOn("opposite-team-swap"));
        assertEquals(300.0, bundled.intervalSeconds("opposite-team-swap"));
        assertEquals(List.of("manhunt swaproles"),
                bundled.commandList("opposite-team-swap", "console"));
        assertTrue(bundled.commandList("opposite-team-swap", "player").isEmpty());
        assertFalse(bundled.isEnabled("opposite-team-swap"));
    }

    @Test
    void bundledCommandsPassSyntaxValidation() throws Exception {
        YamlConfiguration bundled;
        try (InputStream stream = Objects.requireNonNull(
                getClass().getClassLoader().getResourceAsStream("modifiers.yml"),
                "missing test resource: modifiers.yml")) {
            bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        }

        var modifiers = bundled.getConfigurationSection("modifiers");
        int checked = 0;
        for (String name : modifiers.getKeys(false)) {
            var commands = modifiers.getConfigurationSection(name)
                    .getConfigurationSection("behavior").getConfigurationSection("commands");
            if (commands == null) {
                continue;
            }
            for (String list : commands.getKeys(false)) {
                for (String line : commands.getStringList(list)) {
                    assertTrue(CommandSyntax.error(line).isEmpty(),
                            name + "/" + list + ": " + line + " -> "
                                    + CommandSyntax.error(line).orElse(""));
                    checked++;
                }
            }
        }
        assertTrue(checked > 20, "expected bundled command lines, found none");
    }

    @Test
    void addModifierBumpsNameOnIdCollision() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ModifierStore store = new ModifierStore(new ModifiersConfig(), log);
        ModifierEntry first = new ModifierEntry();
        ModifierMeta firstMeta = new ModifierMeta();
        firstMeta.setName("Gear Dice");
        first.setMeta(firstMeta);
        assertEquals("gear-dice", store.addModifier("gear-dice", first));

        ModifierEntry second = new ModifierEntry();
        ModifierMeta secondMeta = new ModifierMeta();
        secondMeta.setName("Gear Dice");
        second.setMeta(secondMeta);

        assertEquals("gear-dice-2", store.addModifier("gear-dice", second));
        assertEquals("Gear Dice 2", store.metaName("gear-dice-2"));
    }

    @Test
    void addPresetBumpsNameOnIdCollision() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ModifierStore store = new ModifierStore(new ModifiersConfig(), log);
        ModifierPreset first = new ModifierPreset();
        first.setMeta(namedMeta("Chaos"));
        assertEquals("chaos", store.addPreset("chaos", first));

        ModifierPreset second = new ModifierPreset();
        second.setMeta(namedMeta("Chaos"));

        assertEquals("chaos-2", store.addPreset("chaos", second));
        assertEquals("Chaos 2", store.presetName("chaos-2"));
    }

    @Test
    void renameModifierFollowsPresetMembers() {
        assertTrue(store.renameModifier("beef", "steak"));
        assertTrue(store.modifierNames().contains("steak"));
        assertFalse(store.modifierNames().contains("beef"));
        assertEquals(List.of("steak", "bare"), store.presetMembers("mixed"));
    }

    @Test
    void renameModifierRefusesTakenAndUnknown() {
        assertFalse(store.renameModifier("beef", "bare"));
        assertFalse(store.renameModifier("missing", "fresh"));
        assertTrue(store.renameModifier("beef", "beef"));
        assertEquals(List.of("beef", "bare"), store.presetMembers("mixed"));
    }

    private static ModifierMeta namedMeta(String name) {
        ModifierMeta meta = new ModifierMeta();
        meta.setName(name);
        return meta;
    }

    private static ModifiersConfig load(File source) throws Exception {
        ModifiersConfig loaded = ConfigManager.create(ModifiersConfig.class, it -> {
            // No SerdesBukkit: it probes Bukkit classes whose static init needs
            // a server, and this model uses no Bukkit types anyway.
            it.withConfigurer(new YamlBukkitConfigurer(), new ModifierCommandsPack());
            it.withBindFile(source);
            it.withRemoveOrphans(true);
        });
        loaded.saveDefaults();
        // Okaeri never closes file loads, which locks the file on Windows;
        // a self-managed stream keeps temp-dir cleanup working.
        try (InputStream stream = Files.newInputStream(source.toPath())) {
            loaded.load(stream);
        }
        return loaded;
    }
}
