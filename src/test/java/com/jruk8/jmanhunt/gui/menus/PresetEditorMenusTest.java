package com.jruk8.jmanhunt.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresetEditorMenusTest {

    private PresetEditorMenus editor;
    private ModifierStore store;
    private MessageService messages;

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
        addModifier(config, "zebra", "Zulu");
        addModifier(config, "apple", "Apple");
        ModifierPreset preset = new ModifierPreset();
        ModifierMeta meta = new ModifierMeta();
        meta.setName("Pair");
        meta.setDescription("Two up");
        meta.setItem("CHEST");
        preset.setMeta(meta);
        preset.setModifiers(new ArrayList<>(List.of("zebra")));
        config.getPresets().put("pair", preset);
        ModifierPreset blank = new ModifierPreset();
        ModifierMeta blankMeta = new ModifierMeta();
        blankMeta.setName("Blank");
        blank.setMeta(blankMeta);
        blank.setModifiers(new ArrayList<>());
        config.getPresets().put("blank", blank);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        messages = new MessageService();
        messages.reload(new MessagesConfig());
        store = new ModifierStore(config, log);
        editor = new PresetEditorMenus(store, messages, null, null, null, null);
    }

    private static void addModifier(ModifierFiles config, String id, String name) {
        ModifierEntry entry = new ModifierEntry();
        ModifierMeta meta = new ModifierMeta();
        meta.setName(name);
        entry.setMeta(meta);
        config.getModifiers().put(id, entry);
    }

    @Test
    void editorIsAQuadRoot() {
        Menu menu = editor.editor("pair", null);

        assertEquals(27, menu.layout().size());
        assertEquals(title("Edit Preset"), menu.title());

        MenuButton meta = menu.buttonAt(10);
        assertEquals(Material.NAME_TAG, meta.material());
        assertEquals("Meta", textOf(meta.name()));
        assertNotNull(meta.action());

        MenuButton modifiers = menu.buttonAt(12);
        assertEquals(Material.FILLED_MAP, modifiers.material());
        assertEquals("Modifiers", textOf(modifiers.name()));
        assertNotNull(modifiers.action());

        MenuButton export = menu.buttonAt(14);
        assertEquals(Material.LOOM, export.material());
        assertNotNull(export.action());

        MenuButton delete = menu.buttonAt(16);
        assertEquals(Material.TNT, delete.material());
        assertEquals("Delete Preset", textOf(delete.name()));
        assertNotNull(delete.action());

        assertEquals(Material.PAPER, menu.buttonAt(22).material());
    }

    @Test
    void membersMenuOrdersMembersFirstWithGlow() {
        Menu menu = editor.membersMenu("pair", null);

        assertEquals(title("Modifiers"), menu.title());
        MenuButton zebra = menu.buttonAt(2);
        assertEquals("Zulu", textOf(zebra.name()));
        assertTrue(zebra.glow());
        assertNull(menu.buttonAt(3));
        assertNull(menu.buttonAt(7));
        MenuButton apple = menu.buttonAt(11);
        assertEquals("Apple", textOf(apple.name()));
        assertFalse(apple.glow());
        assertNotNull(apple.action());
        assertEquals(Material.PAPER, menu.buttonAt(18).material());
    }

    @Test
    void membersMenuWithoutMembersStartsOnFirstRow() {
        Menu menu = editor.membersMenu("blank", null);

        MenuButton zebra = menu.buttonAt(2);
        MenuButton apple = menu.buttonAt(3);
        assertEquals("Zulu", textOf(zebra.name()));
        assertEquals("Apple", textOf(apple.name()));
        assertFalse(zebra.glow());
        assertFalse(apple.glow());
    }

    @Test
    void membersMenuBackParentReadsLiveCount() {
        GuiService gui = mock(GuiService.class);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);
        PresetEditorMenus live = new PresetEditorMenus(store, messages, null, gui, null, null);

        live.editor("pair", null).buttonAt(12).action().accept(player);

        ArgumentCaptor<Menu> opened = ArgumentCaptor.forClass(Menu.class);
        verify(gui).navigate(eq(player), opened.capture());
        store.memberRemove("pair", "zebra");
        Menu back = opened.getValue().parent().get();
        assertEquals("0 members", textOf(back.buttonAt(12).lore().get(0)));
        store.memberAdd("pair", "zebra");
        store.memberAdd("pair", "apple");
        Menu forward = opened.getValue().parent().get();
        assertEquals("2 members", textOf(forward.buttonAt(12).lore().get(0)));
    }
}
