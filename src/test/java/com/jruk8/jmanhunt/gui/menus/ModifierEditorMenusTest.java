package com.jruk8.jmanhunt.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.match.ModifierTriggers;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierChance;
import com.jruk8.jmanhunt.modifiers.config.ModifierCommands;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierExecution;
import com.jruk8.jmanhunt.modifiers.config.ModifierInterval;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
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

class ModifierEditorMenusTest {

    private ModifierStore store;
    private ModifierEditorMenus editor;
    private ModifierDetailMenus detail;

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
        ModifierEntry entry = new ModifierEntry();
        entry.setEnabled(true);
        ModifierMeta meta = new ModifierMeta();
        meta.setName("Zulu");
        meta.setDescription("Stripes");
        meta.setItem("COOKED_BEEF");
        meta.setAuthor("JManhunt");
        entry.setMeta(meta);
        ModifierBehavior behavior = new ModifierBehavior();
        behavior.setRunsOn(new ArrayList<>(List.of("ON_START", "INTERVAL")));
        ModifierOptions options = new ModifierOptions();
        ModifierInterval interval = new ModifierInterval();
        interval.setInterval(30.0);
        interval.setDeviation(5.0);
        interval.setBehavior("PER_EXECUTOR");
        options.setIntervalSettings(interval);
        ModifierChance chance = new ModifierChance();
        chance.setChance(0.5);
        options.setSuccessChance(chance);
        ModifierExecution execution = new ModifierExecution();
        execution.setSelection("PICK_RANDOM");
        options.setExecution(execution);
        options.setDelay(100L);
        behavior.setOptions(options);
        ModifierCommands commands = new ModifierCommands();
        commands.getLists().put("player", new ArrayList<>(List.of("give <p> apple")));
        behavior.setCommands(commands);
        entry.setBehavior(behavior);
        config.getModifiers().put("zebra", entry);
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        store = new ModifierStore(config, log);
        editor = new ModifierEditorMenus(store, messages, null, null, null, null);
        detail = new ModifierDetailMenus(store, messages, null, null, null);
    }

    @Test
    void editorIsAQuadRoot() {
        Menu menu = editor.editor("zebra", null);

        assertEquals(27, menu.layout().size());
        assertEquals(title("Edit Modifier"), menu.title());

        MenuButton meta = menu.buttonAt(10);
        assertEquals(Material.NAME_TAG, meta.material());
        assertEquals("Meta", textOf(meta.name()));
        assertNotNull(meta.action());

        MenuButton behavior = menu.buttonAt(12);
        assertEquals(Material.SCULK_SENSOR, behavior.material());
        assertEquals("Behavior", textOf(behavior.name()));
        assertNotNull(behavior.action());

        MenuButton export = menu.buttonAt(14);
        assertEquals(Material.LOOM, export.material());
        assertNotNull(export.action());

        MenuButton delete = menu.buttonAt(16);
        assertEquals(Material.TNT, delete.material());
        assertNotNull(delete.action());

        assertEquals(Material.PAPER, menu.buttonAt(22).material());
        assertNull(menu.buttonAt(0));
        assertNull(menu.buttonAt(9));
    }

    @Test
    void legacyEditorKeepsEveryField() {
        Menu menu = editor.legacyEditor("zebra", null);

        assertEquals(45, menu.layout().size());

        MenuButton name = menu.buttonAt(1);
        assertEquals(Material.NAME_TAG, name.material());
        assertEquals(plain("Name", NamedTextColor.WHITE), name.name());
        assertEquals(plain("Current: Zulu", NamedTextColor.GRAY), name.lore().get(0));

        MenuButton interval = menu.buttonAt(18);
        assertEquals(plain("Current: 30.0s", NamedTextColor.GRAY), interval.lore().get(0));

        MenuButton chance = menu.buttonAt(24);
        assertEquals(plain("Current: 50%", NamedTextColor.GRAY), chance.lore().get(0));

        MenuButton delay = menu.buttonAt(34);
        assertEquals(plain("Current: 100 ticks", NamedTextColor.GRAY), delay.lore().get(0));

        MenuButton triggers = menu.buttonAt(13);
        assertEquals(plain("2 selected", NamedTextColor.GRAY), triggers.lore().get(0));

        MenuButton prestart = menu.buttonAt(16);
        assertEquals(plain("Current: Default (IN_ORDER)", NamedTextColor.GRAY),
                prestart.lore().get(0));

        assertEquals(Material.COMMAND_BLOCK, menu.buttonAt(36).material());
        assertEquals(Material.LOOM, menu.buttonAt(38).material());
        assertEquals(Material.ANVIL, menu.buttonAt(40).material());
        MenuButton rename = menu.buttonAt(40);
        assertEquals(1, rename.lore().size());
        assertEquals(plain("Current id: zebra", NamedTextColor.GRAY), rename.lore().get(0));
        assertEquals(Material.TNT, menu.buttonAt(42).material());
        assertEquals(Material.PAPER, menu.buttonAt(44).material());
        assertNull(menu.buttonAt(0));
    }

    @Test
    void editorToleratesUnknownIdsAsUnset() {
        Menu menu = editor.legacyEditor("ghost", null);

        assertEquals(plain("Current: Not set", NamedTextColor.GRAY),
                menu.buttonAt(20).lore().get(0));
        assertEquals(plain("Current: Default (100%)", NamedTextColor.GRAY),
                menu.buttonAt(24).lore().get(0));
        assertNotNull(menu.buttonAt(42).action());
    }

    @Test
    void triggersMenuTogglesEveryKnownTrigger() {
        Menu menu = detail.triggersMenu("zebra", null);

        assertEquals(27, menu.layout().size());
        assertEquals(title("Run On"), menu.title());
        assertEquals(ModifierTriggers.KNOWN.size(), 14);
        for (int index = 0; index < ModifierTriggers.KNOWN.size(); index++) {
            MenuButton button = menu.buttonAt(index);
            assertNotNull(button);
            assertEquals(textOf(button.name()), ModifierTriggers.KNOWN.get(index));
            assertNotNull(button.action());
        }
        assertTrue(menu.buttonAt(0).glow());
        assertTrue(menu.buttonAt(1).glow());
        assertFalse(menu.buttonAt(2).glow());
        assertEquals(Material.PAPER, menu.buttonAt(22).material());
    }

    @Test
    void commandsMenuShowsLiveCounts() {
        Menu menu = detail.commandsMenu("zebra", null);

        assertEquals(title("Command Lists"), menu.title());
        assertEquals(plain("0 lines", NamedTextColor.GRAY),
                menu.buttonAt(10).lore().get(0));
        assertEquals(plain("1 lines", NamedTextColor.GRAY),
                menu.buttonAt(11).lore().get(0));
        assertEquals(Material.COMMAND_BLOCK, menu.buttonAt(16).material());
        assertEquals(Material.PAPER, menu.buttonAt(22).material());
    }

    @Test
    void linesMenuShowsLinesWithEditAndDelete() {
        Menu menu = detail.linesMenu("zebra", "player", null);

        assertEquals(title("Commands: player"), menu.title());
        MenuButton line = menu.buttonAt(2);
        assertEquals(Material.PAPER, line.material());
        assertEquals("give <p> apple", textOf(line.name()));
        assertNotNull(line.action());
        assertNotNull(line.rightAction());
        assertEquals(Material.LIME_DYE, menu.buttonAt(36).material());
        assertEquals(Material.PAPER, menu.buttonAt(18).material());
    }
}
