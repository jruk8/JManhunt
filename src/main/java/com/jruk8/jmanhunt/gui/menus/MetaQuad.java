package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.QuadPanel;
import com.jruk8.jmanhunt.gui.dialog.SettingDialog;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierFieldEdits;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Shared Meta quad for one modifier or preset: Name, Description, Icon,
 * and Author over a {@link MetaTarget}. Prompts prefill the live value
 * and validate through {@link ModifierFieldEdits}, so the GUI accepts
 * exactly what the CLI accepts. Left-clicking Name edits the display
 * name while right-clicking it renames the id.
 */
public final class MetaQuad {

    private final MessageService messages;
    private final SoundService sounds;
    private final GuiService gui;
    private final SettingDialog dialogs;

    public MetaQuad(MessageService messages, SoundService sounds,
            GuiService gui, SettingDialog dialogs) {
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.dialogs = dialogs;
    }

    /**
     * @param target live display-data reads and patches
     * @param parent menu back returns to, usually the creator root
     * @param reopenMeta rebuilds this same Meta menu over a fresh target
     * after a rename
     * @return the Meta quad menu
     */
    public Menu menu(MetaTarget target, Supplier<Menu> parent,
            Function<String, Menu> reopenMeta) {
        final Menu[] self = new Menu[1];
        self[0] = QuadPanel.menu(
                GuiTexts.title(messages, text("meta-title", "Meta")),
                List.of(
                        nameButton(target, parent, reopenMeta),
                        EditorButtons.valueButton(messages, Material.BOOK,
                                "Description", orUnset(target.description()),
                                text("editor-click-edit", "Click to edit"),
                                player -> fieldPrompt(player, reopen(target, parent, reopenMeta), "Description",
                                        target.description(), true, raw -> {
                                            target.patchDescription(raw);
                                            return null;
                                        })),
                        EditorButtons.valueButton(messages, target.item(),
                                "Icon", target.item().name(),
                                text("editor-click-edit", "Click to edit"),
                                player -> fieldPrompt(player, reopen(target, parent, reopenMeta), "Icon",
                                        target.item().name(), false, raw -> {
                                            ModifierFieldEdits.Parsed<Material> item =
                                                    ModifierFieldEdits.item(raw);
                                            if (!item.ok()) {
                                                return item.error();
                                            }
                                            target.patchItem(item.value());
                                            messages.message(player, "modifiers.edit-icon-set",
                                                    Map.of("material", item.value().name()));
                                            return null;
                                        })),
                        EditorButtons.valueButton(messages, Material.PLAYER_HEAD,
                                "Author", orUnset(target.author()),
                                text("editor-click-edit", "Click to edit"),
                                player -> fieldPrompt(player, reopen(target, parent, reopenMeta), "Author",
                                        target.author() == null ? "" : target.author(),
                                        true, raw -> {
                                            target.patchAuthor(raw);
                                            return null;
                                        })).withMeta(meta ->
                                                AuthorHeads.applyTo(meta, target.author()))),
                gui,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                parent);
        return self[0];
    }

    private MenuButton nameButton(MetaTarget target, Supplier<Menu> parent,
            Function<String, Menu> reopenMeta) {
        return new MenuButton(Material.NAME_TAG,
                GuiTexts.name(messages, "Name", "Name"),
                GuiTexts.lore(messages, List.of(
                        EditorButtons.currentLine(messages, target.name()),
                        messages.string("modifiers-gui.editor-id", "Id: <white>{id}")
                                .replace("{id}", target.id()),
                        text("editor-click-edit", "Click to edit"),
                        text("editor-rename-hint", "Right-click to rename id"))),
                false, false,
                player -> fieldPrompt(player, reopen(target, parent, reopenMeta),
                        "Name", target.name(), false,
                        raw -> {
                            ModifierFieldEdits.Parsed<String> name =
                                    ModifierFieldEdits.name(raw);
                            if (!name.ok()) {
                                return name.error();
                            }
                            target.patchName(name.value());
                            messages.message(player, "modifiers.edit-renamed",
                                    Map.of("name", target.displayName(target.id())));
                            sounds.playNeutralSound(player);
                            return null;
                        }),
                player -> renamePrompt(player, reopen(target, parent, reopenMeta),
                        target, reopenMeta)).silent();
    }

    /** Rebuild supplier so prompt callbacks reopen a fresh quad. */
    private Supplier<Menu> reopen(MetaTarget target, Supplier<Menu> parent,
            Function<String, Menu> reopenMeta) {
        return () -> menu(target, parent, reopenMeta);
    }

    private void fieldPrompt(Player player, Supplier<Menu> reopen, String label, String current,
            boolean clearable, FieldPrompts.Submit submit) {
        FieldPrompts.prompt(dialogs, gui, messages, sounds, player, reopen,
                text("editor-prompt-title", "Edit {label}").replace("{label}", label),
                current, clearable, submit);
    }

    private void renamePrompt(Player player, Supplier<Menu> reopen, MetaTarget target,
            Function<String, Menu> reopenMeta) {
        if (denied(player)) {
            return;
        }
        dialogs.prompt(player, text("editor-rename-title", "Rename Id"), target.id(),
                List.of(text("editor-rename-prompt", "Type the new id.")),
                raw -> {
                    ModifierFieldEdits.Parsed<String> parsed =
                            ModifierFieldEdits.id(raw, target.takenIds());
                    if (!parsed.ok()) {
                        invalid(player, parsed.error());
                        gui.navigate(player, reopen.get());
                        return;
                    }
                    target.rename(parsed.value());
                    messages.message(player, "modifiers.edit-id-changed",
                            Map.of("name", parsed.value()));
                    sounds.playNeutralSound(player);
                    gui.navigate(player, reopenMeta.apply(parsed.value()));
                },
                () -> gui.navigate(player, reopen.get()));
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
