package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiConfig;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.ScalingLayout;
import com.jruk8.jmanhunt.gui.ScrollList;
import com.jruk8.jmanhunt.gui.dialog.SettingDialog;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.stats.HistoryPlaceholders;
import com.jruk8.jmanhunt.stats.StatsManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Root, settings, history, and list menus. Drill levels follow the
 * setting registry: sections and lists first, then scalar keys, in a
 * scaling menu up to 48 entries and a scroll list beyond that.
 */
public final class ManhuntMenus {

    private final ConfigService config;
    private final GuiConfig guiData;
    private final MessageService messages;
    private final SoundService sounds;
    private final GuiService gui;
    private final SettingDialog dialogs;
    private final SettingFeedback feedback;
    private final StatsManager stats;
    private final ModifierMenus modifiers;
    private final SettingButtons buttons;

    public ManhuntMenus(ConfigService config, GuiConfig guiData, MessageService messages,
            SoundService sounds, GuiService gui, SettingDialog dialogs,
            SettingFeedback feedback, StatsManager stats, ModifierMenus modifiers) {
        this.config = config;
        this.guiData = guiData;
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.dialogs = dialogs;
        this.feedback = feedback;
        this.stats = stats;
        this.modifiers = modifiers;
        this.buttons = new SettingButtons(config, guiData, messages, dialogs, gui, feedback);
    }

    /** 27-slot root with settings, history, and modifiers links. */
    public Menu rootMenu() {
        return new Menu(title("title-root", "Manhunt"),
                MenuLayout.parse("#########", "##s#h#m##", "#########"),
                this::rootStatic, List::of, null);
    }

    /** 27-slot settings menu with the four category links. */
    public Menu settingsMenu() {
        return settingsMenu(this::rootMenu);
    }

    /** Settings menu with an explicit parent. */
    private Menu settingsMenu(Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        self[0] = new Menu(title("title-settings", "Settings"),
                MenuLayout.parse("#m#c#p#s#", "#########", "####b####"),
                () -> settingsStatic(self), List::of, parent);
        return self[0];
    }

    /** Drill level for one settings section. */
    public Menu sectionMenu(String path, Supplier<Menu> parent) {
        return sectionMenu(path, sectionTitle(path), parent);
    }

    /** String-list menu: one paper per index plus a trailing stick. */
    public Menu listMenu(String listPath, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> listButtons(listPath, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(messages, messages
                        .string("manhunt-gui.dialog-title-edit", "Edit {name}")
                        .replace("{name}", SettingButtons.prettify(leaf(listPath)))),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        backButton(self)),
                content::get, parent);
        return self[0];
    }

    /** Lifetime stat lines for the root history book. Null-safe for tests. */
    private List<String> historyLines() {
        HistoryPlaceholders.Totals totals = stats.lifetime();
        if (totals == null) {
            totals = new HistoryPlaceholders.Totals(0, 0, 0, 0, 0, 0, 0.0, 0L);
        }
        Map<String, String> values = HistoryPlaceholders.resolve(totals);
        return List.of(
                historyLine("matches", values.get("server_matches")),
                historyLine("kills", values.get("server_kills")),
                historyLine("hunter-kills", values.get("server_hunter_kills")),
                historyLine("speedrunner-kills", values.get("server_speedrunner_kills")),
                historyLine("hunter-wins", values.get("server_hunter_wins")),
                historyLine("speedrunner-wins", values.get("server_speedrunner_wins")),
                historyLine("damage", values.get("server_damage")),
                historyLine("playtime", values.get("server_playtime")));
    }

    /**
     * First-run nudge: confirm starts the setup guide, cancel skips
     * forever. Both actions mark the global flag; the caller supplies
     * what happens after.
     */
    public Menu setupFirstMenu(Consumer<Player> onConfirm, Consumer<Player> onCancel) {
        return ConfirmMenu.create(
                title("setup-first-title", "First-Time Setup"),
                Material.WRITABLE_BOOK, null,
                GuiTexts.lore(messages, List.of(
                        text("setup-first-line1", "JManhunt is not set up yet."),
                        text("setup-first-line2", "Start the interactive setup guide?"))),
                GuiTexts.name(messages,
                        text("setup-first-cancel", "<red>Skip forever (not recommended)"),
                        "Skip forever"),
                onCancel,
                GuiTexts.name(messages,
                        text("setup-first-confirm", "<green>Start setup"), "Start setup"),
                onConfirm,
                null);
    }

    /** Truncates long list values for lore lines. */
    static String truncateValue(String value, int max) {
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private Menu sectionMenu(String path, Component title, Supplier<Menu> parent) {
        String effective = collapseSingles(path);
        Component effectiveTitle = effective.equals(path)
                ? title
                : sectionTitle(effective);
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> drillButtons(effective, () -> self[0]);
        if (drillSize(effective) > ScalingLayout.capacity(ScalingLayout.MAX_ROWS)) {
            return ScrollList.menu(effectiveTitle, content, parent, gui, messages, sounds);
        }
        self[0] = scalingMenu(effectiveTitle, content, parent);
        return self[0];
    }

    /**
     * Skips pass-through sections that hold exactly one subsection and no
     * settings, so one-button menus never open. Back still returns to the
     * caller supplied parent.
     */
    static String collapseSingles(String path) {
        String current = path;
        while (true) {
            SettingRegistry.DrillChildren children = SettingRegistry.children(current);
            if (children.sections().size() != 1 || !children.leaves().isEmpty()) {
                return current;
            }
            current = current + "." + children.sections().get(0);
        }
    }

    private Menu scalingMenu(Component title, Supplier<List<MenuButton>> content,
            Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(title, layout,
                () -> Map.of(ScalingLayout.backSlot(layout.rowCount()), backButton(self)),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> drillButtons(String path, Supplier<Menu> caller) {
        SettingRegistry.DrillChildren children = SettingRegistry.children(path);
        List<MenuButton> buttons = new ArrayList<>();
        for (String section : children.sections()) {
            buttons.add(childButton(path + "." + section, caller));
        }
        for (String leaf : children.leaves()) {
            buttons.add(this.buttons.settingButton(path + "." + leaf, caller));
        }
        return buttons;
    }

    private int drillSize(String path) {
        SettingRegistry.DrillChildren children = SettingRegistry.children(path);
        return children.sections().size() + children.leaves().size();
    }

    private MenuButton childButton(String path, Supplier<Menu> caller) {
        if (SettingRegistry.isListPath(path)) {
            return listButton(path, caller);
        }
        return new MenuButton(guiData.sectionItem(path),
                GuiTexts.name(messages, SettingButtons.prettify(leaf(path)),
                        SettingButtons.prettify(leaf(path))),
                GuiTexts.lore(messages, sectionLines(path)),
                false, false, open(() -> sectionMenu(path, caller)));
    }

    /** Section button lore: description when present, then the entry count. */
    private List<String> sectionLines(String path) {
        List<String> lines = new ArrayList<>();
        String description = guiData.description(path);
        if (description != null && !description.isBlank()) {
            lines.add(description);
        }
        lines.add(countLine(drillSize(path)));
        return lines;
    }

    private MenuButton listButton(String path, Supplier<Menu> caller) {
        int count = config.getStringList(path).size();
        return new MenuButton(Material.PAPER,
                GuiTexts.name(messages, SettingButtons.prettify(leaf(path)),
                        SettingButtons.prettify(leaf(path))),
                GuiTexts.lore(messages, List.of(countLine(count),
                        text("list-hint-open", "Click to open"),
                        text("setting-hint-reset", "Right-click to reset"))),
                config.isListModified(path), false, open(() -> listMenu(path, caller)),
                player -> listResetConfirm(player, path, caller));
    }

    private void listResetConfirm(Player player, String listPath, Supplier<Menu> caller) {
        // Already at default: resetting would be a no-op, so say so in
        // chat instead of opening a confirm panel for nothing.
        if (!config.isListModified(listPath)) {
            messages.message(player, "manhunt-gui.setting-already-default");
            return;
        }
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages, messages
                        .string("manhunt-gui.setting-reset-title", "Reset {name}?")
                        .replace("{name}", SettingButtons.prettify(leaf(listPath)))),
                Material.PAPER, null,
                GuiTexts.lore(messages, List.of(countLine(config.getStringList(listPath).size()))),
                GuiTexts.name(messages, text("cancel", "Cancel"), "Cancel"),
                back -> gui.navigate(back, caller.get()),
                GuiTexts.name(messages, text("confirm", "Confirm"), "Confirm"),
                done -> {
                    ConfigService.SetOutcome outcome = config.listReset(listPath);
                    if (!outcome.ok()) {
                        feedback.failed(done, outcome);
                        sounds.playAngrySound(done);
                    } else {
                        feedback.listReset(done, listPath, outcome);
                    }
                    gui.navigate(done, caller.get());
                },
                caller);
        gui.navigate(player, confirm);
    }

    private List<MenuButton> listButtons(String listPath, Supplier<Menu> caller) {
        List<String> entries = config.getStringList(listPath);
        int room = ScalingLayout.capacity(ScalingLayout.rowsFor(entries.size() + 1)) - 1;
        List<MenuButton> buttons = new ArrayList<>();
        for (int index = 0; index < entries.size() && index < room; index++) {
            buttons.add(indexButton(listPath, index, entries.get(index), caller));
        }
        buttons.add(new MenuButton(Material.STICK,
                GuiTexts.name(messages, text("list-add-name", "Add entry"), "Add entry"),
                GuiTexts.lore(messages,
                        List.of(text("list-add-lore", "Click to append"))),
                false, false,
                player -> dialogs.openListAppend(player, listPath,
                        GuiTexts.title(messages, addTitle()), caller)));
        return buttons;
    }

    private MenuButton indexButton(String listPath, int index, String value, Supplier<Menu> caller) {
        String name = text("list-entry-name", "#{index}").replace("{index}",
                String.valueOf(index));
        List<String> lore = List.of(
                MiniMessage.miniMessage().escapeTags(truncateValue(value, 60)),
                text("list-hint-edit", "Click to edit"),
                text("list-hint-delete", "Right-click to delete"));
        return new MenuButton(Material.PAPER, GuiTexts.name(messages, name, name),
                GuiTexts.lore(messages, lore), false, false,
                player -> dialogs.openListEntry(player, listPath, index,
                        GuiTexts.title(messages, editTitle(index)), caller),
                player -> deleteConfirm(player, listPath, index, value, caller));
    }

    private void deleteConfirm(Player player, String listPath, int index,
            String value, Supplier<Menu> caller) {
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages, deleteTitle(index)),
                Material.PAPER, null,
                GuiTexts.lore(messages, List.of(MiniMessage.miniMessage()
                        .escapeTags(truncateValue(value, 60)))),
                GuiTexts.name(messages, text("cancel", "Cancel"), "Cancel"),
                back -> gui.navigate(back, caller.get()),
                GuiTexts.name(messages, text("confirm", "Confirm"), "Confirm"),
                done -> {
                    ConfigService.SetOutcome outcome = config.listRemove(listPath, index);
                    if (!outcome.ok()) {
                        feedback.failed(done, outcome);
                        sounds.playAngrySound(done);
                    } else {
                        feedback.listRemoved(done, listPath, outcome);
                    }
                    gui.navigate(done, caller.get());
                },
                caller);
        gui.navigate(player, confirm);
    }

    private Map<Integer, MenuButton> rootStatic() {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(11, new MenuButton(Material.CHEST,
                GuiTexts.name(messages, text("to-settings", "Settings"), "Settings"),
                GuiTexts.lore(messages, List.of(
                        text("to-settings-lore", "Match, Compass, Players, Server"))),
                false, false, open(this::settingsMenu)));
        // Lifetime stats live on the book itself: hover to read, no
        // separate menu. The action stays null so clicks pass through
        // silently like any other display line.
        fixed.put(13, new MenuButton(Material.WRITTEN_BOOK,
                GuiTexts.name(messages, text("to-history", "History"), "History"),
                GuiTexts.lore(messages, historyLines()),
                false, false, null));
        fixed.put(15, new MenuButton(Material.BOOK,
                GuiTexts.name(messages, text("to-modifiers", "Modifiers"), "Modifiers"),
                GuiTexts.lore(messages, List.of(
                        text("to-modifiers-lore", "Toggle modifiers and presets"))),
                false, false, open(() -> modifiers.mainMenu(this::rootMenu))));
        return fixed;
    }

    private Map<Integer, MenuButton> settingsStatic(Menu[] self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        String[] categories = {"match", "compass", "players", "server"};
        int[] slots = {1, 3, 5, 7};
        for (int slot = 0; slot < categories.length; slot++) {
            String category = categories[slot];
            String path = "settings." + category;
            fixed.put(slots[slot], new MenuButton(guiData.categoryItem(category),
                    GuiTexts.name(messages, SettingButtons.prettify(category),
                            SettingButtons.prettify(category)),
                    GuiTexts.lore(messages, sectionLines(path)),
                    false, false, open(() -> sectionMenu(path, this::settingsMenu))));
        }
        fixed.put(22, backButton(self));
        return fixed;
    }

    /**
     * Takes the menu cell, not the menu: factories run once inside the
     * Menu constructor while the cell is still empty, so capturing the
     * value binds a null that later explodes in GuiService.back. Reading
     * the cell at click time always sees the finished menu.
     */
    private MenuButton backButton(Menu[] self) {
        return new MenuButton(Material.PAPER,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                null, false, false,
                player -> {
                    gui.back(player, self[0]);
                    sounds.playNeutralSound(player);
                });
    }

    private Consumer<Player> open(Supplier<Menu> menu) {
        return player -> {
            gui.navigate(player, menu.get());
            sounds.playSound(player, "compass.left-click");
        };
    }

    private String historyLine(String key, String value) {
        return messages.string("manhunt-gui.history-line-" + key, key + ": {value}")
                .replace("{value}", value);
    }

    private String countLine(int count) {
        return messages.string("manhunt-gui.category-lore", "{count} entries")
                .replace("{count}", String.valueOf(count));
    }

    private String editTitle(int index) {
        return messages.string("manhunt-gui.dialog-title-edit-entry", "Edit entry {index}")
                .replace("{index}", String.valueOf(index));
    }

    private String addTitle() {
        return messages.string("manhunt-gui.dialog-title-add-entry", "Add entry");
    }

    private String deleteTitle(int index) {
        return messages.string("manhunt-gui.dialog-title-delete-entry", "Delete entry {index}?")
                .replace("{index}", String.valueOf(index));
    }

    private Component title(String key, String fallback) {
        return GuiTexts.title(messages, text(key, fallback));
    }

    private String text(String key, String fallback) {
        return messages.string("manhunt-gui." + key, fallback);
    }

    private Component sectionTitle(String path) {
        String[] parts = path.split("\\.");
        if (parts.length == 2) {
            return GuiTexts.title(messages, messages
                    .string("manhunt-gui.title-category", "{name} Settings")
                    .replace("{name}", SettingButtons.prettify(parts[1])));
        }
        return GuiTexts.title(messages, SettingButtons.prettify(parts[parts.length - 1]));
    }

    private static String leaf(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
