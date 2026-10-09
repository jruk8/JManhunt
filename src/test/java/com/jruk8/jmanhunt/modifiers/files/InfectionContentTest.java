package com.jruk8.jmanhunt.modifiers.files;

import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the bundled infection defaults: fail chance 80 and bare item ids. */
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
        assertTrue(text.contains("[[rotten_flesh, 3, 7], [bone, 2, 4]]"), text);
        assertTrue(text.contains("[[blindness, 0, 5]]"), text);
        assertTrue(!text.contains("minecraft:rotten_flesh")
                && !text.contains("minecraft:bone") && !text.contains("minecraft:blindness"),
                text);
    }

    @Test
    void bundledItemLineTagsBalance() throws Exception {
        String resource = "mods/modifiers/infection.yml";
        InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, "missing bundled default: " + resource);
        String text;
        try (stream) {
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        String line = null;
        for (String candidate : text.split("\n")) {
            if (candidate.contains("<run:give")) {
                line = candidate;
            }
        }
        assertNotNull(line, "missing item giver line");
        long opens = line.chars().filter(letter -> letter == '<').count();
        long closes = line.chars().filter(letter -> letter == '>').count();
        assertTrue(opens == closes && opens > 0, line);
    }
}
