package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.MatchConfig;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.config.SignalInterferenceSettings;
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
import com.jruk8.jmanhunt.gui.TwinPanel;
import com.jruk8.jmanhunt.gui.dialog.ModifierDialog;
import com.jruk8.jmanhunt.gui.dialog.SettingDialog;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.ManhuntGuiMessages;
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
    private final ManhuntGuiMessages manhuntGui;
    private final SoundService sounds;
    private final GuiService gui;
    private final SettingDialog dialogs;
    private final SettingFeedback feedback;
    private final StatsManager stats;
    private final ModifierMenus modifiers;
    private final ModifierDialog modifierDialogs;
    private final SettingButtons buttons;

    public ManhuntMenus(ConfigService config, OverrideService overrides, GuiConfig guiData,
            MessageService messages, ManhuntGuiMessages manhuntGui, SoundService sounds,
            GuiService gui, SettingDialog dialogs, SettingFeedback feedback, StatsManager stats,
            ModifierMenus modifiers, ModifierDialog modifierDialogs) {
        this.config = config;
        this.overrides = overrides;
        this.guiData = guiData;
        this.messages = messages;
        this.manhuntGui = manhuntGui;
        this.sounds = sounds;
        this.gui = gui;
        this.dialogs = dialogs;
        this.feedback = feedback;
        this.stats = stats;
        this.modifiers = modifiers;
        this.modifierDialogs = modifierDialogs;
        this.buttons = new SettingButtons(config, overrides, guiData, messages, manhuntGui,
                dialogs, gui, feedback, sounds);
    }

    /** 27-slot root with support, settings, history, modifiers, and override links. */
    public Menu rootMenu(Player viewer) {
        return new Menu(title(manhuntGui.getTitleRoot()),
                MenuLayout.parse("#########", "##s#h#m##", "#########"),
                () -> rootStatic(viewer), List::of, null);
    }

    /** 27-slot settings root: General and Advanced panel links. */
    public Menu settingsMenu(Player viewer) {
        return settingsMenu(viewer, () -> rootMenu(viewer));
    }

    /** Settings root with an explicit parent. */
    private Menu settingsMenu(Player viewer, Supplier<Menu> parent) {
        return TwinPanel.menu(title(manhuntGui.getTitleSettings()),
                generalButton(viewer), advancedButton(viewer), gui,
                GuiTexts.name(messages, manhuntGui.getBack(), "Back"), parent);
    }

    /** General quad: the four everyday setting categories. */
    private Menu generalMenu(Player viewer, Supplier<Menu> parent) {
        return QuadPanel.menu(title(manhuntGui.getTitleSettingsGeneral()),
                categorySpecs(viewer, parent), gui,
                GuiTexts.name(messages, manhuntGui.getBack(), "Back"), parent);
    }

    /** Advanced quad: match controls, world engine, lobbies, misc. */
    private Menu advancedMenu(Player viewer, Supplier<Menu> parent) {
        return QuadPanel.menu(title(manhuntGui.getTitleSettingsAdvanced()),
                advancedSpecs(viewer, parent), gui,
                GuiTexts.name(messages, manhuntGui.getBack(), "Back"), parent);
    }

    /** TwinPanel left: General opens the everyday quad. */
    private MenuButton generalButton(Player viewer) {
        Integer lobby = gui.overrideLobby(viewer);
        return new MenuButton(Material.CHEST,
                GuiTexts.name(messages, manhuntGui.getToGeneral(),
                        "General Settings"),
                GuiTexts.lore(messages, List.of(
                        manhuntGui.getToGeneralLore())),
                lobby == null ? ModifiedGlow.section(config, "settings")
                        : overrides.hasOverridesBeneath(lobby, "settings"),
                false,
                open(openViewer -> generalMenu(openViewer, () -> settingsMenu(openViewer))));
    }

    /** TwinPanel right: Advanced opens the power-user quad. */
    private MenuButton advancedButton(Player viewer) {
        Integer lobby = gui.overrideLobby(viewer);
        boolean changed = lobby == null
                ? ModifiedGlow.section(config, "advanced")
                        || ModifiedGlow.section(config, "world-engine")
                : overrides.hasOverridesBeneath(lobby, "advanced")
                        || overrides.hasOverridesBeneath(lobby, "world-engine");
        return new MenuButton(Material.ANVIL,
                GuiTexts.name(messages, manhuntGui.getToAdvanced(),
                        "Advanced Settings"),
                GuiTexts.lore(messages, List.of(
                        manhuntGui.getToAdvancedLore())),
                changed, false,
                open(openViewer -> advancedMenu(openViewer, () -> settingsMenu(openViewer))));
    }

    /** Advanced quad specs: one drill button per advanced section. */
    private List<MenuButton> advancedSpecs(Player viewer, Supplier<Menu> parent) {
        List<MenuButton> specs = new ArrayList<>();
        Supplier<Menu> caller = () -> advancedMenu(viewer, parent);
        for (String path : new String[]{"advanced.advanced-match-controls", "world-engine",
                "advanced.lobbies", "advanced.misc"}) {
            specs.add(childButton(viewer, path, caller));
        }
        return specs;
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
                GuiTexts.title(messages, manhuntGui.getDialogTitleEdit()
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
                title(manhuntGui.getSetupFirstTitle()),
                Material.WRITABLE_BOOK, null,
                GuiTexts.lore(messages, List.of(
                        manhuntGui.getSetupFirstLine1(),
                        manhuntGui.getSetupFirstLine2())),
                GuiTexts.name(messages,
                        manhuntGui.getSetupFirstCancel(),
                        "Skip forever"),
                onCancel,
                GuiTexts.name(messages,
                        manhuntGui.getSetupFirstConfirm(), "Start setup"),
                onConfirm,
                null);
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
            return ScrollList.menu(effectiveTitle, content, parent, gui, messages, manhuntGui);
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
            lines.add(manhuntGui.getOverrideShiftClearCategory());
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
            messages.messageRaw(player, manhuntGui.getOverrideNoOverride());
            return;
        }
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages, manhuntGui.getOverrideClearTitle()
                        .replace("{count}", String.valueOf(count))),
                Material.PAPER, null,
                GuiTexts.lore(messages, List.of(path)),
                GuiTexts.name(messages, manhuntGui.getCancel(), "Cancel"),
                back -> gui.navigate(back, caller.get()),
                GuiTexts.name(messages, manhuntGui.getConfirm(), "Confirm"),
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
                manhuntGui.getListHintOpen(),
                manhuntGui.getSettingHintReset()));
        if (lobby != null) {
            lines.add(manhuntGui.getOverrideShiftClear());
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
                false, openList(player -> listMenu(player, path, caller), path, caller),
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
        return manhuntGui.getOverrideForLobby().replace("{lobby}", String.valueOf(lobby));
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
            messages.messageRaw(player, manhuntGui.getSettingAlreadyDefault());
            return;
        }
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages, manhuntGui.getSettingResetTitle()
                        .replace("{name}", SettingButtons.prettify(leaf(listPath)))),
                Material.PAPER, null,
                GuiTexts.lore(messages, List.of(countLine(config.getStringList(listPath).size()))),
                GuiTexts.name(messages, manhuntGui.getCancel(), "Cancel"),
                back -> gui.navigate(back, caller.get()),
                GuiTexts.name(messages, manhuntGui.getConfirm(), "Confirm"),
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
                manhuntGui.getListAddName(),
                List.of(manhuntGui.getListAddLore()),
                player -> dialogs.openListAppend(player, listPath,
                        GuiTexts.title(messages, addTitle()), caller)));
        return buttons;
    }

    private MenuButton indexButton(String listPath, int index, String value, Supplier<Menu> caller) {
        String name = manhuntGui.getListEntryName().replace("{index}",
                String.valueOf(index));
        List<String> lore = List.of(
                MiniMessage.miniMessage().escapeTags(GuiTexts.truncate(value, 60)),
                manhuntGui.getListHintEdit(),
                manhuntGui.getListHintDelete());
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
                        .escapeTags(GuiTexts.truncate(value, 60)))),
                GuiTexts.name(messages, manhuntGui.getCancel(), "Cancel"),
                back -> gui.navigate(back, caller.get()),
                GuiTexts.name(messages, manhuntGui.getConfirm(), "Confirm"),
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
                GuiTexts.name(messages, manhuntGui.getToSupport(), "Need Help?"),
                GuiTexts.lore(messages, List.of(
                        manhuntGui.getToSupportLore())),
                true, false,
                player -> {
                    player.performCommand("mh support");
                    player.closeInventory();
                }).silent());
        fixed.put(8, overrideButton(viewer));
        fixed.put(11, new MenuButton(Material.CHEST,
                GuiTexts.name(messages, manhuntGui.getToSettings(), "Settings"),
                GuiTexts.lore(messages, List.of(
                        manhuntGui.getToSettingsLore())),
                false, false, open(openViewer -> settingsMenu(openViewer))));
        // Lifetime stats live on the book itself: hover to read, no
        // separate menu. The action stays null so clicks pass through
        // silently like any other display line.
        fixed.put(13, new MenuButton(Material.WRITTEN_BOOK,
                GuiTexts.name(messages, manhuntGui.getToHistory(), "History"),
                GuiTexts.lore(messages, historyLines()),
                false, false, null));
        fixed.put(15, new MenuButton(Material.BOOK,
                GuiTexts.name(messages, manhuntGui.getToModifiers(), "Modifiers"),
                GuiTexts.lore(messages, List.of(
                        manhuntGui.getToModifiersLore())),
                false, false,
                open(openViewer -> modifiers.mainMenu(openViewer, () -> rootMenu(openViewer)))));
        return fixed;
    }

    /** Session indicator: glass globally, yellow once a lobby is set. */
    private MenuButton overrideButton(Player viewer) {
        Integer lobby = gui.overrideLobby(viewer);
        String mode = lobby == null ? "GLOBAL" : "Lobby " + lobby;
        List<String> lore = new ArrayList<>(List.of(
                manhuntGui.getOverrideHintSet(),
                manhuntGui.getOverrideHintGlobal(),
                "",
                manhuntGui.getOverrideCurrent()
                        .replace("{mode}", mode)));
        return new MenuButton(lobby == null ? Material.GLASS : Material.YELLOW_STAINED_GLASS,
                GuiTexts.name(messages, manhuntGui.getOverrideName(),
                        "Lobby Overrides"),
                GuiTexts.lore(messages, lore), false, false,
                player -> openLobbySetter(player),
                player -> {
                    gui.clearOverrideLobby(player);
                    sounds.playNeutralSound(player);
                    gui.navigate(player, rootMenu(player));
                }).silent();
    }

    /** Lobby setter dialog: a lobby id, or GLOBAL to leave the session. */
    private void openLobbySetter(Player player) {
        Integer lobby = gui.overrideLobby(player);
        dialogs.prompt(player,
                manhuntGui.getOverrideSetTitle(),
                lobby == null ? "GLOBAL" : String.valueOf(lobby),
                List.of(manhuntGui.getOverrideSetPrompt()),
                raw -> submitLobby(player, raw == null ? "" : raw.trim()),
                () -> gui.navigate(player, rootMenu(player)));
    }

    /** Commits the setter: GLOBAL clears, an id points, junk errors. */
    private void submitLobby(Player player, String raw) {
        if (raw.equalsIgnoreCase("global")) {
            gui.clearOverrideLobby(player);
            sounds.playNeutralSound(player);
            gui.navigate(player, rootMenu(player));
            return;
        }
        OptionalInt lobby = OverrideService.parseLobbyId(raw);
        if (lobby.isEmpty()) {
            messages.messageRaw(player, manhuntGui.getOverrideSetInvalid(),
                    Map.of("input", raw.isEmpty() ? " " : raw));
            sounds.playAngrySound(player);
            gui.navigate(player, rootMenu(player));
            return;
        }
        gui.setOverrideLobby(player, lobby.getAsInt());
        sounds.playNeutralSound(player);
        gui.navigate(player, rootMenu(player));
    }

    private List<MenuButton> categorySpecs(Player viewer, Supplier<Menu> parent) {
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
                            () -> generalMenu(openViewer, parent))));
            if (lobby == null) {
                specs.add(button);
            } else {
                specs.add(button.shiftAction(player ->
                        clearCategoryConfirm(player, path, () -> generalMenu(player, parent))));
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
                GuiTexts.name(messages, manhuntGui.getBack(), "Back"),
                null, false, false,
                player -> gui.back(player, self[0]));
    }

    private Consumer<Player> open(Function<Player, Menu> menu) {
        return player -> gui.navigate(player, menu.apply(player));
    }

    /**
     * List open action: the game-rules and weather enum arrays open
     * checkbox dialogs, every other list opens the index-based list
     * menu.
     */
    private Consumer<Player> openList(Function<Player, Menu> menu, String path,
            Supplier<Menu> caller) {
        if (MatchConfig.GameRules.RULES_PATH.equals(path)) {
            return player -> {
                Integer lobby = gui.overrideLobby(player);
                List<String> current = overrides.getStringList(lobby, path);
                modifierDialogs.openGameRules(player, current,
                        checked -> applyChecklist(player, lobby, path,
                                new ArrayList<>(checked), caller),
                        () -> gui.navigate(player, caller.get()));
            };
        }
        if (SignalInterferenceSettings.Weather.INTERFERE_DURING_PATH.equals(path)) {
            return player -> {
                Integer lobby = gui.overrideLobby(player);
                List<String> current = overrides.getStringList(lobby, path);
                modifierDialogs.openInterfereDuring(player, current,
                        checked -> applyChecklist(player, lobby, path,
                                new ArrayList<>(checked), caller),
                        () -> gui.navigate(player, caller.get()));
            };
        }
        return open(menu);
    }

    /** Persists a checked enum list, globally or as a lobby override. */
    private void applyChecklist(Player player, Integer lobby, String path,
            List<String> checked, Supplier<Menu> caller) {
        ConfigService.SetOutcome outcome = lobby == null
                ? config.setList(path, checked)
                : overrides.setListOverride(lobby, path, checked);
        if (!outcome.ok()) {
            feedback.failed(player, outcome);
            sounds.playAngrySound(player);
        }
        gui.navigate(player, caller.get());
    }

    private String historyLine(String key, String value) {
        String line = switch (key) {
            case "matches" -> manhuntGui.getHistoryLineMatches();
            case "kills" -> manhuntGui.getHistoryLineKills();
            case "hunter-kills" -> manhuntGui.getHistoryLineHunterKills();
            case "speedrunner-kills" -> manhuntGui.getHistoryLineSpeedrunnerKills();
            case "hunter-wins" -> manhuntGui.getHistoryLineHunterWins();
            case "speedrunner-wins" -> manhuntGui.getHistoryLineSpeedrunnerWins();
            case "damage" -> manhuntGui.getHistoryLineDamage();
            case "playtime" -> manhuntGui.getHistoryLinePlaytime();
            default -> key + ": {value}";
        };
        return line.replace("{value}", value);
    }

    private String countLine(int count) {
        return manhuntGui.getCategoryLore()
                .replace("{count}", String.valueOf(count));
    }

    private String editTitle(int index) {
        return manhuntGui.getDialogTitleEditEntry()
                .replace("{index}", String.valueOf(index));
    }

    private String addTitle() {
        return manhuntGui.getDialogTitleAddEntry();
    }

    private String deleteTitle(int index) {
        return manhuntGui.getDialogTitleDeleteEntry()
                .replace("{index}", String.valueOf(index));
    }

    private Component title(String line) {
        return GuiTexts.title(messages, line);
    }

    private Component sectionTitle(String path) {
        String[] parts = path.split("\\.");
        if (parts.length == 2 && !parts[0].equals("advanced")) {
            return GuiTexts.title(messages, manhuntGui.getTitleCategory()
                    .replace("{name}", SettingButtons.prettify(parts[1])));
        }
        return GuiTexts.title(messages, SettingButtons.prettify(parts[parts.length - 1]));
    }

    private static String leaf(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
