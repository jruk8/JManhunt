package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.QuadPanel;
import com.jruk8.jmanhunt.gui.TwinPanel;
import com.jruk8.jmanhunt.gui.dialog.ModifierDialog;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierFieldEdits;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Modifier creator: a quad root over Meta, Behavior, Export, and Delete.
 *
 * <p>Meta edits display data through the shared {@link MetaQuad};
 * Behavior opens the twin panel over Behavior Options and Command
 * Lists; Export copies a share string and Delete confirms first.
 */
public final class ModifierEditorMenus {

    private final ModifierStore store;
    private final MessageService messages;
    private final SoundService sounds;
    private final GuiService gui;
    private final ModifiersCommand commands;
    private final SettingDialogs dialogs;
    private final ModifierDetailMenus detail;
    private final BehaviorOptionsMenus options;
    private final MetaQuad meta;

    /**
     * @param store modifier reads and patches; sounds, gui, commands, and
     *        dialogs are only touched inside click actions, so builders
     *        tolerate them as null
     */
    public ModifierEditorMenus(ModifierStore store, MessageService messages, SoundService sounds,
            GuiService gui, ModifiersCommand commands, SettingDialogs dialogs,
            ModifierDialog modifierDialogs) {
        this.store = store;
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.commands = commands;
        this.dialogs = dialogs;
        this.detail = new ModifierDetailMenus(store, messages, sounds, gui, dialogs);
        this.options = new BehaviorOptionsMenus(store, messages, sounds, gui, commands,
                dialogs, modifierDialogs);
        this.meta = new MetaQuad(messages, sounds, gui, dialogs);
    }

    /**
     * Create flow: prompts for a display name, then opens the new
     * modifier in the editor with the creator as author. Cancel returns
     * to the list.
     */
    public void createModifier(Player player, Supplier<Menu> listMenu) {
        if (denied(player)) {
            return;
        }
        dialogs.prompt(player,
                text("create-name-title", "Name your modifier"),
                List.of(text("create-name-prompt", "Type a display name.")),
                raw -> {
                    ModifierFieldEdits.Parsed<String> name = ModifierFieldEdits.name(raw);
                    if (!name.ok()) {
                        invalid(player, name.error());
                        gui.navigate(player, listMenu.get());
                        return;
                    }
                    String id = store.createModifier(name.value(), player.getName());
                    messages.message(player, "modifiers.create-success",
                            Map.of("type", "modifier", "name", store.metaName(id)));
                    sounds.playNeutralSound(player);
                    gui.navigate(player, editor(id, listMenu));
                },
                () -> gui.navigate(player, listMenu.get()));
    }

    /** Quad root for one modifier: Meta, Behavior, Export, Delete. */
    public Menu editor(String id, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        self[0] = QuadPanel.menu(
                GuiTexts.title(messages, text("editor-title-modifier", "Edit Modifier")),
                List.of(
                        EditorButtons.actionButton(messages, Material.NAME_TAG,
                                text("meta-title", "Meta"),
                                List.of(text("meta-lore", "Name, description, icon, author"),
                                        text("editor-click-open", "Click to open")),
                                player -> {
                                    if (denied(player)) {
                                        return;
                                    }
                                    gui.navigate(player, meta.menu(modifierTarget(id),
                                            () -> self[0],
                                            renamed -> editor(renamed, parent)));
                                }),
                        EditorButtons.actionButton(messages, Material.SCULK_SENSOR,
                                text("behavior-title", "Behavior"),
                                List.of(text("behavior-lore", "Triggers, options, commands"),
                                        text("editor-click-open", "Click to open")),
                                player -> {
                                    if (denied(player)) {
                                        return;
                                    }
                                    gui.navigate(player, behaviorMenu(id, () -> self[0]));
                                }),
                        EditorButtons.actionButton(messages, Material.LOOM,
                                text("editor-export", "Export"),
                                List.of(text("editor-export-lore", "Copy a share string"),
                                        text("editor-click-copy", "Click to copy")),
                                player -> commands.exportEntry(player, "modifier", id)).silent(),
                        EditorButtons.actionButton(messages, Material.TNT,
                                text("editor-delete-modifier", "Delete Modifier"),
                                List.of(text("editor-delete-lore",
                                                "Removes this modifier forever"),
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

    /** Behavior twin: Options on the left, Commands on the right. */
    public Menu behaviorMenu(String id, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        self[0] = TwinPanel.menu(
                GuiTexts.title(messages, text("behavior-title", "Behavior")),
                EditorButtons.actionButton(messages, Material.TRIPWIRE_HOOK,
                        text("behavior-options-title", "Behavior Options"),
                        List.of(text("behavior-options-lore",
                                        "Triggers, cadence, execution, chance"),
                                text("editor-click-open", "Click to open")),
                        player -> {
                            if (denied(player)) {
                                return;
                            }
                            gui.navigate(player, options.optionsMenu(id, () -> self[0]));
                        }),
                EditorButtons.actionButton(messages, Material.CHAIN_COMMAND_BLOCK,
                        text("commands-title", "Command Lists"),
                        List.of(text("commands-lore", "Runner commands and cleanup"),
                                text("editor-click-open", "Click to open")),
                        player -> {
                            if (denied(player)) {
                                return;
                            }
                            gui.navigate(player,
                                    detail.commandsMenu(id, () -> self[0]));
                        }),
                gui,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                parent);
        return self[0];
    }

    private MetaTarget modifierTarget(String id) {
        return new MetaTarget() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String name() {
                return store.metaName(id);
            }

            @Override
            public String description() {
                return store.metaDescription(id);
            }

            @Override
            public Material item() {
                return store.metaItem(id);
            }

            @Override
            public String author() {
                return store.metaAuthor(id);
            }

            @Override
            public void patchName(String name) {
                store.updateModifier(id,
                        entry -> ModifierStore.ensureMeta(entry).setName(name));
            }

            @Override
            public void patchDescription(String description) {
                store.updateModifier(id,
                        entry -> ModifierStore.ensureMeta(entry).setDescription(description));
            }

            @Override
            public void patchItem(Material item) {
                store.updateModifier(id,
                        entry -> ModifierStore.ensureMeta(entry).setItem(item.name()));
            }

            @Override
            public void patchAuthor(String author) {
                store.updateModifier(id,
                        entry -> ModifierStore.ensureMeta(entry).setAuthor(author));
            }

            @Override
            public Set<String> takenIds() {
                Set<String> taken = new HashSet<>(store.modifierNames());
                taken.remove(id);
                return taken;
            }

            @Override
            public void rename(String newId) {
                store.renameModifier(id, newId);
            }

            @Override
            public String displayName(String renamedId) {
                return store.metaName(renamedId);
            }
        };
    }

    private void deleteConfirm(Player player, String id, Supplier<Menu> parent) {
        String name = store.metaName(id);
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
                    store.removeModifier(id);
                    messages.message(done, "modifiers.edit-deleted", Map.of("name", name));
                    sounds.playNeutralSound(done);
                    gui.navigate(done, parent.get());
                },
                parent);
        gui.navigate(player, confirm);
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
