package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
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
import java.util.concurrent.atomic.AtomicReference;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Behavior Options: five glowing entries, gated Interval Settings,
 * and Execution/Delay/Chance submenus with live values.
 */
class BehaviorOptionsMenusTest {

    private ModifierStore store;
    private BehaviorOptionsMenus options;
    private MessageService messages;

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
        messages = new MessageService();
        messages.reload(new MessagesConfig());
        store = new ModifierStore(config, log);
        options = new BehaviorOptionsMenus(store, messages, null, null, null, null);
    }

    private static List<String> loreLines(MenuButton button) {
        return button.lore().stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
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
    void optionsMenuListsFiveGlowingEntries() {
        Menu menu = options.optionsMenu("zebra", null);

        assertEquals(title("Behavior Options"), menu.title());
        Material[] materials = {Material.LEVER, Material.REPEATER,
                Material.BELL, Material.WHITE_WOOL, Material.HOPPER};
        for (int slot = 0; slot < materials.length; slot++) {
            MenuButton button = menu.buttonAt(slot);
            assertNotNull(button, "missing entry at " + slot);
            assertEquals(materials[slot], button.material());
            assertTrue(button.glow(), "entry at " + slot + " should glow");
            assertNotNull(button.action());
        }
        assertEquals("Runs On", textOf(menu.buttonAt(0).name()));
        assertRunsOnLore(menu);
        assertDelayLore(menu, "zebra", "100 ticks");
        assertEquals(Material.PAPER, menu.buttonAt(8).material());
    }

    private static void assertRunsOnLore(Menu menu) {
        assertEquals(List.of(
                "Events that trigger this modifier.",
                "",
                "Value: 2 selected",
                "Path: modifiers.zebra.behavior.runs-on",
                "Type: Choice",
                "» ON_START",
                "» INTERVAL",
                "» ON_EVERY_KILL",
                "» ON_PLAYER_KILL",
                "» ON_HUNTER_KILL",
                "» ON_SPEEDRUNNER_KILL",
                "» ON_NETHER_ENTER",
                "» ON_END_ENTER",
                "» ON_FIRST_NETHER_ENTER",
                "» ON_FIRST_END_ENTER",
                "» ON_EVERY_ADVANCEMENT",
                "» ON_RESPAWN",
                "» ON_SPEEDRUNNER_RESPAWN",
                "» ON_HUNTER_RESPAWN",
                "Default: ON_START",
                "",
                "Click to open",
                "Right-click to reset"), loreLines(menu.buttonAt(0)));
    }

    private static void assertDelayLore(Menu menu, String id, String value) {
        assertEquals(List.of(
                "Ticks to wait after the trigger before commands run.",
                "",
                "Value: " + value,
                "Path: modifiers." + id + ".behavior.options.delay",
                "Type: Integer",
                "Allowed: 0 or more",
                "Default: Not set",
                "",
                "Click to edit",
                "Right-click to reset"), loreLines(menu.buttonAt(3)));
    }

    @Test
    void runsOnMarksSelectedTriggers() {
        Menu menu = options.optionsMenu("zebra", null);

        Component marked = menu.buttonAt(0).lore().get(5);
        Component unmarked = menu.buttonAt(0).lore().get(7);
        assertEquals("» ON_START", textOf(marked));
        assertEquals("» ON_EVERY_KILL", textOf(unmarked));
        assertTrue(hasGreen(marked));
        assertFalse(hasGreen(unmarked));
    }

    private static boolean hasGreen(Component component) {
        if (component.color() == NamedTextColor.GREEN) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasGreen(child)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void unknownIdsShowDefaultsWithoutGlow() {
        Menu menu = options.optionsMenu("ghost", null);

        for (int slot = 0; slot < 5; slot++) {
            assertFalse(menu.buttonAt(slot).glow(), "slot " + slot + " should not glow");
        }
        assertDelayLore(menu, "ghost", "Not set");
        assertEquals("Value: Default (ON_START)", textOf(menu.buttonAt(0).lore().get(2)));
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
                null, null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        gated.optionsMenu("ghost", null).buttonAt(1).action().accept(player);

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
                null, null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        gated.optionsMenu("zebra", null).buttonAt(1).action().accept(player);

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
                null, dialogs);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        menus.optionsMenu("zebra", null).buttonAt(0).action().accept(player);

        verify(dialogs).openRunsOn(eq(player), eq(List.of("ON_START", "INTERVAL")),
                any(), any());
    }

    @Test
    void intervalMenuShowsLiveValues() {
        Menu menu = options.intervalMenu("zebra", null);

        assertEquals(title("Interval Settings"), menu.title());
        assertEquals(Material.CLOCK, menu.buttonAt(0).material());
        assertEquals(List.of(
                "Seconds between runs while INTERVAL is selected.",
                "",
                "Value: 30.0s",
                "Path: modifiers.zebra.behavior.options.interval-settings.interval",
                "Type: Number",
                "Allowed: 0 or more",
                "Default: Not set",
                "",
                "Click to edit",
                "Right-click to reset"), loreLines(menu.buttonAt(0)));
        assertEquals(Material.COMPASS, menu.buttonAt(1).material());
        assertEquals(List.of(
                "Random jitter added to each interval, never above it.",
                "",
                "Value: 5.0s",
                "Path: modifiers.zebra.behavior.options.interval-settings.deviation",
                "Type: Number",
                "Allowed: 0 up to interval",
                "Default: Not set",
                "",
                "Click to edit",
                "Right-click to reset"), loreLines(menu.buttonAt(1)));
        assertEquals(Material.REPEATER, menu.buttonAt(2).material());
        assertEquals(List.of(
                "Whether the interval clock is shared or runs per player.",
                "",
                "Value: PER_EXECUTOR",
                "Path: modifiers.zebra.behavior.options.interval-settings.behavior",
                "Type: Choice",
                "» PER_INVOKE",
                "» PER_EXECUTOR",
                "Default: PER_INVOKE",
                "",
                "Click to cycle",
                "Right-click to reset"), loreLines(menu.buttonAt(2)));
    }

    @Test
    void executionMenuShowsLiveValues() {
        Menu menu = options.executionMenu("zebra", null);

        assertEquals(title("Execution"), menu.title());
        assertEquals(Material.DISPENSER, menu.buttonAt(0).material());
        assertEquals(List.of(
                "How command lines are picked on each run.",
                "",
                "Value: PICK_RANDOM",
                "Path: modifiers.zebra.behavior.options.execution.selection",
                "Type: Choice",
                "» IN_ORDER",
                "» PICK_RANDOM",
                "Default: IN_ORDER",
                "",
                "Click to cycle",
                "Right-click to reset"), loreLines(menu.buttonAt(0)));
        assertEquals(Material.DROPPER, menu.buttonAt(1).material());
        assertEquals(List.of(
                "How many lines each PICK_RANDOM draw takes.",
                "",
                "Value: Default (1)",
                "Path: modifiers.zebra.behavior.options.execution.pick-random.count",
                "Type: Integer",
                "Allowed: 1 or more",
                "Default: 1",
                "",
                "Click to edit",
                "Right-click to reset"), loreLines(menu.buttonAt(1)));
        assertEquals(List.of(
                "Whether ON_START fires before or after the pre-start window.",
                "",
                "Value: Default (IN_ORDER)",
                "Path: modifiers.zebra.behavior.on-start.pre-start-order",
                "Type: Choice",
                "» IN_ORDER",
                "» AFTER",
                "Default: IN_ORDER",
                "",
                "Click to cycle",
                "Right-click to reset"), loreLines(menu.buttonAt(3)));
    }

    @Test
    void chanceMenuShowsLiveValues() {
        Menu menu = options.chanceMenu("zebra", null);

        assertEquals(title("Success Chance"), menu.title());
        assertEquals(Material.EXPERIENCE_BOTTLE, menu.buttonAt(0).material());
        assertEquals(List.of(
                "Probability the modifier runs at all, from 0 to 1.",
                "",
                "Value: 50%",
                "Path: modifiers.zebra.behavior.options.success-chance.chance",
                "Type: Number",
                "Allowed: 0 to 1",
                "Default: 100%",
                "",
                "Click to edit",
                "Right-click to reset"), loreLines(menu.buttonAt(0)));
        assertEquals(Material.DAYLIGHT_DETECTOR, menu.buttonAt(1).material());
    }

    @Test
    void preStartCyclesThroughAfter() {
        SoundService sounds = mock(SoundService.class);
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        BehaviorOptionsMenus menus = new BehaviorOptionsMenus(store, messages, sounds,
                mock(GuiService.class), null, null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        menus.executionMenu("zebra", null).buttonAt(3).action().accept(player);
        assertEquals("IN_ORDER", store.preStartOrder("zebra"));

        menus.executionMenu("zebra", null).buttonAt(3).action().accept(player);
        assertEquals("AFTER", store.preStartOrder("zebra"));
        assertTrue(ModifiedGlow.behaviorPreStart(store, "zebra"));
    }

    @Test
    void rightClickResetClearsThroughConfirm() {
        SoundService sounds = mock(SoundService.class);
        GuiService gui = mock(GuiService.class);
        BehaviorOptionsMenus menus = new BehaviorOptionsMenus(store, messages, sounds,
                gui, null, null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);
        AtomicReference<Menu> shown = new AtomicReference<>();
        doAnswer(invocation -> {
            shown.set(invocation.getArgument(1));
            return null;
        }).when(gui).navigate(eq(player), any(Menu.class));

        menus.optionsMenu("zebra", null).buttonAt(3).rightAction().accept(player);

        assertEquals("Reset Delay?", textOf(shown.get().title()));
        shown.get().buttonAt(ConfirmMenu.CONFIRM_SLOT).action().accept(player);
        assertEquals(0L, store.delayTicks("zebra"));
        verify(sounds).playNeutralSound(player);
        verify(gui, times(2)).navigate(eq(player), any(Menu.class));
    }

    @Test
    void rightClickResetRefusesWhenAlreadyDefault() {
        GuiService gui = mock(GuiService.class);
        BehaviorOptionsMenus menus = new BehaviorOptionsMenus(store, messages,
                mock(SoundService.class), gui, null, null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        menus.optionsMenu("ghost", null).buttonAt(3).rightAction().accept(player);

        verify(gui, never()).navigate(any(Player.class), any(Menu.class));
        verify(player).sendMessage(any(Component.class));
    }
}
