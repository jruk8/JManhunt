package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared config-field lore: fixed section order, optional bounds
 * or bullets, and green marking for the effective options.
 */
class FieldLoreTest {

    private MessageService messages;
    private MessagesConfig texts;

    @BeforeEach
    void setup() {
        messages = new MessageService();
        texts = new MessagesConfig();
        messages.reload(texts);
    }

    @Test
    void fullSchemaOrdersEverySection() {
        FieldLore.Field field = new FieldLore.Field(
                "Lowest clean block light.", "5", "match.cells.min-light", "Integer",
                "0 to 15", null, null, "5", "Click to edit", false);

        assertEquals(List.of(
                "Lowest clean block light.",
                "",
                "Value: <white>5",
                "Path: <white>match.cells.min-light",
                "Type: <white>Integer",
                "Allowed: <white>0 to 15",
                "Default: <white>5",
                "",
                "Click to edit",
                "Right-click to reset"),
                FieldLore.lines(texts.getManhuntGui(), field));
    }

    @Test
    void blankDescriptionHidesTheBlock() {
        FieldLore.Field field = new FieldLore.Field(
                "", "PER_EXECUTOR", "modifiers.zebra.behavior.x", "Choice",
                null, List.of("PER_INVOKE", "PER_EXECUTOR"), Set.of("PER_EXECUTOR"),
                "PER_INVOKE", "Click to cycle", false);

        assertEquals(List.of(
                "Value: <white>PER_EXECUTOR",
                "Path: <white>modifiers.zebra.behavior.x",
                "Type: <white>Choice",
                "» PER_INVOKE",
                "<green>» PER_EXECUTOR</green>",
                "Default: <white>PER_INVOKE",
                "",
                "Click to cycle",
                "Right-click to reset"),
                FieldLore.lines(texts.getManhuntGui(), field));
    }

    @Test
    void restartNoticeSitsUnderDefaultWithBlankBeforeHint() {
        FieldLore.Field field = new FieldLore.Field(
                "World engine master switch.", "Enabled", "world-engine.enabled", "Boolean",
                null, null, null, "Disabled", "Click to toggle", true);

        assertEquals(List.of(
                "World engine master switch.",
                "",
                "Value: <white>Enabled",
                "Path: <white>world-engine.enabled",
                "Type: <white>Boolean",
                "Default: <white>Disabled",
                "<gray>(requires a server restart)",
                "",
                "Click to toggle",
                "Right-click to reset"),
                FieldLore.lines(texts.getManhuntGui(), field));
    }

    @Test
    void multipleMarksAllRenderGreen() {
        FieldLore.Field field = new FieldLore.Field(
                "Events that trigger this modifier.", "2 selected",
                "modifiers.zebra.behavior.runs-on", "Choice",
                null, List.of("ON_START", "INTERVAL", "ON_RESPAWN"),
                Set.of("ON_START", "INTERVAL"), "ON_START", "Click to open", false);

        assertEquals(List.of(
                "Events that trigger this modifier.",
                "",
                "Value: <white>2 selected",
                "Path: <white>modifiers.zebra.behavior.runs-on",
                "Type: <white>Choice",
                "<green>» ON_START</green>",
                "<green>» INTERVAL</green>",
                "» ON_RESPAWN",
                "Default: <white>ON_START",
                "",
                "Click to open",
                "Right-click to reset"),
                FieldLore.lines(texts.getManhuntGui(), field));
    }
}
