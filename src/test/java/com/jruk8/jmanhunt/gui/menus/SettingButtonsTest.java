package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.gui.GuiConfig;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Setting buttons: prettified names, lore lines in spec order, category
 * icons, and glow only when modified. Actions stay uninvoked: writes
 * and feedback are covered in their own packages.
 */
class SettingButtonsTest {

    private JManhuntConfig root;
    private ConfigService config;
    private GuiConfig guiData;
    private MessageService messages;
    private SettingButtons buttons;
    private GuiService gui;
    private OverrideService overrides;
    private Player viewer;

    @BeforeEach
    void setup() throws Exception {
        messages = new MessageService();
        messages.reload(new MessagesConfig());
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        root = new JManhuntConfig();
        config = new ConfigService(root,
                new ModifierStore(new ModifiersConfig(), log));
        guiData = new GuiConfig();
        guiData.setDescriptions(Map.of(
                "settings.match.autostart.enabled", "Start matches automatically.",
                "settings.match.autostart.countdown-seconds", "Wait before auto-starting."));
        gui = mock(GuiService.class);
        when(gui.overrideLobby(any())).thenReturn(null);
        overrides = new OverrideService(config, new LobbyConfig(), () -> {});
        viewer = mock(Player.class);
        buttons = new SettingButtons(config, overrides, guiData, messages,
                null, gui, null, null);
    }

    @Test
    void prettifyTitleCasesLeafSegments() {
        assertEquals("Countdown Seconds", SettingButtons.prettify("countdown-seconds"));
        assertEquals("On Expire", SettingButtons.prettify("on-expire"));
        assertEquals("Enabled", SettingButtons.prettify("enabled"));
        assertEquals("Show Ids", SettingButtons.prettify("show-ids"));
    }

    @Test
    void nextOptionCyclesAndWraps() {
        assertEquals("LOBBY", SettingButtons.nextOption(
                SettingRegistry.byPath("settings.match.game-leave.destination"), "SPECTATOR"));
        assertEquals("SPECTATOR", SettingButtons.nextOption(
                SettingRegistry.byPath("settings.match.game-leave.destination"), "LOBBY"));
        assertEquals("CANCEL", SettingButtons.nextOption(
                SettingRegistry.byPath("settings.match.start-on-speedrunner-damage.on-expire"),
                "FORCE_START"));
    }

    @Test
    void toggledValueFlipsBooleans() {
        assertEquals("false", SettingButtons.toggledValue(true));
        assertEquals("true", SettingButtons.toggledValue(false));
        assertEquals("true", SettingButtons.toggledValue(null));
        assertEquals("true", SettingButtons.toggledValue("yes"));
    }

    @Test
    void boolButtonLoreFollowsSpecOrder() {
        MenuButton button = buttons.settingButton(viewer,
                "settings.match.autostart.enabled", () -> null);

        assertEquals(Material.CLOCK, button.material());
        assertEquals("Enabled", plain(button.name()));
        assertEquals(List.of(
                "Start matches automatically.",
                "",
                "Value: Enabled",
                "Path: match.autostart.enabled",
                "Type: Boolean",
                "Default: Enabled",
                "",
                "Click to toggle",
                "Right-click to reset"),
                lore(button));
        assertFalse(button.glow());
        assertNotNull(button.action());
        assertNotNull(button.rightAction());
    }

    @Test
    void modifiedSettingsGlow() {
        assertTrue(ConfigPathMapper.set(root,
                "settings.match.autostart.countdown-seconds", 60));

        MenuButton button = buttons.settingButton(viewer,
                "settings.match.autostart.countdown-seconds", () -> null);

        assertTrue(button.glow());
        assertTrue(lore(button).contains("Value: 60"));
    }

    @Test
    void optionButtonBulletsMarkCurrent() {
        MenuButton button = buttons.settingButton(viewer,
                "settings.match.game-leave.destination", () -> null);

        assertTrue(lore(button).contains("» SPECTATOR"));
        assertTrue(lore(button).contains("» LOBBY"));
        assertTrue(lore(button).contains("Click to cycle"));
    }

    @Test
    void resetOnUnmodifiedSettingSendsNotice() {
        MenuButton button = buttons.settingButton(viewer,
                "settings.match.autostart.enabled", () -> null);
        Player player = mock(Player.class);

        button.rightAction().accept(player);

        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(sent.capture());
        assertTrue(plain(sent.getValue()).contains("already the default"));
    }

    @Test
    void resetOnModifiedSettingOpensConfirm() {
        assertTrue(ConfigPathMapper.set(root,
                "settings.match.autostart.enabled", false));
        SettingButtons withGui = new SettingButtons(config, overrides, guiData,
                messages, null, gui, null, null);
        MenuButton button = withGui.settingButton(viewer,
                "settings.match.autostart.enabled", () -> null);
        Player player = mock(Player.class);

        button.rightAction().accept(player);

        verify(player, never()).sendMessage(any(Component.class));
        verify(gui).navigate(any(Player.class), any(Menu.class));
    }

    @Test
    void overrideSessionGlowsOnlyWhenOverridden() {
        when(gui.overrideLobby(viewer)).thenReturn(2);

        MenuButton plain = buttons.settingButton(viewer,
                "settings.match.autostart.enabled", () -> null);

        assertFalse(plain.glow());
        assertNotNull(plain.shiftAction());
        assertTrue(lore(plain).contains("Shift-left-click to remove the override"));

        assertTrue(overrides.setSettingOverride(2,
                "settings.match.autostart.enabled", "false").ok());
        MenuButton glowing = buttons.settingButton(viewer,
                "settings.match.autostart.enabled", () -> null);

        assertTrue(glowing.glow());
        assertTrue(lore(glowing).contains("Value: Disabled"));
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> lore(MenuButton button) {
        return button.lore().stream().map(SettingButtonsTest::plain)
                .collect(Collectors.toList());
    }
}
