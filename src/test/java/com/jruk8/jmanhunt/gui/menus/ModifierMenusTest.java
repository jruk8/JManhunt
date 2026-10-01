package com.jruk8.jmanhunt.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.dialog.ModifierDialog;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModifierMenusTest {

    private ModifierStore store;
    private MessageService messages;
    private MessagesConfig texts;
    private ModifierMenus menus;
    private ModifierMenus bigMenus;

    private static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component title(String text) {
        return Component.text(text).decoration(TextDecoration.ITALIC, false);
    }

    private static String textOf(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @BeforeEach
    void setup() throws Exception {
        ModifierFiles config = ModifierFiles.inMemory();
        addModifier(config, "zebra", true, "Zulu", "Stripes", "COOKED_BEEF", "JManhunt");
        addModifier(config, "mike", false, "<red>Mike</red>", "", null, null);
        addModifier(config, "apple", false, "&aApple", "Fruit\nCrisp", "BOGUS_ITEM", " ");
        addPreset(config, "pair", "Pair", null, "CHEST", "JManhunt", List.of("zebra", "apple"));
        addPreset(config, "solo", "Solo", null, null, null, List.of("zebra"));
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        messages = new MessageService();
        texts = new MessagesConfig();
        messages.reload(texts);
        store = new ModifierStore(config, log);
        menus = new ModifierMenus(store, messages, texts.getModifiersGui(),
                texts.getManhuntGui(), texts.getModifiers(), texts.getCommand(), null, null,
                null, null, null, null, null, null, null);
    }

    private static void addModifier(ModifierFiles config, String id, boolean enabled,
            String name, String description, String item, String author) {
        ModifierEntry entry = new ModifierEntry();
        entry.setEnabled(enabled);
        ModifierMeta meta = new ModifierMeta();
        meta.setName(name);
        meta.setDescription(description);
        meta.setItem(item);
        meta.setAuthor(author);
        entry.setMeta(meta);
        config.getModifiers().put(id, entry);
    }

    private static void addPreset(ModifierFiles config, String id, String name,
            String description, String item, String author, List<String> members) {
        ModifierPreset preset = new ModifierPreset();
        ModifierMeta meta = new ModifierMeta();
        meta.setName(name);
        meta.setDescription(description);
        meta.setItem(item);
        meta.setAuthor(author);
        preset.setMeta(meta);
        preset.setModifiers(members);
        config.getPresets().put(id, preset);
    }

    @Test
    void mainMenuLinksBothListsWithLiveCounts() {
        Menu main = menus.mainMenu();

        assertEquals(27, main.layout().size());
        assertEquals(title("Modifiers"), main.title());
        assertNull(main.parent());

        MenuButton modifiers = main.buttonAt(12);
        assertEquals(Material.DIAMOND, modifiers.material());
        assertEquals(plain("Modifiers", NamedTextColor.WHITE), modifiers.name());
        assertEquals(List.of(plain("1/3 enabled", NamedTextColor.GRAY)), modifiers.lore());
        assertNotNull(modifiers.action());

        MenuButton presets = main.buttonAt(14);
        assertEquals(Material.FILLED_MAP, presets.material());
        assertEquals(plain("Presets", NamedTextColor.WHITE), presets.name());
        assertEquals(List.of(plain("1/2 enabled", NamedTextColor.GRAY)), presets.lore());
        assertNotNull(presets.action());

        assertNull(main.buttonAt(0));
    }

    @Test
    void listMenusCarryImportLooms() {
        Menu modifiers = menus.modifiersMenu();
        Menu presets = menus.presetsMenu();
        MenuButton modifierImport = modifiers.buttonAt(44);
        MenuButton presetImport = presets.buttonAt(44);

        assertEquals(Material.LOOM, modifierImport.material());
        assertEquals("Import Modifier", textOf(modifierImport.name()));
        assertNotNull(modifierImport.action());
        assertEquals(Material.LOOM, presetImport.material());
        assertEquals("Import Preset", textOf(presetImport.name()));
        assertNotNull(presetImport.action());
        assertNull(modifiers.buttonAt(36));
        assertNull(presets.buttonAt(36));
    }

    @Test
    void listMenusCarryCreateButtonsAndEditActions() {
        MenuButton modifierCreate = menus.modifiersMenu().buttonAt(8);
        MenuButton presetCreate = menus.presetsMenu().buttonAt(8);

        assertEquals(Material.WRITABLE_BOOK, modifierCreate.material());
        assertEquals("Modifier Editor", textOf(modifierCreate.name()));
        assertNotNull(modifierCreate.action());
        assertEquals("Create Preset", textOf(presetCreate.name()));
        assertNotNull(presetCreate.action());

        assertNotNull(menus.modifiersMenu().buttonAt(2).rightAction());
        assertNotNull(menus.presetsMenu().buttonAt(2).rightAction());
    }

    @Test
    void modifiersMenuGroupsByFileOrderOnFreshRows() {
        Menu menu = menus.modifiersMenu();

        assertEquals(45, menu.layout().size());
        assertEquals(title("Modifiers"), menu.title());
        assertEquals(Material.ARROW, menu.buttonAt(9).material());
        assertEquals(plain("Scroll up", NamedTextColor.WHITE), menu.buttonAt(9).name());
        assertEquals(Material.PAPER, menu.buttonAt(18).material());
        assertEquals(plain("Back", NamedTextColor.WHITE), menu.buttonAt(18).name());
        assertEquals(Material.ARROW, menu.buttonAt(27).material());
        assertEquals(plain("Scroll down", NamedTextColor.WHITE), menu.buttonAt(27).name());

        MenuButton zebra = menu.buttonAt(2);
        assertEquals(Material.COOKED_BEEF, zebra.material());
        assertEquals(plain("Zulu", NamedTextColor.WHITE), zebra.name());
        assertTrue(zebra.glow());
        assertEquals(List.of(plain("Stripes", NamedTextColor.GRAY), Component.text(" "),
                plain("Enabled", NamedTextColor.GREEN), Component.text(" "),
                plain("by JManhunt", NamedTextColor.GRAY), Component.text(" "),
                plain("Right-click to edit", NamedTextColor.GRAY)), zebra.lore());
        assertNotNull(zebra.action());
        assertNull(menu.buttonAt(3));
        assertNull(menu.buttonAt(4));
        assertNull(menu.buttonAt(5));
        assertNull(menu.buttonAt(6));
        assertNull(menu.buttonAt(7));

        MenuButton mike = menu.buttonAt(11);
        assertEquals(Material.STONE, mike.material());
        assertEquals(plain("Mike", NamedTextColor.RED), mike.name());
        assertEquals(plain("Enable for a twist!", NamedTextColor.GRAY), mike.lore().get(0));

        MenuButton apple = menu.buttonAt(12);
        assertEquals(Material.STONE, apple.material());
        assertEquals(plain("Apple", NamedTextColor.GREEN), apple.name());
        assertFalse(apple.glow());
        assertEquals(List.of(plain("Fruit", NamedTextColor.GRAY),
                plain("Crisp", NamedTextColor.GRAY), Component.text(" "),
                plain("Disabled", NamedTextColor.RED), Component.text(" "),
                plain("Right-click to edit", NamedTextColor.GRAY)), apple.lore());

        assertEquals("Modifiers", textOf(menu.parent().get().title()));
        assertNotSame(menu, menu.parent().get());
    }

    @Test
    void presetsMenuShowsMembersWithStatus() {
        Menu menu = menus.presetsMenu();

        assertEquals(45, menu.layout().size());
        assertEquals(title("Presets"), menu.title());
        assertEquals(Material.PAPER, menu.buttonAt(18).material());

        MenuButton solo = menu.buttonAt(2);
        assertEquals(Material.STONE, solo.material());
        assertEquals(plain("Solo", NamedTextColor.WHITE), solo.name());
        assertTrue(solo.glow());
        assertEquals(List.of(plain("Enable for a twist!", NamedTextColor.GRAY), Component.text(" "),
                plain("» Zulu", NamedTextColor.GREEN), Component.text(" "),
                plain("Enabled", NamedTextColor.GREEN), Component.text(" "),
                plain("Right-click to edit", NamedTextColor.GRAY)), solo.lore());
        assertNull(menu.buttonAt(3));
        assertNull(menu.buttonAt(7));

        MenuButton pair = menu.buttonAt(11);
        assertEquals(Material.CHEST, pair.material());
        assertEquals(10, pair.lore().size());
        assertEquals("Enable for a twist!", textOf(pair.lore().get(0)));
        assertEquals("» Zulu", textOf(pair.lore().get(2)));
        assertEquals("» Apple", textOf(pair.lore().get(3)));
        assertEquals(plain("Disabled", NamedTextColor.RED), pair.lore().get(5));
        assertEquals(plain("by JManhunt", NamedTextColor.GRAY), pair.lore().get(7));

        assertEquals("Modifiers", textOf(menu.parent().get().title()));
    }

    @Test
    void toggleAllShowsTotalAndGlowsWhenAllOn() {
        Menu modifiers = menus.modifiersMenu();
        MenuButton modifiersToggle = modifiers.buttonAt(26);

        assertEquals(Material.STRUCTURE_VOID, modifiersToggle.material());
        assertEquals(plain("Toggle all", NamedTextColor.WHITE), modifiersToggle.name());
        assertEquals(List.of(plain("3 modifiers", NamedTextColor.GRAY)), modifiersToggle.lore());
        assertFalse(modifiersToggle.glow());
        assertNotNull(modifiersToggle.action());

        Menu presets = menus.presetsMenu();
        MenuButton presetsToggle = presets.buttonAt(26);

        assertEquals(Material.STRUCTURE_VOID, presetsToggle.material());
        assertEquals(List.of(plain("2 presets", NamedTextColor.GRAY)), presetsToggle.lore());
        assertFalse(presetsToggle.glow());

        store.setEnabled("mike", true);
        store.setEnabled("apple", true);
        MenuButton allOn = menus.modifiersMenu().buttonAt(26);

        assertTrue(allOn.glow());
        assertEquals(List.of(plain("3 modifiers", NamedTextColor.GRAY)), allOn.lore());
    }

    @Test
    void toggleAllOnAsksForConfirmationFirst() {
        GuiService gui = mock(GuiService.class);
        SoundService sounds = mock(SoundService.class);
        ConfigService service = new ConfigService(null, store);
        ModifiersCommand toggles = new ModifiersCommand(service, messages, texts.getModifiers(), texts.getCommand(),
                gui, null, sounds, null);
        ModifierMenus live = new ModifierMenus(store, messages, texts.getModifiersGui(),
                texts.getManhuntGui(), texts.getModifiers(), texts.getCommand(), sounds, gui,
                toggles, null, null, null, null, null, null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        Menu list = live.modifiersMenu();
        list.buttonAt(26).action().accept(player);

        assertFalse(service.modifierEnabled("mike"));
        ArgumentCaptor<Menu> shown = ArgumentCaptor.forClass(Menu.class);
        verify(gui).navigate(eq(player), shown.capture());
        Menu confirm = shown.getValue();
        assertEquals(title("Turn all modifiers on?"), confirm.title());

        confirm.buttonAt(ConfirmMenu.CONFIRM_SLOT).action().accept(player);

        assertTrue(service.modifierEnabled("mike"));
        assertTrue(service.modifierEnabled("apple"));
        verify(sounds).playNeutralSound(player);
    }

    @Test
    void toggleAllOffAppliesImmediatelyWithoutConfirm() {
        GuiService gui = mock(GuiService.class);
        SoundService sounds = mock(SoundService.class);
        ConfigService service = new ConfigService(null, store);
        ModifiersCommand toggles = new ModifiersCommand(service, messages, texts.getModifiers(), texts.getCommand(),
                gui, null, sounds, null);
        ModifierMenus live = new ModifierMenus(store, messages, texts.getModifiersGui(),
                texts.getManhuntGui(), texts.getModifiers(), texts.getCommand(), sounds, gui,
                toggles, null, null, null, null, null, null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);
        store.setEnabled("mike", true);
        store.setEnabled("apple", true);

        live.modifiersMenu().buttonAt(26).action().accept(player);

        assertFalse(service.modifierEnabled("zebra"));
        assertFalse(service.modifierEnabled("mike"));
        assertFalse(service.modifierEnabled("apple"));
        verify(gui, never()).navigate(any(), any());
        verify(sounds).playNeutralSound(player);
    }

    @Test
    void toggleAllPresetsOnConfirmsAndCancelKeepsState() {
        GuiService gui = mock(GuiService.class);
        SoundService sounds = mock(SoundService.class);
        ConfigService service = new ConfigService(null, store);
        ModifiersCommand toggles = new ModifiersCommand(service, messages, texts.getModifiers(), texts.getCommand(),
                gui, null, sounds, null);
        ModifierMenus live = new ModifierMenus(store, messages, texts.getModifiersGui(),
                texts.getManhuntGui(), texts.getModifiers(), texts.getCommand(), sounds, gui,
                toggles, null, null, null, null, null, null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        Menu list = live.presetsMenu();
        list.buttonAt(26).action().accept(player);

        ArgumentCaptor<Menu> shown = ArgumentCaptor.forClass(Menu.class);
        verify(gui).navigate(eq(player), shown.capture());
        Menu confirm = shown.getValue();
        assertEquals(title("Turn all presets on?"), confirm.title());

        confirm.buttonAt(ConfirmMenu.CANCEL_SLOT).action().accept(player);

        assertFalse(service.modifierEnabled("apple"));
        verify(sounds, never()).playNeutralSound(player);
    }

    @Test
    void presetLoreCollapsesBeyondEightMembers() {
        MessageService fresh = buildMenusWith(10, 0);

        MenuButton button = bigMenus.presetsMenu().buttonAt(2);

        assertEquals(plain("Big", NamedTextColor.WHITE), button.name());
        assertEquals(ModifierMenus.MAX_PRESET_LORE_LINES + 7, button.lore().size());
        assertEquals(plain("Two up", NamedTextColor.GRAY), button.lore().get(0));
        assertEquals("» M0", textOf(button.lore().get(2)));
        assertEquals("» M7", textOf(button.lore().get(9)));
        assertEquals("..and 2 more", textOf(button.lore().get(10)));
        assertEquals(fresh.nonItalic(fresh.parse("<gray>..and <gray>2</gray> more")),
                button.lore().get(10));
        assertEquals(Component.text(" "), button.lore().get(11));
        assertEquals(plain("Enabled", NamedTextColor.GREEN), button.lore().get(12));
    }

    @Test
    void presetLoreOverflowUsesRedWrapperWhenDisabled() {
        MessageService fresh = buildMenusWith(10, 1);

        MenuButton button = bigMenus.presetsMenu().buttonAt(2);

        assertEquals("..and 2 more", textOf(button.lore().get(10)));
        assertEquals(fresh.nonItalic(fresh.parse("<gray><red>..and <gray>2</gray> more")),
                button.lore().get(10));
        assertEquals(plain("Disabled", NamedTextColor.RED), button.lore().get(12));
    }

    @Test
    void presetLoreAtLimitShowsNoOverflow() {
        buildMenusWith(8, 0);

        MenuButton button = bigMenus.presetsMenu().buttonAt(2);

        assertEquals(ModifierMenus.MAX_PRESET_LORE_LINES + 6, button.lore().size());
        for (Component line : button.lore()) {
            assertFalse(textOf(line).contains("..and"));
        }
    }

    @Test
    void emptyPresetLoreShowsNoOverflow() {
        buildMenusWith(0, 0);

        MenuButton button = bigMenus.presetsMenu().buttonAt(2);

        assertEquals(plain("Big", NamedTextColor.WHITE), button.name());
        assertEquals(List.of(plain("No modifiers configured!", NamedTextColor.RED),
                Component.text(" "),
                plain("Disabled", NamedTextColor.RED), Component.text(" "),
                plain("Right-click to edit", NamedTextColor.GRAY)), button.lore());
        for (Component line : button.lore()) {
            assertFalse(textOf(line).contains("..and"));
        }
    }

    @Test
    void toggleEmptyPresetRefusesWithAngrySoundAndNoStateChange() {
        ModifierFiles config = ModifierFiles.inMemory();
        addPreset(config, "big", "Big", null, null, null, List.of());
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ModifierStore emptyStore = new ModifierStore(config, log);
        SoundService sounds = mock(SoundService.class);
        ModifiersCommand toggles = mock(ModifiersCommand.class);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);
        ModifierMenus live = new ModifierMenus(emptyStore, messages, texts.getModifiersGui(),
                texts.getManhuntGui(), texts.getModifiers(), texts.getCommand(), sounds, null,
                toggles, null, null, null, null, null, null);

        live.presetsMenu().buttonAt(2).action().accept(player);

        verify(sounds).playAngrySound(player);
        verify(sounds, never()).playNeutralSound(player);
        verify(toggles, never()).execute(any(), any());
        ArgumentCaptor<Component> chat = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(chat.capture());
        assertEquals("[JManhunt] Preset Big has no modifiers set to it.",
                textOf(chat.getValue()));
        assertTrue(emptyStore.presetMembers("big").isEmpty());
    }

    @Test
    void overrideSessionReadsEffectiveAndGlowsOverrides() {
        GuiService gui = mock(GuiService.class);
        Player viewer = mock(Player.class);
        when(gui.overrideLobby(viewer)).thenReturn(0);
        ConfigService config =
                new ConfigService(new JManhuntConfig(), store);
        OverrideService overrides = new OverrideService(config, new LobbyConfig(), () -> {});
        assertTrue(overrides.setModifierOverride(0, "mike", true));
        ModifierMenus session = new ModifierMenus(store, messages, texts.getModifiersGui(),
                texts.getManhuntGui(), texts.getModifiers(), texts.getCommand(), null, gui,
                null, null, null, null, overrides, null, null);

        Menu menu = session.modifiersMenu(viewer, null);
        MenuButton mike = findButton(menu, "Mike");
        MenuButton zebra = findButton(menu, "Zulu");

        assertNotNull(mike);
        assertTrue(mike.glow());
        assertNotNull(mike.shiftAction());
        assertNotNull(zebra);
        assertFalse(zebra.glow());
        assertNotNull(zebra.shiftAction());
    }

    private static MenuButton findButton(Menu menu, String name) {
        for (int slot = 0; slot < menu.layout().size(); slot++) {
            MenuButton button = menu.buttonAt(slot);
            if (button != null && button.name() != null
                    && textOf(button.name()).equals(name)) {
                return button;
            }
        }
        return null;
    }

    @Test
    void editorTwinPairsTestCommandWithCreate() {
        Menu twin = menus.editorTwin(null, null);

        assertEquals(title("Modifier Editor"), twin.title());
        MenuButton test = twin.buttonAt(12);
        assertEquals(Material.REPEATING_COMMAND_BLOCK, test.material());
        assertEquals("Test a Command", textOf(test.name()));
        assertNotNull(test.action());
        MenuButton create = twin.buttonAt(14);
        assertEquals(Material.WRITABLE_BOOK, create.material());
        assertEquals("Create Modifier", textOf(create.name()));
        assertNotNull(create.action());
        assertEquals(Material.PAPER, twin.buttonAt(13).material());
    }

    @Test
    void testCommandDeniesWithoutPermission() {
        ModifierDialog dialogs = mock(ModifierDialog.class);
        ModifierMenus live = new ModifierMenus(store, messages, texts.getModifiersGui(),
                texts.getManhuntGui(), texts.getModifiers(), texts.getCommand(),
                mock(SoundService.class), mock(GuiService.class), mock(ModifiersCommand.class),
                null, dialogs, null, null, null, mock(ModifierEditorMemory.class));
        Player viewer = mock(Player.class);
        when(viewer.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(false);

        live.editorTwin(viewer, null).buttonAt(12).action().accept(viewer);

        verify(viewer).sendMessage(messages.componentRaw(texts.getCommand().getNoPermission()));
        verify(dialogs, never()).openTestCommands(any(), any(), any(), any());
    }

    @Test
    void testCommandSubmitRunsPipelineAndReturnsToTwin() {
        ModifierDialog dialogs = mock(ModifierDialog.class);
        ModifierEditorMemory memory = mock(ModifierEditorMemory.class);
        ModifiersCommand toggles = mock(ModifiersCommand.class);
        GuiService gui = mock(GuiService.class);
        ModifierDialog.TestSubmission initial = new ModifierDialog.TestSubmission(false,
                "SPEEDRUNNER", List.of("", "", "", "", ""));
        when(memory.initialFor(any())).thenReturn(initial);
        ModifierMenus live = new ModifierMenus(store, messages, texts.getModifiersGui(),
                texts.getManhuntGui(), texts.getModifiers(), texts.getCommand(),
                mock(SoundService.class), gui, toggles, null, dialogs, null, null, null,
                memory);
        Player viewer = mock(Player.class);
        when(viewer.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);
        Menu twin = live.editorTwin(viewer, null);

        twin.buttonAt(12).action().accept(viewer);

        ArgumentCaptor<Function<ModifierDialog.TestSubmission, Runnable>> submit =
                ArgumentCaptor.forClass(Function.class);
        verify(dialogs).openTestCommands(eq(viewer), eq(initial), submit.capture(), any());
        ModifierDialog.TestSubmission answers = new ModifierDialog.TestSubmission(true, "HUNTER",
                List.of("say one", "", "say two", "", ""));
        submit.getValue().apply(answers).run();

        verify(memory).store(viewer, answers);
        verify(toggles).testCommands(viewer, "HUNTER", List.of("say one", "say two"));
        verify(gui).navigate(eq(viewer), eq(twin));
    }

    @Test
    void testCommandEmptySubmitErrorsAndReopens() {
        ModifierDialog dialogs = mock(ModifierDialog.class);
        ModifierEditorMemory memory = mock(ModifierEditorMemory.class);
        ModifiersCommand toggles = mock(ModifiersCommand.class);
        GuiService gui = mock(GuiService.class);
        SoundService sounds = mock(SoundService.class);
        ModifierDialog.TestSubmission initial = new ModifierDialog.TestSubmission(false,
                "SPEEDRUNNER", List.of("", "", "", "", ""));
        when(memory.initialFor(any())).thenReturn(initial);
        ModifierMenus live = new ModifierMenus(store, messages, texts.getModifiersGui(),
                texts.getManhuntGui(), texts.getModifiers(), texts.getCommand(), sounds, gui,
                toggles, null, dialogs, null, null, null, memory);
        Player viewer = mock(Player.class);
        when(viewer.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);
        Menu twin = live.editorTwin(viewer, null);

        twin.buttonAt(12).action().accept(viewer);

        ArgumentCaptor<Function<ModifierDialog.TestSubmission, Runnable>> submit =
                ArgumentCaptor.forClass(Function.class);
        verify(dialogs).openTestCommands(eq(viewer), eq(initial), submit.capture(), any());
        ModifierDialog.TestSubmission answers = new ModifierDialog.TestSubmission(false,
                "SPEEDRUNNER", List.of("", "  ", "", "", ""));
        submit.getValue().apply(answers).run();

        verify(viewer).sendMessage(messages.componentRaw(texts.getModifiers().getTestCommandsEmpty()));
        verify(sounds).playAngrySound(viewer);
        verify(memory, never()).store(any(), any());
        verify(toggles, never()).testCommands(any(), any(), any());
        verify(dialogs, times(1)).openTestCommands(eq(viewer), eq(initial), any(), any());
        verify(dialogs, times(1)).openTestCommands(eq(viewer), eq(answers), any(), any());
        verify(gui, never()).navigate(any(), any());
    }

    private MessageService buildMenusWith(int total, int disabled) {
        ModifierFiles config = ModifierFiles.inMemory();
        List<String> members = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            String id = "m" + index;
            members.add(id);
            addModifier(config, id, index >= disabled, "M" + index, "", null, null);
        }
        addPreset(config, "big", "Big", "Two up", null, null, members);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        MessageService fresh = new MessageService();
        fresh.reload(new MessagesConfig());
        MessagesConfig freshTexts = new MessagesConfig();
        bigMenus = new ModifierMenus(new ModifierStore(config, log), fresh,
                freshTexts.getModifiersGui(), freshTexts.getManhuntGui(),
                freshTexts.getModifiers(), freshTexts.getCommand(), null, null, null, null,
                null, null, null, null, null);
        return fresh;
    }
}
