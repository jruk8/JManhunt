package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.match.ModifierTestService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.modifiers.ModifierCodec;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import com.jruk8.jmanhunt.modifiers.files.ModsLoader;
import com.jruk8.jmanhunt.modifiers.files.ModsSeeder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        ModifierFiles config = ModifierFiles.inMemory();
        for (String name : List.of("zeta", "Alpha", "mike")) {
            config.getModifiers().put(name, new ModifierEntry());
        }
        for (String id : List.of("zulu", "apple")) {
            config.getPresets().put(id, new ModifierPreset());
        }
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        // Nulls are never touched: options read the store, messages unused.
        ModifiersCommand command = new ModifiersCommand(new ConfigService(null, new ModifierStore(config, log)),
                new ModifiersCommand.ModifiersDeps(null, null, null),
                new ModifiersCommand.ModifiersTexts(null, null, null, null));

        assertEquals(List.of("Alpha", "mike", "zeta"), command.modifierNameOptions());
        assertEquals(List.of("apple", "zulu"), command.presetIdOptions());
    }

    @TempDir
    private Path tempDir;

    @Test
    void importAgainstNestedTakenIdNumbersAtRoot() throws Exception {
        Path modsRoot = tempDir.resolve("mods");
        Path nested = modsRoot.resolve("modifiers/sub/dup.yml");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "enabled: false\nmeta:\n  name: Sub Dup\n",
                StandardCharsets.UTF_8);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ModifierStore store = new ModifierStore(ModifierFiles.fromLoad(modsRoot,
                new ModsLoader(ModsSeeder.none(), log).load(modsRoot)), log);
        ConfigService service = new ConfigService(null, store);
        MessageService messages = new MessageService();
        MessagesConfig texts = new MessagesConfig();
        messages.reload(texts);
        ModifiersCommand command = new ModifiersCommand(service, new ModifiersCommand.ModifiersDeps(null, null, null),
                new ModifiersCommand.ModifiersTexts(messages, texts.getModifiers(),
                        texts.getCommand(), null));
        FakeSender sender = FakeSender.permitted();
        byte[] nestedBefore = Files.readAllBytes(nested);

        ModifierEntry entry = new ModifierEntry();
        ModifierMeta meta = new ModifierMeta();
        meta.setName("Dup");
        entry.setMeta(meta);
        String payload = ModifierCodec.exportModifier("dup", entry);

        assertTrue(command.execute(sender, new String[]{"import", "modifier", payload}));

        assertEquals(messages.componentRaw(texts.getModifiers().getImported(), Map.of("name", "Dup 2")),
                sender.received().get(0));
        assertEquals(messages.componentRaw(texts.getModifiers().getImportDuplicate(),
                        Map.of("duplicate", "dup", "id", "dup-2")),
                sender.received().get(1));
        assertTrue(Files.isRegularFile(modsRoot.resolve("modifiers/dup-2.yml")));
        assertTrue(Arrays.equals(nestedBefore, Files.readAllBytes(nested)));
    }

    @Test
    void completionFollowsReloadSwap() {
        ModifierFiles config = ModifierFiles.inMemory();
        config.getModifiers().put("alpha", new ModifierEntry());
        config.getPresets().put("zed", new ModifierPreset());
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ModifierStore store = new ModifierStore(config, log);
        // Nulls are never touched: options read the store, messages unused.
        ModifiersCommand command = new ModifiersCommand(new ConfigService(null, store),
                new ModifiersCommand.ModifiersDeps(null, null, null),
                new ModifiersCommand.ModifiersTexts(null, null, null, null));
        assertEquals(List.of("alpha"), command.modifierNameOptions());
        assertEquals(List.of("zed"), command.presetIdOptions());

        ModLoadResult fresh = new ModLoadResult(
                new ModLoadResult.Loaded(
                        Map.of("beta", new ModLoadResult.LoadedModifier(
                                new ModifierEntry(), Path.of("beta"), "fp")),
                        Map.of("ay", new ModLoadResult.LoadedPreset(
                                new ModifierPreset(), Path.of("ay"), "fp"))),
                new ModLoadResult.Problems(List.of(), List.of(), List.of(), List.of()));
        store.replaceAll(fresh);

        assertEquals(List.of("beta"), command.modifierNameOptions());
        assertEquals(List.of("ay"), command.presetIdOptions());
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
        assertEquals(2, importer.received().size());
        assertEquals(fixture.messages().componentRaw(
                fixture.texts().getModifiers().getImported(), Map.of("name", "Beef 2")),
                importer.received().get(0));
        assertEquals(fixture.messages().componentRaw(fixture.texts().getModifiers().getImportDuplicate(),
                Map.of("duplicate", "beef", "id", "beef-2")), importer.received().get(1));
    }

    @Test
    void presetImportDuplicateIdWarnsWithNewId() {
        Fixture fixture = fixture();
        String payload = ModifierCodec.exportPreset("pack", preset("Pack"));
        FakeSender sender = FakeSender.permitted();

        assertTrue(fixture.command().execute(sender, new String[]{"import", "preset", payload}));
        assertEquals(1, sender.received().size());

        assertTrue(fixture.command().execute(sender, new String[]{"import", "preset", payload}));
        assertEquals(3, sender.received().size());
        assertEquals(fixture.messages().componentRaw(fixture.texts().getModifiers().getImportDuplicate(),
                Map.of("duplicate", "pack", "id", "pack-2")), sender.received().get(2));
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
        assertEquals(fixture.messages().componentRaw(
                fixture.texts().getModifiers().getImportFailed()), sender.received().get(0));
        assertEquals(fixture.messages().componentRaw(
                fixture.texts().getModifiers().getImportFailed()), sender.received().get(1));
        assertEquals(1, fixture.service().modifierNames().size());
    }

    @Test
    void togglesWithoutPermissionChangeNothing() {
        ModifierFiles config = ModifierFiles.inMemory();
        config.getModifiers().put("beef", new ModifierEntry());
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService service = new ConfigService(null, new ModifierStore(config, log));
        MessageService messages = new MessageService();
        MessagesConfig texts = new MessagesConfig();
        messages.reload(texts);
        ModifiersCommand command = new ModifiersCommand(service, new ModifiersCommand.ModifiersDeps(null, null, null),
                new ModifiersCommand.ModifiersTexts(messages, texts.getModifiers(),
                        texts.getCommand(), null));
        FakeSender sender = FakeSender.denied();
        boolean before = service.modifierEnabled("beef");

        assertTrue(command.execute(sender, new String[]{"setmod", "beef", "true"}));
        assertTrue(command.execute(sender, new String[]{"setpreset", "speed", "true"}));

        Component denied = messages.componentRaw(texts.getCommand().getNoPermission());
        assertEquals(List.of(denied, denied), sender.received());
        assertEquals(before, service.modifierEnabled("beef"));
    }

    private record Fixture(ModifiersCommand command, ConfigService service, MessageService messages,
            MessagesConfig texts) {
    }

    private static Fixture fixture() {
        ModifierFiles config = ModifierFiles.inMemory();
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
        MessagesConfig texts = new MessagesConfig();
        messages.reload(texts);
        return new Fixture(new ModifiersCommand(service, new ModifiersCommand.ModifiersDeps(null, null, null),
                new ModifiersCommand.ModifiersTexts(messages, texts.getModifiers(),
                        texts.getCommand(), null)),
                service, messages, texts);
    }

    private static ModifierPreset preset(String name) {
        ModifierPreset preset = new ModifierPreset();
        ModifierMeta meta = new ModifierMeta();
        meta.setName(name);
        meta.setItem("CHEST");
        preset.setMeta(meta);
        return preset;
    }

    @Test
    void parseTestRoleAcceptsBothRoles() {
        assertEquals("HUNTER", ModifiersCommand.parseTestRole("hunter"));
        assertEquals("HUNTER", ModifiersCommand.parseTestRole("  HUNTER  "));
        assertEquals("SPEEDRUNNER", ModifiersCommand.parseTestRole("Speedrunner"));
    }

    @Test
    void parseTestRoleRejectsAnythingElse() {
        assertNull(ModifiersCommand.parseTestRole("referee"));
        assertNull(ModifiersCommand.parseTestRole(""));
        assertNull(ModifiersCommand.parseTestRole(null));
    }

    @Test
    void testFromConsoleNeedsAPlayer() {
        Fixture fixture = fixture();
        FakeSender sender = FakeSender.permitted();

        assertTrue(fixture.command().execute(sender, new String[]{"test", "hunter", "say hi"}));

        assertEquals(List.of(fixture.messages().componentRaw(fixture.texts().getCommand().getPlayerOnly())),
                sender.received());
    }

    @Test
    void testWithBadRoleShowsUsage() {
        Fixture fixture = fixture();
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        assertTrue(fixture.command().execute(player, new String[]{"test", "referee", "say hi"}));

        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(sent.capture());
        assertEquals(fixture.messages().componentRaw(fixture.texts().getModifiers().getTestUsage()), sent.getValue());
    }

    @Test
    void testRunsThePipelineAndReports() {
        Fixture fixture = fixture();
        ModifierTestService service = mock(ModifierTestService.class);
        ModifierTestService.TestResult result = new ModifierTestService.TestResult(12L, List.of(),
                List.of(), List.of(), null, null);
        when(service.run(any(), eq("HUNTER"), eq(List.of("say hi")))).thenReturn(result);
        ModifiersCommand command = new ModifiersCommand(fixture.service(),
                new ModifiersCommand.ModifiersDeps(null, null, service),
                new ModifiersCommand.ModifiersTexts(fixture.messages(),
                        fixture.texts().getModifiers(), fixture.texts().getCommand(),
                        null));
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);
        when(player.getName()).thenReturn("Steve");

        assertTrue(command.execute(player,
                new String[]{"test", "hunter", "say", "hi"}));

        verify(service).run(player, "HUNTER", List.of("say hi"));
        verify(service).report(player, result);
    }
}
