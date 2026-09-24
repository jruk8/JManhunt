package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.CommandSyntax;
import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.match.ModifierTriggers;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
    private static final int ADD_SLOT = 36;

    private final ModifierStore store;
    private final MessageService messages;
    private final SoundService sounds;
    private final GuiService gui;
    private final SettingDialogs dialogs;

    /**
     * @param store modifier reads and patches; sounds, gui, and dialogs
     *        are only touched inside click actions, so builders tolerate
     *        them as null
     */
    public ModifierDetailMenus(ModifierStore store, MessageService messages, SoundService sounds,
            GuiService gui, SettingDialogs dialogs) {
        this.store = store;
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.dialogs = dialogs;
    }

    /** Trigger toggles over every known trigger. */
    public Menu triggersMenu(String id, Supplier<Menu> parent) {
        MenuLayout layout = MenuLayout.parse("#########", "#########", "#########");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(messages, text("triggers-title", "Run On")),
                layout, () -> triggersStatic(id, self[0]), List::of, parent);
        return self[0];
    }

    private Map<Integer, MenuButton> triggersStatic(String id, Menu self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        List<String> triggers = store.runsOn(id);
        List<String> known = ModifierTriggers.KNOWN;
        for (int index = 0; index < known.size(); index++) {
            String trigger = known.get(index);
            boolean on = triggers.stream().anyMatch(trigger::equalsIgnoreCase);
            fixed.put(index, new MenuButton(on ? Material.LIME_DYE : Material.GRAY_DYE,
                    GuiTexts.name(messages, trigger, trigger),
                    GuiTexts.lore(messages, List.of(
                            currentLine(on ? text("state-on", "Enabled")
                                    : text("state-off", "Disabled")),
                            text("editor-click-toggle", "Click to toggle"))),
                    on, false,
                    player -> toggleTrigger(player, id, trigger)));
        }
        fixed.put(22, new MenuButton(Material.PAPER,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                null, false, false,
                player -> {
                    gui.back(player, self);
                    sounds.playNeutralSound(player);
                }));
        return fixed;
    }

    private void toggleTrigger(Player player, String id, String trigger) {
        if (denied(player)) {
            return;
        }
        patch(id, entry -> {
            ModifierBehavior behavior = ModifierStore.ensureBehavior(entry);
            List<String> runsOn = behavior.getRunsOn();
            if (runsOn == null) {
                runsOn = new ArrayList<>();
                behavior.setRunsOn(runsOn);
            }
            if (!runsOn.removeIf(trigger::equalsIgnoreCase)) {
                runsOn.add(trigger);
            }
        });
        sounds.playNeutralSound(player);
    }

    /** Command list picker with live line counts. */
    public Menu commandsMenu(String id, Supplier<Menu> parent) {
        MenuLayout layout = MenuLayout.parse("#########", "#########", "#########");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(messages, text("commands-title", "Command Lists")),
                layout, () -> commandsStatic(id, self[0]), List::of, parent);
        return self[0];
    }

    private Map<Integer, MenuButton> commandsStatic(String id, Menu self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int index = 0; index < COMMAND_LISTS.size(); index++) {
            String list = COMMAND_LISTS.get(index);
            int count = store.commandList(id, list).size();
            fixed.put(slots[index], fieldButton(Material.COMMAND_BLOCK, list,
                    text("editor-commands-lore", "{total} lines")
                            .replace("{total}", String.valueOf(count)),
                    "editor-click-open", "Click to open", player -> {
                        if (denied(player)) {
                            return;
                        }
                        gui.navigate(player,
                                linesMenu(id, list, () -> commandsMenu(id, self.parent())));
                        sounds.playNeutralSound(player);
                    }));
        }
        fixed.put(22, new MenuButton(Material.PAPER,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                null, false, false,
                player -> {
                    gui.back(player, self);
                    sounds.playNeutralSound(player);
                }));
        return fixed;
    }

    /** Scrollable lines of one command list with add, edit, and delete. */
    public Menu linesMenu(String id, String list, Supplier<Menu> parent) {
        MenuLayout layout = MenuLayout.parse(
                "##xxxxxx#", "u#xxxxxx#", "b#xxxxxxt", "d#xxxxxx#", "##xxxxxx#");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(messages,
                text("lines-title", "Commands: {list}").replace("{list}", list)),
                layout, () -> linesStatic(id, list, self),
                () -> lineButtons(id, list, self[0]), parent);
        return self[0];
    }

    private Map<Integer, MenuButton> linesStatic(String id, String list, Menu[] self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        fixed.put(9, scrollButton("scroll-up", "Scroll up", self, -1));
        fixed.put(18, new MenuButton(Material.PAPER,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                null, false, false,
                player -> {
                    gui.back(player, self[0]);
                    sounds.playNeutralSound(player);
                }));
        fixed.put(27, scrollButton("scroll-down", "Scroll down", self, 1));
        fixed.put(ADD_SLOT, new MenuButton(Material.LIME_DYE,
                GuiTexts.name(messages, text("lines-add", "Add Line"), "Add Line"),
                GuiTexts.lore(messages, text("editor-click-edit", "Click to edit")),
                false, false,
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    linePrompt(player, self[0], text("lines-add-title", "Add Command"), "",
                            id, list, -1);
                }));
        return fixed;
    }

    private List<MenuButton> lineButtons(String id, String list, Menu self) {
        List<String> lines = store.commandList(id, list);
        List<MenuButton> buttons = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            int lineIndex = index;
            buttons.add(new MenuButton(Material.PAPER,
                    GuiTexts.name(messages, lines.get(index), "(blank)"),
                    GuiTexts.lore(messages, List.of(
                            text("editor-click-edit", "Click to edit"),
                            text("lines-delete-hint", "Right-click to delete"))),
                    false, false,
                    player -> {
                        if (denied(player)) {
                            return;
                        }
                        linePrompt(player, self, text("lines-edit-title", "Edit Command"),
                                store.commandList(id, list).get(lineIndex), id, list, lineIndex);
                    },
                    player -> {
                        if (denied(player)) {
                            return;
                        }
                        deleteLineConfirm(player, self, id, list, lineIndex);
                    }));
        }
        return buttons;
    }

    /**
     * Prompts for one command line; index -1 appends, otherwise the line
     * is replaced. Fatal syntax errors refuse the save, warnings pass
     * through to chat.
     */
    private void linePrompt(Player player, Menu self, String title, String initial,
            String id, String list, int index) {
        dialogs.prompt(player, title, initial,
                List.of(text("lines-prompt", "Tags like <p> and <random-num:1,6> resolve.")),
                raw -> {
                    Optional<String> problem = CommandSyntax.error(raw);
                    if (problem.isPresent()) {
                        messages.message(player, "modifiers.create-bad-command",
                                Map.of("list", list, "error", problem.get()));
                        sounds.playAngrySound(player);
                        gui.navigate(player, self);
                        return;
                    }
                    patch(id, entry -> {
                        List<String> lines = ModifierStore.ensureCommands(
                                ModifierStore.ensureBehavior(entry))
                                .getLists().computeIfAbsent(list, ignored -> new ArrayList<>());
                        if (index < 0) {
                            lines.add(raw);
                        } else if (index < lines.size()) {
                            lines.set(index, raw);
                        }
                    });
                    for (String warning : CommandSyntax.warnings(raw)) {
                        messages.message(player, "modifiers.create-command-warning",
                                Map.of("warning", warning));
                    }
                    sounds.playNeutralSound(player);
                    gui.navigate(player, self);
                },
                () -> gui.navigate(player, self));
    }

    private void deleteLineConfirm(Player player, Menu self, String id, String list, int index) {
        List<String> lines = store.commandList(id, list);
        String line = index < lines.size() ? lines.get(index) : "";
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages, text("lines-delete-title", "Delete this line?")),
                Material.PAPER, null,
                GuiTexts.lore(messages, List.of(line)),
                GuiTexts.name(messages, text("cancel", "Cancel"), "Cancel"),
                back -> gui.navigate(back, self),
                GuiTexts.name(messages, text("confirm", "Confirm"), "Confirm"),
                done -> {
                    patch(id, entry -> {
                        ModifierBehavior behavior = entry.getBehavior();
                        if (behavior == null || behavior.getCommands() == null) {
                            return;
                        }
                        List<String> kept = behavior.getCommands().getLists().get(list);
                        if (kept != null && index < kept.size()) {
                            kept.remove(index);
                        }
                    });
                    sounds.playNeutralSound(done);
                    gui.navigate(done, self);
                },
                () -> self);
        gui.navigate(player, confirm);
    }

    private MenuButton fieldButton(Material material, String label, String value,
            Consumer<Player> action) {
        return fieldButton(material, label, value,
                "editor-click-edit", "Click to edit", action);
    }

    private MenuButton fieldButton(Material material, String label, String value,
            String hintKey, String hintFallback, Consumer<Player> action) {
        return new MenuButton(material,
                GuiTexts.name(messages, label, label),
                GuiTexts.lore(messages, List.of(
                        currentLine(value),
                        text(hintKey, hintFallback))),
                false, false, action);
    }

    private MenuButton scrollButton(String nameKey, String fallback, Menu[] self, int delta) {
        return new MenuButton(Material.ARROW,
                GuiTexts.name(messages, text(nameKey, fallback), fallback),
                null, false, false,
                player -> {
                    if (self[0].window().scrollLine(delta)) {
                        sounds.playSound(player, "compass.left-click");
                    }
                });
    }

    private String currentLine(String value) {
        return text("editor-current", "Current: {value}").replace("{value}", value);
    }

    private void patch(String id, Consumer<ModifierEntry> patch) {
        store.updateModifier(id, patch);
    }

    private boolean denied(Player player) {
        if (player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)) {
            return false;
        }
        messages.message(player, "command.no-permission");
        return true;
    }

    private String text(String key, String fallback) {
        return messages.string("modifiers-gui." + key, fallback);
    }
}
