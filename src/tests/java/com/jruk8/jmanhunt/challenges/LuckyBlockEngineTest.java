package com.jruk8.jmanhunt.challenges;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void parsesFeedbackWithSoundMessageAndBroadcast() throws IOException {
        File file = writeYaml("""
                outcomes:
                  diamonds:
                    type: ITEM
                    item-settings:
                      name: diamond
                    feedback:
                      sound:
                        enabled: true
                        sound: block.amethyst_block.step
                        pitch: 2.0
                        volume: 1.0
                      message: "<aqua>You fancied yourself some diamonds!</aqua>"
                      broadcast: "<aqua><white>{player}</white> fancied themselves diamonds!</aqua>"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        LuckyBlockEngine.Feedback feedback = outcome.feedback();
        assertNotNull(feedback);
        assertNotNull(feedback.sound());
        assertTrue(feedback.sound().enabled());
        assertEquals("block.amethyst_block.step", feedback.sound().sound());
        assertEquals(2.0f, feedback.sound().pitch());
        assertEquals(1.0f, feedback.sound().volume());
        assertEquals("<aqua>You fancied yourself some diamonds!</aqua>", feedback.message());
        assertEquals("<aqua><white>{player}</white> fancied themselves diamonds!</aqua>", feedback.broadcast());
    }

    @Test
    void parsesFeedbackWithDefaultPitchAndVolume() throws IOException {
        File file = writeYaml("""
                outcomes:
                  nothing:
                    type: NONE
                    feedback:
                      sound:
                        enabled: true
                        sound: block.anvil.hit
                      message: "<gray>Nothing happened.</gray>"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        LuckyBlockEngine.Feedback feedback = outcome.feedback();
        assertNotNull(feedback);
        assertNotNull(feedback.sound());
        assertEquals(1.0f, feedback.sound().pitch());
        assertEquals(1.0f, feedback.sound().volume());
        assertEquals("<gray>Nothing happened.</gray>", feedback.message());
        assertNull(feedback.broadcast());
    }

    @Test
    void parsesOutcomeWithoutFeedback() throws IOException {
        File file = writeYaml("""
                outcomes:
                  plain:
                    type: NONE
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertNull(outcome.feedback());
    }

    @Test
    void rejectsFeedbackSoundMissingEnabled() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    type: NONE
                    feedback:
                      sound:
                        sound: block.anvil.hit
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.load(file));
        assertTrue(ex.getMessage().contains("bad"));
    }

    @Test
    void rejectsFeedbackSoundMissingSound() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    type: NONE
                    feedback:
                      sound:
                        enabled: true
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.load(file));
        assertTrue(ex.getMessage().contains("bad"));
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

    @Test
    void parsesStructureOutcome() throws IOException {
        File file = writeYaml("""
                outcomes:
                  coin-well:
                    weight: 1.0
                    type: STRUCTURE
                    structure-settings:
                      name: coin-well
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals(LuckyBlockEngine.OutcomeType.STRUCTURE, outcome.type());
        assertNotNull(outcome.structureSettings());
        assertEquals("coin-well", outcome.structureSettings().name());
        assertFalse(outcome.structureSettings().randomRotation());
    }

    @Test
    void parsesStructureOutcomeWithRandomRotation() throws IOException {
        File file = writeYaml("""
                outcomes:
                  coin-well:
                    type: STRUCTURE
                    structure-settings:
                      name: coin-well
                      random-rotation: true
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertTrue(outcome.structureSettings().randomRotation());
    }

    @Test
    void rejectsStructureMissingSettings() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    type: STRUCTURE
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.load(file));
        assertTrue(ex.getMessage().contains("bad"));
    }

    @Test
    void rejectsStructureMissingName() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    type: STRUCTURE
                    structure-settings:
                      random-rotation: true
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.load(file));
        assertTrue(ex.getMessage().contains("bad"));
    }

    @Test
    void clearResetsOutcomes() throws IOException {
        File file = writeYaml("""
                outcomes:
                  a:
                    type: NONE
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        assertEquals(1, engine.outcomes().size());
        engine.clear();
        assertEquals(0, engine.outcomes().size());
        assertNull(engine.roll());
    }
}
