package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.CommandSyntax;
import com.jruk8.jmanhunt.command.CommandValidation;
import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.command.Ordinal;
import com.jruk8.jmanhunt.command.PlaceholderCheatsheet;
import com.jruk8.jmanhunt.command.TagFunctionScope;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersGuiMessages;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Modifier editor submenus: trigger toggles, the command list picker,
 * and per-list line editing with syntax validation. Owned by
 * {@link ModifierEditorMenus}, which passes the editor as the parent
 * supplier on every navigation.
 */
public final class ModifierDetailMenus {

    /** Command lists in runner order. */
    public static final List<String> COMMAND_LISTS = List.of("console", "player", "hunter",
            "speedrunner", "console-cleanup", "player-cleanup");
    /** Command lists in picker display order, separate from runner order. */
    static final List<String> DISPLAY_ORDER = List.of("player", "speedrunner", "hunter",
            "console", "player-cleanup", "console-cleanup");
    private static final int[] DISPLAY_SLOTS = {10, 11, 12, 13, 15, 16};
    private static final Material[] DISPLAY_MATERIALS = {Material.LIGHT_GRAY_CONCRETE,
            Material.LIME_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.BLACK_SHULKER_BOX};

    /** Message bus, gui/modifier/command texts, and sounds. */
    public record DetailTexts(MessageService messages, ModifiersGuiMessages modifiersGui,
            ModifiersMessages modifiers, CommandMessages command, SoundService sounds) {
    }

    /** Gui, dialogs, validation, and commands; nulls tolerated per action. */
    public record DetailDeps(GuiService gui, SettingDialogs dialogs,
            BooleanSupplier commandValidation, ModifiersCommand commands) {
    }

    private final ModifierStore store;
    private final DetailTexts texts;
    private final DetailDeps deps;

    public ModifierDetailMenus(ModifierStore store, DetailTexts texts, DetailDeps deps) {
        this.store = store;
        this.texts = texts;
        this.deps = deps;
    }

    /** Command list picker with live line counts. */
    public Menu commandsMenu(String id, int behavior, Supplier<Menu> parent) {
        MenuLayout layout = MenuLayout.parse("#########", "#########", "#########");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(texts.messages(), texts.modifiersGui().getCommandsTitle()),
                layout, () -> commandsStatic(id, behavior, self[0]), List::of, parent);
        return self[0];
    }

    private Map<Integer, MenuButton> commandsStatic(String id, int behavior, Menu self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        for (int index = 0; index < DISPLAY_ORDER.size(); index++) {
            String list = DISPLAY_ORDER.get(index);
            int count = store.commandList(id, behavior, list).size();
            List<String> lore = new ArrayList<>();
            if (count > 0) {
                lore.add("Lines: <white>" + count);
            }
            lore.add(texts.modifiersGui().getEditorClickOpen());
            if (count > 0) {
                lore.add(texts.modifiersGui().getCommandsTestHint());
            }
            MenuButton button = EditorButtons.actionButton(texts.messages(),
                    DISPLAY_MATERIALS[index], list, lore, count > 0,
                    player -> {
                        if (denied(player)) {
                            return;
                        }
                        deps.gui().navigate(player, linesMenu(id, behavior, list,
                                () -> commandsMenu(id, behavior, self.parent())));
                    });
            if (count > 0) {
                button = button.shiftAction(player -> {
                    if (denied(player)) {
                        return;
                    }
                    List<String> live = store.commandList(id, behavior, list);
                    if (!live.isEmpty()) {
                        deps.commands().testList(player, list, live);
                    }
                });
            }
            fixed.put(DISPLAY_SLOTS[index], button);
        }
        fixed.put(22, new MenuButton(new MenuButton.Spec(Material.PAPER,
                GuiTexts.name(texts.messages(), texts.modifiersGui().getBack(), "Back"),
                null, false, false,
                player -> deps.gui().back(player, self), null, null, MenuButton.SoundPolicy.CLICK, null)));
        return fixed;
    }

    /** Scrollable lines of one command list with add, edit, and delete. */
    public Menu linesMenu(String id, int behavior, String list, Supplier<Menu> parent) {
        MenuLayout layout = MenuLayout.parse(
                "##xxxxxx#", "u#xxxxxx#", "b#xxxxxxt", "d#xxxxxx#", "##xxxxxx#");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(texts.messages(),
                texts.modifiersGui().getLinesTitle().replace("{list}", list)),
                layout, () -> linesStatic(id, list, self),
                () -> lineButtons(id, behavior, list, self[0]), parent);
        return self[0];
    }

    private Map<Integer, MenuButton> linesStatic(String id, String list, Menu[] self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(9, scrollButton(texts.modifiersGui().getScrollUp(), "Scroll up", self, -1));
        fixed.put(18, new MenuButton(new MenuButton.Spec(Material.PAPER,
                GuiTexts.name(texts.messages(), texts.modifiersGui().getBack(), "Back"),
                null, false, false,
                player -> deps.gui().back(player, self[0]), null, null, MenuButton.SoundPolicy.CLICK, null)));
        fixed.put(27, scrollButton(texts.modifiersGui().getScrollDown(), "Scroll down", self, 1));
        return fixed;
    }

    private List<MenuButton> lineButtons(String id, int behavior, String list, Menu self) {
        List<String> lines = store.commandList(id, behavior, list);
        List<MenuButton> buttons = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            int lineIndex = index;
            buttons.add(new MenuButton(new MenuButton.Spec(Material.PAPER,
                    GuiTexts.name(texts.messages(), GuiTexts.truncate(lines.get(index), 32), "(blank)"),
                    GuiTexts.lore(texts.messages(), List.of(
                            texts.modifiersGui().getEditorClickEdit(),
                            texts.modifiersGui().getLinesDeleteHint(),
                            texts.modifiersGui().getLinesTestHint())),
                    false, false,
                    player -> {
                        if (denied(player)) {
                            return;
                        }
                        linePrompt(player, self, texts.modifiersGui().getLinesEditTitle(),
                                store.commandList(id, behavior, list).get(lineIndex), id, behavior,
                                list, lineIndex);
                    },
                    player -> {
                        if (denied(player)) {
                            return;
                        }
                        deleteLineConfirm(player, self, id, behavior, list, lineIndex);
                    }, null, MenuButton.SoundPolicy.CLICK, null)).shiftAction(player -> {
                        if (denied(player)) {
                            return;
                        }
                        deps.commands().testList(player, list,
                                List.of(store.commandList(id, behavior, list).get(lineIndex)));
                    }).silent());
        }
        buttons.add(AddStick.button(texts.messages(),
                texts.modifiersGui().getLinesAdd(),
                List.of(texts.modifiersGui().getEditorClickEdit()),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    linePrompt(player, self, texts.modifiersGui().getLinesAddTitle(), "",
                            id, behavior, list, -1);
                }));
        return buttons;
    }

    /**
     * Prompts for one command line; index -1 appends, otherwise the line
     * is replaced. Fatal syntax errors refuse the save, warnings pass
     * through to chat, and a submit chats ordinal feedback.
     */
    private void linePrompt(Player player, Menu self, String title, String initial,
            String id, int behavior, String list, int index) {
        deps.dialogs().prompt(player, title, initial, PlaceholderCheatsheet.commandDialogLines(),
                raw -> {
                    Optional<String> problem = CommandValidation.validateLine(raw,
                            validateCommands(), CommandValidation.knownRoots(),
                            CommandValidation::isKnownMaterial,
                            CommandValidation.materialNames());
                    if (problem.isPresent()) {
                        texts.messages().messageRaw(player, texts.modifiers().getCreateBadCommand(),
                                Map.of("list", list, "error", problem.get()));
                        texts.sounds().playAngrySound(player);
                        deps.gui().navigate(player, self);
                        return;
                    }
                    if (index >= 0 && unchanged(id, behavior, list, index, raw)) {
                        texts.messages().messageRaw(player, texts.modifiers().getEditCommandUnchanged());
                        texts.sounds().playNeutralSound(player);
                        deps.gui().navigate(player, self);
                        return;
                    }
                    patch(id, entry -> {
                        List<String> lines = ModifierStore.ensureCommands(
                                ModifierStore.ensureBehavior(entry, behavior))
                                .getLists().computeIfAbsent(list, ignored -> new ArrayList<>());
                        if (index < 0) {
                            lines.add(raw);
                        } else if (index < lines.size()) {
                            lines.set(index, raw);
                        }
                    });
                    warnForLine(player, raw, id, behavior, list);
                    List<String> fresh = store.commandList(id, behavior, list);
                    int position = index < 0
                            ? fresh.size() : Math.min(index + 1, Math.max(fresh.size(), 1));
                    texts.messages().messageRaw(player, texts.modifiers().getEditCommandSet(),
                            commandSetValues(list, position, raw));
                    texts.sounds().playNeutralSound(player);
                    deps.gui().navigate(player, self);
                },
                () -> deps.gui().navigate(player, self));
    }

    /** Warns for one saved line with its function scope's defs in view. */
    private void warnForLine(Player player, String raw, String id, int behavior, String list) {
        List<String> scopeLines = new ArrayList<>();
        for (String scopeList : TagFunctionScope.functionScopeLists(list)) {
            scopeLines.addAll(store.commandList(id, behavior, scopeList));
        }
        Set<String> functions = TagFunctionScope.definedFunctions(scopeLines);
        for (String warning : CommandSyntax.warnings(raw, functions)) {
            texts.messages().messageRaw(player, texts.modifiers().getCreateCommandWarning(),
                    Map.of("warning", warning));
        }
    }

    private boolean validateCommands() {
        return deps.commandValidation() == null || deps.commandValidation().getAsBoolean();
    }

    /** True when the edit resubmits the live line unchanged. */
    private boolean unchanged(String id, int behavior, String list, int index, String raw) {
        List<String> before = store.commandList(id, behavior, list);
        return index < before.size() && raw.equals(before.get(index));
    }

    /** Feedback params with the command escaped so tags print literally. */
    static Map<String, String> commandSetValues(String list, int position, String command) {
        return Map.of("ordinal", Ordinal.format(position),
                "list", Ordinal.listName(list),
                "command", MiniMessage.miniMessage().escapeTags(command));
    }

    private void deleteLineConfirm(Player player, Menu self, String id, int behavior, String list,
            int index) {
        List<String> lines = store.commandList(id, behavior, list);
        String line = index < lines.size() ? lines.get(index) : "";
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(texts.messages(), texts.modifiersGui().getLinesDeleteTitle()),
                Material.PAPER, null,
                GuiTexts.lore(texts.messages(), List.of(line)),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getCancel(), "Cancel"),
                back -> deps.gui().navigate(back, self),
                GuiTexts.name(texts.messages(), texts.modifiersGui().getConfirm(), "Confirm"),
                done -> {
                    patch(id, entry -> {
                        if (entry.getBehavior() == null) {
                            return;
                        }
                        ModifierBehavior keptBehavior =
                                entry.getBehavior().get(String.valueOf(behavior));
                        if (keptBehavior == null || keptBehavior.getCommands() == null) {
                            return;
                        }
                        List<String> kept = keptBehavior.getCommands().getLists().get(list);
                        if (kept != null && index < kept.size()) {
                            kept.remove(index);
                        }
                    });
                    texts.sounds().playNeutralSound(done);
                    deps.gui().navigate(done, self);
                },
                () -> self);
        deps.gui().navigate(player, confirm);
        texts.sounds().playSound(player, "compass.left-click");
    }

    private MenuButton scrollButton(String name, String fallback, Menu[] self, int delta) {
        return new MenuButton(new MenuButton.Spec(Material.ARROW,
                GuiTexts.name(texts.messages(), name, fallback),
                null, false, false,
                player -> self[0].window().scrollLine(delta), null, null, MenuButton.SoundPolicy.CLICK, null));
    }

    private void patch(String id, Consumer<ModifierEntry> patch) {
        store.updateModifier(id, patch);
    }

    private boolean denied(Player player) {
        if (player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
            return false;
        }
        texts.messages().messageRaw(player, texts.command().getNoPermission());
        return true;
    }


}
