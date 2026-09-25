package com.jruk8.jmanhunt.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModifierMenusTest {

    private ModifierStore store;
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
        ModifiersConfig config = new ModifiersConfig();
        addModifier(config, "zebra", true, "Zulu", "Stripes", "COOKED_BEEF", "JManhunt");
        addModifier(config, "mike", false, "<red>Mike</red>", "", null, null);
        addModifier(config, "apple", false, "&aApple", "Fruit\nCrisp", "BOGUS_ITEM", " ");
        addPreset(config, "pair", "Pair", "CHEST", "JManhunt", List.of("zebra", "apple"));
        addPreset(config, "solo", "Solo", null, null, List.of("zebra"));
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        store = new ModifierStore(config, log);
        menus = new ModifierMenus(store, messages, null, null, null, null, null, null);
    }

    private static void addModifier(ModifiersConfig config, String id, boolean enabled,
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

    private static void addPreset(ModifiersConfig config, String id, String name,
            String item, String author, List<String> members) {
        ModifierPreset preset = new ModifierPreset();
        ModifierMeta meta = new ModifierMeta();
        meta.setName(name);
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
        assertEquals("Create Modifier", textOf(modifierCreate.name()));
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
        assertEquals(List.of(plain("» Zulu", NamedTextColor.GREEN), Component.text(" "),
                plain("Enabled", NamedTextColor.GREEN), Component.text(" "),
                plain("Right-click to edit", NamedTextColor.GRAY)), solo.lore());
        assertNull(menu.buttonAt(3));
        assertNull(menu.buttonAt(7));

        MenuButton pair = menu.buttonAt(11);
        assertEquals(Material.CHEST, pair.material());
        assertEquals(8, pair.lore().size());
        assertEquals("» Zulu", textOf(pair.lore().get(0)));
        assertEquals("» Apple", textOf(pair.lore().get(1)));
        assertEquals(plain("Disabled", NamedTextColor.RED), pair.lore().get(3));
        assertEquals(plain("by JManhunt", NamedTextColor.GRAY), pair.lore().get(5));

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
    void presetLoreCollapsesBeyondEightMembers() {
        MessageService fresh = buildMenusWith(10, 0);

        MenuButton button = bigMenus.presetsMenu().buttonAt(2);

        assertEquals(plain("Big", NamedTextColor.WHITE), button.name());
        assertEquals(ModifierMenus.MAX_PRESET_LORE_LINES + 5, button.lore().size());
        assertEquals("» M0", textOf(button.lore().get(0)));
        assertEquals("» M7", textOf(button.lore().get(7)));
        assertEquals("..and 2 more", textOf(button.lore().get(8)));
        assertEquals(fresh.nonItalic(fresh.parse("<gray>..and <gray>2</gray> more")),
                button.lore().get(8));
        assertEquals(Component.text(" "), button.lore().get(9));
        assertEquals(plain("Enabled", NamedTextColor.GREEN), button.lore().get(10));
    }

    @Test
    void presetLoreOverflowUsesRedWrapperWhenDisabled() {
        MessageService fresh = buildMenusWith(10, 1);

        MenuButton button = bigMenus.presetsMenu().buttonAt(2);

        assertEquals("..and 2 more", textOf(button.lore().get(8)));
        assertEquals(fresh.nonItalic(fresh.parse("<gray><red>..and <gray>2</gray> more")),
                button.lore().get(8));
        assertEquals(plain("Disabled", NamedTextColor.RED), button.lore().get(10));
    }

    @Test
    void presetLoreAtLimitShowsNoOverflow() {
        buildMenusWith(8, 0);

        MenuButton button = bigMenus.presetsMenu().buttonAt(2);

        assertEquals(ModifierMenus.MAX_PRESET_LORE_LINES + 4, button.lore().size());
        for (Component line : button.lore()) {
            assertFalse(textOf(line).contains("..and"));
        }
    }

    @Test
    void emptyPresetLoreShowsNoOverflow() {
        buildMenusWith(0, 0);

        MenuButton button = bigMenus.presetsMenu().buttonAt(2);

        assertEquals(plain("Disabled", NamedTextColor.RED), button.lore().get(0));
        for (Component line : button.lore()) {
            assertFalse(textOf(line).contains("..and"));
        }
    }

    private MessageService buildMenusWith(int total, int disabled) {
        ModifiersConfig config = new ModifiersConfig();
        List<String> members = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            String id = "m" + index;
            members.add(id);
            addModifier(config, id, index >= disabled, "M" + index, "", null, null);
        }
        addPreset(config, "big", "Big", null, null, members);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        MessageService fresh = new MessageService();
        fresh.reload(new MessagesConfig());
        bigMenus = new ModifierMenus(new ModifierStore(config, log), fresh,
                null, null, null, null, null, null);
        return fresh;
    }
}
