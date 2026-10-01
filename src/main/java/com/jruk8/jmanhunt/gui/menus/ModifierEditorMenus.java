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
    /** Message bus, gui texts, modifier/command texts, and sounds. */
    public record EditorTexts(MessageService messages, ModifiersGuiMessages modifiersGui,
            ManhuntGuiMessages manhuntGui, ModifiersMessages modifiers,
            CommandMessages command, SoundService sounds) {
    }

    /** Gui, commands, dialogs, and validation; nulls tolerated per action. */
    public record EditorDeps(GuiService gui, ModifiersCommand commands, SettingDialogs dialogs,
            ModifierDialog modifierDialogs, BooleanSupplier commandValidation) {
    }

    private final ModifierStore store;
    private final EditorTexts texts;
    private final EditorDeps deps;
    private final ModifierDetailMenus detail;
    private final BehaviorOptionsMenus options;
    private final MetaQuad meta;

    public ModifierEditorMenus(ModifierStore store, EditorTexts texts, EditorDeps deps) {
        this.store = store;
        this.texts = texts;
        this.deps = deps;
        this.detail = new ModifierDetailMenus(store,
                new ModifierDetailMenus.DetailTexts(texts.messages(), texts.modifiersGui(),
                        texts.modifiers(), texts.command(), texts.sounds()),
                new ModifierDetailMenus.DetailDeps(deps.gui(), deps.dialogs(),
                        deps.commandValidation(), deps.commands()));
        this.options = new BehaviorOptionsMenus(store,
                new BehaviorOptionsMenus.BehaviorTexts(texts.messages(), texts.modifiersGui(),
                        texts.manhuntGui(), texts.modifiers(), texts.command(),
                        texts.sounds()),
                new BehaviorOptionsMenus.BehaviorDeps(deps.gui(), deps.dialogs(),
                        deps.modifierDialogs()));
        this.meta = new MetaQuad(
                new MetaQuad.MetaTexts(texts.messages(), texts.modifiersGui(),
                        texts.modifiers(), texts.command(), texts.sounds()),
                deps.gui(), deps.dialogs());
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
        deps.dialogs().prompt(player,
                texts.modifiersGui().getCreateNameTitle(),
                List.of(texts.modifiersGui().getCreateNamePrompt()),
                raw -> {
                    ModifierFieldEdits.Parsed<String> name = ModifierFieldEdits.name(raw);
                    if (!name.ok()) {
                        invalid(player, name.error());
                        deps.gui().navigate(player, listMenu.get());
                        return;
                    }
                    String id = store.createModifier(name.value(), player.getName());
                    texts.messages().messageRaw(player, texts.modifiers().getCreateSuccess(),
                            Map.of("type", "modifier", "name", store.metaName(id)));
                    texts.sounds().playNeutralSound(player);
                    deps.gui().navigate(player, editor(id, listMenu));
                },
                () -> deps.gui().navigate(player, listMenu.get()));
    }

    /** Quad root for one modifier: Meta, Behavior, Export, Delete. */
    public Menu editor(String id, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        self[0] = QuadPanel.menu(
                GuiTexts.title(texts.messages(), texts.modifiersGui().getEditorTitleModifier()),
                List.of(
                        EditorButtons.actionButton(texts.messages(), Material.NAME_TAG,
                                texts.modifiersGui().getMetaTitle(),
                                List.of(texts.modifiersGui().getMetaLore(),
                                        texts.modifiersGui().getEditorClickOpen()),
                                player -> openMeta(player, id, parent, () -> self[0])),
                        EditorButtons.actionButton(texts.messages(), Material.SCULK_SENSOR,
                                texts.modifiersGui().getBehaviorTitle(),
                                List.of(texts.modifiersGui().getBehaviorLore(),
                                        texts.modifiersGui().getEditorClickOpen()),
                                player -> {
                                    if (denied(player)) {
                                        return;
                                    }
                                    deps.gui().navigate(player, behaviorMenu(id, () -> self[0]));
                                }),
                        EditorButtons.actionButton(texts.messages(), Material.LOOM,
                                texts.modifiersGui().getEditorExport(),
                                List.of(texts.modifiersGui().getEditorExportLore(),
                                        texts.modifiersGui().getEditorClickCopy()),
                                player -> deps.commands().exportEntry(player, "modifier", id)).silent(),
                        deleteButton(id, parent, () -> self[0])),
                deps.gui(),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getBack(), "Back"),
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
        deps.gui().navigate(player, meta.menu(modifierTarget(id), self, reopenMeta));
    }

    private MenuButton deleteButton(String id, Supplier<Menu> parent, Supplier<Menu> editor) {
        return EditorButtons.actionButton(texts.messages(), Material.TNT,
                texts.modifiersGui().getEditorDeleteModifier(),
                List.of(texts.modifiersGui().getEditorDeleteLore(),
                        texts.modifiersGui().getEditorClickDelete()),
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
                GuiTexts.title(texts.messages(), "Behaviors"),
                layout,
                () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        new MenuButton(new MenuButton.Spec(Material.PAPER,
                                GuiTexts.name(texts.messages(), texts.modifiersGui().getBack(), "Back"),
                                null, false, false,
                                player -> deps.gui().back(player, self[0]), null, null,
                                        MenuButton.SoundPolicy.CLICK, null))),
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
            buttons.add(new MenuButton(new MenuButton.Spec(Material.TRIPWIRE_HOOK,
                    GuiTexts.name(texts.messages(),
                            "Behavior {index}"
                                    .replace("{index}", String.valueOf(index)),
                            "Behavior " + index),
                    GuiTexts.lore(texts.messages(), List.of(
                            summary,
                            texts.modifiersGui().getEditorClickOpen(),
                            "Right-click to delete")),
                    false, false,
                    player -> {
                        if (denied(player)) {
                            return;
                        }
                        deps.gui().navigate(player, behaviorTwin(id, index, self));
                    },
                    player -> {
                        if (denied(player)) {
                            return;
                        }
                        deleteBehaviorConfirm(player, id, index, self);
                    }, null, MenuButton.SoundPolicy.CLICK, null)).silent());
        }
        buttons.add(AddStick.button(texts.messages(),
                "Add Behavior",
                List.of(texts.modifiersGui().getEditorClickOpen()),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    int created = store.addBehavior(id);
                    deps.gui().navigate(player, behaviorTwin(id, created, self));
                }));
        return buttons;
    }

    private void deleteBehaviorConfirm(Player player, String id, int index, Supplier<Menu> self) {
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(texts.messages(), "Delete behavior {index}?"
                        .replace("{index}", String.valueOf(index))),
                Material.TRIPWIRE_HOOK, null,
                GuiTexts.lore(texts.messages(), texts.modifiersGui().getEditorDeleteConfirm()),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getCancel(), "Cancel"),
                back -> deps.gui().navigate(back, self.get()),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getConfirm(), "Confirm"),
                done -> {
                    store.removeBehavior(id, index);
                    texts.sounds().playNeutralSound(done);
                    deps.gui().navigate(done, self.get());
                },
                self);
        deps.gui().navigate(player, confirm);
    }

    /** Behavior twin: Options on the left, Commands on the right. */
    public Menu behaviorTwin(String id, int index, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        self[0] = TwinPanel.menu(
                GuiTexts.title(texts.messages(), texts.modifiersGui().getBehaviorTitle()),
                EditorButtons.actionButton(texts.messages(), Material.TRIPWIRE_HOOK,
                        texts.modifiersGui().getBehaviorOptionsTitle(),
                        List.of(texts.modifiersGui().getBehaviorOptionsLore(),
                                texts.modifiersGui().getEditorClickOpen()),
                        player -> {
                            if (denied(player)) {
                                return;
                            }
                            deps.gui().navigate(player, options.optionsMenu(id, index, () -> self[0]));
                        }),
                EditorButtons.actionButton(texts.messages(), Material.CHAIN_COMMAND_BLOCK,
                        texts.modifiersGui().getCommandsTitle(),
                        List.of(texts.modifiersGui().getCommandsLore(),
                                texts.modifiersGui().getEditorClickOpen()),
                        player -> {
                            if (denied(player)) {
                                return;
                            }
                            deps.gui().navigate(player,
                                    detail.commandsMenu(id, index, () -> self[0]));
                        }),
                deps.gui(),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getBack(), "Back"),
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
                GuiTexts.title(texts.messages(),
                        texts.modifiersGui().getEditorDeleteTitle().replace("{name}", name)),
                Material.TNT, null,
                GuiTexts.lore(texts.messages(), texts.modifiersGui().getEditorDeleteConfirm()),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getCancel(), "Cancel"),
                back -> deps.gui().navigate(back, editor.get()),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getConfirm(), "Confirm"),
                done -> {
                    store.removeModifier(id);
                    texts.messages().messageRaw(done, texts.modifiers().getEditDeleted(), Map.of("name", name));
                    texts.sounds().playDestructiveSound(done);
                    deps.gui().navigate(done, parent.get());
                },
                parent);
        deps.gui().navigate(player, confirm);
    }

    private boolean denied(Player player) {
        if (player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
            return false;
        }
        texts.messages().messageRaw(player, texts.command().getNoPermission());
        return true;
    }

    private void invalid(Player player, String error) {
        texts.messages().messageRaw(player, texts.modifiers().getEditInvalid(), Map.of("error", error));
        texts.sounds().playAngrySound(player);
    }


}
