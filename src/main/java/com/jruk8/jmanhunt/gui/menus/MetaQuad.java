package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.QuadPanel;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
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
    private final SettingDialogs dialogs;

    public MetaQuad(MessageService messages, SoundService sounds,
            GuiService gui, SettingDialogs dialogs) {
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.dialogs = dialogs;
    }

    /**
     * @param target live display-data reads and patches
     * @param parent menu back returns to, usually the creator root
     * @param reopenRoot rebuilds the creator root after a rename
     * @return the Meta quad menu
     */
    public Menu menu(MetaTarget target, Supplier<Menu> parent,
            Function<String, Menu> reopenRoot) {
        final Menu[] self = new Menu[1];
        self[0] = QuadPanel.menu(
                GuiTexts.title(messages, text("meta-title", "Meta")),
                List.of(
                        nameButton(target, self, reopenRoot),
                        EditorButtons.valueButton(messages, Material.BOOK,
                                "Description", orUnset(target.description()),
                                text("editor-click-edit", "Click to edit"),
                                player -> fieldPrompt(player, self[0], "Description",
                                        target.description(), true, raw -> {
                                            target.patchDescription(raw);
                                            return null;
                                        })),
                        EditorButtons.valueButton(messages, target.item(),
                                "Icon", target.item().name(),
                                text("editor-click-edit", "Click to edit"),
                                player -> fieldPrompt(player, self[0], "Icon",
                                        target.item().name(), false, raw -> {
                                            ModifierFieldEdits.Parsed<Material> item =
                                                    ModifierFieldEdits.item(raw);
                                            if (!item.ok()) {
                                                return item.error();
                                            }
                                            target.patchItem(item.value());
                                            // The icon-set chat confirmation
                                            // lands with the command
                                            // feedback work in Phase 6.
                                            return null;
                                        })),
                        EditorButtons.valueButton(messages, Material.PLAYER_HEAD,
                                "Author", orUnset(target.author()),
                                text("editor-click-edit", "Click to edit"),
                                player -> fieldPrompt(player, self[0], "Author",
                                        target.author() == null ? "" : target.author(),
                                        true, raw -> {
                                            target.patchAuthor(raw);
                                            return null;
                                        }))),
                gui,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                parent);
        return self[0];
    }

    private MenuButton nameButton(MetaTarget target, Menu[] self,
            Function<String, Menu> reopenRoot) {
        return new MenuButton(Material.NAME_TAG,
                GuiTexts.name(messages, "Name", "Name"),
                GuiTexts.lore(messages, List.of(
                        EditorButtons.currentLine(messages, target.name()),
                        text("editor-click-edit", "Click to edit"),
                        text("editor-rename-hint", "Right-click to rename id"))),
                false, false,
                player -> fieldPrompt(player, self[0], "Name", target.name(), false,
                        raw -> {
                            ModifierFieldEdits.Parsed<String> name =
                                    ModifierFieldEdits.name(raw);
                            if (!name.ok()) {
                                return name.error();
                            }
                            target.patchName(name.value());
                            return null;
                        }),
                player -> renamePrompt(player, self[0], target, reopenRoot)).silent();
    }

    private void fieldPrompt(Player player, Menu self, String label, String current,
            boolean clearable, FieldPrompts.Submit submit) {
        FieldPrompts.prompt(dialogs, gui, messages, sounds, player, self,
                text("editor-prompt-title", "Edit {label}").replace("{label}", label),
                current, clearable, submit);
    }

    private void renamePrompt(Player player, Menu self, MetaTarget target,
            Function<String, Menu> reopenRoot) {
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
                        gui.navigate(player, self);
                        return;
                    }
                    target.rename(parsed.value());
                    messages.message(player, "modifiers.edit-renamed",
                            Map.of("name", target.displayName(parsed.value())));
                    sounds.playNeutralSound(player);
                    gui.navigate(player, reopenRoot.apply(parsed.value()));
                },
                () -> gui.navigate(player, self));
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
