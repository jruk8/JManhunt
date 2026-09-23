package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierToggleAllTest {

    private static ConfigService service(ModifiersConfig config) {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return new ConfigService(null, new ModifierStore(config, log));
    }

    private static MessageService messages() {
        MessageService messages = new MessageService();
        messages.reload(new YamlConfiguration());
        return messages;
    }

    private static void addModifier(ModifiersConfig config, String id, boolean enabled) {
        ModifierEntry entry = new ModifierEntry();
        entry.setEnabled(enabled);
        config.getModifiers().put(id, entry);
    }

    @Test
    void toggleAllModifiersFlipsEveryModifierWithOneSummary() {
        ModifiersConfig config = new ModifiersConfig();
        addModifier(config, "a", false);
        addModifier(config, "b", true);
        ConfigService service = service(config);
        MessageService messages = messages();
        ModifiersCommand command = new ModifiersCommand(service, messages, null, null, null);
        FakeSender sender = FakeSender.permitted();

        assertTrue(command.toggleAllModifiers(sender, List.of("a", "b"), true));

        assertTrue(service.modifierEnabled("a"));
        assertTrue(service.modifierEnabled("b"));
        Component expected = messages.component("modifiers.toggle-all-success",
                Map.of("count", "2", "kind", "modifiers", "state", "on"));
        assertEquals(List.of(expected), sender.received());
    }

    @Test
    void toggleAllModifiersSkipsUnknownIdsQuietly() {
        ModifiersConfig config = new ModifiersConfig();
        addModifier(config, "a", false);
        ConfigService service = service(config);
        MessageService messages = messages();
        ModifiersCommand command = new ModifiersCommand(service, messages, null, null, null);
        FakeSender sender = FakeSender.permitted();

        assertTrue(command.toggleAllModifiers(sender, List.of("a", "nope"), true));

        assertTrue(service.modifierEnabled("a"));
        Component expected = messages.component("modifiers.toggle-all-success",
                Map.of("count", "1", "kind", "modifiers", "state", "on"));
        assertEquals(List.of(expected), sender.received());
    }

    @Test
    void toggleAllModifiersWithoutPermissionChangesNothing() {
        ModifiersConfig config = new ModifiersConfig();
        addModifier(config, "a", false);
        ConfigService service = service(config);
        MessageService messages = messages();
        ModifiersCommand command = new ModifiersCommand(service, messages, null, null, null);
        FakeSender sender = FakeSender.denied();

        assertTrue(command.toggleAllModifiers(sender, List.of("a"), true));

        assertFalse(service.modifierEnabled("a"));
        assertEquals(List.of(messages.component("command.no-permission")), sender.received());
    }

    @Test
    void toggleAllPresetsFlipsMembersWithOneSummary() {
        ModifiersConfig config = new ModifiersConfig();
        addModifier(config, "a", false);
        addModifier(config, "b", false);
        ModifierPreset preset = new ModifierPreset();
        preset.setModifiers(List.of("a", "b"));
        config.getPresets().put("pack", preset);
        ConfigService service = service(config);
        MessageService messages = messages();
        ModifiersCommand command = new ModifiersCommand(service, messages, null, null, null);
        FakeSender sender = FakeSender.permitted();

        assertTrue(command.toggleAllPresets(sender, List.of("pack", "nope"), true));

        assertTrue(service.modifierEnabled("a"));
        assertTrue(service.modifierEnabled("b"));
        Component expected = messages.component("modifiers.toggle-all-success",
                Map.of("count", "1", "kind", "presets", "state", "on"));
        assertEquals(List.of(expected), sender.received());
    }

    @Test
    void toggleAllPresetsWithoutPermissionChangesNothing() {
        ModifiersConfig config = new ModifiersConfig();
        addModifier(config, "a", false);
        ModifierPreset preset = new ModifierPreset();
        preset.setModifiers(List.of("a"));
        config.getPresets().put("pack", preset);
        ConfigService service = service(config);
        MessageService messages = messages();
        ModifiersCommand command = new ModifiersCommand(service, messages, null, null, null);
        FakeSender sender = FakeSender.denied();

        assertTrue(command.toggleAllPresets(sender, List.of("pack"), true));

        assertFalse(service.modifierEnabled("a"));
        assertEquals(List.of(messages.component("command.no-permission")), sender.received());
    }
}
