package com.jruk8.jmanhunt.modifiers.files;

import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierFileCodecTest {

    @TempDir
    private Path tempDir;

    @Test
    void modifierRoundTrips() throws Exception {
        Path file = write("beef.yml", """
                enabled: true
                meta:
                  name: "Beef"
                  description: "Steak"
                  item: COOKED_BEEF
                  author: JManhunt
                behavior:
                  0:
                    runs-on:
                      - ON_START
                    commands:
                      player:
                        - "give <p> beef 8"
                      custom-list:
                        - "say hi"
                """);

        ModifierEntry entry = ModifierFileCodec.readModifier(file);
        assertTrue(entry.isEnabled());
        assertEquals("Beef", entry.getMeta().getName());
        assertEquals(List.of("ON_START"), entry.getBehavior().get("0").getRunsOn());
        assertEquals(List.of("give <p> beef 8"),
                entry.getBehavior().get("0").getCommands().getLists().get("player"));
        assertEquals(List.of("say hi"),
                entry.getBehavior().get("0").getCommands().getLists().get("custom-list"));

        String once = ModifierFileCodec.serializeModifier(entry);
        ModifierEntry again = ModifierFileCodec.readModifier(write("again.yml", once));
        assertEquals(ModifierFileCodec.fingerprint(once),
                ModifierFileCodec.fingerprint(ModifierFileCodec.serializeModifier(again)));
        assertTrue(once.contains("enabled: true"));
    }

    @Test
    void presetRoundTrips() throws Exception {
        Path file = write("mixed.yml", """
                meta:
                  name: "Mixed"
                modifiers:
                  - beef
                  - bare
                """);

        ModifierPreset preset = ModifierFileCodec.readPreset(file);
        assertEquals("Mixed", preset.getMeta().getName());
        assertEquals(List.of("beef", "bare"), preset.getModifiers());

        String text = ModifierFileCodec.serializePreset(preset);
        assertEquals(List.of("beef", "bare"),
                ModifierFileCodec.readPreset(write("again.yml", text)).getModifiers());
    }

    @Test
    void emptyFileReadsDefaults() throws Exception {
        ModifierEntry entry = ModifierFileCodec.readModifier(write("empty.yml", ""));

        assertFalse(entry.isEnabled());
        assertNull(entry.getMeta());
        assertNull(entry.getBehavior());
    }

    @Test
    void freshEntrySerializesWithoutBinding() throws Exception {
        ModifierEntry entry = new ModifierEntry();
        ModifierMeta meta = new ModifierMeta();
        meta.setName("Fresh");
        entry.setMeta(meta);

        String text = ModifierFileCodec.serializeModifier(entry);

        assertTrue(text.contains("enabled: false"));
        assertTrue(text.contains("Fresh"));
    }

    @Test
    void fingerprintIsStableAndDistinct() {
        assertEquals(64, ModifierFileCodec.fingerprint("a").length());
        assertEquals(ModifierFileCodec.fingerprint("same"), ModifierFileCodec.fingerprint("same"));
        assertTrue(!ModifierFileCodec.fingerprint("a").equals(ModifierFileCodec.fingerprint("b")));
    }

    @Test
    void rejectsNonBooleanEnabled() throws Exception {
        Path file = write("bad.yml", "enabled: \"yes\"\n");

        ModFileException failure =
                assertThrows(ModFileException.class, () -> ModifierFileCodec.readModifier(file));
        assertEquals("enabled must be true or false", failure.getMessage());
    }

    @Test
    void rejectsNonMappingMeta() throws Exception {
        Path file = write("bad.yml", "meta: Beef\n");

        ModFileException failure =
                assertThrows(ModFileException.class, () -> ModifierFileCodec.readModifier(file));
        assertEquals("meta must be a mapping", failure.getMessage());
    }

    @Test
    void rejectsNonMappingBehavior() throws Exception {
        Path file = write("bad.yml", "behavior: oops\n");

        ModFileException failure =
                assertThrows(ModFileException.class, () -> ModifierFileCodec.readModifier(file));
        assertEquals("behavior must be a mapping", failure.getMessage());
    }

    @Test
    void rejectsNonMappingBehaviorValue() throws Exception {
        Path file = write("bad.yml", "behavior:\n  0: junk\n");

        ModFileException failure =
                assertThrows(ModFileException.class, () -> ModifierFileCodec.readModifier(file));
        assertEquals("behavior '0' must be a mapping", failure.getMessage());
    }

    @Test
    void rejectsNonListRunsOn() throws Exception {
        Path file = write("bad.yml", "behavior:\n  0:\n    runs-on: ON_START\n");

        ModFileException failure =
                assertThrows(ModFileException.class, () -> ModifierFileCodec.readModifier(file));
        assertEquals("behavior '0' runs-on must be a list", failure.getMessage());
    }

    @Test
    void rejectsNonListPresetMembers() throws Exception {
        Path file = write("bad.yml", "modifiers: beef\n");

        ModFileException failure =
                assertThrows(ModFileException.class, () -> ModifierFileCodec.readPreset(file));
        assertEquals("modifiers must be a list of ids", failure.getMessage());
    }

    @Test
    void rejectsBadYamlWithSingleLineError() throws Exception {
        Path file = write("bad.yml", "enabled: [oops\n");

        ModFileException failure =
                assertThrows(ModFileException.class, () -> ModifierFileCodec.readModifier(file));
        assertTrue(!failure.getMessage().isBlank());
        assertTrue(!failure.getMessage().contains("\n"));
    }

    @Test
    void rejectsScalarRoot() throws Exception {
        Path file = write("bad.yml", "just a string\n");

        assertThrows(ModFileException.class, () -> ModifierFileCodec.readModifier(file));
    }

    private Path write(String name, String body) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, body, StandardCharsets.UTF_8);
        return file;
    }
}
