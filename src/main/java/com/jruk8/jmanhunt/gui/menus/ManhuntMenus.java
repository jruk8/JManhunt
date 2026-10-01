package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.MatchConfig;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.config.SignalInterferenceSettings;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
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
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
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
    /** Dialogs, stats, modifier menus, and modifier dialogs. */
    public record ManhuntDeps(SettingDialog dialogs, StatsManager stats,
            ModifierMenus modifiers, ModifierDialog modifierDialogs) {
    }

    private final SettingDialogs.SettingStores stores;
    private final SettingDialogs.SettingTexts texts;
    private final SettingDialogs.SettingUi ui;
    private final ManhuntDeps deps;
    private final SettingButtons buttons;

    public ManhuntMenus(SettingDialogs.SettingStores stores, SettingDialogs.SettingTexts texts,
            SettingDialogs.SettingUi ui, ManhuntDeps deps) {
        this.stores = stores;
        this.texts = texts;
        this.ui = ui;
        this.deps = deps;
        this.buttons = new SettingButtons(stores, texts, ui, deps.dialogs());
    }

    /** 27-slot root with support, settings, history, modifiers, and override links. */
    public Menu rootMenu(Player viewer) {
        return new Menu(title(texts.guiTexts().getTitleRoot()),
                MenuLayout.parse("#########", "##s#h#m##", "#########"),
                () -> rootStatic(viewer), List::of, null);
    }

    /** 27-slot settings root: General and Advanced panel links. */
    public Menu settingsMenu(Player viewer) {
        return settingsMenu(viewer, () -> rootMenu(viewer));
    }

    /** Settings root with an explicit parent. */
    private Menu settingsMenu(Player viewer, Supplier<Menu> parent) {
        return TwinPanel.menu(title(texts.guiTexts().getTitleSettings()),
                generalButton(viewer), advancedButton(viewer), ui.gui(),
                GuiTexts.name(texts.messages(), texts.guiTexts().getBack(), "Back"), parent);
    }

    /** General quad: the four everyday setting categories. */
    private Menu generalMenu(Player viewer, Supplier<Menu> parent) {
        return QuadPanel.menu(title(texts.guiTexts().getTitleSettingsGeneral()),
                categorySpecs(viewer, parent), ui.gui(),
                GuiTexts.name(texts.messages(), texts.guiTexts().getBack(), "Back"), parent);
    }

    /** Advanced quad: match controls, world engine, lobbies, misc. */
    private Menu advancedMenu(Player viewer, Supplier<Menu> parent) {
        return QuadPanel.menu(title(texts.guiTexts().getTitleSettingsAdvanced()),
                advancedSpecs(viewer, parent), ui.gui(),
                GuiTexts.name(texts.messages(), texts.guiTexts().getBack(), "Back"), parent);
    }

    /** TwinPanel left: General opens the everyday quad. */
    private MenuButton generalButton(Player viewer) {
        Integer lobby = ui.gui().overrideLobby(viewer);
        return new MenuButton(new MenuButton.Spec(Material.CHEST,
                GuiTexts.name(texts.messages(), texts.guiTexts().getToGeneral(),
                        "General Settings"),
                GuiTexts.lore(texts.messages(), List.of(
                        texts.guiTexts().getToGeneralLore())),
                lobby == null ? ModifiedGlow.section(stores.config(), "settings")
                        : stores.overrides().hasOverridesBeneath(lobby, "settings"),
                false,
                open(openViewer -> generalMenu(openViewer, () -> settingsMenu(openViewer))), null, null,
                        MenuButton.SoundPolicy.CLICK, null));
    }

    /** TwinPanel right: Advanced opens the power-user quad. */
    private MenuButton advancedButton(Player viewer) {
        Integer lobby = ui.gui().overrideLobby(viewer);
        boolean changed = lobby == null
                ? ModifiedGlow.section(stores.config(), "advanced")
                        || ModifiedGlow.section(stores.config(), "world-engine")
                : stores.overrides().hasOverridesBeneath(lobby, "advanced")
                        || stores.overrides().hasOverridesBeneath(lobby, "world-engine");
        return new MenuButton(new MenuButton.Spec(Material.ANVIL,
                GuiTexts.name(texts.messages(), texts.guiTexts().getToAdvanced(),
                        "Advanced Settings"),
                GuiTexts.lore(texts.messages(), List.of(
                        texts.guiTexts().getToAdvancedLore())),
                changed, false,
                open(openViewer -> advancedMenu(openViewer, () -> settingsMenu(openViewer))), null,
                        null, MenuButton.SoundPolicy.CLICK, null));
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
                GuiTexts.title(texts.messages(), texts.guiTexts().getDialogTitleEdit()
                        .replace("{name}", SettingButtons.prettify(leaf(listPath)))),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        backButton(self)),
                content::get, parent);
        return self[0];
    }

    /** Lifetime stat lines for the root history book. Null-safe for tests. */
    private List<String> historyLines() {
        HistoryPlaceholders.Totals totals = deps.stats().lifetime();
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
                title(texts.guiTexts().getSetupFirstTitle()),
                Material.WRITABLE_BOOK, null,
                GuiTexts.lore(texts.messages(), List.of(
                        texts.guiTexts().getSetupFirstLine1(),
                        texts.guiTexts().getSetupFirstLine2())),
                GuiTexts.name(texts.messages(),
                        texts.guiTexts().getSetupFirstCancel(),
                        "Skip forever"),
                onCancel,
                GuiTexts.name(texts.messages(),
                        texts.guiTexts().getSetupFirstConfirm(), "Start setup"),
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
            return ScrollList.menu(effectiveTitle, content, parent, ui.gui(), texts.messages(), texts.guiTexts());
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
        Integer lobby = ui.gui().overrideLobby(viewer);
        MenuButton button = new MenuButton(new MenuButton.Spec(ui.guiData().sectionItem(path),
                GuiTexts.name(texts.messages(), SettingButtons.prettify(leaf(path)),
                        SettingButtons.prettify(leaf(path))),
                GuiTexts.lore(texts.messages(), sectionLines(path, lobby)),
                lobby == null ? ModifiedGlow.section(stores.config(), path)
                        : stores.overrides().hasOverridesBeneath(lobby, path),
                false, open(openViewer -> sectionMenu(openViewer, path, caller)), null, null,
                        MenuButton.SoundPolicy.CLICK, null));
        if (lobby == null) {
            return button;
        }
        return button.shiftAction(player -> clearCategoryConfirm(player, path, caller));
    }

    /** Section button lore: description when present, then the entry count. */
    private List<String> sectionLines(String path, Integer lobby) {
        List<String> lines = new ArrayList<>();
        String description = ui.guiData().description(path);
        if (description != null && !description.isBlank()) {
            lines.add(description);
        }
        lines.add(countLine(drillSize(path)));
        if (lobby != null) {
            lines.add(texts.guiTexts().getOverrideShiftClearCategory());
            if (stores.overrides().hasOverridesBeneath(lobby, path)) {
                lines.add(overridesLine(lobby));
            }
        }
        return lines;
    }

    /** Shift-left on a category: confirm, then clear every override beneath. */
    private void clearCategoryConfirm(Player player, String path, Supplier<Menu> caller) {
        Integer lobby = ui.gui().overrideLobby(player);
        if (lobby == null) {
            return;
        }
        int count = stores.overrides().countOverrides(lobby, path);
        if (count == 0) {
            texts.messages().messageRaw(player, texts.guiTexts().getOverrideNoOverride());
            return;
        }
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(texts.messages(), texts.guiTexts().getOverrideClearTitle()
                        .replace("{count}", String.valueOf(count))),
                Material.PAPER, null,
                GuiTexts.lore(texts.messages(), List.of(path)),
                GuiTexts.name(texts.messages(), texts.guiTexts().getCancel(), "Cancel"),
                back -> ui.gui().navigate(back, caller.get()),
                GuiTexts.name(texts.messages(), texts.guiTexts().getConfirm(), "Confirm"),
                done -> {
                    int removed = stores.overrides().clearOverrides(lobby, path);
                    ui.feedback().overrideCleared(done, lobby, path, removed);
                    ui.gui().navigate(done, caller.get());
                },
                caller);
        ui.gui().navigate(player, confirm);
    }

    private MenuButton listButton(Player viewer, String path, Supplier<Menu> caller) {
        Integer lobby = ui.gui().overrideLobby(viewer);
        int count = stores.overrides().getStringList(lobby, path).size();
        List<String> lines = new ArrayList<>(List.of(countLine(count),
                texts.guiTexts().getListHintOpen(),
                texts.guiTexts().getSettingHintReset()));
        if (lobby != null) {
            lines.add(texts.guiTexts().getOverrideShiftClear());
            if (stores.overrides().hasListOverride(lobby, path)) {
                lines.add(overridesLine(lobby));
            }
        }
        MenuButton button = new MenuButton(new MenuButton.Spec(Material.PAPER,
                GuiTexts.name(texts.messages(), SettingButtons.prettify(leaf(path)),
                        SettingButtons.prettify(leaf(path))),
                GuiTexts.lore(texts.messages(), lines),
                lobby == null ? stores.config().isListModified(path)
                        : stores.overrides().hasListOverride(lobby, path),
                false, openList(player -> listMenu(player, path, caller), path, caller),
                player -> listResetConfirm(player, path, caller), null, MenuButton.SoundPolicy.CLICK, null));
        if (lobby == null) {
            return button;
        }
        return button.shiftAction(player -> clearListOverride(player, path));
    }

    /** Shift-left (and right-click) on a list: drop the list override. */
    private void clearListOverride(Player player, String path) {
        Integer lobby = ui.gui().overrideLobby(player);
        if (lobby == null) {
            return;
        }
        int removed = stores.overrides().clearOverrides(lobby, path);
        ui.feedback().overrideCleared(player, lobby, path, removed);
    }

    private String overridesLine(int lobby) {
        return texts.guiTexts().getOverrideForLobby().replace("{lobby}", String.valueOf(lobby));
    }

    private void listResetConfirm(Player player, String listPath, Supplier<Menu> caller) {
        Integer lobby = ui.gui().overrideLobby(player);
        if (lobby != null) {
            clearListOverride(player, listPath);
            return;
        }
        // Already at default: resetting would be a no-op, so say so in
        // chat instead of opening a confirm panel for nothing.
        if (!stores.config().isListModified(listPath)) {
            texts.messages().messageRaw(player, texts.guiTexts().getSettingAlreadyDefault());
            return;
        }
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(texts.messages(), texts.guiTexts().getSettingResetTitle()
                        .replace("{name}", SettingButtons.prettify(leaf(listPath)))),
                Material.PAPER, null,
                GuiTexts.lore(texts.messages(), List.of(countLine(stores.config().getStringList(listPath).size()))),
                GuiTexts.name(texts.messages(), texts.guiTexts().getCancel(), "Cancel"),
                back -> ui.gui().navigate(back, caller.get()),
                GuiTexts.name(texts.messages(), texts.guiTexts().getConfirm(), "Confirm"),
                done -> {
                    ConfigService.SetOutcome outcome = stores.config().listReset(listPath);
                    if (!outcome.ok()) {
                        ui.feedback().failed(done, outcome);
                        texts.sounds().playAngrySound(done);
                    } else {
                        ui.feedback().listReset(done, listPath, outcome);
                    }
                    ui.gui().navigate(done, caller.get());
                },
                caller);
        ui.gui().navigate(player, confirm);
    }

    private List<MenuButton> listButtons(Player viewer, String listPath, Supplier<Menu> caller) {
        List<String> entries = stores.overrides().getStringList(ui.gui().overrideLobby(viewer), listPath);
        int room = ScalingLayout.capacity(ScalingLayout.rowsFor(entries.size() + 1)) - 1;
        List<MenuButton> buttons = new ArrayList<>();
        for (int index = 0; index < entries.size() && index < room; index++) {
            buttons.add(indexButton(listPath, index, entries.get(index), caller));
        }
        buttons.add(AddStick.button(texts.messages(),
                texts.guiTexts().getListAddName(),
                List.of(texts.guiTexts().getListAddLore()),
                player -> deps.dialogs().openListAppend(player, listPath,
                        GuiTexts.title(texts.messages(), addTitle()), caller)));
        return buttons;
    }

    private MenuButton indexButton(String listPath, int index, String value, Supplier<Menu> caller) {
        String name = texts.guiTexts().getListEntryName().replace("{index}",
                String.valueOf(index));
        List<String> lore = List.of(
                MiniMessage.miniMessage().escapeTags(GuiTexts.truncate(value, 60)),
                texts.guiTexts().getListHintEdit(),
                texts.guiTexts().getListHintDelete());
        return new MenuButton(new MenuButton.Spec(Material.PAPER, GuiTexts.name(texts.messages(), name, name),
                GuiTexts.lore(texts.messages(), lore), false, false,
                player -> deps.dialogs().openListEntry(player, listPath, index,
                        GuiTexts.title(texts.messages(), editTitle(index)), caller),
                player -> deleteConfirm(player, listPath, index, value, caller), null,
                        MenuButton.SoundPolicy.CLICK, null)).silent();
    }

    private void deleteConfirm(Player player, String listPath, int index,
            String value, Supplier<Menu> caller) {
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(texts.messages(), deleteTitle(index)),
                Material.PAPER, null,
                GuiTexts.lore(texts.messages(), List.of(MiniMessage.miniMessage()
                        .escapeTags(GuiTexts.truncate(value, 60)))),
                GuiTexts.name(texts.messages(), texts.guiTexts().getCancel(), "Cancel"),
                back -> ui.gui().navigate(back, caller.get()),
                GuiTexts.name(texts.messages(), texts.guiTexts().getConfirm(), "Confirm"),
                done -> {
                    Integer lobby = ui.gui().overrideLobby(done);
                    ConfigService.SetOutcome outcome = lobby == null
                            ? stores.config().listRemove(listPath, index)
                            : stores.overrides().listRemoveOverride(lobby, listPath, index);
                    if (!outcome.ok()) {
                        ui.feedback().failed(done, outcome);
                        texts.sounds().playAngrySound(done);
                    } else if (lobby == null) {
                        ui.feedback().listRemoved(done, listPath, outcome);
                    } else {
                        ui.feedback().overrideListRemoved(done, lobby, listPath, outcome);
                    }
                    ui.gui().navigate(done, caller.get());
                },
                caller);
        ui.gui().navigate(player, confirm);
        texts.sounds().playSound(player, "compass.left-click");
    }

    private Map<Integer, MenuButton> rootStatic(Player viewer) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(0, new MenuButton(new MenuButton.Spec(Material.RECOVERY_COMPASS,
                GuiTexts.name(texts.messages(), texts.guiTexts().getToSupport(), "Need Help?"),
                GuiTexts.lore(texts.messages(), List.of(
                        texts.guiTexts().getToSupportLore())),
                true, false,
                player -> {
                    player.performCommand("mh support");
                    player.closeInventory();
                }, null, null, MenuButton.SoundPolicy.CLICK, null)).silent());
        fixed.put(8, overrideButton(viewer));
        fixed.put(11, new MenuButton(new MenuButton.Spec(Material.CHEST,
                GuiTexts.name(texts.messages(), texts.guiTexts().getToSettings(), "Settings"),
                GuiTexts.lore(texts.messages(), List.of(
                        texts.guiTexts().getToSettingsLore())),
                false, false, open(openViewer -> settingsMenu(openViewer)), null, null,
                        MenuButton.SoundPolicy.CLICK, null)));
        // Lifetime stats live on the book itself: hover to read, no
        // separate menu. The action stays null so clicks pass through
        // silently like any other display line.
        fixed.put(13, new MenuButton(new MenuButton.Spec(Material.WRITTEN_BOOK,
                GuiTexts.name(texts.messages(), texts.guiTexts().getToHistory(), "History"),
                GuiTexts.lore(texts.messages(), historyLines()),
                false, false, null, null, null, MenuButton.SoundPolicy.CLICK, null)));
        fixed.put(15, new MenuButton(new MenuButton.Spec(Material.BOOK,
                GuiTexts.name(texts.messages(), texts.guiTexts().getToModifiers(), "Modifiers"),
                GuiTexts.lore(texts.messages(), List.of(
                        texts.guiTexts().getToModifiersLore())),
                false, false,
                open(openViewer -> deps.modifiers().mainMenu(openViewer, () -> rootMenu(openViewer))),
                        null, null, MenuButton.SoundPolicy.CLICK, null)));
        return fixed;
    }

    /** Session indicator: glass globally, yellow once a lobby is set. */
    private MenuButton overrideButton(Player viewer) {
        Integer lobby = ui.gui().overrideLobby(viewer);
        String mode = lobby == null ? "GLOBAL" : "Lobby " + lobby;
        List<String> lore = new ArrayList<>(List.of(
                texts.guiTexts().getOverrideHintSet(),
                texts.guiTexts().getOverrideHintGlobal(),
                "",
                texts.guiTexts().getOverrideCurrent()
                        .replace("{mode}", mode)));
        return new MenuButton(new MenuButton.Spec(lobby == null ? Material.GLASS : Material.YELLOW_STAINED_GLASS,
                GuiTexts.name(texts.messages(), texts.guiTexts().getOverrideName(),
                        "Lobby Overrides"),
                GuiTexts.lore(texts.messages(), lore), false, false,
                player -> openLobbySetter(player),
                player -> {
                    ui.gui().clearOverrideLobby(player);
                    texts.sounds().playNeutralSound(player);
                    ui.gui().navigate(player, rootMenu(player));
                }, null, MenuButton.SoundPolicy.CLICK, null)).silent();
    }

    /** Lobby setter dialog: a lobby id, or GLOBAL to leave the session. */
    private void openLobbySetter(Player player) {
        Integer lobby = ui.gui().overrideLobby(player);
        deps.dialogs().prompt(player,
                texts.guiTexts().getOverrideSetTitle(),
                lobby == null ? "GLOBAL" : String.valueOf(lobby),
                List.of(texts.guiTexts().getOverrideSetPrompt()),
                raw -> submitLobby(player, raw == null ? "" : raw.trim()),
                () -> ui.gui().navigate(player, rootMenu(player)));
    }

    /** Commits the setter: GLOBAL clears, an id points, junk errors. */
    private void submitLobby(Player player, String raw) {
        if (raw.equalsIgnoreCase("global")) {
            ui.gui().clearOverrideLobby(player);
            texts.sounds().playNeutralSound(player);
            ui.gui().navigate(player, rootMenu(player));
            return;
        }
        OptionalInt lobby = OverrideService.parseLobbyId(raw);
        if (lobby.isEmpty()) {
            texts.messages().messageRaw(player, texts.guiTexts().getOverrideSetInvalid(),
                    Map.of("input", raw.isEmpty() ? " " : raw));
            texts.sounds().playAngrySound(player);
            ui.gui().navigate(player, rootMenu(player));
            return;
        }
        ui.gui().setOverrideLobby(player, lobby.getAsInt());
        texts.sounds().playNeutralSound(player);
        ui.gui().navigate(player, rootMenu(player));
    }

    private List<MenuButton> categorySpecs(Player viewer, Supplier<Menu> parent) {
        List<MenuButton> specs = new ArrayList<>();
        Integer lobby = ui.gui().overrideLobby(viewer);
        for (String category : new String[]{"match", "compass", "players", "server"}) {
            String path = "settings." + category;
            MenuButton button = new MenuButton(new MenuButton.Spec(ui.guiData().categoryItem(category),
                    GuiTexts.name(texts.messages(), SettingButtons.prettify(category),
                            SettingButtons.prettify(category)),
                    GuiTexts.lore(texts.messages(), sectionLines(path, lobby)),
                    lobby == null ? ModifiedGlow.section(stores.config(), path)
                            : stores.overrides().hasOverridesBeneath(lobby, path),
                    false,
                    open(openViewer -> sectionMenu(openViewer, path,
                            () -> generalMenu(openViewer, parent))), null, null, MenuButton.SoundPolicy.CLICK, null));
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
        return new MenuButton(new MenuButton.Spec(Material.PAPER,
                GuiTexts.name(texts.messages(), texts.guiTexts().getBack(), "Back"),
                null, false, false,
                player -> ui.gui().back(player, self[0]), null, null, MenuButton.SoundPolicy.CLICK, null));
    }

    private Consumer<Player> open(Function<Player, Menu> menu) {
        return player -> ui.gui().navigate(player, menu.apply(player));
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
                Integer lobby = ui.gui().overrideLobby(player);
                List<String> current = stores.overrides().getStringList(lobby, path);
                deps.modifierDialogs().openGameRules(player, current,
                        checked -> applyChecklist(player, lobby, path,
                                new ArrayList<>(checked), caller),
                        () -> ui.gui().navigate(player, caller.get()));
            };
        }
        if (SignalInterferenceSettings.Weather.INTERFERE_DURING_PATH.equals(path)) {
            return player -> {
                Integer lobby = ui.gui().overrideLobby(player);
                List<String> current = stores.overrides().getStringList(lobby, path);
                deps.modifierDialogs().openInterfereDuring(player, current,
                        checked -> applyChecklist(player, lobby, path,
                                new ArrayList<>(checked), caller),
                        () -> ui.gui().navigate(player, caller.get()));
            };
        }
        return open(menu);
    }

    /** Persists a checked enum list, globally or as a lobby override. */
    private void applyChecklist(Player player, Integer lobby, String path,
            List<String> checked, Supplier<Menu> caller) {
        ConfigService.SetOutcome outcome = lobby == null
                ? stores.config().setList(path, checked)
                : stores.overrides().setListOverride(lobby, path, checked);
        if (!outcome.ok()) {
            ui.feedback().failed(player, outcome);
            texts.sounds().playAngrySound(player);
        }
        ui.gui().navigate(player, caller.get());
    }

    private String historyLine(String key, String value) {
        String line = switch (key) {
            case "matches" -> texts.guiTexts().getHistoryLineMatches();
            case "kills" -> texts.guiTexts().getHistoryLineKills();
            case "hunter-kills" -> texts.guiTexts().getHistoryLineHunterKills();
            case "speedrunner-kills" -> texts.guiTexts().getHistoryLineSpeedrunnerKills();
            case "hunter-wins" -> texts.guiTexts().getHistoryLineHunterWins();
            case "speedrunner-wins" -> texts.guiTexts().getHistoryLineSpeedrunnerWins();
            case "damage" -> texts.guiTexts().getHistoryLineDamage();
            case "playtime" -> texts.guiTexts().getHistoryLinePlaytime();
            default -> key + ": {value}";
        };
        return line.replace("{value}", value);
    }

    private String countLine(int count) {
        return texts.guiTexts().getCategoryLore()
                .replace("{count}", String.valueOf(count));
    }

    private String editTitle(int index) {
        return texts.guiTexts().getDialogTitleEditEntry()
                .replace("{index}", String.valueOf(index));
    }

    private String addTitle() {
        return texts.guiTexts().getDialogTitleAddEntry();
    }

    private String deleteTitle(int index) {
        return texts.guiTexts().getDialogTitleDeleteEntry()
                .replace("{index}", String.valueOf(index));
    }

    private Component title(String line) {
        return GuiTexts.title(texts.messages(), line);
    }

    private Component sectionTitle(String path) {
        String[] parts = path.split("\\.");
        if (parts.length == 2 && !parts[0].equals("advanced")) {
            return GuiTexts.title(texts.messages(), texts.guiTexts().getTitleCategory()
                    .replace("{name}", SettingButtons.prettify(parts[1])));
        }
        return GuiTexts.title(texts.messages(), SettingButtons.prettify(parts[parts.length - 1]));
    }

    private static String leaf(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
