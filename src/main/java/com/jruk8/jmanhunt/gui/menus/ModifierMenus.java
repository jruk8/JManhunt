package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.TwinPanel;
import com.jruk8.jmanhunt.gui.dialog.ModifierDialog;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final ModifierEditorMenus modifierEditor;
    private final PresetEditorMenus presetEditor;
    private final OverrideService overrides;
    private final SettingFeedback feedback;

    /**
     * @param store modifier and preset reads
     * @param messages GUI text; sounds, gui, toggles, dialogs,
     *        commandValidation, overrides, and feedback are only touched
     *        inside click actions or override sessions, so builders
     *        tolerate them as null
     */
    public ModifierMenus(ModifierStore store, MessageService messages, SoundService sounds,
            GuiService gui, ModifiersCommand toggles, SettingDialogs dialogs,
            ModifierDialog modifierDialogs, BooleanSupplier commandValidation,
            OverrideService overrides, SettingFeedback feedback) {
        this.store = store;
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.toggles = toggles;
        this.dialogs = dialogs;
        this.modifierEditor = new ModifierEditorMenus(store, messages, sounds, gui, toggles,
                dialogs, modifierDialogs, commandValidation);
        this.presetEditor = new PresetEditorMenus(store, messages, sounds, gui, toggles, dialogs);
        this.overrides = overrides;
        this.feedback = feedback;
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
                () -> mainMenu(viewer, parent), () -> toggleAllModifiersButton(viewer),
                self -> importButton(self, "modifier", "import-modifier",
                        "import-modifier-lore", "import-modifier-title"),
                createButton(Material.WRITABLE_BOOK, "create-modifier", "Create Modifier",
                        "create-modifier-lore", "Start a new modifier",
                        player -> modifierEditor.createModifier(player,
                                () -> modifiersMenu(player, parent))));
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
                () -> mainMenu(viewer, parent), () -> toggleAllPresetsButton(viewer),
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
            Supplier<Menu> parent, Supplier<MenuButton> toggleAll,
            Function<Menu[], MenuButton> importButton, MenuButton create) {
        MenuLayout layout = MenuLayout.parse(
                "##xxxxxx#", "u#xxxxxx#", "b#xxxxxxt", "d#xxxxxx#", "##xxxxxx#");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(messages, text(titleKey, "Modifiers")),
                layout, () -> listStatic(self, toggleAll, importButton, create),
                () -> content.apply(layout.contentColumns()), parent);
        return self[0];
    }

    private Map<Integer, MenuButton> listStatic(
            Menu[] self, Supplier<MenuButton> toggleAll,
            Function<Menu[], MenuButton> importButton, MenuButton create) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(8, create);
        fixed.put(9, scrollButton(Material.ARROW, "scroll-up", "Scroll up", self, -1));
        fixed.put(18, new MenuButton(Material.PAPER,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                null, false, false,
                player -> gui.back(player, self[0])));
        fixed.put(26, toggleAll.get());
        fixed.put(27, scrollButton(Material.ARROW, "scroll-down", "Scroll down", self, 1));
        fixed.put(44, importButton.apply(self));
        return fixed;
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

    /** Top-right create button opening the creator flow. */
    private MenuButton createButton(Material material, String nameKey, String nameFallback,
            String loreKey, String loreFallback, Consumer<Player> action) {
        return new MenuButton(material,
                GuiTexts.name(messages, text(nameKey, nameFallback), nameFallback),
                GuiTexts.lore(messages, text(loreKey, loreFallback)),
                false, false, action).silent();
    }

    private MenuButton toggleAllModifiersButton(Player viewer) {
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
                    if (lobby == null) {
                        toggles.toggleAllModifiers(player,
                                new ArrayList<>(store.modifierNames()),
                                !allModifiersOn(null));
                        sounds.playNeutralSound(player);
                        return;
                    }
                    boolean next = !allModifiersOn(lobby);
                    for (String id : store.modifierNames()) {
                        overrides.setModifierOverride(lobby, id, next);
                    }
                    feedback.overrideBulkSet(player, lobby, "modifiers",
                            store.modifierNames().size(), next);
                }).silent();
    }

    private MenuButton toggleAllPresetsButton(Player viewer) {
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
                    if (lobby == null) {
                        toggles.toggleAllPresets(player,
                                new ArrayList<>(store.presetNames()),
                                !allPresetsOn(null));
                        sounds.playNeutralSound(player);
                        return;
                    }
                    boolean next = !allPresetsOn(lobby);
                    for (String id : store.presetNames()) {
                        for (String member : store.presetMembers(id)) {
                            overrides.setModifierOverride(lobby, member, next);
                        }
                    }
                    feedback.overrideBulkSet(player, lobby, "presets",
                            store.presetNames().size(), next);
                }).silent();
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

    private MenuButton scrollButton(Material material, String nameKey, String fallback,
            Menu[] self, int delta) {
        return new MenuButton(material,
                GuiTexts.name(messages, text(nameKey, fallback), fallback),
                null, false, false,
                player -> self[0].window().scrollLine(delta));
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
        padGroup(buttons, enabled, columns);
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

    /**
     * Pads the leading group with nulls so the next group starts on a
     * fresh content row. Nulls render as empty slots.
     */
    private static void padGroup(List<MenuButton> buttons, int groupSize, int columns) {
        int pad = (columns - groupSize % columns) % columns;
        for (int index = 0; index < pad; index++) {
            buttons.add(null);
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
        padGroup(buttons, allOn, columns);
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
        List<Component> lore = new ArrayList<>();
        List<String> members = store.presetMembers(id);
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
        if (!lore.isEmpty()) {
            lore.add(Component.text(" "));
        }
        lore.addAll(GuiTexts.lore(messages, text(stateKey(allOn), allOn ? "Enabled" : "Disabled")));
        String author = store.presetAuthor(id);
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
            if (hasPresetOverride(lobby, id)) {
                lore.addAll(GuiTexts.lore(messages, overridesLine(lobby)));
            }
        }
        return lore;
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
            if (lobby == null) {
                boolean next = !store.presetEnabled(id);
                toggles.execute(player, new String[]{"setpreset", id, String.valueOf(next)});
                sounds.playNeutralSound(player);
                return;
            }
            boolean next = !overrides.presetEnabled(lobby, id);
            List<String> members = store.presetMembers(id);
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
