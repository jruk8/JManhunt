package com.jruk8.jmanhunt.modifiers;

import com.jruk8.jmanhunt.command.CommandSyntax;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import com.jruk8.jmanhunt.modifiers.files.ModsDefaults;
import com.jruk8.jmanhunt.modifiers.files.ModsLoader;
import com.jruk8.jmanhunt.modifiers.files.ModsSeeder;
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

    private static final String BEEF = """
            enabled: true
            meta:
              name: "Everyone Gets Beef"
              description: "Steak at the start"
              item: COOKED_BEEF
              author: JManhunt
            behavior:
              0:
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
              2:
                runs-on:
                  - ON_KILL
            """;

    private static final String THIN = """
            enabled: false
            behavior:
              0:
                commands:
                  player:
                    - "say thin"
              extra:
                runs-on:
                  - ON_START
            """;

    private static final String MIXED = """
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

    private Path modsRoot;
    private ModifierFiles config;
    private ModifierStore store;
    private List<String> warnings;

    @BeforeEach
    void setup() throws Exception {
        modsRoot = tempDir.resolve("mods");
        writeFixture("modifiers", "beef", BEEF);
        writeFixture("modifiers", "bare", "enabled: false\n");
        writeFixture("modifiers", "thin", THIN);
        writeFixture("presets", "mixed", MIXED);
        config = ModifierFiles.fromLoad(modsRoot, loadRoot());

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
    void toggleListenersHearFlipsOnly() {
        List<String> heard = new ArrayList<>();
        store.addToggleListener(heard::add);

        assertTrue(store.setEnabled("bare", true));
        assertTrue(store.setEnabled("bare", true));
        assertTrue(store.setEnabled("beef", false));
        assertFalse(store.setEnabled("missing", true));

        assertEquals(List.of("bare", "beef"), heard);
    }

    @Test
    void setEnabledRoundtripsAndPersists() {
        assertTrue(store.setEnabled("bare", true));
        assertTrue(store.isEnabled("bare"));

        YamlConfiguration reread = YamlConfiguration.loadConfiguration(modFile("bare"));
        assertTrue(reread.getBoolean("enabled"));
    }

    @Test
    void setEnabledUnknownReturnsFalse() {
        assertFalse(store.setEnabled("missing", true));
    }

    @Test
    void typedBehaviorReads() {
        assertEquals(List.of("ON_START"), store.runsOn("beef", 0));
        assertEquals(90.0, store.intervalSeconds("beef", 0));
        assertEquals(5.0, store.intervalDeviation("beef", 0));
        assertEquals("PER_EXECUTOR", store.intervalBehavior("beef", 0));
        assertEquals(0.5, store.chance("beef", 0));
        assertEquals("PER_EXECUTOR", store.chanceBehavior("beef", 0));
        assertEquals("PER_EXECUTOR", store.pickBehavior("beef", 0));
        assertEquals(100L, store.delayTicks("beef", 0));
        assertEquals("PICK_RANDOM", store.selection("beef", 0));
        assertEquals(2, store.pickCount("beef", 0));
        assertEquals(List.of("give <p> beef 8"), store.commandList("beef", 0, "player"));
        assertEquals(List.of("say hi"), store.commandList("beef", 0, "custom-list"));
        assertTrue(store.commandList("beef", 0, "missing").isEmpty());
        assertNull(store.preStartOrder("beef", 0));
    }

    @Test
    void bareModifierUsesDefaults() {
        assertTrue(store.runsOn("bare", 0).isEmpty());
        assertEquals(60.0, store.intervalSeconds("bare", 0));
        assertEquals(0.0, store.intervalDeviation("bare", 0));
        assertNull(store.intervalBehavior("bare", 0));
        assertEquals(1.0, store.chance("bare", 0));
        assertNull(store.chanceBehavior("bare", 0));
        assertNull(store.pickBehavior("bare", 0));
        assertEquals(0L, store.delayTicks("bare", 0));
        assertNull(store.selection("bare", 0));
        assertEquals(1, store.pickCount("bare", 0));
        assertTrue(store.commandList("bare", 0, "player").isEmpty());
        assertNull(store.preStartOrder("bare", 0));
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
    void behaviorIndexesAreSortedAndSparse() {
        assertEquals(List.of(0, 2), store.behaviorIndexes("beef"));
        assertEquals(List.of(), store.behaviorIndexes("bare"));
        assertEquals(List.of(), store.behaviorIndexes("missing"));
    }

    @Test
    void behaviorsReadIndependentlyByIndex() {
        assertEquals(List.of("ON_START"), store.runsOn("beef", 0));
        assertEquals(List.of("ON_KILL"), store.runsOn("beef", 2));
        assertTrue(store.runsOn("beef", 1).isEmpty());
        assertEquals(90.0, store.intervalSeconds("beef", 0));
        assertEquals(60.0, store.intervalSeconds("beef", 2));
    }

    @Test
    void nonNumericBehaviorKeyWarnsOnceAndSkips() {
        assertEquals(List.of(0), store.behaviorIndexes("thin"));
        assertEquals(List.of(0), store.behaviorIndexes("thin"));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("thin"));
        assertTrue(warnings.get(0).contains("extra"));

        store.clearItemWarnings();
        assertEquals(List.of(0), store.behaviorIndexes("thin"));
        assertEquals(2, warnings.size());
    }

    @Test
    void addAndRemoveBehaviorRoundTrip() {
        assertEquals(3, store.addBehavior("beef"));
        assertEquals(List.of(0, 2, 3), store.behaviorIndexes("beef"));
        assertEquals(-1, store.addBehavior("missing"));

        assertTrue(store.removeBehavior("beef", 2));
        assertEquals(List.of(0, 3), store.behaviorIndexes("beef"));
        assertFalse(store.removeBehavior("beef", 2));
        assertFalse(store.removeBehavior("missing", 0));
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

        YamlConfiguration reread = YamlConfiguration.loadConfiguration(modFile("bare"));
        assertTrue(reread.getBoolean("enabled"));
        assertFalse(reread.contains("meta"));
        assertFalse(reread.contains("behavior"));

        YamlConfiguration thin = YamlConfiguration.loadConfiguration(modFile("thin"));
        var behavior = thin.getConfigurationSection("behavior.0");
        assertTrue(behavior.contains("commands"));
        assertFalse(behavior.contains("runs-on"));
        assertFalse(behavior.contains("options"));
        assertFalse(behavior.contains("on-start"));
    }

    @Test
    void saveKeepsCustomListsAndOptions() {
        assertTrue(store.setEnabled("bare", true));

        ModifierStore reread = new ModifierStore(
                ModifierFiles.fromLoad(modsRoot, loadRoot()), Logger.getAnonymousLogger());
        assertEquals(List.of("say hi"), reread.commandList("beef", 0, "custom-list"));
        assertEquals("PICK_RANDOM", reread.selection("beef", 0));
        assertEquals(2, reread.pickCount("beef", 0));
        assertEquals("PER_EXECUTOR", reread.pickBehavior("beef", 0));
        assertEquals(List.of("say thin"), reread.commandList("thin", 0, "player"));
    }

    @Test
    void bundledDefaultsHaveCompleteMeta() throws Exception {
        assertEquals(21, ModsDefaults.MODIFIERS.size());
        for (String id : ModsDefaults.MODIFIERS) {
            YamlConfiguration yaml = bundledYaml("modifiers", id);
            assertTrue(yaml.contains("enabled"), id);
            var meta = yaml.getConfigurationSection("meta");
            assertTrue(meta.contains("name"), id);
            assertTrue(meta.contains("description"), id);
            assertTrue(meta.contains("item"), id);
            assertTrue(meta.contains("author"), id);
            var behavior = yaml.getConfigurationSection("behavior");
            for (String index : behavior.getKeys(false)) {
                List<String> behaviorKeys = new ArrayList<>(
                        behavior.getConfigurationSection(index).getKeys(false));
                assertEquals("commands", behaviorKeys.get(behaviorKeys.size() - 1),
                        id + "/" + index);
            }
        }

        assertEquals(3, ModsDefaults.PRESETS.size());
        for (String id : ModsDefaults.PRESETS) {
            YamlConfiguration yaml = bundledYaml("presets", id);
            for (String member : yaml.getStringList("modifiers")) {
                assertTrue(ModsDefaults.MODIFIERS.contains(member),
                        "preset " + id + " references missing modifier " + member);
            }
        }
    }

    @Test
    void bundledDefaultsLoadThroughStore() {
        Path bundledRoot = tempDir.resolve("bundled");
        ModsLoader seeder = new ModsLoader((kind, dir) -> {
            for (String id : ModsDefaults.ids(kind)) {
                String resource = "mods/" + kind.dirName() + "/" + id + ".yml";
                try (InputStream stream = Objects.requireNonNull(
                        getClass().getClassLoader().getResourceAsStream(resource),
                        "missing test resource: " + resource)) {
                    Files.copy(stream, dir.resolve(id + ".yml"));
                }
            }
        }, Logger.getAnonymousLogger());
        ModifierStore bundled = new ModifierStore(
                ModifierFiles.fromLoad(bundledRoot, seeder.load(bundledRoot)),
                Logger.getAnonymousLogger());

        assertEquals(21, bundled.modifierNames().size());
        assertEquals(3, bundled.presetNames().size());
        assertEquals("PICK_RANDOM", bundled.selection("gear-dice", 0));
        assertEquals(15.0, bundled.intervalSeconds("gear-dice", 0));
        assertEquals(0.5, bundled.chance("gear-dice", 0));
        assertEquals(1, bundled.pickCount("gear-dice", 0));
        assertEquals(100L, bundled.delayTicks("tpall-on-end", 0));
        assertEquals("AFTER", bundled.preStartOrder("hunter-start-debuffs", 0));
        assertEquals(3, bundled.presetMembers("chaos-mode").size());
        assertEquals(Material.TNT, bundled.presetItem("chaos-mode"));
    }

    @Test
    void bundledCommandsPassSyntaxValidation() throws Exception {
        int checked = 0;
        for (String id : ModsDefaults.MODIFIERS) {
            YamlConfiguration yaml = bundledYaml("modifiers", id);
            var behavior = yaml.getConfigurationSection("behavior");
            if (behavior == null) {
                continue;
            }
            for (String index : behavior.getKeys(false)) {
                var commands = behavior.getConfigurationSection(index)
                        .getConfigurationSection("commands");
                if (commands == null) {
                    continue;
                }
                for (String list : commands.getKeys(false)) {
                    for (String line : commands.getStringList(list)) {
                        assertTrue(CommandSyntax.error(line).isEmpty(),
                                id + "/" + index + "/" + list + ": " + line + " -> "
                                        + CommandSyntax.error(line).orElse(""));
                        checked++;
                    }
                }
            }
        }
        assertTrue(checked > 20, "expected bundled command lines, found none");
    }

    @Test
    void addModifierBumpsNameOnIdCollision() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ModifierStore store = new ModifierStore(ModifierFiles.inMemory(), log);
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
        ModifierStore store = new ModifierStore(ModifierFiles.inMemory(), log);
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

    private void writeFixture(String kind, String id, String body) throws Exception {
        Path dir = modsRoot.resolve(kind);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(id + ".yml"), body, StandardCharsets.UTF_8);
    }

    private ModLoadResult loadRoot() {
        return new ModsLoader(ModsSeeder.none(), Logger.getAnonymousLogger()).load(modsRoot);
    }

    private File modFile(String id) {
        return modsRoot.resolve("modifiers").resolve(id + ".yml").toFile();
    }

    private static YamlConfiguration bundledYaml(String kind, String id) throws Exception {
        String resource = "mods/" + kind + "/" + id + ".yml";
        try (InputStream stream = Objects.requireNonNull(
                ModifierStoreTest.class.getClassLoader().getResourceAsStream(resource),
                "missing test resource: " + resource)) {
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }
}
