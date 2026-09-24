package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.modifiers.ModifierCodec;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifiersCommandTest {

    @Test
    void parseStateAcceptsStrictBooleans() {
        assertTrue(ModifiersCommand.parseState("true"));
        assertTrue(ModifiersCommand.parseState("  TRUE  "));
        assertFalse(ModifiersCommand.parseState("false"));
        assertFalse(ModifiersCommand.parseState("False"));
    }

    @Test
    void parseStateRejectsAnythingElse() {
        assertNull(ModifiersCommand.parseState("on"));
        assertNull(ModifiersCommand.parseState("off"));
        assertNull(ModifiersCommand.parseState("1"));
        assertNull(ModifiersCommand.parseState("yes"));
        assertNull(ModifiersCommand.parseState(""));
        assertNull(ModifiersCommand.parseState(null));
    }

    @Test
    void optionsSortCaseInsensitively() {
        ModifiersConfig config = new ModifiersConfig();
        for (String name : List.of("zeta", "Alpha", "mike")) {
            config.getModifiers().put(name, new ModifierEntry());
        }
        for (String id : List.of("zulu", "apple")) {
            config.getPresets().put(id, new ModifierPreset());
        }
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        // Nulls are never touched: options read the store, messages unused.
        ModifiersCommand command = new ModifiersCommand(
                new ConfigService(null, new ModifierStore(config, log)), null, null, null, null);

        assertEquals(List.of("Alpha", "mike", "zeta"), command.modifierNameOptions());
        assertEquals(List.of("apple", "zulu"), command.presetIdOptions());
    }

    @Test
    void parseEntryTypeAcceptsBothKinds() {
        assertEquals("modifier", ModifiersCommand.parseEntryType("modifier"));
        assertEquals("preset", ModifiersCommand.parseEntryType("  Preset "));
        assertNull(ModifiersCommand.parseEntryType("mod"));
        assertNull(ModifiersCommand.parseEntryType(null));
    }

    @Test
    void exportImportRoundTripBumpsCollidingIds() {
        Fixture fixture = fixture();
        FakeSender exporter = FakeSender.permitted();

        assertTrue(fixture.command().execute(exporter, new String[]{"export", "modifier", "beef"}));
        assertEquals(1, exporter.received().size());
        ClickEvent click = exporter.received().get(0).clickEvent();
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, click.action());
        String payload = click.value();
        assertTrue(payload.startsWith("JMH1"));

        FakeSender importer = FakeSender.permitted();
        assertTrue(fixture.command().execute(importer, new String[]{"import", "modifier", payload}));

        assertTrue(fixture.service().modifierNames().contains("beef-2"));
        assertEquals("Beef 2", fixture.service().modifiers().metaName("beef-2"));
    }

    @Test
    void importRejectsGarbageAndWrongKinds() {
        Fixture fixture = fixture();
        FakeSender sender = FakeSender.permitted();
        String presetPayload = ModifierCodec.exportPreset(
                "pack", preset("Pack"));

        assertTrue(fixture.command().execute(sender, new String[]{"import", "modifier", "garbage"}));
        assertTrue(fixture.command()
                .execute(sender, new String[]{"import", "modifier", presetPayload}));
        assertTrue(fixture.command().execute(sender, new String[]{"export", "modifier", "nope"}));

        assertEquals(3, sender.received().size());
        assertEquals(fixture.messages().component("modifiers.import-failed"), sender.received().get(0));
        assertEquals(fixture.messages().component("modifiers.import-failed"), sender.received().get(1));
        assertEquals(1, fixture.service().modifierNames().size());
    }

    @Test
    void togglesWithoutPermissionChangeNothing() {
        ModifiersConfig config = new ModifiersConfig();
        config.getModifiers().put("beef", new ModifierEntry());
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService service = new ConfigService(null, new ModifierStore(config, log));
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        ModifiersCommand command = new ModifiersCommand(service, messages, null, null, null);
        FakeSender sender = FakeSender.denied();
        boolean before = service.modifierEnabled("beef");

        assertTrue(command.execute(sender, new String[]{"setmod", "beef", "true"}));
        assertTrue(command.execute(sender, new String[]{"setpreset", "speed", "true"}));

        Component denied = messages.component("command.no-permission");
        assertEquals(List.of(denied, denied), sender.received());
        assertEquals(before, service.modifierEnabled("beef"));
    }

    private record Fixture(ModifiersCommand command, ConfigService service, MessageService messages) {
    }

    private static Fixture fixture() {
        ModifiersConfig config = new ModifiersConfig();
        ModifierEntry entry = new ModifierEntry();
        ModifierMeta meta =
                new ModifierMeta();
        meta.setName("Beef");
        meta.setItem("COOKED_BEEF");
        entry.setMeta(meta);
        config.getModifiers().put("beef", entry);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService service = new ConfigService(null, new ModifierStore(config, log));
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        return new Fixture(new ModifiersCommand(service, messages, null, null, null),
                service, messages);
    }

    private static ModifierPreset preset(String name) {
        ModifierPreset preset = new ModifierPreset();
        preset.setName(name);
        preset.setItem("CHEST");
        return preset;
    }
}
