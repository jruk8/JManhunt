package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierToggleAllTest {

    private static ConfigService service(ModifierFiles config) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return new ConfigService(null, new ModifierStore(config, log));
    }

    private static MessageService messages(MessagesConfig texts) {
        MessageService messages = new MessageService();
        messages.reload(texts);
        return messages;
    }

    private static void addModifier(ModifierFiles config, String id, boolean enabled) {
        ModifierEntry entry = new ModifierEntry();
        entry.setEnabled(enabled);
        config.getModifiers().put(id, entry);
    }

    @Test
    void toggleAllModifiersFlipsEveryModifierWithOneSummary() {
        ModifierFiles config = ModifierFiles.inMemory();
        addModifier(config, "a", false);
        addModifier(config, "b", true);
        ConfigService service = service(config);
        MessagesConfig texts = new MessagesConfig();
        MessageService messages = messages(texts);
        ModifiersCommand command = new ModifiersCommand(service, messages,
                texts.getModifiers(), texts.getCommand(), null, null, null, null);
        FakeSender sender = FakeSender.permitted();

        assertTrue(command.toggleAllModifiers(sender, List.of("a", "b"), true));

        assertTrue(service.modifierEnabled("a"));
        assertTrue(service.modifierEnabled("b"));
        Component expected = messages.componentRaw(texts.getModifiers().getToggleAllSuccess(),
                Map.of("count", "2", "kind", "modifiers", "state", "on"));
        assertEquals(List.of(expected), sender.received());
    }

    @Test
    void toggleAllModifiersSkipsUnknownIdsQuietly() {
        ModifierFiles config = ModifierFiles.inMemory();
        addModifier(config, "a", false);
        ConfigService service = service(config);
        MessagesConfig texts = new MessagesConfig();
        MessageService messages = messages(texts);
        ModifiersCommand command = new ModifiersCommand(service, messages,
                texts.getModifiers(), texts.getCommand(), null, null, null, null);
        FakeSender sender = FakeSender.permitted();

        assertTrue(command.toggleAllModifiers(sender, List.of("a", "nope"), true));

        assertTrue(service.modifierEnabled("a"));
        Component expected = messages.componentRaw(texts.getModifiers().getToggleAllSuccess(),
                Map.of("count", "1", "kind", "modifiers", "state", "on"));
        assertEquals(List.of(expected), sender.received());
    }

    @Test
    void toggleAllModifiersWithoutPermissionChangesNothing() {
        ModifierFiles config = ModifierFiles.inMemory();
        addModifier(config, "a", false);
        ConfigService service = service(config);
        MessagesConfig texts = new MessagesConfig();
        MessageService messages = messages(texts);
        ModifiersCommand command = new ModifiersCommand(service, messages,
                texts.getModifiers(), texts.getCommand(), null, null, null, null);
        FakeSender sender = FakeSender.denied();

        assertTrue(command.toggleAllModifiers(sender, List.of("a"), true));

        assertFalse(service.modifierEnabled("a"));
        assertEquals(List.of(messages.componentRaw(texts.getCommand().getNoPermission(), Map.of())), sender.received());
    }

    @Test
    void toggleAllPresetsFlipsMembersWithOneSummary() {
        ModifierFiles config = ModifierFiles.inMemory();
        addModifier(config, "a", false);
        addModifier(config, "b", false);
        ModifierPreset preset = new ModifierPreset();
        preset.setModifiers(List.of("a", "b"));
        config.getPresets().put("pack", preset);
        ConfigService service = service(config);
        MessagesConfig texts = new MessagesConfig();
        MessageService messages = messages(texts);
        ModifiersCommand command = new ModifiersCommand(service, messages,
                texts.getModifiers(), texts.getCommand(), null, null, null, null);
        FakeSender sender = FakeSender.permitted();

        assertTrue(command.toggleAllPresets(sender, List.of("pack", "nope"), true));

        assertTrue(service.modifierEnabled("a"));
        assertTrue(service.modifierEnabled("b"));
        Component expected = messages.componentRaw(texts.getModifiers().getToggleAllSuccess(),
                Map.of("count", "1", "kind", "presets", "state", "on"));
        assertEquals(List.of(expected), sender.received());
    }

    @Test
    void toggleAllPresetsWithoutPermissionChangesNothing() {
        ModifierFiles config = ModifierFiles.inMemory();
        addModifier(config, "a", false);
        ModifierPreset preset = new ModifierPreset();
        preset.setModifiers(List.of("a"));
        config.getPresets().put("pack", preset);
        ConfigService service = service(config);
        MessagesConfig texts = new MessagesConfig();
        MessageService messages = messages(texts);
        ModifiersCommand command = new ModifiersCommand(service, messages,
                texts.getModifiers(), texts.getCommand(), null, null, null, null);
        FakeSender sender = FakeSender.denied();

        assertTrue(command.toggleAllPresets(sender, List.of("pack"), true));

        assertFalse(service.modifierEnabled("a"));
        assertEquals(List.of(messages.componentRaw(texts.getCommand().getNoPermission(), Map.of())), sender.received());
    }
}
