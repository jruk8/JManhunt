package com.jruk8.jmanhunt.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
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
        messages = new MessageService();
        messages.reload(new MessagesConfig());
        store = new ModifierStore(config, log);
        editor = new ModifierEditorMenus(store, messages, null, null, null, null, null, null);
        detail = new ModifierDetailMenus(store, messages, null, null, null, null);
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
    void behaviorMenuIsATwin() {
        Menu menu = editor.behaviorMenu("zebra", null);

        assertEquals(27, menu.layout().size());
        assertEquals(title("Behavior"), menu.title());

        MenuButton options = menu.buttonAt(12);
        assertEquals(Material.TRIPWIRE_HOOK, options.material());
        assertNotNull(options.action());

        assertEquals(Material.PAPER, menu.buttonAt(13).material());

        MenuButton commands = menu.buttonAt(14);
        assertEquals(Material.CHAIN_COMMAND_BLOCK, commands.material());
        assertNotNull(commands.action());
    }

    @Test
    void commandsMenuShowsLiveCounts() {
        Menu menu = detail.commandsMenu("zebra", null);

        assertEquals(title("Command Lists"), menu.title());
        MenuButton player = menu.buttonAt(10);
        assertEquals(Material.LIGHT_GRAY_CONCRETE, player.material());
        assertEquals("player", textOf(player.name()));
        assertTrue(player.glow());
        assertEquals(2, player.lore().size());
        assertEquals(messages.nonItalic(messages.parse("<gray>Lines: <white>1")),
                player.lore().get(0));
        assertEquals(plain("Click to open", NamedTextColor.GRAY), player.lore().get(1));

        MenuButton speedrunner = menu.buttonAt(11);
        assertEquals(Material.LIME_CONCRETE, speedrunner.material());
        assertFalse(speedrunner.glow());
        assertEquals(1, speedrunner.lore().size());
        assertEquals(Material.RED_CONCRETE, menu.buttonAt(12).material());
        assertFalse(menu.buttonAt(12).glow());
        assertEquals(Material.BLACK_CONCRETE, menu.buttonAt(13).material());
        assertFalse(menu.buttonAt(13).glow());

        assertNull(menu.buttonAt(14));

        assertEquals(Material.LIGHT_GRAY_SHULKER_BOX, menu.buttonAt(15).material());
        assertEquals(Material.BLACK_SHULKER_BOX, menu.buttonAt(16).material());
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
        MenuButton stick = menu.buttonAt(3);
        assertEquals(Material.STICK, stick.material());
        assertEquals("Add Line", textOf(stick.name()));
        assertNotNull(stick.action());
        assertNull(stick.rightAction());
        assertNull(menu.buttonAt(36));
        assertEquals(Material.PAPER, menu.buttonAt(18).material());
    }

    @Test
    void emptyLinesMenuShowsStickFirst() {
        Menu menu = detail.linesMenu("zebra", "console", null);

        MenuButton stick = menu.buttonAt(2);
        assertEquals(Material.STICK, stick.material());
        assertNotNull(stick.action());
    }

    @Test
    void commandFeedbackEscapesTags() {
        var values = ModifierDetailMenus.commandSetValues("player", 2,
                "give <p> <red>apple");

        assertEquals("2nd", values.get("ordinal"));
        assertEquals("player", values.get("list"));
        Component rendered = messages.component("modifiers.edit-command-set", values);
        assertTrue(textOf(rendered)
                .endsWith("2nd command for player set to give <p> <red>apple"));
    }
}
