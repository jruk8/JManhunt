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
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersGuiMessages;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierFieldEdits;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
    /** Message bus, gui/modifier/command texts, and sounds. */
    public record PresetTexts(MessageService messages, ModifiersGuiMessages modifiersGui,
            ModifiersMessages modifiers, CommandMessages command, SoundService sounds) {
    }

    /** Gui, commands, and dialogs; nulls tolerated per action. */
    public record PresetDeps(GuiService gui, ModifiersCommand commands, SettingDialogs dialogs) {
    }

    private final ModifierStore store;
    private final PresetTexts texts;
    private final PresetDeps deps;
    private final MetaQuad meta;

    public PresetEditorMenus(ModifierStore store, PresetTexts texts, PresetDeps deps) {
        this.store = store;
        this.texts = texts;
        this.deps = deps;
        this.meta = new MetaQuad(
                new MetaQuad.MetaTexts(texts.messages(), texts.modifiersGui(),
                        texts.modifiers(), texts.command(), texts.sounds()),
                deps.gui(), deps.dialogs());
    }

    private MetaTarget presetTarget(String id) {
        return new PresetMetaTarget(store, id);
    }

    /** Store-backed target so the shared meta quad edits one preset. */
    private static final class PresetMetaTarget implements MetaTarget {
        private final ModifierStore store;
        private final String id;

        private PresetMetaTarget(ModifierStore store, String id) {
            this.store = store;
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return store.presetName(id);
        }

        @Override
        public String description() {
            return store.presetDescription(id);
        }

        @Override
        public Material item() {
            return store.presetItem(id);
        }

        @Override
        public String author() {
            return store.presetAuthor(id);
        }

        @Override
        public void patchName(String name) {
            store.updatePreset(id, preset -> preset.getMeta().setName(name));
        }

        @Override
        public void patchDescription(String description) {
            store.updatePreset(id, preset -> preset.getMeta().setDescription(description));
        }

        @Override
        public void patchItem(Material item) {
            store.updatePreset(id, preset -> preset.getMeta().setItem(item.name()));
        }

        @Override
        public void patchAuthor(String author) {
            store.updatePreset(id, preset -> preset.getMeta().setAuthor(author));
        }

        @Override
        public Set<String> takenIds() {
            Set<String> taken = new HashSet<>(store.presetNames());
            taken.remove(id);
            return taken;
        }

        @Override
        public void rename(String newId) {
            store.renamePreset(id, newId);
        }

        @Override
        public String displayName(String renamedId) {
            return store.presetName(renamedId);
        }
    }

    /**
     * Create flow: prompts for a display name, then opens the new
     * preset in the editor. Cancel returns to the list.
     */
    public void createPreset(Player player, Supplier<Menu> listMenu) {
        if (denied(player)) {
            return;
        }
        deps.dialogs().prompt(player,
                texts.modifiersGui().getCreatePresetNameTitle(),
                List.of(texts.modifiersGui().getCreateNamePrompt()),
                raw -> {
                    ModifierFieldEdits.Parsed<String> name = ModifierFieldEdits.name(raw);
                    if (!name.ok()) {
                        invalid(player, name.error());
                        deps.gui().navigate(player, listMenu.get());
                        return;
                    }
                    String id = store.createPreset(name.value(), player.getName());
                    texts.messages().messageRaw(player, texts.modifiers().getCreateSuccess(),
                            Map.of("type", "preset", "name", store.presetName(id)));
                    texts.sounds().playNeutralSound(player);
                    deps.gui().navigate(player, editor(id, listMenu));
                },
                () -> deps.gui().navigate(player, listMenu.get()));
    }

    /** Quad root for one preset: Meta, Modifiers, Export, Delete. */
    public Menu editor(String id, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        self[0] = QuadPanel.menu(
                GuiTexts.title(texts.messages(), texts.modifiersGui().getEditorTitlePreset()),
                List.of(
                        EditorButtons.actionButton(texts.messages(), Material.NAME_TAG,
                                texts.modifiersGui().getMetaTitle(),
                                List.of(texts.modifiersGui().getMetaLore(),
                                        texts.modifiersGui().getEditorClickOpen()),
                                player -> openMeta(player, id, parent, () -> self[0])),
                        EditorButtons.actionButton(texts.messages(), Material.FILLED_MAP,
                                texts.modifiersGui().getModifiersTitle(),
                                List.of(texts.modifiersGui().getMembersLore().replace("{total}",
                                                String.valueOf(store.presetMembers(id).size())),
                                        texts.modifiersGui().getEditorClickOpen()),
                                player -> {
                                    if (denied(player)) {
                                        return;
                                    }
                                    deps.gui().navigate(player,
                                            membersMenu(id, () -> editor(id, parent)));
                                }),
                        EditorButtons.actionButton(texts.messages(), Material.LOOM,
                                texts.modifiersGui().getEditorExport(),
                                List.of(texts.modifiersGui().getEditorExportLore(),
                                        texts.modifiersGui().getEditorClickCopy()),
                                player -> deps.commands().exportEntry(player, "preset", id)).silent(),
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
                return meta.menu(presetTarget(renamed),
                        () -> editor(renamed, parent), this);
            }
        };
        deps.gui().navigate(player, meta.menu(presetTarget(id), self, reopenMeta));
    }

    private MenuButton deleteButton(String id, Supplier<Menu> parent, Supplier<Menu> editor) {
        return EditorButtons.actionButton(texts.messages(), Material.TNT,
                texts.modifiersGui().getEditorDeletePreset(),
                List.of(texts.modifiersGui().getEditorDeletePresetLore(),
                        texts.modifiersGui().getEditorClickDelete()),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    deleteConfirm(player, id, parent, editor);
                });
    }

    /** Scrollable modifier toggles over every modifier. */
    public Menu membersMenu(String id, Supplier<Menu> parent) {
        MenuLayout layout = MenuLayout.parse(
                "##xxxxxx#", "u#xxxxxx#", "b#xxxxxxt", "d#xxxxxx#", "##xxxxxx#");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(texts.messages(), texts.modifiersGui().getModifiersTitle()),
                layout, () -> membersStatic(self),
                () -> memberButtons(id, layout.contentColumns()), parent);
        return self[0];
    }

    private Map<Integer, MenuButton> membersStatic(Menu[] self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(9, scrollButton(texts.modifiersGui().getScrollUp(), "Scroll up", self, -1));
        fixed.put(18, new MenuButton(new MenuButton.Spec(Material.PAPER,
                GuiTexts.name(texts.messages(), texts.modifiersGui().getBack(), "Back"),
                null, false, false,
                player -> deps.gui().back(player, self[0]), null, null, MenuButton.SoundPolicy.CLICK, null)));
        fixed.put(27, scrollButton(texts.modifiersGui().getScrollDown(), "Scroll down", self, 1));
        return fixed;
    }

    private List<MenuButton> memberButtons(String id, int columns) {
        Set<String> members = new HashSet<>(store.presetMembers(id));
        Map<String, Integer> order = MenuOrder.fileOrder(store.modifierNames());
        List<String> ids = new ArrayList<>(store.modifierNames());
        ids.sort(MenuOrder.modifiers(store::metaName, members::contains, order::get));
        int enabled = 0;
        while (enabled < ids.size() && members.contains(ids.get(enabled))) {
            enabled++;
        }
        List<MenuButton> buttons = new ArrayList<>();
        for (int index = 0; index < enabled; index++) {
            buttons.add(memberButton(id, ids.get(index), true));
        }
        MenuOrder.padGroup(buttons, enabled, columns);
        for (int index = enabled; index < ids.size(); index++) {
            buttons.add(memberButton(id, ids.get(index), false));
        }
        return buttons;
    }

    private MenuButton memberButton(String id, String member, boolean on) {
        return EditorButtons.actionButton(texts.messages(), store.metaItem(member),
                store.metaName(member),
                List.of(on ? texts.modifiersGui().getStateOn() : texts.modifiersGui().getStateOff(),
                        texts.modifiersGui().getEditorClickToggle()),
                on,
                player -> toggleMember(player, id, member, on)).silent();
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
        texts.sounds().playNeutralSound(player);
    }

    private void deleteConfirm(Player player, String id, Supplier<Menu> parent, Supplier<Menu> editor) {
        String name = store.presetName(id);
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(texts.messages(),
                        texts.modifiersGui().getEditorDeleteTitle().replace("{name}", name)),
                Material.TNT, null,
                GuiTexts.lore(texts.messages(), texts.modifiersGui().getEditorDeleteConfirm()),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getCancel(), "Cancel"),
                back -> deps.gui().navigate(back, editor.get()),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getConfirm(), "Confirm"),
                done -> {
                    store.removePreset(id);
                    texts.messages().messageRaw(done, texts.modifiers().getEditDeleted(), Map.of("name", name));
                    texts.sounds().playDestructiveSound(done);
                    deps.gui().navigate(done, parent.get());
                },
                parent);
        deps.gui().navigate(player, confirm);
    }

    private MenuButton scrollButton(String name, String fallback, Menu[] self, int delta) {
        return new MenuButton(new MenuButton.Spec(Material.ARROW,
                GuiTexts.name(texts.messages(), name, fallback),
                null, false, false,
                player -> self[0].window().scrollLine(delta), null, null, MenuButton.SoundPolicy.CLICK, null));
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
