package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiConfig;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.ScalingLayout;
import com.jruk8.jmanhunt.gui.ScrollList;
import com.jruk8.jmanhunt.gui.dialog.SettingDialog;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.stats.HistoryPlaceholders;
import com.jruk8.jmanhunt.stats.StatsManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Menu structure: root links, the eight-line history hover book,
 * category slots, list entries plus the add button, and registry drill
 * counts.
 */
class ManhuntMenusTest {

    private ConfigService config;
    private GuiConfig guiData;
    private GuiService gui;
    private MessageService messages;
    private StatsManager stats;
    private ManhuntMenus menus;

    @BeforeEach
    void setUp() {
        config = mock(ConfigService.class);
        guiData = mock(GuiConfig.class);
        messages = mock(MessageService.class);
        gui = mock(GuiService.class);
        when(guiData.categoryItem(anyString())).thenReturn(Material.CLOCK);
        when(guiData.sectionItem(anyString())).thenReturn(Material.CLOCK);
        when(guiData.description(anyString())).thenReturn("");
        when(messages.string(anyString(), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(messages.parse(anyString()))
                .thenAnswer(invocation ->
                        Component.text(invocation.getArgument(0, String.class)));
        when(messages.nonItalic(any(Component.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(config.getValue(anyString())).thenAnswer(invocation ->
                SettingRegistry.byPath(invocation.getArgument(0)).defaultValue());
        when(config.getStringList(anyString())).thenReturn(List.of());
        stats = mock(StatsManager.class);
        menus = new ManhuntMenus(config, guiData, messages, mock(SoundService.class),
                gui, mock(SettingDialog.class),
                mock(SettingFeedback.class), stats,
                mock(ModifierMenus.class));
    }

    @Test
    void listMenuRebuildsEntriesOnRefresh() {
        when(config.getStringList(anyString())).thenReturn(List.of("a"));
        Menu menu = menus.listMenu("settings.compass.analyze.debuffs.commands.player", () -> null);

        assertEquals(2, menu.window().visibleEntries().size());

        when(config.getStringList(anyString())).thenReturn(List.of("a", "b", "c"));
        menu.refresh();

        assertEquals(4, menu.window().visibleEntries().size());
    }

    @Test
    void rootHasThreeLinksAndNoParent() {
        Menu root = menus.rootMenu();

        assertEquals(27, root.layout().size());
        assertLink(root, 11, Material.CHEST);
        assertLink(root, 15, Material.BOOK);
        assertNull(root.buttonAt(0));
        assertNull(root.parent());
    }

    @Test
    void settingsMenuHasFourCategoriesAndBack() {
        Menu settings = menus.settingsMenu();

        assertLink(settings, 1, Material.CLOCK);
        assertLink(settings, 3, Material.CLOCK);
        assertLink(settings, 5, Material.CLOCK);
        assertLink(settings, 7, Material.CLOCK);
        assertLink(settings, 22, Material.PAPER);
        assertNotNull(settings.parent());
    }

    @Test
    void rootHistoryBookShowsLifetimeOnHover() {
        when(stats.lifetime()).thenReturn(new HistoryPlaceholders.Totals(
                7, 42, 30, 12, 5, 2, 1234.56, 3_661_000L));

        Menu root = menus.rootMenu();

        MenuButton book = root.buttonAt(13);
        assertNotNull(book);
        assertEquals(Material.WRITTEN_BOOK, book.material());
        assertEquals(8, book.lore().size());
        assertNull(book.action());
    }

    @Test
    void listMenuShowsEntriesPlusAdd() {
        when(config.getStringList("settings.compass.signal-interference.weather.interfere-during"))
                .thenReturn(List.of("a", "b"));

        Menu list = menus.listMenu(
                "settings.compass.signal-interference.weather.interfere-during", menus::rootMenu);

        List<MenuButton> shown = shownButtons(list,
                ScalingLayout.backSlot(list.layout().rowCount()));
        assertEquals(2, shown.stream()
                .filter(button -> button.material() == Material.PAPER).count());
        assertEquals(1, shown.stream()
                .filter(button -> button.material() == Material.STICK).count());
        assertNotNull(list.parent());
    }

    @Test
    void sectionMenuDrillsEveryRegistryChild() {
        SettingRegistry.DrillChildren children = SettingRegistry.children("settings.match");

        Menu section = menus.sectionMenu("settings.match", menus::settingsMenu);

        assertEquals(children.sections().size() + children.leaves().size(),
                shownButtons(section,
                        ScalingLayout.backSlot(section.layout().rowCount())).size());
    }

    @Test
    void singleChildSectionsCollapseToTheirChild() {
        assertEquals("settings.match.win-conditions.cancel.survived-time",
                ManhuntMenus.collapseSingles("settings.match.win-conditions.cancel"));
        assertEquals("settings.match",
                ManhuntMenus.collapseSingles("settings.match"));

        Menu collapsed = menus.sectionMenu(
                "settings.match.win-conditions.cancel", menus::settingsMenu);

        assertEquals("Survived Time", textOf(collapsed.title()));
        assertEquals(2, shownButtons(collapsed,
                ScalingLayout.backSlot(collapsed.layout().rowCount())).size());
        assertNotNull(collapsed.parent());
    }

    @Test
    void sectionButtonsResolveIconsByFullPath() {
        menus.sectionMenu("settings.match", menus::settingsMenu);

        verify(guiData, atLeastOnce()).sectionItem("settings.match.win-conditions");
        verify(guiData, never()).sectionItem("settings.match");
    }

    @Test
    void sectionButtonsShowDescriptionThenCount() {
        when(guiData.description(anyString()))
                .thenAnswer(invocation -> "Blurb for " + invocation.getArgument(0));

        Menu section = menus.sectionMenu("settings.server", menus::settingsMenu);
        MenuButton button = findButton(section, "Anti Spawn Camp");

        assertNotNull(button);
        assertEquals(2, button.lore().size());
        assertTrue(textOf(button.lore().get(0)).endsWith(
                "Blurb for settings.server.anti-spawn-camp"));
        assertTrue(textOf(button.lore().get(1)).endsWith("entries"));
    }

    @Test
    void sectionButtonsOmitBlankDescriptions() {
        Menu section = menus.sectionMenu("settings.server", menus::settingsMenu);
        MenuButton button = findButton(section, "Anti Spawn Camp");

        assertNotNull(button);
        assertEquals(1, button.lore().size());
    }

    @Test
    void backButtonsCarryTheirOwnMenu() {
        Player player = mock(Player.class);
        when(config.getStringList("settings.compass.signal-interference.weather.interfere-during"))
                .thenReturn(List.of("a", "b"));

        assertBackResolves(menus.settingsMenu(), player);
        assertBackResolves(menus.sectionMenu("settings.match", menus::settingsMenu), player);
        assertBackResolves(menus.listMenu(
                "settings.compass.signal-interference.weather.interfere-during",
                menus::settingsMenu), player);
        assertBackResolves(ScrollList.menu(Component.text("Keys"), List::of,
                menus::settingsMenu, gui, messages, mock(SoundService.class)), player);
    }

    private void assertBackResolves(Menu menu, Player player) {
        MenuButton back = null;
        for (int slot = 0; slot < menu.layout().size(); slot++) {
            MenuButton button = menu.buttonAt(slot);
            if (button != null && button.material() == Material.PAPER
                    && button.lore().isEmpty() && button.action() != null) {
                back = button;
            }
        }
        assertNotNull(back, "no back button found");
        back.action().accept(player);
        verify(gui).back(player, menu);
    }

    @Test
    void setupFirstWiresConfirmAndCancelActions() {
        Consumer<Player> confirm = player -> {};
        Consumer<Player> cancel = player -> {};

        Menu panel = menus.setupFirstMenu(confirm, cancel);

        assertEquals(9, panel.layout().size());
        assertEquals(Material.WRITABLE_BOOK, panel.buttonAt(ConfirmMenu.ICON_SLOT).material());
        assertEquals(2, panel.buttonAt(ConfirmMenu.ICON_SLOT).lore().size());
        assertNull(panel.buttonAt(ConfirmMenu.ICON_SLOT).action());
        assertSame(confirm, panel.buttonAt(ConfirmMenu.CONFIRM_SLOT).action());
        assertSame(cancel, panel.buttonAt(ConfirmMenu.CANCEL_SLOT).action());
        assertNull(panel.parent());
    }

    @Test
    void truncateValueKeepsShortTextAndClipsLongText() {
        assertEquals("abc", ManhuntMenus.truncateValue("abc", 60));
        assertEquals(60, ManhuntMenus.truncateValue("x".repeat(100), 60).length());
        assertEquals("...", ManhuntMenus.truncateValue("x".repeat(100), 3));
    }

    private static MenuButton findButton(Menu menu, String name) {
        for (int slot = 0; slot < menu.layout().size(); slot++) {
            MenuButton button = menu.buttonAt(slot);
            if (button != null && button.name() != null
                    && textOf(button.name()).endsWith(name)) {
                return button;
            }
        }
        return null;
    }

    private static String textOf(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static void assertLink(Menu menu, int slot, Material material) {
        MenuButton button = menu.buttonAt(slot);
        assertNotNull(button, "missing button at slot " + slot);
        assertEquals(material, button.material());
        assertNotNull(button.action(), "link at slot " + slot + " must navigate");
    }

    private static List<MenuButton> shownButtons(Menu menu, int... excluded) {
        List<MenuButton> shown = new ArrayList<>();
        for (int slot = 0; slot < menu.layout().size(); slot++) {
            if (excluded(slot, excluded)) {
                continue;
            }
            MenuButton button = menu.buttonAt(slot);
            if (button != null) {
                shown.add(button);
            }
        }
        return shown;
    }

    private static boolean excluded(int slot, int... excluded) {
        for (int skip : excluded) {
            if (slot == skip) {
                return true;
            }
        }
        return false;
    }
}
