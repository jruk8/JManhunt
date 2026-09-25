package com.jruk8.jmanhunt.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

class PresetEditorMenusTest {

    private PresetEditorMenus editor;

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
        addModifier(config, "zebra", "Zulu");
        addModifier(config, "apple", "Apple");
        ModifierPreset preset = new ModifierPreset();
        preset.setName("Pair");
        preset.setDescription("Two up");
        preset.setItem("CHEST");
        preset.setModifiers(new ArrayList<>(List.of("zebra")));
        config.getPresets().put("pair", preset);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        ModifierStore store = new ModifierStore(config, log);
        editor = new PresetEditorMenus(store, messages, null, null, null, null);
    }

    private static void addModifier(ModifiersConfig config, String id, String name) {
        ModifierEntry entry = new ModifierEntry();
        ModifierMeta meta = new ModifierMeta();
        meta.setName(name);
        entry.setMeta(meta);
        config.getModifiers().put(id, entry);
    }

    @Test
    void editorLaysOutEveryField() {
        Menu menu = editor.editor("pair", null);

        assertEquals(27, menu.layout().size());
        assertEquals(title("Edit Preset"), menu.title());

        MenuButton name = menu.buttonAt(1);
        assertEquals(Material.NAME_TAG, name.material());
        assertEquals(plain("Current: Pair", NamedTextColor.GRAY), name.lore().get(0));

        MenuButton members = menu.buttonAt(13);
        assertEquals(Material.FILLED_MAP, members.material());
        assertEquals(plain("1 members", NamedTextColor.GRAY), members.lore().get(0));

        assertEquals(Material.LOOM, menu.buttonAt(10).material());
        assertEquals(Material.ANVIL, menu.buttonAt(12).material());
        MenuButton rename = menu.buttonAt(12);
        assertEquals(1, rename.lore().size());
        assertEquals(plain("Current id: pair", NamedTextColor.GRAY), rename.lore().get(0));
        assertEquals(Material.TNT, menu.buttonAt(14).material());
        assertEquals(Material.PAPER, menu.buttonAt(16).material());
    }

    @Test
    void membersMenuTogglesMembershipWithGlow() {
        Menu menu = editor.membersMenu("pair", null);

        assertEquals(title("Members"), menu.title());
        MenuButton apple = menu.buttonAt(2);
        MenuButton zebra = menu.buttonAt(3);
        assertEquals("Apple", textOf(apple.name()));
        assertEquals("Zulu", textOf(zebra.name()));
        assertFalse(apple.glow());
        assertTrue(zebra.glow());
        assertNotNull(apple.action());
        assertEquals(Material.PAPER, menu.buttonAt(18).material());
    }
}
