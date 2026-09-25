package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.QuadPanel;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierFieldEdits;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Creator and editor menus for one preset: display data, member
 * toggles, export, rename, and delete. Mirrors the modifier editor's
 * prompt and validation flow. Builders read live and tolerate missing
 * entries as blank defaults.
 */
public final class PresetEditorMenus {

    private final ModifierStore store;
    private final MessageService messages;
    private final SoundService sounds;
    private final GuiService gui;
    private final ModifiersCommand commands;
    private final SettingDialogs dialogs;

    /**
     * @param store preset reads and patches; sounds, gui, commands, and
     *        dialogs are only touched inside click actions, so builders
     *        tolerate them as null
     */
    public PresetEditorMenus(ModifierStore store, MessageService messages, SoundService sounds,
            GuiService gui, ModifiersCommand commands, SettingDialogs dialogs) {
        this.store = store;
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.commands = commands;
        this.dialogs = dialogs;
    }

    /**
     * Create flow: prompts for a display name, then opens the new
     * preset in the editor. Cancel returns to the list.
     */
    public void createPreset(Player player, Supplier<Menu> listMenu) {
        if (denied(player)) {
            return;
        }
        dialogs.prompt(player,
                text("create-preset-name-title", "Name your preset"),
                List.of(text("create-name-prompt", "Type a display name.")),
                raw -> {
                    ModifierFieldEdits.Parsed<String> name = ModifierFieldEdits.name(raw);
                    if (!name.ok()) {
                        invalid(player, name.error());
                        gui.navigate(player, listMenu.get());
                        return;
                    }
                    String id = store.createPreset(name.value());
                    messages.message(player, "modifiers.create-success",
                            Map.of("type", "preset", "name", store.presetName(id)));
                    sounds.playNeutralSound(player);
                    gui.navigate(player, editor(id, listMenu));
                },
                () -> gui.navigate(player, listMenu.get()));
    }

    /** Quad root for one preset: Meta, Modifiers, Export, Delete. */
    public Menu editor(String id, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        self[0] = QuadPanel.menu(
                GuiTexts.title(messages, text("editor-title-preset", "Edit Preset")),
                List.of(
                        EditorButtons.actionButton(messages, Material.NAME_TAG,
                                text("meta-title", "Meta"),
                                List.of(text("meta-lore", "Name, description, icon, author"),
                                        text("editor-click-open", "Click to open")),
                                player -> {
                                    if (denied(player)) {
                                        return;
                                    }
                                    gui.navigate(player, legacyEditor(id, parent));
                                }),
                        EditorButtons.actionButton(messages, Material.FILLED_MAP,
                                text("modifiers-title", "Modifiers"),
                                List.of(text("members-lore", "{total} members").replace("{total}",
                                                String.valueOf(store.presetMembers(id).size())),
                                        text("editor-click-open", "Click to open")),
                                player -> {
                                    if (denied(player)) {
                                        return;
                                    }
                                    gui.navigate(player,
                                            membersMenu(id, () -> self[0]));
                                }),
                        EditorButtons.actionButton(messages, Material.LOOM,
                                text("editor-export", "Export"),
                                List.of(text("editor-export-lore", "Copy a share string"),
                                        text("editor-click-copy", "Click to copy")),
                                player -> commands.exportEntry(player, "preset", id)).silent(),
                        EditorButtons.actionButton(messages, Material.TNT,
                                text("editor-delete-preset", "Delete Preset"),
                                List.of(text("editor-delete-preset-lore",
                                                "Removes this preset forever"),
                                        text("editor-click-delete", "Click to delete")),
                                player -> {
                                    if (denied(player)) {
                                        return;
                                    }
                                    deleteConfirm(player, id, parent);
                                })),
                gui,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                parent);
        return self[0];
    }

    /**
     * Phase 3 placeholder: the previous full editor, kept as the Meta
     * target until Phase 4 builds the Meta quad. Back and delete return
     * to the passed parent (the preset list), like the old editor did.
     */
    Menu legacyEditor(String id, Supplier<Menu> parent) {
        MenuLayout layout = MenuLayout.parse("#########", "#########", "#########");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(messages, text("editor-title-preset", "Edit Preset")),
                layout, () -> editorStatic(id, parent, self[0]), List::of, parent);
        return self[0];
    }

    private Map<Integer, MenuButton> editorStatic(String id, Supplier<Menu> parent, Menu self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        metaRow(fixed, id, self);
        membersRow(fixed, id, self);
        actionRow(fixed, id, parent, self);
        return fixed;
    }

    private void metaRow(Map<Integer, MenuButton> fixed, String id, Menu self) {
        fixed.put(1, fieldButton(Material.NAME_TAG, "Name", store.presetName(id),
                player -> fieldPrompt(player, self, "Name", store.presetName(id), id, false,
                        (target, raw) -> {
                            ModifierFieldEdits.Parsed<String> name =
                                    ModifierFieldEdits.name(raw);
                            if (!name.ok()) {
                                return name.error();
                            }
                            store.updatePreset(target,
                                    preset -> preset.setName(name.value()));
                            return null;
                        })));
        fixed.put(3, fieldButton(Material.BOOK, "Description",
                orUnset(store.presetDescription(id)),
                player -> fieldPrompt(player, self, "Description", store.presetDescription(id),
                        id, true, (target, raw) -> {
                            store.updatePreset(target, preset -> preset.setDescription(raw));
                            return null;
                        })));
        fixed.put(5, fieldButton(store.presetItem(id), "Icon", store.presetItem(id).name(),
                player -> fieldPrompt(player, self, "Icon", store.presetItem(id).name(), id, false,
                        (target, raw) -> {
                            ModifierFieldEdits.Parsed<Material> item =
                                    ModifierFieldEdits.item(raw);
                            if (!item.ok()) {
                                return item.error();
                            }
                            store.updatePreset(target,
                                    preset -> preset.setItem(item.value().name()));
                            return null;
                        })));
    }

    private void membersRow(Map<Integer, MenuButton> fixed, String id, Menu self) {
        int members = store.presetMembers(id).size();
        fixed.put(13, EditorButtons.actionButton(messages, Material.FILLED_MAP,
                text("members-title", "Members"),
                List.of(text("members-lore", "{total} members")
                                .replace("{total}", String.valueOf(members)),
                        text("editor-click-open", "Click to open")),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    gui.navigate(player, membersMenu(id, () -> legacyEditor(id, self.parent())));
                }));
    }

    private void actionRow(Map<Integer, MenuButton> fixed, String id, Supplier<Menu> parent,
            Menu self) {
        fixed.put(10, EditorButtons.actionButton(messages, Material.LOOM,
                text("editor-export", "Export"),
                List.of(text("editor-export-lore", "Copy a share string"),
                        text("editor-click-copy", "Click to copy")),
                player -> commands.exportEntry(player, "preset", id)).silent());
        fixed.put(12, EditorButtons.actionButton(messages, Material.ANVIL,
                text("editor-rename", "Rename Id"),
                List.of(text("editor-rename-lore", "Current id: {value}").replace("{value}", id)),
                player -> renamePrompt(player, id, self)).silent());
        fixed.put(14, EditorButtons.actionButton(messages, Material.TNT,
                text("editor-delete", "Delete"),
                List.of(text("editor-delete-preset-lore", "Removes this preset forever"),
                        text("editor-click-delete", "Click to delete")),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    deleteConfirm(player, id, parent);
                }));
        fixed.put(16, new MenuButton(Material.PAPER,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                null, false, false,
                player -> gui.back(player, self)));
    }

    /** Scrollable membership toggles over every modifier. */
    public Menu membersMenu(String id, Supplier<Menu> parent) {
        MenuLayout layout = MenuLayout.parse(
                "##xxxxxx#", "u#xxxxxx#", "b#xxxxxxt", "d#xxxxxx#", "##xxxxxx#");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(messages, text("members-title", "Members")),
                layout, () -> membersStatic(self),
                () -> memberButtons(id), parent);
        return self[0];
    }

    private Map<Integer, MenuButton> membersStatic(Menu[] self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(9, scrollButton("scroll-up", "Scroll up", self, -1));
        fixed.put(18, new MenuButton(Material.PAPER,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                null, false, false,
                player -> gui.back(player, self[0])));
        fixed.put(27, scrollButton("scroll-down", "Scroll down", self, 1));
        return fixed;
    }

    private List<MenuButton> memberButtons(String id) {
        Set<String> members = new HashSet<>(store.presetMembers(id));
        List<String> ids = new ArrayList<>(store.modifierNames());
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        List<MenuButton> buttons = new ArrayList<>();
        for (String member : ids) {
            boolean on = members.contains(member);
            buttons.add(EditorButtons.actionButton(messages, store.metaItem(member),
                    store.metaName(member),
                    List.of(on ? text("state-on", "Enabled") : text("state-off", "Disabled"),
                            text("editor-click-toggle", "Click to toggle")),
                    on,
                    player -> toggleMember(player, id, member, on)).silent());
        }
        return buttons;
    }

    private void toggleMember(Player player, String id, String member, boolean on) {
        if (denied(player)) {
            return;
        }
        if (on) {
            store.memberRemove(id, member);
        } else {
            store.memberAdd(id, member);
        }
        sounds.playNeutralSound(player);
    }

    private void renamePrompt(Player player, String id, Menu self) {
        if (denied(player)) {
            return;
        }
        dialogs.prompt(player, text("editor-rename-title", "Rename Id"), id,
                List.of(text("editor-rename-prompt", "Type the new id.")),
                raw -> {
                    Set<String> taken = new HashSet<>(store.presetNames());
                    taken.remove(id);
                    ModifierFieldEdits.Parsed<String> parsed = ModifierFieldEdits.id(raw, taken);
                    if (!parsed.ok()) {
                        invalid(player, parsed.error());
                        gui.navigate(player, self);
                        return;
                    }
                    store.renamePreset(id, parsed.value());
                    messages.message(player, "modifiers.edit-renamed",
                            Map.of("name", store.presetName(parsed.value())));
                    sounds.playNeutralSound(player);
                    gui.navigate(player, legacyEditor(parsed.value(), self.parent()));
                },
                () -> gui.navigate(player, self));
    }

    private void deleteConfirm(Player player, String id, Supplier<Menu> parent) {
        String name = store.presetName(id);
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages,
                        text("editor-delete-title", "Delete {name}?").replace("{name}", name)),
                Material.TNT, null,
                GuiTexts.lore(messages, text("editor-delete-confirm",
                        "This cannot be undone.")),
                GuiTexts.name(messages, text("cancel", "Cancel"), "Cancel"),
                back -> gui.navigate(back, parent.get()),
                GuiTexts.name(messages, text("confirm", "Confirm"), "Confirm"),
                done -> {
                    store.removePreset(id);
                    messages.message(done, "modifiers.edit-deleted", Map.of("name", name));
                    sounds.playNeutralSound(done);
                    gui.navigate(done, parent.get());
                },
                parent);
        gui.navigate(player, confirm);
    }

    private void fieldPrompt(Player player, Menu self, String label, String current, String id,
            boolean clearable, PresetSubmit submit) {
        String shown = current == null ? text("editor-unset", "Not set") : current;
        dialogs.prompt(player,
                text("editor-prompt-title", "Edit {label}").replace("{label}", label),
                SettingDialogs.safeInitial(current),
                List.of(text("editor-prompt-current", "Current value: {value}")
                        .replace("{value}", shown)),
                raw -> {
                    String error = clearable && raw.isBlank()
                            ? submit.submit(id, null)
                            : submit.submit(id, raw);
                    if (error != null) {
                        invalid(player, error);
                    } else {
                        sounds.playNeutralSound(player);
                    }
                    gui.navigate(player, self);
                },
                () -> gui.navigate(player, self));
    }

    /** Preset field submitter: patches the preset, returning an error or null. */
    private interface PresetSubmit {
        String submit(String id, String raw);
    }

    private MenuButton fieldButton(Material material, String label, String value,
            Consumer<Player> action) {
        return fieldButton(material, label, value,
                "editor-click-edit", "Click to edit", action);
    }

    private MenuButton fieldButton(Material material, String label, String value,
            String hintKey, String hintFallback, Consumer<Player> action) {
        return EditorButtons.valueButton(messages, material, label, value,
                text(hintKey, hintFallback), action);
    }

    private MenuButton scrollButton(String nameKey, String fallback, Menu[] self, int delta) {
        return new MenuButton(Material.ARROW,
                GuiTexts.name(messages, text(nameKey, fallback), fallback),
                null, false, false,
                player -> self[0].window().scrollLine(delta));
    }

    private String orUnset(String value) {
        return value == null || value.isBlank()
                ? text("editor-unset", "Not set") : value;
    }

    private boolean denied(Player player) {
        if (player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
            return false;
        }
        messages.message(player, "command.no-permission");
        return true;
    }

    private void invalid(Player player, String error) {
        messages.message(player, "modifiers.edit-invalid", Map.of("error", error));
        sounds.playAngrySound(player);
    }

    private String text(String key, String fallback) {
        return messages.string("modifiers-gui." + key, fallback);
    }
}
