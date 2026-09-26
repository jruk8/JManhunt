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
import com.jruk8.jmanhunt.gui.QuadPanel;
import com.jruk8.jmanhunt.gui.ScalingLayout;
import com.jruk8.jmanhunt.gui.ScrollList;
import com.jruk8.jmanhunt.gui.dialog.SettingDialog;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.stats.HistoryPlaceholders;
import com.jruk8.jmanhunt.stats.StatsManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Function;
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
    private final OverrideService overrides;
    private final GuiConfig guiData;
    private final MessageService messages;
    private final SoundService sounds;
    private final GuiService gui;
    private final SettingDialog dialogs;
    private final SettingFeedback feedback;
    private final StatsManager stats;
    private final ModifierMenus modifiers;
    private final SettingButtons buttons;

    public ManhuntMenus(ConfigService config, OverrideService overrides, GuiConfig guiData,
            MessageService messages, SoundService sounds, GuiService gui,
            SettingDialog dialogs, SettingFeedback feedback, StatsManager stats,
            ModifierMenus modifiers) {
        this.config = config;
        this.overrides = overrides;
        this.guiData = guiData;
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.dialogs = dialogs;
        this.feedback = feedback;
        this.stats = stats;
        this.modifiers = modifiers;
        this.buttons = new SettingButtons(config, overrides, guiData, messages, dialogs, gui,
                feedback, sounds);
    }

    /** 27-slot root with support, settings, history, modifiers, and override links. */
    public Menu rootMenu(Player viewer) {
        return new Menu(title("title-root", "Manhunt"),
                MenuLayout.parse("#########", "##s#h#m##", "#########"),
                () -> rootStatic(viewer), List::of, null);
    }

    /** 27-slot settings menu with the four category links. */
    public Menu settingsMenu(Player viewer) {
        return settingsMenu(viewer, () -> rootMenu(viewer));
    }

    /** Settings menu with an explicit parent. */
    private Menu settingsMenu(Player viewer, Supplier<Menu> parent) {
        return QuadPanel.menu(title("title-settings", "Settings"),
                categorySpecs(viewer), gui,
                GuiTexts.name(messages, text("back", "Back"), "Back"), parent);
    }

    /** Drill level for one settings section. */
    public Menu sectionMenu(Player viewer, String path, Supplier<Menu> parent) {
        return sectionMenu(viewer, path, sectionTitle(path), parent);
    }

    /** String-list menu: one paper per index plus a trailing stick. */
    public Menu listMenu(Player viewer, String listPath, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> listButtons(viewer, listPath, () -> self[0]);
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
     * First-run panel: confirm runs the one-click recommended setup,
     * cancel runs the step-by-step guide. The caller supplies both
     * actions, including the setup-done marking.
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

    private Menu sectionMenu(Player viewer, String path, Component title,
            Supplier<Menu> parent) {
        String effective = collapseSingles(path);
        Component effectiveTitle = effective.equals(path)
                ? title
                : sectionTitle(effective);
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> drillButtons(viewer, effective, () -> self[0]);
        if (drillSize(effective) > ScalingLayout.capacity(ScalingLayout.MAX_ROWS)) {
            return ScrollList.menu(effectiveTitle, content, parent, gui, messages);
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

    private List<MenuButton> drillButtons(Player viewer, String path, Supplier<Menu> caller) {
        SettingRegistry.DrillChildren children = SettingRegistry.children(path);
        List<MenuButton> buttons = new ArrayList<>();
        for (String section : children.sections()) {
            buttons.add(childButton(viewer, path + "." + section, caller));
        }
        for (String leaf : children.leaves()) {
            buttons.add(this.buttons.settingButton(viewer, path + "." + leaf, caller));
        }
        return buttons;
    }

    private int drillSize(String path) {
        SettingRegistry.DrillChildren children = SettingRegistry.children(path);
        return children.sections().size() + children.leaves().size();
    }

    private MenuButton childButton(Player viewer, String path, Supplier<Menu> caller) {
        if (SettingRegistry.isListPath(path)) {
            return listButton(viewer, path, caller);
        }
        Integer lobby = gui.overrideLobby(viewer);
        MenuButton button = new MenuButton(guiData.sectionItem(path),
                GuiTexts.name(messages, SettingButtons.prettify(leaf(path)),
                        SettingButtons.prettify(leaf(path))),
                GuiTexts.lore(messages, sectionLines(path, lobby)),
                lobby == null ? ModifiedGlow.section(config, path)
                        : overrides.hasOverridesBeneath(lobby, path),
                false, open(openViewer -> sectionMenu(openViewer, path, caller)));
        if (lobby == null) {
            return button;
        }
        return button.shiftAction(player -> clearCategoryConfirm(player, path, caller));
    }

    /** Section button lore: description when present, then the entry count. */
    private List<String> sectionLines(String path, Integer lobby) {
        List<String> lines = new ArrayList<>();
        String description = guiData.description(path);
        if (description != null && !description.isBlank()) {
            lines.add(description);
        }
        lines.add(countLine(drillSize(path)));
        if (lobby != null) {
            lines.add(text("override-shift-clear-category",
                    "Shift-left-click to clear overrides below"));
            if (overrides.hasOverridesBeneath(lobby, path)) {
                lines.add(overridesLine(lobby));
            }
        }
        return lines;
    }

    /** Shift-left on a category: confirm, then clear every override beneath. */
    private void clearCategoryConfirm(Player player, String path, Supplier<Menu> caller) {
        Integer lobby = gui.overrideLobby(player);
        if (lobby == null) {
            return;
        }
        int count = overrides.countOverrides(lobby, path);
        if (count == 0) {
            messages.message(player, "manhunt-gui.override-no-override");
            return;
        }
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages, messages
                        .string("manhunt-gui.override-clear-title", "Clear {count} Overrides?")
                        .replace("{count}", String.valueOf(count))),
                Material.PAPER, null,
                GuiTexts.lore(messages, List.of(path)),
                GuiTexts.name(messages, text("cancel", "Cancel"), "Cancel"),
                back -> gui.navigate(back, caller.get()),
                GuiTexts.name(messages, text("confirm", "Confirm"), "Confirm"),
                done -> {
                    int removed = overrides.clearOverrides(lobby, path);
                    feedback.overrideCleared(done, lobby, path, removed);
                    gui.navigate(done, caller.get());
                },
                caller);
        gui.navigate(player, confirm);
    }

    private MenuButton listButton(Player viewer, String path, Supplier<Menu> caller) {
        Integer lobby = gui.overrideLobby(viewer);
        int count = overrides.getStringList(lobby, path).size();
        List<String> lines = new ArrayList<>(List.of(countLine(count),
                text("list-hint-open", "Click to open"),
                text("setting-hint-reset", "Right-click to reset")));
        if (lobby != null) {
            lines.add(text("override-shift-clear",
                    "Shift-left-click to remove the override"));
            if (overrides.hasListOverride(lobby, path)) {
                lines.add(overridesLine(lobby));
            }
        }
        MenuButton button = new MenuButton(Material.PAPER,
                GuiTexts.name(messages, SettingButtons.prettify(leaf(path)),
                        SettingButtons.prettify(leaf(path))),
                GuiTexts.lore(messages, lines),
                lobby == null ? config.isListModified(path)
                        : overrides.hasListOverride(lobby, path),
                false, open(openViewer -> listMenu(openViewer, path, caller)),
                player -> listResetConfirm(player, path, caller));
        if (lobby == null) {
            return button;
        }
        return button.shiftAction(player -> clearListOverride(player, path));
    }

    /** Shift-left (and right-click) on a list: drop the list override. */
    private void clearListOverride(Player player, String path) {
        Integer lobby = gui.overrideLobby(player);
        if (lobby == null) {
            return;
        }
        int removed = overrides.clearOverrides(lobby, path);
        feedback.overrideCleared(player, lobby, path, removed);
    }

    private String overridesLine(int lobby) {
        return messages.string("manhunt-gui.override-for-lobby",
                "<red>Overrides for Lobby {lobby}").replace("{lobby}", String.valueOf(lobby));
    }

    private void listResetConfirm(Player player, String listPath, Supplier<Menu> caller) {
        Integer lobby = gui.overrideLobby(player);
        if (lobby != null) {
            clearListOverride(player, listPath);
            return;
        }
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

    private List<MenuButton> listButtons(Player viewer, String listPath, Supplier<Menu> caller) {
        List<String> entries = overrides.getStringList(gui.overrideLobby(viewer), listPath);
        int room = ScalingLayout.capacity(ScalingLayout.rowsFor(entries.size() + 1)) - 1;
        List<MenuButton> buttons = new ArrayList<>();
        for (int index = 0; index < entries.size() && index < room; index++) {
            buttons.add(indexButton(listPath, index, entries.get(index), caller));
        }
        buttons.add(AddStick.button(messages,
                text("list-add-name", "Add entry"),
                List.of(text("list-add-lore", "Click to append")),
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
                player -> deleteConfirm(player, listPath, index, value, caller)).silent();
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
                    Integer lobby = gui.overrideLobby(done);
                    ConfigService.SetOutcome outcome = lobby == null
                            ? config.listRemove(listPath, index)
                            : overrides.listRemoveOverride(lobby, listPath, index);
                    if (!outcome.ok()) {
                        feedback.failed(done, outcome);
                        sounds.playAngrySound(done);
                    } else if (lobby == null) {
                        feedback.listRemoved(done, listPath, outcome);
                    } else {
                        feedback.overrideListRemoved(done, lobby, listPath, outcome);
                    }
                    gui.navigate(done, caller.get());
                },
                caller);
        gui.navigate(player, confirm);
        sounds.playSound(player, "compass.left-click");
    }

    private Map<Integer, MenuButton> rootStatic(Player viewer) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(0, new MenuButton(Material.RECOVERY_COMPASS,
                GuiTexts.name(messages, text("to-support", "<#de7766>Need Help?"), "Need Help?"),
                GuiTexts.lore(messages, List.of(
                        text("to-support-lore", "Click to see our help channels"))),
                true, false,
                player -> {
                    player.performCommand("mh support");
                    player.closeInventory();
                }).silent());
        fixed.put(8, overrideButton(viewer));
        fixed.put(11, new MenuButton(Material.CHEST,
                GuiTexts.name(messages, text("to-settings", "Settings"), "Settings"),
                GuiTexts.lore(messages, List.of(
                        text("to-settings-lore", "Match, Compass, Players, Server"))),
                false, false, open(openViewer -> settingsMenu(openViewer))));
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
                false, false,
                open(openViewer -> modifiers.mainMenu(openViewer, () -> rootMenu(openViewer)))));
        return fixed;
    }

    /** Session indicator: glass globally, yellow once a lobby is set. */
    private MenuButton overrideButton(Player viewer) {
        Integer lobby = gui.overrideLobby(viewer);
        String mode = lobby == null ? "GLOBAL" : "Lobby " + lobby;
        List<String> lore = new ArrayList<>(List.of(
                text("override-hint-set", "Left-click to set lobby"),
                text("override-hint-global", "Right-click for global"),
                "",
                messages.string("manhunt-gui.override-current", "Current: <white>{mode}")
                        .replace("{mode}", mode)));
        return new MenuButton(lobby == null ? Material.GLASS : Material.YELLOW_STAINED_GLASS,
                GuiTexts.name(messages, text("override-name", "Lobby Overrides"),
                        "Lobby Overrides"),
                GuiTexts.lore(messages, lore), false, false,
                player -> openLobbySetter(player),
                player -> {
                    gui.clearOverrideLobby(player);
                    gui.navigate(player, rootMenu(player));
                }).silent();
    }

    /** Lobby setter dialog: a lobby id, or GLOBAL to leave the session. */
    private void openLobbySetter(Player player) {
        Integer lobby = gui.overrideLobby(player);
        dialogs.prompt(player,
                text("override-set-title", "Set Override Lobby"),
                lobby == null ? "GLOBAL" : String.valueOf(lobby),
                List.of(text("override-set-prompt", "Lobby id or GLOBAL")),
                raw -> submitLobby(player, raw == null ? "" : raw.trim()),
                () -> gui.navigate(player, rootMenu(player)));
    }

    /** Commits the setter: GLOBAL clears, an id points, junk errors. */
    private void submitLobby(Player player, String raw) {
        if (raw.equalsIgnoreCase("global")) {
            gui.clearOverrideLobby(player);
            gui.navigate(player, rootMenu(player));
            return;
        }
        OptionalInt lobby = OverrideService.parseLobbyId(raw);
        if (lobby.isEmpty()) {
            messages.message(player, "manhunt-gui.override-set-invalid",
                    Map.of("input", raw.isEmpty() ? " " : raw));
            sounds.playAngrySound(player);
            gui.navigate(player, rootMenu(player));
            return;
        }
        gui.setOverrideLobby(player, lobby.getAsInt());
        sounds.playNeutralSound(player);
        gui.navigate(player, rootMenu(player));
    }

    private List<MenuButton> categorySpecs(Player viewer) {
        List<MenuButton> specs = new ArrayList<>();
        Integer lobby = gui.overrideLobby(viewer);
        for (String category : new String[]{"match", "compass", "players", "server"}) {
            String path = "settings." + category;
            MenuButton button = new MenuButton(guiData.categoryItem(category),
                    GuiTexts.name(messages, SettingButtons.prettify(category),
                            SettingButtons.prettify(category)),
                    GuiTexts.lore(messages, sectionLines(path, lobby)),
                    lobby == null ? ModifiedGlow.section(config, path)
                            : overrides.hasOverridesBeneath(lobby, path),
                    false,
                    open(openViewer -> sectionMenu(openViewer, path,
                            () -> settingsMenu(openViewer))));
            if (lobby == null) {
                specs.add(button);
            } else {
                specs.add(button.shiftAction(player ->
                        clearCategoryConfirm(player, path, () -> settingsMenu(player))));
            }
        }
        return specs;
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
                player -> gui.back(player, self[0]));
    }

    private Consumer<Player> open(Function<Player, Menu> menu) {
        return player -> gui.navigate(player, menu.apply(player));
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
