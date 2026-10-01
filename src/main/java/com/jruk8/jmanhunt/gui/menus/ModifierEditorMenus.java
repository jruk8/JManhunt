package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.QuadPanel;
import com.jruk8.jmanhunt.gui.ScalingLayout;
import com.jruk8.jmanhunt.gui.TwinPanel;
import com.jruk8.jmanhunt.gui.dialog.ModifierDialog;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.ManhuntGuiMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersGuiMessages;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierFieldEdits;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
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
    private final ModifiersGuiMessages modifiersGui;
    private final ManhuntGuiMessages manhuntGui;
    private final ModifiersMessages modifiers;
    private final CommandMessages command;
    private final SoundService sounds;
    private final GuiService gui;
    private final ModifiersCommand commands;
    private final SettingDialogs dialogs;
    private final ModifierDetailMenus detail;
    private final BehaviorOptionsMenus options;
    private final MetaQuad meta;

    /**
     * @param store modifier reads and patches; sounds, gui, commands,
     *        dialogs, and commandValidation are only touched inside click
     *        actions, so builders tolerate them as null
     */
    public ModifierEditorMenus(ModifierStore store, MessageService messages,
            ModifiersGuiMessages modifiersGui, ManhuntGuiMessages manhuntGui,
            ModifiersMessages modifiers, CommandMessages command, SoundService sounds,
            GuiService gui, ModifiersCommand commands, SettingDialogs dialogs,
            ModifierDialog modifierDialogs, BooleanSupplier commandValidation) {
        this.store = store;
        this.messages = messages;
        this.modifiersGui = modifiersGui;
        this.manhuntGui = manhuntGui;
        this.modifiers = modifiers;
        this.command = command;
        this.sounds = sounds;
        this.gui = gui;
        this.commands = commands;
        this.dialogs = dialogs;
        this.detail = new ModifierDetailMenus(store, messages, modifiersGui, modifiers, command,
                sounds, gui, dialogs, commandValidation, commands);
        this.options = new BehaviorOptionsMenus(store, messages, modifiersGui, manhuntGui,
                modifiers, command, sounds, gui, dialogs, modifierDialogs);
        this.meta = new MetaQuad(messages, modifiersGui, modifiers, command, sounds, gui, dialogs);
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
                modifiersGui.getCreateNameTitle(),
                List.of(modifiersGui.getCreateNamePrompt()),
                raw -> {
                    ModifierFieldEdits.Parsed<String> name = ModifierFieldEdits.name(raw);
                    if (!name.ok()) {
                        invalid(player, name.error());
                        gui.navigate(player, listMenu.get());
                        return;
                    }
                    String id = store.createModifier(name.value(), player.getName());
                    messages.messageRaw(player, modifiers.getCreateSuccess(),
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
                GuiTexts.title(messages, modifiersGui.getEditorTitleModifier()),
                List.of(
                        EditorButtons.actionButton(messages, Material.NAME_TAG,
                                modifiersGui.getMetaTitle(),
                                List.of(modifiersGui.getMetaLore(),
                                        modifiersGui.getEditorClickOpen()),
                                player -> openMeta(player, id, parent, () -> self[0])),
                        EditorButtons.actionButton(messages, Material.SCULK_SENSOR,
                                modifiersGui.getBehaviorTitle(),
                                List.of(modifiersGui.getBehaviorLore(),
                                        modifiersGui.getEditorClickOpen()),
                                player -> {
                                    if (denied(player)) {
                                        return;
                                    }
                                    gui.navigate(player, behaviorMenu(id, () -> self[0]));
                                }),
                        EditorButtons.actionButton(messages, Material.LOOM,
                                modifiersGui.getEditorExport(),
                                List.of(modifiersGui.getEditorExportLore(),
                                        modifiersGui.getEditorClickCopy()),
                                player -> commands.exportEntry(player, "modifier", id)).silent(),
                        deleteButton(id, parent, () -> self[0])),
                gui,
                GuiTexts.name(messages, modifiersGui.getBack(), "Back"),
                parent);
        return self[0];
    }

    /** Opens the Meta quad, staying on it across id renames. */
    private void openMeta(Player player, String id, Supplier<Menu> parent,
            Supplier<Menu> self) {
        if (denied(player)) {
            return;
        }
        Function<String, Menu> reopenMeta = new Function<>() {
            @Override
            public Menu apply(String renamed) {
                return meta.menu(modifierTarget(renamed),
                        () -> editor(renamed, parent), this);
            }
        };
        gui.navigate(player, meta.menu(modifierTarget(id), self, reopenMeta));
    }

    private MenuButton deleteButton(String id, Supplier<Menu> parent, Supplier<Menu> editor) {
        return EditorButtons.actionButton(messages, Material.TNT,
                modifiersGui.getEditorDeleteModifier(),
                List.of(modifiersGui.getEditorDeleteLore(),
                        modifiersGui.getEditorClickDelete()),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    deleteConfirm(player, id, parent, editor);
                });
    }

    /** Behavior list: one button per index, plus add. Edits open the twin. */
    public Menu behaviorMenu(String id, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> behaviorButtons(id, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(messages, "Behaviors"),
                layout,
                () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        new MenuButton(Material.PAPER,
                                GuiTexts.name(messages, modifiersGui.getBack(), "Back"),
                                null, false, false,
                                player -> gui.back(player, self[0]))),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> behaviorButtons(String id, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        for (int index : store.behaviorIndexes(id)) {
            List<String> triggers = store.runsOn(id, index);
            String summary = triggers.isEmpty()
                    ? "No triggers"
                    : String.join(", ", triggers);
            buttons.add(new MenuButton(Material.TRIPWIRE_HOOK,
                    GuiTexts.name(messages,
                            "Behavior {index}"
                                    .replace("{index}", String.valueOf(index)),
                            "Behavior " + index),
                    GuiTexts.lore(messages, List.of(
                            summary,
                            modifiersGui.getEditorClickOpen(),
                            "Right-click to delete")),
                    false, false,
                    player -> {
                        if (denied(player)) {
                            return;
                        }
                        gui.navigate(player, behaviorTwin(id, index, self));
                    },
                    player -> {
                        if (denied(player)) {
                            return;
                        }
                        deleteBehaviorConfirm(player, id, index, self);
                    }).silent());
        }
        buttons.add(AddStick.button(messages,
                "Add Behavior",
                List.of(modifiersGui.getEditorClickOpen()),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    int created = store.addBehavior(id);
                    gui.navigate(player, behaviorTwin(id, created, self));
                }));
        return buttons;
    }

    private void deleteBehaviorConfirm(Player player, String id, int index, Supplier<Menu> self) {
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages, "Delete behavior {index}?"
                        .replace("{index}", String.valueOf(index))),
                Material.TRIPWIRE_HOOK, null,
                GuiTexts.lore(messages, modifiersGui.getEditorDeleteConfirm()),
                GuiTexts.name(messages, modifiersGui.getCancel(), "Cancel"),
                back -> gui.navigate(back, self.get()),
                GuiTexts.name(messages, modifiersGui.getConfirm(), "Confirm"),
                done -> {
                    store.removeBehavior(id, index);
                    sounds.playNeutralSound(done);
                    gui.navigate(done, self.get());
                },
                self);
        gui.navigate(player, confirm);
    }

    /** Behavior twin: Options on the left, Commands on the right. */
    public Menu behaviorTwin(String id, int index, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        self[0] = TwinPanel.menu(
                GuiTexts.title(messages, modifiersGui.getBehaviorTitle()),
                EditorButtons.actionButton(messages, Material.TRIPWIRE_HOOK,
                        modifiersGui.getBehaviorOptionsTitle(),
                        List.of(modifiersGui.getBehaviorOptionsLore(),
                                modifiersGui.getEditorClickOpen()),
                        player -> {
                            if (denied(player)) {
                                return;
                            }
                            gui.navigate(player, options.optionsMenu(id, index, () -> self[0]));
                        }),
                EditorButtons.actionButton(messages, Material.CHAIN_COMMAND_BLOCK,
                        modifiersGui.getCommandsTitle(),
                        List.of(modifiersGui.getCommandsLore(),
                                modifiersGui.getEditorClickOpen()),
                        player -> {
                            if (denied(player)) {
                                return;
                            }
                            gui.navigate(player,
                                    detail.commandsMenu(id, index, () -> self[0]));
                        }),
                gui,
                GuiTexts.name(messages, modifiersGui.getBack(), "Back"),
                parent);
        return self[0];
    }

    private MetaTarget modifierTarget(String id) {
        return new ModifierMetaTarget(store, id);
    }

    /** Store-backed target so the shared meta quad edits one modifier. */
    private static final class ModifierMetaTarget implements MetaTarget {
        private final ModifierStore store;
        private final String id;

        private ModifierMetaTarget(ModifierStore store, String id) {
            this.store = store;
            this.id = id;
        }

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
    }

    private void deleteConfirm(Player player, String id, Supplier<Menu> parent, Supplier<Menu> editor) {
        String name = store.metaName(id);
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages,
                        modifiersGui.getEditorDeleteTitle().replace("{name}", name)),
                Material.TNT, null,
                GuiTexts.lore(messages, modifiersGui.getEditorDeleteConfirm()),
                GuiTexts.name(messages, modifiersGui.getCancel(), "Cancel"),
                back -> gui.navigate(back, editor.get()),
                GuiTexts.name(messages, modifiersGui.getConfirm(), "Confirm"),
                done -> {
                    store.removeModifier(id);
                    messages.messageRaw(done, modifiers.getEditDeleted(), Map.of("name", name));
                    sounds.playDestructiveSound(done);
                    gui.navigate(done, parent.get());
                },
                parent);
        gui.navigate(player, confirm);
    }

    private boolean denied(Player player) {
        if (player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
            return false;
        }
        messages.messageRaw(player, command.getNoPermission());
        return true;
    }

    private void invalid(Player player, String error) {
        messages.messageRaw(player, modifiers.getEditInvalid(), Map.of("error", error));
        sounds.playAngrySound(player);
    }


}
