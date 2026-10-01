package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.QuadPanel;
import com.jruk8.jmanhunt.gui.dialog.SettingDialog;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersGuiMessages;
import com.jruk8.jmanhunt.message.ModifiersMessages;
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
    /** Message bus, gui/modifier/command texts, and sounds. */
    public record MetaTexts(MessageService messages, ModifiersGuiMessages modifiersGui,
            ModifiersMessages modifiers, CommandMessages command, SoundService sounds) {
    }

    private final MetaTexts texts;
    private final GuiService gui;
    private final SettingDialog dialogs;

    public MetaQuad(MetaTexts texts, GuiService gui, SettingDialog dialogs) {
        this.texts = texts;
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
                GuiTexts.title(texts.messages(), texts.modifiersGui().getMetaTitle()),
                List.of(
                        nameButton(target, parent, reopenMeta),
                        EditorButtons.valueButton(texts.messages(), texts.modifiersGui(), Material.BOOK,
                                "Description", orUnset(target.description()),
                                texts.modifiersGui().getEditorClickEdit(),
                                player -> fieldPrompt(player, reopen(target, parent, reopenMeta), "Description",
                                        target.description(), true, false, raw -> {
                                            target.patchDescription(raw);
                                            return null;
                                        })),
                        EditorButtons.valueButton(texts.messages(), texts.modifiersGui(), target.item(),
                                "Icon", target.item().name(),
                                texts.modifiersGui().getEditorClickEdit(),
                                player -> fieldPrompt(player, reopen(target, parent, reopenMeta), "Icon",
                                        target.item().name(), false, true, raw -> {
                                            ModifierFieldEdits.Parsed<Material> item =
                                                    ModifierFieldEdits.item(raw);
                                            if (!item.ok()) {
                                                return item.error();
                                            }
                                            target.patchItem(item.value());
                                            texts.messages().messageRaw(player, texts.modifiers().getEditIconSet(),
                                                    Map.of("material", item.value().name()));
                                            return null;
                                        })),
                        EditorButtons.valueButton(texts.messages(), texts.modifiersGui(), Material.PLAYER_HEAD,
                                "Author", orUnset(target.author()),
                                texts.modifiersGui().getEditorClickEdit(),
                                player -> fieldPrompt(player, reopen(target, parent, reopenMeta), "Author",
                                        target.author() == null ? "" : target.author(),
                                        true, false, raw -> {
                                            target.patchAuthor(raw);
                                            return null;
                                        })).withMeta(meta ->
                                                AuthorHeads.applyTo(meta, target.author()))),
                gui,
                GuiTexts.name(texts.messages(), texts.modifiersGui().getBack(), "Back"),
                parent);
        return self[0];
    }

    private MenuButton nameButton(MetaTarget target, Supplier<Menu> parent,
            Function<String, Menu> reopenMeta) {
        return new MenuButton(new MenuButton.Spec(Material.NAME_TAG,
                GuiTexts.name(texts.messages(), "Name", "Name"),
                GuiTexts.lore(texts.messages(), List.of(
                        EditorButtons.currentLine(texts.modifiersGui(), target.name()),
                        "Id: <white>{id}".replace("{id}", target.id()),
                        texts.modifiersGui().getEditorClickEdit(),
                        texts.modifiersGui().getEditorRenameHint())),
                false, false,
                player -> fieldPrompt(player, reopen(target, parent, reopenMeta),
                        "Name", target.name(), false, false,
                        raw -> {
                            ModifierFieldEdits.Parsed<String> name =
                                    ModifierFieldEdits.name(raw);
                            if (!name.ok()) {
                                return name.error();
                            }
                            target.patchName(name.value());
                            texts.messages().messageRaw(player, texts.modifiers().getEditRenamed(),
                                    Map.of("name", target.displayName(target.id())));
                            texts.sounds().playNeutralSound(player);
                            return null;
                        }),
                player -> renamePrompt(player, reopen(target, parent, reopenMeta),
                        target, reopenMeta), null, MenuButton.SoundPolicy.CLICK, null)).silent();
    }

    /** Rebuild supplier so prompt callbacks reopen a fresh quad. */
    private Supplier<Menu> reopen(MetaTarget target, Supplier<Menu> parent,
            Function<String, Menu> reopenMeta) {
        return () -> menu(target, parent, reopenMeta);
    }

    private void fieldPrompt(Player player, Supplier<Menu> reopen, String label, String current,
            boolean clearable, boolean withIcon, FieldPrompts.Submit submit) {
        String title = texts.modifiersGui().getEditorPromptTitle().replace("{label}", label);
        if (withIcon) {
            FieldPrompts.promptWithIcon(dialogs, gui, texts.messages(), texts.modifiersGui(),
                    texts.modifiers(), texts.sounds(), player, reopen,
                    title, current, clearable, submit);
        } else {
            FieldPrompts.prompt(dialogs, gui, texts.messages(), texts.modifiersGui(), texts.modifiers(),
                    texts.sounds(), player, reopen,
                    title, current, clearable, submit);
        }
    }

    private void renamePrompt(Player player, Supplier<Menu> reopen, MetaTarget target,
            Function<String, Menu> reopenMeta) {
        if (denied(player)) {
            return;
        }
        dialogs.prompt(player, texts.modifiersGui().getEditorRenameTitle(), target.id(),
                List.of(texts.modifiersGui().getEditorRenamePrompt()),
                raw -> {
                    ModifierFieldEdits.Parsed<String> parsed =
                            ModifierFieldEdits.id(raw, target.takenIds());
                    if (!parsed.ok()) {
                        invalid(player, parsed.error());
                        gui.navigate(player, reopen.get());
                        return;
                    }
                    target.rename(parsed.value());
                    texts.messages().messageRaw(player, texts.modifiers().getEditIdChanged(),
                            Map.of("name", parsed.value()));
                    texts.sounds().playNeutralSound(player);
                    gui.navigate(player, reopenMeta.apply(parsed.value()));
                },
                () -> gui.navigate(player, reopen.get()));
    }

    private String orUnset(String value) {
        return value == null || value.isBlank()
                ? texts.modifiersGui().getEditorUnset() : value;
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
