package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierFieldEdits;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierChance;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierExecution;
import com.jruk8.jmanhunt.modifiers.config.ModifierInterval;
import com.jruk8.jmanhunt.modifiers.config.ModifierOnStart;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import com.jruk8.jmanhunt.modifiers.config.ModifierPickRandom;
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
 * Creator and editor menus for one modifier: display data, triggers,
 * options, command lines, export, rename, and delete. Field buttons
 * prompt through shared dialogs and validate through
 * {@link ModifierFieldEdits}, so the GUI accepts exactly what the CLI
 * accepts. Trigger and command submenus live in
 * {@link ModifierDetailMenus}. Clicks refresh in place; dialog submits
 * rebuild the caller. Builders read live and tolerate missing entries
 * as blank defaults.
 */
public final class ModifierEditorMenus {


    private final ModifierStore store;
    private final MessageService messages;
    private final SoundService sounds;
    private final GuiService gui;
    private final ModifiersCommand commands;
    private final SettingDialogs dialogs;
    private final ModifierDetailMenus detail;

    /**
     * @param store modifier reads and patches; sounds, gui, commands, and
     *        dialogs are only touched inside click actions, so builders
     *        tolerate them as null
     */
    public ModifierEditorMenus(ModifierStore store, MessageService messages, SoundService sounds,
            GuiService gui, ModifiersCommand commands, SettingDialogs dialogs) {
        this.store = store;
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.commands = commands;
        this.dialogs = dialogs;
        this.detail = new ModifierDetailMenus(store, messages, sounds, gui, dialogs);
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

    /** 45-slot editor for one modifier. */
    public Menu editor(String id, Supplier<Menu> parent) {
        MenuLayout layout = MenuLayout.parse(
                "#########", "#########", "#########", "#########", "#########");
        final Menu[] self = new Menu[1];
        self[0] = new Menu(GuiTexts.title(messages, text("editor-title-modifier", "Edit Modifier")),
                layout, () -> editorStatic(id, parent, self[0]), List::of, parent);
        return self[0];
    }

    private Map<Integer, MenuButton> editorStatic(String id, Supplier<Menu> parent, Menu self) {
        Map<Integer, MenuButton> fixed = new HashMap<>();
        metaRow(fixed, id, self);
        triggerRow(fixed, id, self);
        chanceRow(fixed, id, self);
        executionRow(fixed, id, self);
        actionRow(fixed, id, parent, self);
        return fixed;
    }

    private void metaRow(Map<Integer, MenuButton> fixed, String id, Menu self) {
        fixed.put(1, fieldButton(Material.NAME_TAG, "Name", store.metaName(id),
                player -> fieldPrompt(player, self, "Name", store.metaName(id), false, raw -> {
                    ModifierFieldEdits.Parsed<String> name = ModifierFieldEdits.name(raw);
                    if (!name.ok()) {
                        return name.error();
                    }
                    patch(id, entry -> ModifierStore.ensureMeta(entry).setName(name.value()));
                    return null;
                })));
        fixed.put(3, fieldButton(Material.BOOK, "Description", orUnset(store.metaDescription(id)),
                player -> fieldPrompt(player, self, "Description", store.metaDescription(id), true,
                        raw -> {
                            patch(id, entry ->
                                    ModifierStore.ensureMeta(entry).setDescription(raw));
                            return null;
                        })));
        fixed.put(5, fieldButton(store.metaItem(id), "Icon", store.metaItem(id).name(),
                player -> fieldPrompt(player, self, "Icon", store.metaItem(id).name(), false,
                        raw -> {
                            ModifierFieldEdits.Parsed<Material> item =
                                    ModifierFieldEdits.item(raw);
                            if (!item.ok()) {
                                return item.error();
                            }
                            patch(id, entry -> ModifierStore.ensureMeta(entry)
                                    .setItem(item.value().name()));
                            return null;
                        })));
        String author = store.metaAuthor(id);
        fixed.put(7, fieldButton(Material.PLAYER_HEAD, "Author", orUnset(author),
                player -> fieldPrompt(player, self, "Author", author == null ? "" : author, true,
                        raw -> {
                            patch(id, entry ->
                                    ModifierStore.ensureMeta(entry).setAuthor(raw));
                            return null;
                        })));
    }

    private void triggerRow(Map<Integer, MenuButton> fixed, String id, Menu self) {
        boolean enabled = store.isEnabled(id);
        fixed.put(10, new MenuButton(Material.LEVER,
                GuiTexts.name(messages, "Enabled", "Enabled"),
                GuiTexts.lore(messages, List.of(
                        currentLine(store.isEnabled(id)
                                ? text("state-on", "Enabled") : text("state-off", "Disabled")),
                        text("editor-click-toggle", "Click to toggle"))),
                enabled, false,
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    commands.execute(player,
                            new String[]{"setmod", id, String.valueOf(!store.isEnabled(id))});
                    sounds.playNeutralSound(player);
                }).silent());
        List<String> triggers = store.runsOn(id);
        fixed.put(13, navButton(Material.COMPARATOR, "Run on",
                triggers.isEmpty() ? orUnset(null) : triggers.size() + " selected",
                "editor-click-open", "Click to open", player -> {
                    if (denied(player)) {
                        return;
                    }
                    gui.navigate(player, detail.triggersMenu(id, () -> editor(id, self.parent())));
                }));
        fixed.put(16, fieldButton(Material.HOPPER, "Pre-start order",
                orDefault(store.preStartOrder(id), "IN_ORDER"),
                "editor-click-cycle", "Click to change", player -> cyclePreStart(player, id)));
    }

    private void cyclePreStart(Player player, String id) {
        if (denied(player)) {
            return;
        }
        patch(id, entry -> {
            ModifierBehavior behavior = ModifierStore.ensureBehavior(entry);
            ModifierOnStart onStart = behavior.getOnStart();
            if (onStart == null) {
                onStart = new ModifierOnStart();
                behavior.setOnStart(onStart);
            }
            onStart.setPreStartOrder(
                    cycle(onStart.getPreStartOrder(), "IN_ORDER", "PICK_RANDOM"));
        });
        sounds.playNeutralSound(player);
    }

    private void chanceRow(Map<Integer, MenuButton> fixed, String id, Menu self) {
        fixed.put(18, fieldButton(Material.CLOCK, "Interval", intervalText(id),
                player -> fieldPrompt(player, self, "Interval", intervalRaw(id), true,
                        raw -> submitInterval(id, raw))));
        fixed.put(20, fieldButton(Material.COMPASS, "Deviation", deviationText(id),
                player -> fieldPrompt(player, self, "Deviation", deviationRaw(id), true,
                        raw -> submitDeviation(id, raw))));
        fixed.put(22, fieldButton(Material.REPEATER, "Interval scope",
                orDefault(store.intervalBehavior(id), "PER_INVOKE"),
                "editor-click-cycle", "Click to change", player -> {
                    if (denied(player)) {
                        return;
                    }
                    patch(id, entry -> ensureInterval(entry).setBehavior(cycle(
                            store.intervalBehavior(id), "PER_INVOKE", "PER_EXECUTOR")));
                    sounds.playNeutralSound(player);
                }));
        fixed.put(24, fieldButton(Material.EXPERIENCE_BOTTLE, "Chance", chanceText(id),
                player -> fieldPrompt(player, self, "Chance", chanceRaw(id), true,
                        raw -> submitChance(id, raw))));
        fixed.put(26, fieldButton(Material.DAYLIGHT_DETECTOR, "Chance scope",
                orDefault(store.chanceBehavior(id), "PER_INVOKE"),
                "editor-click-cycle", "Click to change", player -> {
                    if (denied(player)) {
                        return;
                    }
                    patch(id, entry -> ensureChance(entry).setBehavior(cycle(
                            store.chanceBehavior(id), "PER_INVOKE", "PER_EXECUTOR")));
                    sounds.playNeutralSound(player);
                }));
    }

    private void executionRow(Map<Integer, MenuButton> fixed, String id, Menu self) {
        fixed.put(28, fieldButton(Material.DISPENSER, "Selection",
                orDefault(store.selection(id), "IN_ORDER"),
                "editor-click-cycle", "Click to change", player -> {
                    if (denied(player)) {
                        return;
                    }
                    patch(id, entry -> ModifierStore
                            .ensureExecution(ModifierStore.ensureOptions(
                                    ModifierStore.ensureBehavior(entry)))
                            .setSelection(cycle(store.selection(id), "IN_ORDER", "PICK_RANDOM")));
                    sounds.playNeutralSound(player);
                }));
        fixed.put(30, fieldButton(Material.DROPPER, "Pick count", pickCountText(id),
                player -> fieldPrompt(player, self, "Pick count", pickCountRaw(id), true,
                        raw -> submitPickCount(id, raw))));
        fixed.put(32, fieldButton(Material.OBSERVER, "Pick scope",
                orDefault(store.pickBehavior(id), "PER_INVOKE"),
                "editor-click-cycle", "Click to change", player -> {
                    if (denied(player)) {
                        return;
                    }
                    patch(id, entry -> ensurePickRandom(entry).setBehavior(cycle(
                            store.pickBehavior(id), "PER_INVOKE", "PER_EXECUTOR")));
                    sounds.playNeutralSound(player);
                }));
        fixed.put(34, fieldButton(Material.TARGET, "Delay", delayText(id),
                player -> fieldPrompt(player, self, "Delay", delayRaw(id), true,
                        raw -> submitDelay(id, raw))));
    }

    private void actionRow(Map<Integer, MenuButton> fixed, String id, Supplier<Menu> parent,
            Menu self) {
        int lines = 0;
        for (String list : ModifierDetailMenus.COMMAND_LISTS) {
            lines += store.commandList(id, list).size();
        }
        fixed.put(36, navButton(Material.COMMAND_BLOCK,
                text("editor-commands", "Commands"),
                text("editor-commands-lore", "{total} lines")
                        .replace("{total}", String.valueOf(lines)),
                "editor-click-open", "Click to open", player -> {
                    if (denied(player)) {
                        return;
                    }
                    gui.navigate(player, detail.commandsMenu(id, () -> editor(id, self.parent())));
                }));
        fixed.put(38, fieldButton(Material.LOOM,
                text("editor-export", "Export"),
                text("editor-export-lore", "Copy a share string"),
                "editor-click-copy", "Click to copy",
                player -> commands.exportEntry(player, "modifier", id)));
        fixed.put(40, fieldButton(Material.ANVIL,
                text("editor-rename", "Rename Id"),
                text("editor-rename-lore", "Current id: {value}").replace("{value}", id),
                player -> renamePrompt(player, id, self)));
        fixed.put(42, navButton(Material.TNT,
                text("editor-delete", "Delete"),
                text("editor-delete-lore", "Removes this modifier forever"),
                "editor-click-delete", "Click to delete", player -> {
                    if (denied(player)) {
                        return;
                    }
                    deleteConfirm(player, id, parent);
                }));
        fixed.put(44, new MenuButton(Material.PAPER,
                GuiTexts.name(messages, text("back", "Back"), "Back"),
                null, false, false,
                player -> gui.back(player, self)));
    }

    private void renamePrompt(Player player, String id, Menu self) {
        if (denied(player)) {
            return;
        }
        dialogs.prompt(player, text("editor-rename-title", "Rename Id"), id,
                List.of(text("editor-rename-prompt", "Type the new id.")),
                raw -> {
                    Set<String> taken = new HashSet<>(store.modifierNames());
                    taken.remove(id);
                    ModifierFieldEdits.Parsed<String> parsed = ModifierFieldEdits.id(raw, taken);
                    if (!parsed.ok()) {
                        invalid(player, parsed.error());
                        gui.navigate(player, self);
                        return;
                    }
                    store.renameModifier(id, parsed.value());
                    messages.message(player, "modifiers.edit-renamed",
                            Map.of("name", store.metaName(parsed.value())));
                    sounds.playNeutralSound(player);
                    gui.navigate(player, editor(parsed.value(), self.parent()));
                },
                () -> gui.navigate(player, self));
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

    /**
     * Prompts for one field. Blank input clears the field when
     * clearable, otherwise the raw text goes to the submitter, which
     * returns an error or null. Either way the caller rebuilds.
     */
    private void fieldPrompt(Player player, Menu self, String label, String current,
            boolean clearable, FieldSubmit submit) {
        String shown = current == null ? text("editor-unset", "Not set") : current;
        dialogs.prompt(player,
                text("editor-prompt-title", "Edit {label}").replace("{label}", label),
                "",
                List.of(text("editor-prompt-current", "Current value: {value}")
                        .replace("{value}", shown)),
                raw -> finishField(player, self,
                        clearable && raw.isBlank() ? submit.submit(null) : submit.submit(raw)),
                () -> gui.navigate(player, self));
    }

    private void finishField(Player player, Menu self, String error) {
        if (error != null) {
            invalid(player, error);
        } else {
            sounds.playNeutralSound(player);
        }
        gui.navigate(player, self);
    }

    /** Field submitter: patches the entry, returning an error or null. */
    private interface FieldSubmit {
        String submit(String raw);
    }

    private void patch(String id, Consumer<ModifierEntry> patch) {
        store.updateModifier(id, patch);
    }

    private String submitInterval(String id, String raw) {
        if (raw == null) {
            intervalPatch(id, null, null);
            return null;
        }
        ModifierFieldEdits.Parsed<Double> parsed =
                ModifierFieldEdits.number("Interval", raw, 0.0, null);
        if (!parsed.ok()) {
            return parsed.error();
        }
        Double deviation = deviationOf(id);
        if (deviation != null && deviation > parsed.value()) {
            return "Deviation (" + deviation + "s) exceeds the new interval.";
        }
        intervalPatch(id, parsed.value(), deviation);
        return null;
    }

    private String submitDeviation(String id, String raw) {
        if (raw == null) {
            intervalPatch(id, intervalOf(id), null);
            return null;
        }
        ModifierFieldEdits.Parsed<Double> parsed =
                ModifierFieldEdits.number("Deviation", raw, 0.0, null);
        if (!parsed.ok()) {
            return parsed.error();
        }
        Double interval = intervalOf(id);
        if (interval == null) {
            return "Deviation needs an interval first.";
        }
        if (parsed.value() > interval) {
            return "Deviation must not exceed the interval (" + interval + "s).";
        }
        intervalPatch(id, interval, parsed.value());
        return null;
    }

    private String submitChance(String id, String raw) {
        if (raw == null) {
            chancePatch(id, null);
            return null;
        }
        ModifierFieldEdits.Parsed<Double> parsed =
                ModifierFieldEdits.number("Chance", raw, 0.0, 1.0);
        if (!parsed.ok()) {
            return parsed.error();
        }
        chancePatch(id, parsed.value());
        return null;
    }

    private String submitPickCount(String id, String raw) {
        if (raw == null) {
            pickCountPatch(id, null);
            return null;
        }
        ModifierFieldEdits.Parsed<Long> parsed =
                ModifierFieldEdits.whole("Pick count", raw, 1L, null);
        if (!parsed.ok()) {
            return parsed.error();
        }
        pickCountPatch(id, parsed.value().intValue());
        return null;
    }

    private String submitDelay(String id, String raw) {
        if (raw == null) {
            delayPatch(id, null);
            return null;
        }
        ModifierFieldEdits.Parsed<Long> parsed =
                ModifierFieldEdits.whole("Delay", raw, 0L, null);
        if (!parsed.ok()) {
            return parsed.error();
        }
        delayPatch(id, parsed.value());
        return null;
    }

    private void intervalPatch(String id, Double interval, Double deviation) {
        patch(id, entry -> {
            ModifierInterval settings = ensureInterval(entry);
            settings.setInterval(interval);
            settings.setDeviation(deviation);
        });
    }

    private void chancePatch(String id, Double chance) {
        patch(id, entry -> ensureChance(entry).setChance(chance));
    }

    private void pickCountPatch(String id, Integer count) {
        patch(id, entry -> ensurePickRandom(entry).setCount(count));
    }

    private void delayPatch(String id, Long delay) {
        patch(id, entry -> ModifierStore
                .ensureOptions(ModifierStore.ensureBehavior(entry)).setDelay(delay));
    }

    private static ModifierInterval ensureInterval(ModifierEntry entry) {
        ModifierOptions options =
                ModifierStore.ensureOptions(ModifierStore.ensureBehavior(entry));
        ModifierInterval settings = options.getIntervalSettings();
        if (settings == null) {
            settings = new ModifierInterval();
            options.setIntervalSettings(settings);
        }
        return settings;
    }

    private static ModifierChance ensureChance(ModifierEntry entry) {
        ModifierOptions options =
                ModifierStore.ensureOptions(ModifierStore.ensureBehavior(entry));
        ModifierChance chance = options.getSuccessChance();
        if (chance == null) {
            chance = new ModifierChance();
            options.setSuccessChance(chance);
        }
        return chance;
    }

    private static ModifierPickRandom ensurePickRandom(ModifierEntry entry) {
        ModifierExecution execution = ModifierStore.ensureExecution(
                ModifierStore.ensureOptions(ModifierStore.ensureBehavior(entry)));
        ModifierPickRandom pick = execution.getPickRandom();
        if (pick == null) {
            pick = new ModifierPickRandom();
            execution.setPickRandom(pick);
        }
        return pick;
    }

    /** Three-state cycle: unset, first, second, then back to unset. */
    private static String cycle(String current, String first, String second) {
        if (current == null) {
            return first;
        }
        if (first.equalsIgnoreCase(current)) {
            return second;
        }
        return null;
    }

    private MenuButton fieldButton(Material material, String label, String value,
            Consumer<Player> action) {
        return fieldButton(material, label, value,
                "editor-click-edit", "Click to edit", action);
    }

    private MenuButton fieldButton(Material material, String label, String value,
            String hintKey, String hintFallback, Consumer<Player> action) {
        return navButton(material, label, value, hintKey, hintFallback, action).silent();
    }

    /**
     * Same lore shape as {@link #fieldButton} but with the central click:
     * for submenu openers, which navigate instead of committing a value.
     */
    private MenuButton navButton(Material material, String label, String value,
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
                player -> self[0].window().scrollLine(delta));
    }

    private String currentLine(String value) {
        return text("editor-current", "Current: {value}").replace("{value}", value);
    }

    private String orUnset(String value) {
        return value == null || value.isBlank()
                ? text("editor-unset", "Not set") : value;
    }

    private String orDefault(String value, String fallback) {
        return value == null ? "Default (" + fallback + ")" : value;
    }

    private Double intervalOf(String id) {
        ModifierOptions options = rawOptions(id);
        return options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getInterval();
    }

    private Double deviationOf(String id) {
        ModifierOptions options = rawOptions(id);
        return options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getDeviation();
    }

    private Double chanceOf(String id) {
        ModifierOptions options = rawOptions(id);
        return options == null || options.getSuccessChance() == null
                ? null : options.getSuccessChance().getChance();
    }

    private Integer pickCountOf(String id) {
        ModifierOptions options = rawOptions(id);
        if (options == null || options.getExecution() == null
                || options.getExecution().getPickRandom() == null) {
            return null;
        }
        return options.getExecution().getPickRandom().getCount();
    }

    private Long delayOf(String id) {
        ModifierOptions options = rawOptions(id);
        return options == null ? null : options.getDelay();
    }

    private ModifierOptions rawOptions(String id) {
        ModifierEntry entry = store.modifierEntry(id);
        return entry == null ? null : rawOptions(entry);
    }

    private static ModifierOptions rawOptions(ModifierEntry entry) {
        return entry.getBehavior() == null ? null : entry.getBehavior().getOptions();
    }

    private String intervalText(String id) {
        Double interval = intervalOf(id);
        return interval == null ? orUnset(null) : interval + "s";
    }

    private String intervalRaw(String id) {
        Double interval = intervalOf(id);
        return interval == null ? "" : String.valueOf(interval);
    }

    private String deviationText(String id) {
        Double deviation = deviationOf(id);
        return deviation == null ? orUnset(null) : deviation + "s";
    }

    private String deviationRaw(String id) {
        Double deviation = deviationOf(id);
        return deviation == null ? "" : String.valueOf(deviation);
    }

    private String chanceText(String id) {
        Double chance = chanceOf(id);
        return chance == null ? "Default (100%)" : Math.round(chance * 100) + "%";
    }

    private String chanceRaw(String id) {
        Double chance = chanceOf(id);
        return chance == null ? "" : String.valueOf(chance);
    }

    private String pickCountText(String id) {
        Integer count = pickCountOf(id);
        return count == null ? "Default (1)" : String.valueOf(count);
    }

    private String pickCountRaw(String id) {
        Integer count = pickCountOf(id);
        return count == null ? "" : String.valueOf(count);
    }

    private String delayText(String id) {
        Long delay = delayOf(id);
        return delay == null ? orUnset(null) : delay + " ticks";
    }

    private String delayRaw(String id) {
        Long delay = delayOf(id);
        return delay == null ? "" : String.valueOf(delay);
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
