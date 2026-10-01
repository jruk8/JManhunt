package com.jruk8.jmanhunt.modifiers;

import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import com.jruk8.jmanhunt.modifiers.files.ModsLoader;
import com.jruk8.jmanhunt.modifiers.files.ModsSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierStoreFilesTest {

    @TempDir
    private Path tempDir;

    private Path modsRoot;
    private ModifierStore store;
    private Logger log;

    @BeforeEach
    void setup() {
        modsRoot = tempDir.resolve("mods");
        log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        store = new ModifierStore(ModifierFiles.atRoot(modsRoot), log);
    }

    @Test
    void toggleWritesOnlyTouchedFile() throws Exception {
        store.createModifier("Alpha", "me");
        store.createModifier("Beta", "me");
        byte[] betaBefore = Files.readAllBytes(modFile("beta"));

        assertTrue(store.setEnabled("alpha", true));

        assertTrue(Arrays.equals(betaBefore, Files.readAllBytes(modFile("beta"))));
        assertTrue(read(modFile("alpha")).contains("enabled: true"));
        assertEquals(Set.of("alpha.yml", "beta.yml"), fileNames(modsRoot.resolve("modifiers")));
    }

    @Test
    void createWritesRootLevelFiles() throws Exception {
        assertEquals("my-mod", store.createModifier("My Mod", "me"));
        assertEquals("my-preset", store.createPreset("My Preset", "me"));

        assertTrue(Files.isRegularFile(modFile("my-mod")));
        assertTrue(Files.isRegularFile(presetFile("my-preset")));
        assertEquals(1, fileNames(modsRoot.resolve("modifiers")).size());
        assertEquals(1, fileNames(modsRoot.resolve("presets")).size());
    }

    @Test
    void removeDeletesOnlyItsFile() throws Exception {
        store.createModifier("Alpha", "me");
        store.createModifier("Beta", "me");

        assertTrue(store.removeModifier("alpha"));
        assertFalse(store.removeModifier("missing"));

        assertFalse(Files.exists(modFile("alpha")));
        assertTrue(Files.isRegularFile(modFile("beta")));
    }

    @Test
    void renameMovesFileAndFollowsPresets() throws Exception {
        store.createModifier("Olive", "me");
        store.createPreset("P", "me");
        assertTrue(store.memberAdd("p", "olive"));

        assertTrue(store.renameModifier("olive", "grape"));

        assertFalse(Files.exists(modFile("olive")));
        assertTrue(Files.isRegularFile(modFile("grape")));
        String presetText = read(presetFile("p"));
        assertTrue(presetText.contains("- grape"));
        assertFalse(presetText.contains("olive"));
        assertEquals(List.of("grape"), store.presetMembers("p"));
    }

    @Test
    void memberOpsSaveOnlyPresetFile() throws Exception {
        store.createModifier("Solo", "me");
        store.createPreset("P", "me");
        byte[] modBefore = Files.readAllBytes(modFile("solo"));

        assertTrue(store.memberAdd("p", "solo"));
        assertTrue(Arrays.equals(modBefore, Files.readAllBytes(modFile("solo"))));
        assertTrue(read(presetFile("p")).contains("- solo"));

        assertTrue(store.memberRemove("p", "solo"));
        assertTrue(Arrays.equals(modBefore, Files.readAllBytes(modFile("solo"))));
        assertFalse(read(presetFile("p")).contains("- solo"));
    }

    @Test
    void updatesPersistToTheirFiles() throws Exception {
        store.createModifier("M", "me");
        store.createPreset("P", "me");

        assertTrue(store.updateModifier("m", entry -> entry.getMeta().setName("Renamed")));
        assertTrue(store.updatePreset("p", preset -> preset.getMeta().setName("Preset Renamed")));

        assertTrue(read(modFile("m")).contains("Renamed"));
        assertTrue(read(presetFile("p")).contains("Preset Renamed"));
    }

    @Test
    void replaceAllSwapsContents() throws Exception {
        writeMod("a", "enabled: true\n");
        store.replaceAll(loadRoot());
        assertTrue(store.hasModifier("a"));
        assertEquals(modsRoot.resolve("modifiers/a.yml"), store.modifierPath("a"));

        Files.delete(modFile("a"));
        writeMod("b", "enabled: false\n");
        store.replaceAll(loadRoot());

        assertTrue(store.hasModifier("b"));
        assertFalse(store.hasModifier("a"));
        assertNull(store.modifierPath("a"));
        assertEquals(modsRoot.resolve("modifiers/b.yml"), store.modifierPath("b"));
        assertEquals(Set.of("b"), store.modifierNames());
    }

    @Test
    void createAgainstNestedTakenIdNumbersAtRoot() throws Exception {
        writeMod("sub/dup", "enabled: false\nmeta:\n  name: Sub Dup\n");
        store.replaceAll(loadRoot());
        byte[] nestedBefore = Files.readAllBytes(modsRoot.resolve("modifiers/sub/dup.yml"));

        ModifierEntry entry = new ModifierEntry();
        ModifierMeta meta = new ModifierMeta();
        meta.setName("Dup");
        entry.setMeta(meta);

        assertEquals("dup-2", store.addModifier("dup", entry));
        assertTrue(Files.isRegularFile(modFile("dup-2")));
        assertTrue(read(modFile("dup-2")).contains("Dup 2"));
        assertTrue(Arrays.equals(nestedBefore,
                Files.readAllBytes(modsRoot.resolve("modifiers/sub/dup.yml"))));
    }

    @Test
    void renameRefusesNestedTakenId() throws Exception {
        writeMod("sub/dup", "enabled: false\n");
        writeMod("olive", "enabled: false\n");
        store.replaceAll(loadRoot());

        assertFalse(store.renameModifier("olive", "dup"));

        assertTrue(Files.isRegularFile(modFile("olive")));
        assertFalse(Files.exists(modsRoot.resolve("modifiers/dup-2.yml")));
    }

    @Test
    void renamePreservesDirectory() throws Exception {
        writeMod("sub/old", "enabled: true\nmeta:\n  name: Old\n");
        writePreset("p", "modifiers:\n  - old\n");
        store.replaceAll(loadRoot());

        assertTrue(store.renameModifier("old", "new"));

        assertFalse(Files.exists(modsRoot.resolve("modifiers/sub/old.yml")));
        assertTrue(Files.isRegularFile(modsRoot.resolve("modifiers/sub/new.yml")));
        assertFalse(Files.exists(modFile("new")));
        assertEquals(List.of("new"), store.presetMembers("p"));
        assertTrue(read(presetFile("p")).contains("- new"));
    }

    @Test
    void deleteLeavesSiblingsAndEmptyDirs() throws Exception {
        writeMod("sub/a", "enabled: false\n");
        writeMod("sub/b", "enabled: false\n");
        writeMod("keeper", "enabled: false\n");
        store.replaceAll(loadRoot());

        assertTrue(store.removeModifier("a"));
        assertFalse(Files.exists(modsRoot.resolve("modifiers/sub/a.yml")));
        assertTrue(Files.isRegularFile(modsRoot.resolve("modifiers/sub/b.yml")));

        assertTrue(store.removeModifier("b"));

        assertFalse(Files.exists(modsRoot.resolve("modifiers/sub/b.yml")));
        assertTrue(Files.isDirectory(modsRoot.resolve("modifiers/sub")));
        assertTrue(Files.isRegularFile(modFile("keeper")));
    }

    @Test
    void nestedFilesKeepTheirDirectoryOnSave() throws Exception {
        writeMod("sub/nest", "enabled: false\n");
        store.replaceAll(loadRoot());

        assertTrue(store.setEnabled("nest", true));

        assertTrue(read(modsRoot.resolve("modifiers/sub/nest.yml")).contains("enabled: true"));
        assertFalse(Files.exists(modFile("nest")));
        assertEquals(modsRoot.resolve("modifiers/sub/nest.yml"), store.modifierPath("nest"));
    }

    private ModLoadResult loadRoot() {
        return new ModsLoader(ModsSeeder.none(), log).load(modsRoot);
    }

    private void writeMod(String id, String body) throws Exception {
        Path file = modFile(id);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body, StandardCharsets.UTF_8);
    }

    private void writePreset(String id, String body) throws Exception {
        Path file = presetFile(id);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body, StandardCharsets.UTF_8);
    }

    private Path modFile(String id) {
        return modsRoot.resolve("modifiers").resolve(id + ".yml");
    }

    private Path presetFile(String id) {
        return modsRoot.resolve("presets").resolve(id + ".yml");
    }

    private static String read(Path file) throws Exception {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static Set<String> fileNames(Path dir) throws Exception {
        try (var stream = Files.list(dir)) {
            return stream.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }
}
