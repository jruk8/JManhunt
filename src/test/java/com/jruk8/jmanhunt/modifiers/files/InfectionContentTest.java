package com.jruk8.jmanhunt.modifiers.files;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the bundled infection defaults: fail chance 80, bare item ids, final-only guard. */
class InfectionContentTest {

    @Test
    void bundledInfectionUsesFailChance80AndBareIds() throws Exception {
        String resource = "mods/modifiers/infection.yml";
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, "missing bundled default: " + resource);
        String text;
        try (stream) {
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(text.contains("<lflag:give_fail_chance,80>"), text);
        assertTrue(text.contains("<prole:<args:0>> == SPEEDRUNNER"), text);
        assertTrue(text.contains("[[rotten_flesh, 3, 7], [bone, 2, 4]]"), text);
        assertTrue(text.contains("[[blindness, 0, 5]]"), text);
        assertTrue(!text.contains("minecraft:rotten_flesh")
                && !text.contains("minecraft:bone") && !text.contains("minecraft:blindness"),
                text);
    }

    @Test
    void bundledItemLineTagsBalance() throws Exception {
        assertBalanced(lineWith(bundledText(), "<run:give"), "missing item giver line");
    }

    @Test
    void finalityGuardTagsBalance() throws Exception {
        assertBalanced(lineWith(bundledText(), "<prole:<args:0>>"), "missing finality guard");
    }

    private static String bundledText() throws Exception {
        String resource = "mods/modifiers/infection.yml";
        InputStream stream = InfectionContentTest.class.getClassLoader()
                .getResourceAsStream(resource);
        assertNotNull(stream, "missing bundled default: " + resource);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String lineWith(String text, String needle) {
        String line = null;
        for (String candidate : text.split("\n")) {
            if (candidate.contains(needle)) {
                line = candidate;
            }
        }
        return line;
    }

    private static void assertBalanced(String line, String message) {
        assertNotNull(line, message);
        long opens = line.chars().filter(letter -> letter == '<').count();
        long closes = line.chars().filter(letter -> letter == '>').count();
        assertTrue(opens == closes && opens > 0, line);
    }
}
