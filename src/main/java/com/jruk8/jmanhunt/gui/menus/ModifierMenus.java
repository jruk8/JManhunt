package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.PagedList;
import com.jruk8.jmanhunt.gui.TwinPanel;
import com.jruk8.jmanhunt.gui.dialog.DialogInputs;
import com.jruk8.jmanhunt.gui.dialog.ModifierDialog;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * The three modifiers menus: main page plus the modifiers and presets
 * scroll lists. Each builder returns a fresh menu, so counts, order, and
 * glow are always read live. Buttons capture ids only; toggles run the
 * chat command as the clicking player, so permission, chat text, and
 * announces behave exactly like chat.
 */
public final class ModifierMenus {

    /** Member lines shown in preset lore before collapsing to "and n more". */
    public static final int MAX_PRESET_LORE_LINES = 8;

    private final ModifierStore store;
    private final MessageService messages;
    private final SoundService sounds;
    private final GuiService gui;
    private final ModifiersCommand toggles;
    private final SettingDialogs dialogs;
    private final ModifierDialog modifierDialogs;
    private final ModifierEditorMenus modifierEditor;
    private final PresetEditorMenus presetEditor;
    private final OverrideService overrides;
    private final SettingFeedback feedback;
    private final ModifierEditorMemory memory;

    /**
     * @param store modifier and preset reads
     * @param messages GUI text; sounds, gui, toggles, dialogs,
     *        commandValidation, overrides, feedback, and memory are only
     *        touched inside click actions or override sessions, so
     *        builders tolerate them as null
     */
    public ModifierMenus(ModifierStore store, MessageService messages, SoundService sounds,
            GuiService gui, ModifiersCommand toggles, SettingDialogs dialogs,
            ModifierDialog modifierDialogs, BooleanSupplier commandValidation,
            OverrideService overrides, SettingFeedback feedback, ModifierEditorMemory memory) {
        this.store = store;
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.toggles = toggles;
        this.dialogs = dialogs;
        this.modifierDialogs = modifierDialogs;
        this.modifierEditor = new ModifierEditorMenus(store, messages, sounds, gui, toggles,
                dialogs, modifierDialogs, commandValidation);
        this.presetEditor = new PresetEditorMenus(store, messages, sounds, gui, toggles, dialogs);
        this.overrides = overrides;
        this.feedback = feedback;
        this.memory = memory;
    }

    /** 27-slot root with links to both lists. */
    public Menu mainMenu() {
        return mainMenu(null, null);
    }

    /**
     * Root with an optional parent. An embedded root (parent supplied)
     * gains a back button so Back returns to the embedding menu.
     */
    public Menu mainMenu(Supplier<Menu> parent) {
        return mainMenu(null, parent);
    }

    /**
     * Root for one viewer. In an override session the counts read
     * effective and the lists open in the same session.
     */
    public Menu mainMenu(Player viewer, Supplier<Menu> parent) {
        Integer lobby = lobbyOf(viewer);
        return TwinPanel.menu(GuiTexts.title(messages, text("title-main", "Modifiers")),
                linkButton(Material.DIAMOND, "to-modifiers", "to-modifiers-lore",
                        "Modifiers", enabledModifiers(lobby), store.modifierNames().size(),
                        () -> modifiersMenu(viewer, parent)),
                linkButton(Material.FILLED_MAP, "to-presets", "to-presets-lore",
                        "Presets", enabledPresets(lobby), store.presetNames().size(),
                        () -> presetsMenu(viewer, parent)),
                gui,
                parent == null ? null
                        : GuiTexts.name(messages, text("back", "Back"), "Back"),
                parent);
    }

    /** 45-slot modifiers scroll list. */
    public Menu modifiersMenu() {
        return modifiersMenu(null, null);
    }

    /** Modifiers scroll list with an explicit root parent. */
    public Menu modifiersMenu(Supplier<Menu> parent) {
        return modifiersMenu(null, parent);
    }

    /** Modifiers scroll list for one viewer, session-aware. */
    public Menu modifiersMenu(Player viewer, Supplier<Menu> parent) {
        return listMenu("title-modifiers",
                columns -> modifierButtons(columns, viewer, () -> modifiersMenu(viewer, parent)),
                () -> mainMenu(viewer, parent), self -> toggleAllModifiersButton(viewer, self),
                self -> importButton(self, "modifier", "import-modifier",
                        "import-modifier-lore", "import-modifier-title"),
                createButton(Material.WRITABLE_BOOK, "modifier-editor", "Modifier Editor",
                        "modifier-editor-lore", "Create a modifier or test commands",
                        player -> gui.navigate(player,
                                editorTwin(player, () -> modifiersMenu(player, parent)))));
    }

    /** 45-slot presets scroll list. */
    public Menu presetsMenu() {
        return presetsMenu(null, null);
    }

    /** Presets scroll list with an explicit root parent. */
    public Menu presetsMenu(Supplier<Menu> parent) {
        return presetsMenu(null, parent);
    }

    /** Presets scroll list for one viewer, session-aware. */
    public Menu presetsMenu(Player viewer, Supplier<Menu> parent) {
        return listMenu("title-presets",
                columns -> presetButtons(columns, viewer, () -> presetsMenu(viewer, parent)),
                () -> mainMenu(viewer, parent), self -> toggleAllPresetsButton(viewer, self),
                self -> importButton(self, "preset", "import-preset",
                        "import-preset-lore", "import-preset-title"),
                createButton(Material.WRITABLE_BOOK, "create-preset", "Create Preset",
                        "create-preset-lore", "Start a new preset",
                        player -> presetEditor.createPreset(player,
                                () -> presetsMenu(player, parent))));
    }

    /** One viewer's override-session lobby, or null for global mode. */
    private Integer lobbyOf(Player viewer) {
        if (viewer == null || gui == null || overrides == null) {
            return null;
        }
        return gui.overrideLobby(viewer);
    }

    private Menu listMenu(String titleKey, Function<Integer, List<MenuButton>> content,
            Supplier<Menu> parent, Function<Menu[], MenuButton> toggleAll,
            Function<Menu[], MenuButton> importButton, MenuButton create) {
        return PagedList.menu(GuiTexts.title(messages, text(titleKey, "Modifiers")), content,
                parent, gui,
                GuiTexts.name(messages, text("scroll-up", "Scroll up"), "Scroll up"),
                GuiTexts.name(messages, text("scroll-down", "Scroll down"), "Scroll down"),
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                self -> new PagedList.Chrome(create, toggleAll.apply(self), importButton.apply(self)));
    }

    /** Bottom-right import loom: prompts for a share string, then refreshes. */
    private MenuButton importButton(Menu[] self, String type, String nameKey,
            String loreKey, String titleKey) {
        return new MenuButton(Material.LOOM,
                GuiTexts.name(messages,
                        text(nameKey, type.equals("preset") ? "Import Preset" : "Import Modifier"),
                        "Import"),
                GuiTexts.lore(messages, text(loreKey, "Paste an exported string.")),
                false, false, importAction(self, type, titleKey)).silent();
    }

    private Consumer<Player> importAction(Menu[] self, String type, String titleKey) {
        return player -> {
            if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                messages.message(player, "command.no-permission");
                return;
            }
            dialogs.prompt(player,
                    text(titleKey,
                            type.equals("preset") ? "Import Preset" : "Import Modifier"),
                    List.of(text("import-prompt", "Paste an exported string.")),
                    payload -> {
                        if (!toggles.importEntry(player, type, payload)) {
                            sounds.playAngrySound(player);
                        }
                        gui.navigate(player, self[0]);
                    },
                    () -> gui.navigate(player, self[0]));
        };
    }

    /**
     * Modifier Editor twin panel: Test-a-Command on the left, the
     * create flow on the right with back to this panel.
     */
    public Menu editorTwin(Player viewer, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        self[0] = TwinPanel.menu(
                GuiTexts.title(messages, text("modifier-editor-title", "Modifier Editor")),
                new MenuButton(Material.REPEATING_COMMAND_BLOCK,
                        GuiTexts.name(messages, text("test-command", "Test a Command"),
                                "Test a Command"),
                        GuiTexts.lore(messages, text("test-command-lore",
                                "Dry-run up to five commands")),
                        false, false,
                        player -> openTestDialog(player, self[0])).silent(),
                createButton(Material.WRITABLE_BOOK, "create-modifier", "Create Modifier",
                        "create-modifier-lore", "Start a new modifier",
                        player -> modifierEditor.createModifier(player, () -> self[0])),
                gui,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                parent);
        return self[0];
    }

    /** Test-a-Command dialog over the remembered initials. */
    private void openTestDialog(Player player, Menu twin) {
        if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
            messages.message(player, "command.no-permission");
            return;
        }
        openTestDialogWith(player, twin, memory.initialFor(player));
    }

    /** Test-a-Command dialog over explicit initials (reopens). */
    private void openTestDialogWith(Player player, Menu twin,
            ModifierDialog.TestSubmission initial) {
        modifierDialogs.openTestCommands(player, initial,
                submission -> submitTestDialog(player, twin, submission),
                () -> gui.navigate(player, twin));
    }

    /**
     * Runs one test submit through the dry-run pipeline and returns
     * the follow-up: an empty submit chats an error and reopens the
     * dialog with the same inputs, otherwise memory stores the
     * submit and the twin panel returns.
     */
    private Runnable submitTestDialog(Player player, Menu twin,
            ModifierDialog.TestSubmission submission) {
        List<String> lines = DialogInputs.collapseTestCommands(submission.commands());
        if (lines.isEmpty()) {
            messages.message(player, "modifiers.test-commands-empty");
            sounds.playAngrySound(player);
            return () -> openTestDialogWith(player, twin, submission);
        }
        memory.store(player, submission);
        toggles.testCommands(player, submission.role(), lines);
        return () -> gui.navigate(player, twin);
    }

    /** Top-right create button opening the creator flow. */
    private MenuButton createButton(Material material, String nameKey, String nameFallback,
            String loreKey, String loreFallback, Consumer<Player> action) {
        return new MenuButton(material,
                GuiTexts.name(messages, text(nameKey, nameFallback), nameFallback),
                GuiTexts.lore(messages, text(loreKey, loreFallback)),
                false, false, action).silent();
    }

    private MenuButton toggleAllModifiersButton(Player viewer, Menu[] self) {
        boolean allOn = allModifiersOn(lobbyOf(viewer));
        String loreText = text("toggle-all-modifiers-lore", "{total} modifiers")
                .replace("{total}", String.valueOf(store.modifierNames().size()));
        return new MenuButton(Material.STRUCTURE_VOID,
                GuiTexts.name(messages, text("toggle-all", "Toggle all"), "Toggle all"),
                GuiTexts.lore(messages, loreText), allOn, false,
                player -> {
                    if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                        messages.message(player, "command.no-permission");
                        return;
                    }
                    Integer lobby = lobbyOf(player);
                    boolean next = lobby == null
                            ? !allModifiersOn(null) : !allModifiersOn(lobby);
                    if (!next) {
                        applyToggleAllModifiers(player, lobby, false);
                        return;
                    }
                    confirmToggleAll(player, self, "modifiers",
                            () -> applyToggleAllModifiers(player, lobby, true));
                }).silent();
    }

    private void applyToggleAllModifiers(Player player, Integer lobby, boolean next) {
        if (lobby == null) {
            toggles.toggleAllModifiers(player,
                    new ArrayList<>(store.modifierNames()), next);
            sounds.playNeutralSound(player);
            return;
        }
        for (String id : store.modifierNames()) {
            overrides.setModifierOverride(lobby, id, next);
        }
        feedback.overrideBulkSet(player, lobby, "modifiers",
                store.modifierNames().size(), next);
    }

    private MenuButton toggleAllPresetsButton(Player viewer, Menu[] self) {
        boolean allOn = allPresetsOn(lobbyOf(viewer));
        String loreText = text("toggle-all-presets-lore", "{total} presets")
                .replace("{total}", String.valueOf(store.presetNames().size()));
        return new MenuButton(Material.STRUCTURE_VOID,
                GuiTexts.name(messages, text("toggle-all", "Toggle all"), "Toggle all"),
                GuiTexts.lore(messages, loreText), allOn, false,
                player -> {
                    if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                        messages.message(player, "command.no-permission");
                        return;
                    }
                    Integer lobby = lobbyOf(player);
                    boolean next = lobby == null
                            ? !allPresetsOn(null) : !allPresetsOn(lobby);
                    if (!next) {
                        applyToggleAllPresets(player, lobby, false);
                        return;
                    }
                    confirmToggleAll(player, self, "presets",
                            () -> applyToggleAllPresets(player, lobby, true));
                }).silent();
    }

    private void applyToggleAllPresets(Player player, Integer lobby, boolean next) {
        if (lobby == null) {
            toggles.toggleAllPresets(player,
                    new ArrayList<>(store.presetNames()), next);
            sounds.playNeutralSound(player);
            return;
        }
        for (String id : store.presetNames()) {
            for (String member : store.presetMembers(id)) {
                overrides.setModifierOverride(lobby, member, next);
            }
        }
        feedback.overrideBulkSet(player, lobby, "presets",
                store.presetNames().size(), next);
    }

    /** Confirm panel before bulk-enabling; bulk-disabling applies immediately. */
    private void confirmToggleAll(Player player, Menu[] self, String kind, Runnable apply) {
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages, text("toggle-all-confirm-title", "Turn all {kind} on?")
                        .replace("{kind}", kind)),
                Material.STRUCTURE_VOID, null,
                GuiTexts.lore(messages, text("toggle-all-confirm-lore", "This enables every {kind}.")
                        .replace("{kind}", kind)),
                GuiTexts.name(messages, text("cancel", "Cancel"), "Cancel"),
                back -> gui.navigate(back, self[0]),
                GuiTexts.name(messages, text("confirm", "Confirm"), "Confirm"),
                done -> {
                    apply.run();
                    gui.navigate(done, self[0]);
                },
                () -> self[0]);
        gui.navigate(player, confirm);
    }

    private MenuButton linkButton(Material material, String nameKey, String loreKey,
            String fallback, int enabled, int total, Supplier<Menu> target) {
        String loreText = text(loreKey, "{enabled}/{total} enabled")
                .replace("{enabled}", String.valueOf(enabled))
                .replace("{total}", String.valueOf(total));
        return new MenuButton(material,
                GuiTexts.name(messages, text(nameKey, fallback), fallback),
                GuiTexts.lore(messages, loreText), false, false,
                player -> gui.navigate(player, target.get()));
    }

    private List<MenuButton> modifierButtons(int columns, Player viewer,
            Supplier<Menu> listParent) {
        Integer lobby = lobbyOf(viewer);
        Map<String, Integer> order = MenuOrder.fileOrder(store.modifierNames());
        List<String> ids = new ArrayList<>(store.modifierNames());
        ids.sort(MenuOrder.modifiers(store::metaName, id -> modifierEnabled(lobby, id),
                order::get));
        int enabled = 0;
        while (enabled < ids.size() && modifierEnabled(lobby, ids.get(enabled))) {
            enabled++;
        }
        List<MenuButton> buttons = new ArrayList<>();
        for (int index = 0; index < enabled; index++) {
            buttons.add(modifierButton(ids.get(index), true, lobby, listParent));
        }
        MenuOrder.padGroup(buttons, enabled, columns);
        for (int index = enabled; index < ids.size(); index++) {
            buttons.add(modifierButton(ids.get(index), false, lobby, listParent));
        }
        return buttons;
    }

    private MenuButton modifierButton(String id, boolean enabled, Integer lobby,
            Supplier<Menu> listParent) {
        MenuButton button = new MenuButton(store.metaItem(id),
                GuiTexts.name(messages, store.metaName(id), ModifierStore.DEFAULT_NAME),
                modifierLore(id, enabled, lobby),
                lobby == null ? enabled : overrides.hasModifierOverride(lobby, id),
                false, toggleModifier(id),
                player -> {
                    if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                        messages.message(player, "command.no-permission");
                        return;
                    }
                    gui.navigate(player, modifierEditor.editor(id, listParent));
                    sounds.playSound(player, "compass.left-click");
                }).silent();
        if (lobby == null) {
            return button;
        }
        return button.shiftAction(player -> clearModifierOverride(player, id));
    }

    /** Shift-left in an override session: drop the modifier override. */
    private void clearModifierOverride(Player player, String id) {
        Integer lobby = lobbyOf(player);
        if (lobby == null) {
            return;
        }
        if (overrides.clearModifierOverride(lobby, id)) {
            feedback.overrideModifierCleared(player, lobby, id);
        } else {
            messages.message(player, "manhunt-gui.override-no-override");
        }
    }

    private List<Component> modifierLore(String id, boolean enabled, Integer lobby) {
        List<Component> lore = new ArrayList<>(GuiTexts.lore(messages, store.metaDescription(id)));
        if (!lore.isEmpty()) {
            lore.add(Component.text(" "));
        }
        lore.addAll(GuiTexts.lore(messages, text(stateKey(enabled), enabled ? "Enabled" : "Disabled")));
        String author = store.metaAuthor(id);
        if (author != null) {
            lore.add(Component.text(" "));
            lore.addAll(GuiTexts.lore(messages, "by " + author));
        }
        lore.add(Component.text(" "));
        lore.addAll(GuiTexts.lore(messages, text("edit-hint", "Right-click to edit")));
        if (lobby != null) {
            lore.addAll(GuiTexts.lore(messages, messages.string(
                    "manhunt-gui.override-shift-clear",
                    "Shift-left-click to remove the override")));
            if (overrides.hasModifierOverride(lobby, id)) {
                lore.addAll(GuiTexts.lore(messages, overridesLine(lobby)));
            }
        }
        return lore;
    }

    private List<MenuButton> presetButtons(int columns, Player viewer,
            Supplier<Menu> listParent) {
        Integer lobby = lobbyOf(viewer);
        Map<String, Integer> order = MenuOrder.fileOrder(store.presetNames());
        List<String> ids = new ArrayList<>(store.presetNames());
        ids.sort(MenuOrder.presets(store::presetName, id -> presetEnabled(lobby, id),
                order::get));
        int allOn = 0;
        while (allOn < ids.size() && presetEnabled(lobby, ids.get(allOn))) {
            allOn++;
        }
        List<MenuButton> buttons = new ArrayList<>();
        for (int index = 0; index < allOn; index++) {
            buttons.add(presetButton(ids.get(index), true, lobby, listParent));
        }
        MenuOrder.padGroup(buttons, allOn, columns);
        for (int index = allOn; index < ids.size(); index++) {
            buttons.add(presetButton(ids.get(index), false, lobby, listParent));
        }
        return buttons;
    }

    private MenuButton presetButton(String id, boolean allOn, Integer lobby,
            Supplier<Menu> listParent) {
        MenuButton button = new MenuButton(store.presetItem(id),
                GuiTexts.name(messages, store.presetName(id), ModifierStore.DEFAULT_NAME),
                presetLore(id, allOn, lobby),
                lobby == null ? allOn : hasPresetOverride(lobby, id),
                false, togglePreset(id),
                player -> {
                    if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                        messages.message(player, "command.no-permission");
                        return;
                    }
                    gui.navigate(player, presetEditor.editor(id, listParent));
                    sounds.playSound(player, "compass.left-click");
                }).silent();
        if (lobby == null) {
            return button;
        }
        return button.shiftAction(player -> clearPresetOverrides(player, id));
    }

    /** Shift-left in an override session: drop every member override. */
    private void clearPresetOverrides(Player player, String id) {
        Integer lobby = lobbyOf(player);
        if (lobby == null) {
            return;
        }
        int removed = 0;
        for (String member : store.presetMembers(id)) {
            if (overrides.clearModifierOverride(lobby, member)) {
                removed++;
            }
        }
        if (removed == 0) {
            messages.message(player, "manhunt-gui.override-no-override");
            return;
        }
        feedback.overrideCleared(player, lobby, "preset." + id, removed);
    }

    private List<Component> presetLore(String id, boolean allOn, Integer lobby) {
        List<String> members = store.presetMembers(id);
        if (members.isEmpty()) {
            return emptyPresetLore(id, lobby);
        }
        List<Component> lore = new ArrayList<>(
                GuiTexts.lore(messages, store.presetDescription(id)));
        if (!lore.isEmpty()) {
            lore.add(Component.text(" "));
        }
        int shown = Math.min(members.size(), MAX_PRESET_LORE_LINES);
        for (int index = 0; index < shown; index++) {
            String member = members.get(index);
            String color = modifierEnabled(lobby, member) ? "<green>" : "<red>";
            lore.addAll(GuiTexts.lore(messages, color + "» " + store.metaName(member)));
        }
        if (members.size() > shown) {
            String wrapper = allOn ? "" : "<red>";
            lore.addAll(GuiTexts.lore(messages, wrapper + "..and <gray>"
                    + (members.size() - shown) + "</gray> more"));
        }
        lore.add(Component.text(" "));
        lore.addAll(GuiTexts.lore(messages, text(stateKey(allOn), allOn ? "Enabled" : "Disabled")));
        String author = store.presetAuthor(id);
        if (author != null) {
            lore.add(Component.text(" "));
            lore.addAll(GuiTexts.lore(messages, "by " + author));
        }
        lore.add(Component.text(" "));
        lore.addAll(GuiTexts.lore(messages, text("edit-hint", "Right-click to edit")));
        if (lobby != null) {
            appendOverrideLore(lore, lobby, id);
        }
        return lore;
    }

    /** Memberless preset lore: the error line, state, then the usual hints. */
    private List<Component> emptyPresetLore(String id, Integer lobby) {
        List<Component> lore = new ArrayList<>();
        lore.addAll(GuiTexts.lore(messages,
                text("preset-empty-lore", "<red>No modifiers configured!")));
        lore.add(Component.text(" "));
        lore.addAll(GuiTexts.lore(messages, text(stateKey(false), "Disabled")));
        lore.add(Component.text(" "));
        lore.addAll(GuiTexts.lore(messages, text("edit-hint", "Right-click to edit")));
        if (lobby != null) {
            appendOverrideLore(lore, lobby, id);
        }
        return lore;
    }

    private void appendOverrideLore(List<Component> lore, int lobby, String id) {
        lore.addAll(GuiTexts.lore(messages, messages.string(
                "manhunt-gui.override-shift-clear",
                "Shift-left-click to remove the override")));
        if (hasPresetOverride(lobby, id)) {
            lore.addAll(GuiTexts.lore(messages, overridesLine(lobby)));
        }
    }

    private String overridesLine(int lobby) {
        return messages.string("manhunt-gui.override-for-lobby",
                "<red>Overrides for Lobby {lobby}").replace("{lobby}", String.valueOf(lobby));
    }

    private Consumer<Player> toggleModifier(String id) {
        return player -> {
            if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                messages.message(player, "command.no-permission");
                return;
            }
            Integer lobby = lobbyOf(player);
            if (lobby == null) {
                boolean next = !store.isEnabled(id);
                toggles.execute(player, new String[]{"setmod", id, String.valueOf(next)});
                sounds.playNeutralSound(player);
                return;
            }
            boolean next = !overrides.modifierEnabled(lobby, id);
            overrides.setModifierOverride(lobby, id, next);
            feedback.overrideModifierSet(player, lobby, id, next);
        };
    }

    private Consumer<Player> togglePreset(String id) {
        return player -> {
            if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                messages.message(player, "command.no-permission");
                return;
            }
            Integer lobby = lobbyOf(player);
            List<String> members = store.presetMembers(id);
            boolean next = lobby == null
                    ? !store.presetEnabled(id)
                    : !overrides.presetEnabled(lobby, id);
            if (members.isEmpty() && next) {
                messages.message(player, "modifiers.preset-empty",
                        Map.of("name", store.presetName(id)));
                sounds.playAngrySound(player);
                return;
            }
            if (lobby == null) {
                toggles.execute(player, new String[]{"setpreset", id, String.valueOf(next)});
                sounds.playNeutralSound(player);
                return;
            }
            for (String member : members) {
                overrides.setModifierOverride(lobby, member, next);
            }
            feedback.overridePresetSet(player, lobby, id, next, members.size());
        };
    }

    /** Effective flag: the lobby override wins, else the global. */
    private boolean modifierEnabled(Integer lobby, String id) {
        if (lobby == null || overrides == null) {
            return store.isEnabled(id);
        }
        return overrides.modifierEnabled(lobby, id);
    }

    /** Effective preset flag, same fallback when no session runs. */
    private boolean presetEnabled(Integer lobby, String id) {
        if (lobby == null || overrides == null) {
            return store.presetEnabled(id);
        }
        return overrides.presetEnabled(lobby, id);
    }

    /** True when any member carries a modifier override. */
    private boolean hasPresetOverride(int lobby, String id) {
        for (String member : store.presetMembers(id)) {
            if (overrides.hasModifierOverride(lobby, member)) {
                return true;
            }
        }
        return false;
    }

    private int enabledModifiers(Integer lobby) {
        int enabled = 0;
        for (String id : store.modifierNames()) {
            if (modifierEnabled(lobby, id)) {
                enabled++;
            }
        }
        return enabled;
    }

    private int enabledPresets(Integer lobby) {
        int enabled = 0;
        for (String id : store.presetNames()) {
            if (presetEnabled(lobby, id)) {
                enabled++;
            }
        }
        return enabled;
    }

    private boolean allModifiersOn(Integer lobby) {
        return !store.modifierNames().isEmpty()
                && enabledModifiers(lobby) == store.modifierNames().size();
    }

    private boolean allPresetsOn(Integer lobby) {
        return !store.presetNames().isEmpty()
                && enabledPresets(lobby) == store.presetNames().size();
    }

    private static String stateKey(boolean on) {
        return on ? "state-on" : "state-off";
    }

    private String text(String key, String fallback) {
        return messages.string("modifiers-gui." + key, fallback);
    }
}
