package com.jruk8.jmanhunt.challenges;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuckyBlockEngineTest {

    @TempDir
    Path tempDir;

    private File writeYaml(String content) throws IOException {
        File file = tempDir.resolve("lucky-blocks.yml").toFile();
        Files.writeString(file.toPath(), content);
        return file;
    }

    @Test
    void parsesItemOutcome() throws IOException {
        File file = writeYaml("""
                outcomes:
                  diamonds:
                    weight: 5.0
                    type: ITEM
                    item-settings:
                      name: diamond
                      quantity: 2
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals("diamonds", outcome.name());
        assertEquals(5.0, outcome.weight());
        assertEquals(LuckyBlockEngine.OutcomeType.ITEM, outcome.type());
        assertEquals("diamond", outcome.itemName());
        assertEquals(2, outcome.quantity());
    }

    @Test
    void parsesNoneOutcomeWithDefaultWeight() throws IOException {
        File file = writeYaml("""
                outcomes:
                  nothing:
                    type: NONE
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals(LuckyBlockEngine.OutcomeType.NONE, outcome.type());
        assertEquals(1.0, outcome.weight());
    }

    @Test
    void parsesCommandOutcomeWithDefaultRelativeTo() throws IOException {
        File file = writeYaml("""
                outcomes:
                  sharp-sword:
                    type: COMMAND
                    command-settings:
                      commands:
                      - "give @p golden_sword 1"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals(LuckyBlockEngine.OutcomeType.COMMAND, outcome.type());
        assertEquals("BLOCK", outcome.relativeTo());
        assertEquals(1, outcome.commands().size());
        assertEquals("give @p golden_sword 1", outcome.commands().get(0));
    }

    @Test
    void parsesCommandOutcomeWithPlayerRelativeTo() throws IOException {
        File file = writeYaml("""
                outcomes:
                  tnt:
                    type: COMMAND
                    command-settings:
                      relative-to: PLAYER
                      commands:
                      - "summon tnt ~ ~1.5 ~ {fuse:40}"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals("PLAYER", outcome.relativeTo());
    }

    @Test
    void rollReturnsOutcomeWithinWeightedRange() throws IOException {
        File file = writeYaml("""
                outcomes:
                  a:
                    weight: 1.0
                    type: NONE
                  b:
                    weight: 3.0
                    type: NONE
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        // Roll many times; both outcomes should appear and never null.
        boolean sawA = false;
        boolean sawB = false;
        for (int i = 0; i < 1000; i++) {
            LuckyBlockEngine.Outcome outcome = engine.roll();
            assertNotNull(outcome);
            if (outcome.name().equals("a")) sawA = true;
            if (outcome.name().equals("b")) sawB = true;
        }
        assertTrue(sawA);
        assertTrue(sawB);
    }

    @Test
    void rollReturnsNullWhenNoOutcomes() {
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertNull(engine.roll());
    }

    @Test
    void rejectsInvalidType() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    type: EXPLODE
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.load(file));
        assertTrue(ex.getMessage().contains("bad"));
    }

    @Test
    void rejectsItemMissingName() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    type: ITEM
                    item-settings:
                      quantity: 1
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.load(file));
        assertTrue(ex.getMessage().contains("bad"));
    }

    @Test
    void rejectsCommandMissingCommands() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    type: COMMAND
                    command-settings:
                      relative-to: BLOCK
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.load(file));
        assertTrue(ex.getMessage().contains("bad"));
    }

    @Test
    void rejectsNonPositiveWeight() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    weight: 0.0
                    type: NONE
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.load(file));
        assertTrue(ex.getMessage().contains("bad"));
    }

    @Test
    void rejectsInvalidRelativeTo() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    type: COMMAND
                    command-settings:
                      relative-to: SELF
                      commands:
                      - "say hi"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.load(file));
        assertTrue(ex.getMessage().contains("bad"));
    }
}