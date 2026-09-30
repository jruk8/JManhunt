package com.jruk8.jmanhunt.modifiers.files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModsLoaderTest {

    @TempDir
    private Path tempDir;

    private Path modsRoot;
    private List<String> warnings;
    private Logger log;

    @BeforeEach
    void setup() {
        modsRoot = tempDir.resolve("mods");
        warnings = new ArrayList<>();
        log = Logger.getAnonymousLogger();
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
    }

    @Test
    void duplicateFirstInLoadOrderWins() throws Exception {
        write("modifiers/dup.yml", "enabled: true\nmeta:\n  name: Root\n");
        write("modifiers/sub/dup.yml", "enabled: false\nmeta:\n  name: Sub\n");

        ModLoadResult result = loader().load(modsRoot);

        assertEquals("Sub", result.modifiers().get("dup").entry().getMeta().getName());
        assertEquals(1, result.duplicates().size());
        var dupe = result.duplicates().get(0);
        assertEquals("dup", dupe.id());
        assertEquals(ModFileKind.MODIFIER, dupe.kind());
        assertEquals(modsRoot.resolve("modifiers/sub/dup.yml"), dupe.winner());
        assertEquals(List.of(modsRoot.resolve("modifiers/dup.yml")), dupe.skipped());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains(display("modifiers/sub/dup.yml")));
        assertTrue(warnings.get(0).contains(display("modifiers/dup.yml")));
    }

    @Test
    void crossKindSameIdIsFine() throws Exception {
        write("modifiers/shared.yml", "enabled: true\n");
        write("presets/shared.yml", "modifiers:\n  - shared\n");

        ModLoadResult result = loader().load(modsRoot);

        assertTrue(result.modifiers().containsKey("shared"));
        assertTrue(result.presets().containsKey("shared"));
        assertTrue(result.duplicates().isEmpty());
        assertTrue(result.unknownMembers().isEmpty());
        assertEquals(List.of("shared"), result.presets().get("shared").preset().getModifiers());
    }

    @Test
    void unknownFilesRecordedWithExtensionInWalkOrder() throws Exception {
        write("modifiers/z.yml", "enabled: true\n");
        write("modifiers/note.txt", "hello");
        write("modifiers/sub/index.html", "<b>hi</b>");

        ModLoadResult result = loader().load(modsRoot);

        assertEquals(List.of("index.html", "note.txt"),
                result.unknownFiles().stream().map(file -> file.filename()).toList());
        assertTrue(result.modifiers().containsKey("z"));
    }

    @Test
    void dotfilesAndTempIgnoredSilently() throws Exception {
        write("modifiers/.hidden.yml", "enabled: [broken");
        write("modifiers/stale.yml.tmp", "enabled: [broken");
        write("modifiers/ok.yml", "enabled: true\n");

        ModLoadResult result = loader().load(modsRoot);

        assertEquals(List.of("ok"), new ArrayList<>(result.modifiers().keySet()));
        assertTrue(result.unknownFiles().isEmpty());
        assertTrue(result.failedFiles().isEmpty());
        assertTrue(result.duplicates().isEmpty());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void failedFilesSkippedAndRecorded() throws Exception {
        write("modifiers/good.yml", "enabled: true\n");
        write("modifiers/broken.yml", "enabled: [oops\n");
        write("presets/broken.yml", "modifiers: oops\n");

        ModLoadResult result = loader().load(modsRoot);

        assertEquals(List.of("good"), new ArrayList<>(result.modifiers().keySet()));
        assertTrue(result.presets().isEmpty());
        assertEquals(2, result.failedFiles().size());
        var modifierFailure = result.failedFiles().get(0);
        assertEquals("broken.yml", modifierFailure.filename());
        assertEquals(ModFileKind.MODIFIER, modifierFailure.kind());
        assertTrue(!modifierFailure.error().isBlank());
        assertTrue(!modifierFailure.error().contains("\n"));
        var presetFailure = result.failedFiles().get(1);
        assertEquals("broken.yml", presetFailure.filename());
        assertEquals(ModFileKind.PRESET, presetFailure.kind());
        assertEquals("modifiers must be a list of ids", presetFailure.error());
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).startsWith(display("modifiers/broken.yml") + ": "));
        assertTrue(warnings.get(1).startsWith(display("presets/broken.yml") + ": "));
    }

    @Test
    void unknownMemberWarnsAndSkipsInMemoryOnly() throws Exception {
        write("modifiers/real.yml", "enabled: true\n");
        write("presets/p.yml", "modifiers:\n  - ghost\n  - real\n");

        ModLoadResult result = loader().load(modsRoot);

        assertEquals(List.of("real"), result.presets().get("p").preset().getModifiers());
        assertEquals(1, result.unknownMembers().size());
        assertEquals("p", result.unknownMembers().get(0).presetId());
        assertEquals("ghost", result.unknownMembers().get(0).memberId());
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("p"));
        assertTrue(warnings.get(0).contains("ghost"));
        String disk = Files.readString(modsRoot.resolve("presets/p.yml"), StandardCharsets.UTF_8);
        assertTrue(disk.contains("ghost"));
    }

    @Test
    void seedsOnlyWhenDirMissing() throws Exception {
        Map<ModFileKind, Integer> calls = new EnumMap<>(ModFileKind.class);
        ModsSeeder seeder = (kind, dir) -> {
            calls.merge(kind, 1, Integer::sum);
            Files.writeString(dir.resolve("seeded.yml"), "enabled: true\n", StandardCharsets.UTF_8);
        };

        ModLoadResult first = loader(seeder).load(modsRoot);
        assertEquals(1, first.modifiers().size());
        assertEquals(1, calls.get(ModFileKind.MODIFIER));
        assertEquals(1, calls.get(ModFileKind.PRESET));

        try (var stream = Files.walk(modsRoot)) {
            stream.filter(Files::isRegularFile).forEach(path -> path.toFile().delete());
        }
        ModLoadResult second = loader(seeder).load(modsRoot);

        assertTrue(second.modifiers().isEmpty());
        assertTrue(second.presets().isEmpty());
        assertEquals(1, calls.get(ModFileKind.MODIFIER));
        assertEquals(1, calls.get(ModFileKind.PRESET));
    }

    private ModsLoader loader() {
        return new ModsLoader(ModsSeeder.none(), log);
    }

    private ModsLoader loader(ModsSeeder seeder) {
        return new ModsLoader(seeder, log);
    }

    private String display(String relative) {
        return modsRoot.relativize(modsRoot.resolve(relative)).toString();
    }

    private void write(String relative, String body) throws Exception {
        Path file = modsRoot.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, body, StandardCharsets.UTF_8);
    }
}
