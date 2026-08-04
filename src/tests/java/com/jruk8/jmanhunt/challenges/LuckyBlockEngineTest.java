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
    void parsesItemsOutcome() throws IOException {
        File file = writeYaml("""
                outcomes:
                  diamonds:
                    weight: 5.0
                    items:
                      - diamond 2
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals("diamonds", outcome.name());
        assertEquals(5.0, outcome.weight());
        assertEquals(1, outcome.items().size());
        assertEquals("diamond", outcome.items().get(0).name());
        assertEquals(2, outcome.items().get(0).quantity());
        assertTrue(outcome.commands().isEmpty());
        assertNull(outcome.structure());
        assertNull(outcome.feedback());
    }

    @Test
    void parsesItemWithDefaultQuantity() throws IOException {
        File file = writeYaml("""
                outcomes:
                  gold:
                    items:
                      - gold_ingot
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals(1, outcome.items().size());
        assertEquals("gold_ingot", outcome.items().get(0).name());
        assertEquals(1, outcome.items().get(0).quantity());
    }

    @Test
    void parsesEmptyOutcomeWithDefaultWeight() throws IOException {
        File file = writeYaml("""
                outcomes:
                  nothing: {}
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals(1.0, outcome.weight());
        assertTrue(outcome.items().isEmpty());
        assertTrue(outcome.commands().isEmpty());
        assertNull(outcome.structure());
        assertNull(outcome.feedback());
    }

    @Test
    void parsesCommandsOutcomeWithDefaultRelativeTo() throws IOException {
        File file = writeYaml("""
                outcomes:
                  sharp-sword:
                    commands:
                      commands:
                        - "give @p golden_sword 1"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals("BLOCK", outcome.relativeTo());
        assertEquals(1, outcome.commands().size());
        assertEquals("give @p golden_sword 1", outcome.commands().get(0));
    }

    @Test
    void parsesCommandsOutcomeWithPlayerRelativeTo() throws IOException {
        File file = writeYaml("""
                outcomes:
                  tnt:
                    commands:
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
                    items:
                      - diamond 1
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
                  plain: {}
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
                  b:
                    weight: 3.0
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
    void rejectsNonPositiveWeight() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    weight: 0.0
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
                    commands:
                      relative-to: SELF
                      commands:
                        - "say hi"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.load(file));
        assertTrue(ex.getMessage().contains("bad"));
    }

    @Test
    void rejectsCommandsSectionWithoutCommands() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    commands:
                      relative-to: BLOCK
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
                    structure:
                      name: coin-well
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertNotNull(outcome.structure());
        assertEquals("coin-well", outcome.structure().name());
        assertFalse(outcome.structure().randomRotation());
    }

    @Test
    void parsesStructureOutcomeWithRandomRotation() throws IOException {
        File file = writeYaml("""
                outcomes:
                  coin-well:
                    structure:
                      name: coin-well
                      random-rotation: true
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertTrue(outcome.structure().randomRotation());
    }

    @Test
    void rejectsStructureMissingName() throws IOException {
        File file = writeYaml("""
                outcomes:
                  bad:
                    structure:
                      random-rotation: true
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> engine.load(file));
        assertTrue(ex.getMessage().contains("bad"));
    }

    @Test
    void parsesComposableOutcomeWithAllSections() throws IOException {
        File file = writeYaml("""
                outcomes:
                  pirate-ship:
                    weight: 2
                    structure:
                      name: pirate-ship
                    commands:
                      relative-to: BLOCK
                      commands:
                        - "summon pillager ~ ~ ~"
                    items:
                      - spyglass 1
                      - cooked_cod 16
                    feedback:
                      message: "<gold>Land ho!</gold>"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals("pirate-ship", outcome.name());
        assertEquals(2.0, outcome.weight());
        assertEquals(2, outcome.items().size());
        assertEquals("spyglass", outcome.items().get(0).name());
        assertEquals(1, outcome.items().get(0).quantity());
        assertEquals("cooked_cod", outcome.items().get(1).name());
        assertEquals(16, outcome.items().get(1).quantity());
        assertEquals(1, outcome.commands().size());
        assertEquals("summon pillager ~ ~ ~", outcome.commands().get(0));
        assertEquals("BLOCK", outcome.relativeTo());
        assertNotNull(outcome.structure());
        assertEquals("pirate-ship", outcome.structure().name());
        assertNotNull(outcome.feedback());
        assertEquals("<gold>Land ho!</gold>", outcome.feedback().message());
    }

    @Test
    void parsesComposableOutcomeWithItemsAndCommands() throws IOException {
        File file = writeYaml("""
                outcomes:
                  combo:
                    items:
                      - diamond 3
                    commands:
                      commands:
                        - "give <p> golden_sword 1"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals(1, outcome.items().size());
        assertEquals("diamond", outcome.items().get(0).name());
        assertEquals(3, outcome.items().get(0).quantity());
        assertEquals(1, outcome.commands().size());
        assertNull(outcome.structure());
    }

    @Test
    void parsesComposableOutcomeWithStructureAndCommands() throws IOException {
        File file = writeYaml("""
                outcomes:
                  combo:
                    structure:
                      name: castle
                    commands:
                      commands:
                        - "say A castle appeared!"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertNotNull(outcome.structure());
        assertEquals("castle", outcome.structure().name());
        assertEquals(1, outcome.commands().size());
    }

    @Test
    void parsesComposableOutcomeWithItemsAndFeedback() throws IOException {
        File file = writeYaml("""
                outcomes:
                  combo:
                    items:
                      - diamond 1
                    feedback:
                      message: "<gold>Shiny!</gold>"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals(1, outcome.items().size());
        assertNotNull(outcome.feedback());
        assertEquals("<gold>Shiny!</gold>", outcome.feedback().message());
    }

    @Test
    void parsesComposableOutcomeWithStructureOnly() throws IOException {
        File file = writeYaml("""
                outcomes:
                  castle:
                    structure:
                      name: castle
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertNotNull(outcome.structure());
        assertTrue(outcome.items().isEmpty());
        assertTrue(outcome.commands().isEmpty());
        assertNull(outcome.feedback());
    }

    @Test
    void parsesComposableOutcomeWithCommandsOnly() throws IOException {
        File file = writeYaml("""
                outcomes:
                  say-hi:
                    commands:
                      commands:
                        - "say hello"
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals(1, outcome.commands().size());
        assertTrue(outcome.items().isEmpty());
        assertNull(outcome.structure());
    }

    @Test
    void parsesComposableOutcomeWithItemsOnly() throws IOException {
        File file = writeYaml("""
                outcomes:
                  loot:
                    items:
                      - diamond 5
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        LuckyBlockEngine.Outcome outcome = engine.outcomes().get(0);
        assertEquals(1, outcome.items().size());
        assertEquals(5, outcome.items().get(0).quantity());
        assertTrue(outcome.commands().isEmpty());
        assertNull(outcome.structure());
    }

    @Test
    void clearResetsOutcomes() throws IOException {
        File file = writeYaml("""
                outcomes:
                  a: {}
                """);
        LuckyBlockEngine engine = new LuckyBlockEngine();
        assertTrue(engine.load(file));
        assertEquals(1, engine.outcomes().size());
        engine.clear();
        assertEquals(0, engine.outcomes().size());
        assertNull(engine.roll());
    }
}
