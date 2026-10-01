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
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.ManhuntGuiMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersGuiMessages;
import com.jruk8.jmanhunt.message.ModifiersMessages;
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

    /** Message bus, gui texts, modifier/command texts, and sounds. */
    public record MenusTexts(MessageService messages, ModifiersGuiMessages modifiersGui,
            ManhuntGuiMessages manhuntGui, ModifiersMessages modifiers,
            CommandMessages command, SoundService sounds) {
    }

    /** Gui, toggles, dialogs, validation, overrides, feedback, memory. */
    public record MenusDeps(GuiService gui, ModifiersCommand toggles, SettingDialogs dialogs,
            ModifierDialog modifierDialogs, BooleanSupplier commandValidation,
            OverrideService overrides, SettingFeedback feedback,
            ModifierEditorMemory memory) {
    }

    private final ModifierStore store;
    private final MenusTexts texts;
    private final MenusDeps deps;
    private final ModifierEditorMenus modifierEditor;
    private final PresetEditorMenus presetEditor;

    public ModifierMenus(ModifierStore store, MenusTexts texts, MenusDeps deps) {
        this.store = store;
        this.texts = texts;
        this.deps = deps;
        this.modifierEditor = new ModifierEditorMenus(store,
                new ModifierEditorMenus.EditorTexts(texts.messages(), texts.modifiersGui(),
                        texts.manhuntGui(), texts.modifiers(), texts.command(),
                        texts.sounds()),
                new ModifierEditorMenus.EditorDeps(deps.gui(), deps.toggles(), deps.dialogs(),
                        deps.modifierDialogs(), deps.commandValidation()));
        this.presetEditor = new PresetEditorMenus(store,
                new PresetEditorMenus.PresetTexts(texts.messages(), texts.modifiersGui(),
                        texts.modifiers(), texts.command(), texts.sounds()),
                new PresetEditorMenus.PresetDeps(deps.gui(), deps.toggles(), deps.dialogs()));
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
        return TwinPanel.menu(GuiTexts.title(texts.messages(), texts.modifiersGui().getTitleMain()),
                linkButton(Material.DIAMOND, texts.modifiersGui().getToModifiers(),
                        texts.modifiersGui().getToModifiersLore(),
                        "Modifiers", enabledModifiers(lobby), store.modifierNames().size(),
                        () -> modifiersMenu(viewer, parent)),
                linkButton(Material.FILLED_MAP, texts.modifiersGui().getToPresets(),
                        texts.modifiersGui().getToPresetsLore(),
                        "Presets", enabledPresets(lobby), store.presetNames().size(),
                        () -> presetsMenu(viewer, parent)),
                deps.gui(),
                parent == null ? null
                        : GuiTexts.name(texts.messages(), texts.modifiersGui().getBack(), "Back"),
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
        return listMenu(texts.modifiersGui().getTitleModifiers(),
                columns -> modifierButtons(columns, viewer, () -> modifiersMenu(viewer, parent)),
                () -> mainMenu(viewer, parent), self -> toggleAllModifiersButton(viewer, self),
                self -> importButton(self, "modifier", texts.modifiersGui().getImportModifier(),
                        texts.modifiersGui().getImportModifierLore(), texts.modifiersGui().getImportModifierTitle()),
                createButton(Material.WRITABLE_BOOK, texts.modifiersGui().getModifierEditor(), "Modifier Editor",
                        texts.modifiersGui().getModifierEditorLore(), "Create a modifier or test commands",
                        player -> deps.gui().navigate(player,
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
        return listMenu(texts.modifiersGui().getTitlePresets(),
                columns -> presetButtons(columns, viewer, () -> presetsMenu(viewer, parent)),
                () -> mainMenu(viewer, parent), self -> toggleAllPresetsButton(viewer, self),
                self -> importButton(self, "preset", texts.modifiersGui().getImportPreset(),
                        texts.modifiersGui().getImportPresetLore(), texts.modifiersGui().getImportPresetTitle()),
                createButton(Material.WRITABLE_BOOK, texts.modifiersGui().getCreatePreset(), "Create Preset",
                        texts.modifiersGui().getCreatePresetLore(), "Start a new preset",
                        player -> presetEditor.createPreset(player,
                                () -> presetsMenu(player, parent))));
    }

    /** One viewer's override-session lobby, or null for global mode. */
    private Integer lobbyOf(Player viewer) {
        if (viewer == null || deps.gui() == null || deps.overrides() == null) {
            return null;
        }
        return deps.gui().overrideLobby(viewer);
    }

    private Menu listMenu(String title, Function<Integer, List<MenuButton>> content,
            Supplier<Menu> parent, Function<Menu[], MenuButton> toggleAll,
            Function<Menu[], MenuButton> importButton, MenuButton create) {
        return PagedList.menu(GuiTexts.title(texts.messages(), title), content,
                parent, deps.gui(),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getScrollUp(), "Scroll up"),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getScrollDown(), "Scroll down"),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getBack(), "Back"),
                self -> new PagedList.Chrome(create, toggleAll.apply(self), importButton.apply(self)));
    }

    /** Bottom-right import loom: prompts for a share string, then refreshes. */
    private MenuButton importButton(Menu[] self, String type, String name,
            String lore, String title) {
        return new MenuButton(new MenuButton.Spec(Material.LOOM,
                GuiTexts.name(texts.messages(), name, "Import"),
                GuiTexts.lore(texts.messages(), lore),
                false, false, importAction(self, type, title), null, null, MenuButton.SoundPolicy.CLICK,
                        null)).silent();
    }

    private Consumer<Player> importAction(Menu[] self, String type, String title) {
        return player -> {
            if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                texts.messages().messageRaw(player, texts.command().getNoPermission());
                return;
            }
            deps.dialogs().prompt(player, title,
                    List.of(texts.modifiersGui().getImportPrompt()),
                    payload -> {
                        if (!deps.toggles().importEntry(player, type, payload)) {
                            texts.sounds().playAngrySound(player);
                        }
                        deps.gui().navigate(player, self[0]);
                    },
                    () -> deps.gui().navigate(player, self[0]));
        };
    }

    /**
     * Modifier Editor twin panel: Test-a-Command on the left, the
     * create flow on the right with back to this panel.
     */
    public Menu editorTwin(Player viewer, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        self[0] = TwinPanel.menu(
                GuiTexts.title(texts.messages(), texts.modifiersGui().getModifierEditorTitle()),
                new MenuButton(new MenuButton.Spec(Material.REPEATING_COMMAND_BLOCK,
                        GuiTexts.name(texts.messages(), texts.modifiersGui().getTestCommand(),
                                "Test a Command"),
                        GuiTexts.lore(texts.messages(), texts.modifiersGui().getTestCommandLore()),
                        false, false,
                        player -> openTestDialog(player, self[0]), null, null,
                                MenuButton.SoundPolicy.CLICK, null)).silent(),
                createButton(Material.WRITABLE_BOOK, texts.modifiersGui().getCreateModifier(),
                        "Create Modifier", texts.modifiersGui().getCreateModifierLore(),
                        "Start a new modifier",
                        player -> modifierEditor.createModifier(player, () -> self[0])),
                deps.gui(),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getBack(), "Back"),
                parent);
        return self[0];
    }

    /** Test-a-Command dialog over the remembered initials. */
    private void openTestDialog(Player player, Menu twin) {
        if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
            texts.messages().messageRaw(player, texts.command().getNoPermission());
            return;
        }
        openTestDialogWith(player, twin, deps.memory().initialFor(player));
    }

    /** Test-a-Command dialog over explicit initials (reopens). */
    private void openTestDialogWith(Player player, Menu twin,
            ModifierDialog.TestSubmission initial) {
        deps.modifierDialogs().openTestCommands(player, initial,
                submission -> submitTestDialog(player, twin, submission),
                () -> deps.gui().navigate(player, twin));
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
            texts.messages().messageRaw(player, texts.modifiers().getTestCommandsEmpty());
            texts.sounds().playAngrySound(player);
            return () -> openTestDialogWith(player, twin, submission);
        }
        deps.memory().store(player, submission);
        deps.toggles().testCommands(player, submission.role(), lines);
        return () -> deps.gui().navigate(player, twin);
    }

    /** Top-right create button opening the creator flow. */
    private MenuButton createButton(Material material, String name, String nameFallback,
            String lore, String loreFallback, Consumer<Player> action) {
        return new MenuButton(new MenuButton.Spec(material,
                GuiTexts.name(texts.messages(), name, nameFallback),
                GuiTexts.lore(texts.messages(), lore),
                false, false, action, null, null, MenuButton.SoundPolicy.CLICK, null)).silent();
    }

    private MenuButton toggleAllModifiersButton(Player viewer, Menu[] self) {
        boolean allOn = allModifiersOn(lobbyOf(viewer));
        String loreText = texts.modifiersGui().getToggleAllModifiersLore()
                .replace("{total}", String.valueOf(store.modifierNames().size()));
        return new MenuButton(new MenuButton.Spec(Material.STRUCTURE_VOID,
                GuiTexts.name(texts.messages(), texts.modifiersGui().getToggleAll(), "Toggle all"),
                GuiTexts.lore(texts.messages(), loreText), allOn, false,
                player -> {
                    if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                        texts.messages().messageRaw(player, texts.command().getNoPermission());
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
                }, null, null, MenuButton.SoundPolicy.CLICK, null)).silent();
    }

    private void applyToggleAllModifiers(Player player, Integer lobby, boolean next) {
        if (lobby == null) {
            deps.toggles().toggleAllModifiers(player,
                    new ArrayList<>(store.modifierNames()), next);
            texts.sounds().playNeutralSound(player);
            return;
        }
        for (String id : store.modifierNames()) {
            deps.overrides().setModifierOverride(lobby, id, next);
        }
        deps.feedback().overrideBulkSet(player, lobby, "modifiers",
                store.modifierNames().size(), next);
    }

    private MenuButton toggleAllPresetsButton(Player viewer, Menu[] self) {
        boolean allOn = allPresetsOn(lobbyOf(viewer));
        String loreText = texts.modifiersGui().getToggleAllPresetsLore()
                .replace("{total}", String.valueOf(store.presetNames().size()));
        return new MenuButton(new MenuButton.Spec(Material.STRUCTURE_VOID,
                GuiTexts.name(texts.messages(), texts.modifiersGui().getToggleAll(), "Toggle all"),
                GuiTexts.lore(texts.messages(), loreText), allOn, false,
                player -> {
                    if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                        texts.messages().messageRaw(player, texts.command().getNoPermission());
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
                }, null, null, MenuButton.SoundPolicy.CLICK, null)).silent();
    }

    private void applyToggleAllPresets(Player player, Integer lobby, boolean next) {
        if (lobby == null) {
            deps.toggles().toggleAllPresets(player,
                    new ArrayList<>(store.presetNames()), next);
            texts.sounds().playNeutralSound(player);
            return;
        }
        for (String id : store.presetNames()) {
            for (String member : store.presetMembers(id)) {
                deps.overrides().setModifierOverride(lobby, member, next);
            }
        }
        deps.feedback().overrideBulkSet(player, lobby, "presets",
                store.presetNames().size(), next);
    }

    /** Confirm panel before bulk-enabling; bulk-disabling applies immediately. */
    private void confirmToggleAll(Player player, Menu[] self, String kind, Runnable apply) {
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(texts.messages(), "Turn all {kind} on?"
                        .replace("{kind}", kind)),
                Material.STRUCTURE_VOID, null,
                GuiTexts.lore(texts.messages(), "This enables every {kind}."
                        .replace("{kind}", kind)),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getCancel(), "Cancel"),
                back -> deps.gui().navigate(back, self[0]),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getConfirm(), "Confirm"),
                done -> {
                    apply.run();
                    deps.gui().navigate(done, self[0]);
                },
                () -> self[0]);
        deps.gui().navigate(player, confirm);
    }

    private MenuButton linkButton(Material material, String name, String lore,
            String fallback, int enabled, int total, Supplier<Menu> target) {
        String loreText = lore
                .replace("{enabled}", String.valueOf(enabled))
                .replace("{total}", String.valueOf(total));
        return new MenuButton(new MenuButton.Spec(material,
                GuiTexts.name(texts.messages(), name, fallback),
                GuiTexts.lore(texts.messages(), loreText), false, false,
                player -> deps.gui().navigate(player, target.get()), null, null, MenuButton.SoundPolicy.CLICK, null));
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
        MenuButton button = new MenuButton(new MenuButton.Spec(store.metaItem(id),
                GuiTexts.name(texts.messages(), store.metaName(id), ModifierStore.DEFAULT_NAME),
                modifierLore(id, enabled, lobby),
                lobby == null ? enabled : deps.overrides().hasModifierOverride(lobby, id),
                false, toggleModifier(id),
                player -> {
                    if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                        texts.messages().messageRaw(player, texts.command().getNoPermission());
                        return;
                    }
                    deps.gui().navigate(player, modifierEditor.editor(id, listParent));
                    texts.sounds().playSound(player, "compass.left-click");
                }, null, MenuButton.SoundPolicy.CLICK, null)).silent();
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
        if (deps.overrides().clearModifierOverride(lobby, id)) {
            deps.feedback().overrideModifierCleared(player, lobby, id);
        } else {
            texts.messages().messageRaw(player, texts.manhuntGui().getOverrideNoOverride());
        }
    }

    private List<Component> modifierLore(String id, boolean enabled, Integer lobby) {
        List<Component> lore = new ArrayList<>(GuiTexts.lore(texts.messages(), store.metaDescription(id)));
        if (!lore.isEmpty()) {
            lore.add(Component.text(" "));
        }
        lore.addAll(GuiTexts.lore(texts.messages(),
                (enabled ? texts.modifiersGui().getStateOn() : texts.modifiersGui().getStateOff())));
        String author = store.metaAuthor(id);
        if (author != null) {
            lore.add(Component.text(" "));
            lore.addAll(GuiTexts.lore(texts.messages(), "by " + author));
        }
        lore.add(Component.text(" "));
        lore.addAll(GuiTexts.lore(texts.messages(), texts.modifiersGui().getEditHint()));
        if (lobby != null) {
            lore.addAll(GuiTexts.lore(texts.messages(), texts.manhuntGui().getOverrideShiftClear()));
            if (deps.overrides().hasModifierOverride(lobby, id)) {
                lore.addAll(GuiTexts.lore(texts.messages(), overridesLine(lobby)));
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
        MenuButton button = new MenuButton(new MenuButton.Spec(store.presetItem(id),
                GuiTexts.name(texts.messages(), store.presetName(id), ModifierStore.DEFAULT_NAME),
                presetLore(id, allOn, lobby),
                lobby == null ? allOn : hasPresetOverride(lobby, id),
                false, togglePreset(id),
                player -> {
                    if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                        texts.messages().messageRaw(player, texts.command().getNoPermission());
                        return;
                    }
                    deps.gui().navigate(player, presetEditor.editor(id, listParent));
                    texts.sounds().playSound(player, "compass.left-click");
                }, null, MenuButton.SoundPolicy.CLICK, null)).silent();
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
            if (deps.overrides().clearModifierOverride(lobby, member)) {
                removed++;
            }
        }
        if (removed == 0) {
            texts.messages().messageRaw(player, texts.manhuntGui().getOverrideNoOverride());
            return;
        }
        deps.feedback().overrideCleared(player, lobby, "preset." + id, removed);
    }

    private List<Component> presetLore(String id, boolean allOn, Integer lobby) {
        List<String> members = store.presetMembers(id);
        if (members.isEmpty()) {
            return emptyPresetLore(id, lobby);
        }
        List<Component> lore = new ArrayList<>(
                GuiTexts.lore(texts.messages(), store.presetDescription(id)));
        if (!lore.isEmpty()) {
            lore.add(Component.text(" "));
        }
        int shown = Math.min(members.size(), MAX_PRESET_LORE_LINES);
        for (int index = 0; index < shown; index++) {
            String member = members.get(index);
            String color = modifierEnabled(lobby, member) ? "<green>" : "<red>";
            lore.addAll(GuiTexts.lore(texts.messages(), color + "» " + store.metaName(member)));
        }
        if (members.size() > shown) {
            String wrapper = allOn ? "" : "<red>";
            lore.addAll(GuiTexts.lore(texts.messages(), wrapper + "..and <gray>"
                    + (members.size() - shown) + "</gray> more"));
        }
        lore.add(Component.text(" "));
        lore.addAll(GuiTexts.lore(texts.messages(),
                (allOn ? texts.modifiersGui().getStateOn() : texts.modifiersGui().getStateOff())));
        String author = store.presetAuthor(id);
        if (author != null) {
            lore.add(Component.text(" "));
            lore.addAll(GuiTexts.lore(texts.messages(), "by " + author));
        }
        lore.add(Component.text(" "));
        lore.addAll(GuiTexts.lore(texts.messages(), texts.modifiersGui().getEditHint()));
        if (lobby != null) {
            appendOverrideLore(lore, lobby, id);
        }
        return lore;
    }

    /** Memberless preset lore: the error line, state, then the usual hints. */
    private List<Component> emptyPresetLore(String id, Integer lobby) {
        List<Component> lore = new ArrayList<>();
        lore.addAll(GuiTexts.lore(texts.messages(),
                texts.modifiersGui().getPresetEmptyLore()));
        lore.add(Component.text(" "));
        lore.addAll(GuiTexts.lore(texts.messages(), texts.modifiersGui().getStateOff()));
        lore.add(Component.text(" "));
        lore.addAll(GuiTexts.lore(texts.messages(), texts.modifiersGui().getEditHint()));
        if (lobby != null) {
            appendOverrideLore(lore, lobby, id);
        }
        return lore;
    }

    private void appendOverrideLore(List<Component> lore, int lobby, String id) {
        lore.addAll(GuiTexts.lore(texts.messages(), texts.manhuntGui().getOverrideShiftClear()));
        if (hasPresetOverride(lobby, id)) {
            lore.addAll(GuiTexts.lore(texts.messages(), overridesLine(lobby)));
        }
    }

    private String overridesLine(int lobby) {
        return texts.manhuntGui().getOverrideForLobby().replace("{lobby}", String.valueOf(lobby));
    }

    private Consumer<Player> toggleModifier(String id) {
        return player -> {
            if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                texts.messages().messageRaw(player, texts.command().getNoPermission());
                return;
            }
            Integer lobby = lobbyOf(player);
            if (lobby == null) {
                boolean next = !store.isEnabled(id);
                deps.toggles().execute(player, new String[]{"setmod", id, String.valueOf(next)});
                texts.sounds().playNeutralSound(player);
                return;
            }
            boolean next = !deps.overrides().modifierEnabled(lobby, id);
            deps.overrides().setModifierOverride(lobby, id, next);
            deps.feedback().overrideModifierSet(player, lobby, id, next);
        };
    }

    private Consumer<Player> togglePreset(String id) {
        return player -> {
            if (!player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
                texts.messages().messageRaw(player, texts.command().getNoPermission());
                return;
            }
            Integer lobby = lobbyOf(player);
            List<String> members = store.presetMembers(id);
            boolean next = lobby == null
                    ? !store.presetEnabled(id)
                    : !deps.overrides().presetEnabled(lobby, id);
            if (members.isEmpty() && next) {
                texts.messages().messageRaw(player, texts.modifiers().getPresetEmpty(),
                        Map.of("name", store.presetName(id)));
                texts.sounds().playAngrySound(player);
                return;
            }
            if (lobby == null) {
                deps.toggles().execute(player, new String[]{"setpreset", id, String.valueOf(next)});
                texts.sounds().playNeutralSound(player);
                return;
            }
            for (String member : members) {
                deps.overrides().setModifierOverride(lobby, member, next);
            }
            deps.feedback().overridePresetSet(player, lobby, id, next, members.size());
        };
    }

    /** Effective flag: the lobby override wins, else the global. */
    private boolean modifierEnabled(Integer lobby, String id) {
        if (lobby == null || deps.overrides() == null) {
            return store.isEnabled(id);
        }
        return deps.overrides().modifierEnabled(lobby, id);
    }

    /** Effective preset flag, same fallback when no session runs. */
    private boolean presetEnabled(Integer lobby, String id) {
        if (lobby == null || deps.overrides() == null) {
            return store.presetEnabled(id);
        }
        return deps.overrides().presetEnabled(lobby, id);
    }

    /** True when any member carries a modifier override. */
    private boolean hasPresetOverride(int lobby, String id) {
        for (String member : store.presetMembers(id)) {
            if (deps.overrides().hasModifierOverride(lobby, member)) {
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

}
