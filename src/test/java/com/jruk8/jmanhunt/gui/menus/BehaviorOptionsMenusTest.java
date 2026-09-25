package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.dialog.ModifierDialog;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierChance;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierExecution;
import com.jruk8.jmanhunt.modifiers.config.ModifierInterval;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavior Options: six glowing entries, gated Interval Settings,
 * and Execution/Delay/Chance submenus with live values.
 */
class BehaviorOptionsMenusTest {

    private ModifierStore store;
    private BehaviorOptionsMenus options;

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static Component title(String text) {
        return Component.text(text).decoration(TextDecoration.ITALIC, false);
    }

    private static String textOf(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @BeforeEach
    void setup() {
        ModifiersConfig config = new ModifiersConfig();
        config.getModifiers().put("zebra", behaviorEntry());
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        store = new ModifierStore(config, log);
        options = new BehaviorOptionsMenus(store, messages, null, null, null, null, null);
    }

    private static ModifierEntry behaviorEntry() {
        ModifierEntry entry = new ModifierEntry();
        entry.setEnabled(true);
        ModifierBehavior behavior = new ModifierBehavior();
        behavior.setRunsOn(new ArrayList<>(List.of("ON_START", "INTERVAL")));
        ModifierOptions opts = new ModifierOptions();
        ModifierInterval interval = new ModifierInterval();
        interval.setInterval(30.0);
        interval.setDeviation(5.0);
        interval.setBehavior("PER_EXECUTOR");
        opts.setIntervalSettings(interval);
        ModifierChance chance = new ModifierChance();
        chance.setChance(0.5);
        opts.setSuccessChance(chance);
        ModifierExecution execution = new ModifierExecution();
        execution.setSelection("PICK_RANDOM");
        opts.setExecution(execution);
        opts.setDelay(100L);
        behavior.setOptions(opts);
        entry.setBehavior(behavior);
        return entry;
    }

    @Test
    void optionsMenuListsSixGlowingEntries() {
        Menu menu = options.optionsMenu("zebra", null);

        assertEquals(title("Behavior Options"), menu.title());
        Material[] materials = {Material.REDSTONE_TORCH, Material.LEVER, Material.REPEATER,
                Material.BELL, Material.WHITE_WOOL, Material.HOPPER};
        for (int slot = 0; slot < materials.length; slot++) {
            MenuButton button = menu.buttonAt(slot);
            assertNotNull(button, "missing entry at " + slot);
            assertEquals(materials[slot], button.material());
            assertTrue(button.glow(), "entry at " + slot + " should glow");
            assertNotNull(button.action());
        }
        assertEquals("Runs On", textOf(menu.buttonAt(1).name()));
        assertEquals("2 selected", textOf(menu.buttonAt(1).lore().get(1)));
        assertEquals(plain("Current: 100 ticks", NamedTextColor.GRAY),
                menu.buttonAt(4).lore().get(0));
        assertEquals(Material.PAPER, menu.buttonAt(8).material());
    }

    @Test
    void unknownIdsShowDefaultsWithoutGlow() {
        Menu menu = options.optionsMenu("ghost", null);

        for (int slot = 0; slot < 6; slot++) {
            assertFalse(menu.buttonAt(slot).glow(), "slot " + slot + " should not glow");
        }
        assertEquals(plain("Current: Not set", NamedTextColor.GRAY),
                menu.buttonAt(4).lore().get(0));
        assertEquals("Not set", textOf(menu.buttonAt(1).lore().get(1)));
    }

    @Test
    void intervalGateBlocksWithoutInterval() {
        SoundService sounds = mock(SoundService.class);
        GuiService gui = mock(GuiService.class);
        MessageService messages = mock(MessageService.class);
        when(messages.string(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(messages.parse(anyString()))
                .thenAnswer(invocation -> Component.text((String) invocation.getArgument(0)));
        when(messages.nonItalic(any(Component.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        BehaviorOptionsMenus gated = new BehaviorOptionsMenus(store, messages, sounds, gui,
                null, null, null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        gated.optionsMenu("ghost", null).buttonAt(2).action().accept(player);

        verify(sounds).playAngrySound(player);
        verify(messages).message(eq(player), eq("modifiers.edit-invalid"), any(Map.class));
        verify(gui, never()).navigate(any(Player.class), any(Menu.class));
    }

    @Test
    void intervalGateOpensWithInterval() {
        SoundService sounds = mock(SoundService.class);
        GuiService gui = mock(GuiService.class);
        MessageService messages = mock(MessageService.class);
        when(messages.string(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(messages.parse(anyString()))
                .thenAnswer(invocation -> Component.text((String) invocation.getArgument(0)));
        when(messages.nonItalic(any(Component.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        BehaviorOptionsMenus gated = new BehaviorOptionsMenus(store, messages, sounds, gui,
                null, null, null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        gated.optionsMenu("zebra", null).buttonAt(2).action().accept(player);

        verify(gui).navigate(eq(player), any(Menu.class));
        verify(sounds).playSound(player, "compass.left-click");
        verify(sounds, never()).playAngrySound(player);
    }

    @Test
    void runsOnOpensTheCheckboxDialog() {
        ModifierDialog dialogs = mock(ModifierDialog.class);
        MessageService messages = mock(MessageService.class);
        when(messages.string(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(messages.parse(anyString()))
                .thenAnswer(invocation -> Component.text((String) invocation.getArgument(0)));
        when(messages.nonItalic(any(Component.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        BehaviorOptionsMenus menus = new BehaviorOptionsMenus(store, messages,
                mock(SoundService.class), mock(GuiService.class),
                null, null, dialogs);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        menus.optionsMenu("zebra", null).buttonAt(1).action().accept(player);

        verify(dialogs).openRunsOn(eq(player), eq(List.of("ON_START", "INTERVAL")),
                any(), any());
    }

    @Test
    void intervalMenuShowsLiveValues() {
        Menu menu = options.intervalMenu("zebra", null);

        assertEquals(title("Interval Settings"), menu.title());
        assertEquals(Material.CLOCK, menu.buttonAt(0).material());
        assertEquals(plain("Current: 30.0s", NamedTextColor.GRAY),
                menu.buttonAt(0).lore().get(0));
        assertEquals(Material.COMPASS, menu.buttonAt(1).material());
        assertEquals(plain("Current: 5.0s", NamedTextColor.GRAY),
                menu.buttonAt(1).lore().get(0));
        assertEquals(Material.REPEATER, menu.buttonAt(2).material());
        assertEquals(plain("Current: PER_EXECUTOR", NamedTextColor.GRAY),
                menu.buttonAt(2).lore().get(0));
    }

    @Test
    void executionMenuShowsLiveValues() {
        Menu menu = options.executionMenu("zebra", null);

        assertEquals(title("Execution"), menu.title());
        assertEquals(Material.DISPENSER, menu.buttonAt(0).material());
        assertEquals(plain("Current: PICK_RANDOM", NamedTextColor.GRAY),
                menu.buttonAt(0).lore().get(0));
        assertEquals(Material.DROPPER, menu.buttonAt(1).material());
        assertEquals(plain("Current: Default (1)", NamedTextColor.GRAY),
                menu.buttonAt(1).lore().get(0));
        assertEquals(plain("Current: Default (IN_ORDER)", NamedTextColor.GRAY),
                menu.buttonAt(3).lore().get(0));
    }

    @Test
    void chanceMenuShowsLiveValues() {
        Menu menu = options.chanceMenu("zebra", null);

        assertEquals(title("Success Chance"), menu.title());
        assertEquals(Material.EXPERIENCE_BOTTLE, menu.buttonAt(0).material());
        assertEquals(plain("Current: 50%", NamedTextColor.GRAY),
                menu.buttonAt(0).lore().get(0));
        assertEquals(Material.DAYLIGHT_DETECTOR, menu.buttonAt(1).material());
    }

    @Test
    void preStartCyclesThroughAfter() {
        SoundService sounds = mock(SoundService.class);
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        BehaviorOptionsMenus menus = new BehaviorOptionsMenus(store, messages, sounds,
                mock(GuiService.class), null, null, null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        menus.executionMenu("zebra", null).buttonAt(3).action().accept(player);
        assertEquals("IN_ORDER", store.preStartOrder("zebra"));

        menus.executionMenu("zebra", null).buttonAt(3).action().accept(player);
        assertEquals("AFTER", store.preStartOrder("zebra"));
        assertTrue(ModifiedGlow.behaviorPreStart(store, "zebra"));
    }
}
