package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.Panels;
import com.jruk8.jmanhunt.gui.ScalingLayout;
import com.jruk8.jmanhunt.gui.dialog.ModifierDialog;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.match.ModifierTriggers;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierFieldEdits;
import com.jruk8.jmanhunt.modifiers.ModifierOptionDescriptors;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierChance;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierExecution;
import com.jruk8.jmanhunt.modifiers.config.ModifierInterval;
import com.jruk8.jmanhunt.modifiers.config.ModifierOnStart;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import com.jruk8.jmanhunt.modifiers.config.ModifierPickRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Behavior Options scaling menu for one modifier: Runs On, Interval
 * Settings, Execution, Delay, and Success Chance. Leaf rows share the
 * settings lore schema; submenu rows stay navigation buttons.
 *
 * <p>Runs On opens the trigger checkbox dialog; Interval Settings stays
 * gated until runs-on includes INTERVAL; Execution and Success Chance
 * open their own submenus while Delay edits inline. Every row glows by
 * the shared {@link ModifiedGlow} rule: leaves glow when they differ
 * from the engine default, entries glow when ANY of their leaves glow.
 */
public final class BehaviorOptionsMenus {

    private final ModifierStore store;
    private final MessageService messages;
    private final SoundService sounds;
    private final GuiService gui;
    private final SettingDialogs dialogs;
    private final ModifierDialog modifierDialogs;

    /**
     * @param store behavior reads and patches; sounds, gui, and
     *        dialogs are only touched inside click actions, so
     *        builders tolerate them as null
     */
    public BehaviorOptionsMenus(ModifierStore store, MessageService messages,
            SoundService sounds, GuiService gui,
            SettingDialogs dialogs, ModifierDialog modifierDialogs) {
        this.store = store;
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.dialogs = dialogs;
        this.modifierDialogs = modifierDialogs;
    }

    /** Five-entry options menu for one modifier. */
    public Menu optionsMenu(String id, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> optionButtons(id, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(messages, text("behavior-options-title", "Behavior Options")),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(gui, backName(), self)),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> optionButtons(String id, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        List<String> triggers = store.runsOn(id);
        buttons.add(leafRow(id, self, "runs-on",
                text("runs-on-title", "Runs On"), runsOnValue(triggers),
                ModifierTriggers.KNOWN, runsOnMarked(triggers),
                ModifiedGlow.behaviorRunsOn(store, id),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    modifierDialogs.openRunsOn(player, triggers,
                            checked -> applyRunsOn(player, id, triggers, checked, self.get()),
                            () -> gui.navigate(player, self.get()));
                },
                () -> patch(id, entry -> ModifierStore.ensureBehavior(entry).setRunsOn(null))));
        buttons.add(EditorButtons.actionButton(messages, Material.REPEATER,
                text("interval-settings-title", "Interval Settings"),
                List.of(text("interval-settings-lore", "Cadence, jitter, and scope"),
                        text("editor-click-open", "Click to open")),
                ModifiedGlow.behaviorIntervalGroup(store, id),
                player -> openInterval(player, id, self.get())).silent());
        buttons.add(EditorButtons.actionButton(messages, Material.BELL,
                text("execution-title", "Execution"),
                List.of(text("execution-lore", "Line selection and order"),
                        text("editor-click-open", "Click to open")),
                ModifiedGlow.behaviorExecutionGroup(store, id),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    gui.navigate(player, executionMenu(id, self::get));
                }));
        buttons.add(leafRow(id, self, "delay", null, delayText(id),
                null, null, ModifiedGlow.behaviorDelay(store, id),
                player -> fieldPrompt(player, self.get(), "Delay", delayRaw(id), true,
                        raw -> submitDelay(id, raw)),
                () -> delayPatch(id, null)));
        buttons.add(EditorButtons.actionButton(messages, Material.HOPPER,
                text("chance-title", "Success Chance"),
                List.of(text("chance-lore", "Roll chance and scope"),
                        text("editor-click-open", "Click to open")),
                ModifiedGlow.behaviorChanceGroup(store, id),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    gui.navigate(player, chanceMenu(id, self::get));
                }));
        return buttons;
    }

    /** Interval submenu: cadence, jitter, and scope rows. */
    public Menu intervalMenu(String id, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> intervalButtons(id, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(messages, text("interval-settings-title", "Interval Settings")),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(gui, backName(), self)),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> intervalButtons(String id, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        buttons.add(leafRow(id, self, "interval", null, intervalText(id),
                null, null, ModifiedGlow.behaviorInterval(store, id),
                player -> fieldPrompt(player, self.get(), "Interval", intervalRaw(id), true,
                        raw -> submitInterval(id, raw)),
                () -> intervalPatch(id, null, deviationOf(id))));
        buttons.add(leafRow(id, self, "deviation", null, deviationText(id),
                null, null, ModifiedGlow.behaviorDeviation(store, id),
                player -> fieldPrompt(player, self.get(), "Deviation", deviationRaw(id), true,
                        raw -> submitDeviation(id, raw)),
                () -> intervalPatch(id, intervalOf(id), null)));
        buttons.add(choiceRow(id, self, "interval-scope",
                store.intervalBehavior(id), "PER_INVOKE",
                ModifiedGlow.behaviorIntervalScope(store, id),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    patch(id, entry -> ensureInterval(entry).setBehavior(cycle(
                            store.intervalBehavior(id), "PER_INVOKE", "PER_EXECUTOR")));
                    sounds.playNeutralSound(player);
                },
                () -> patch(id, entry -> ensureInterval(entry).setBehavior(null))));
        return buttons;
    }

    /** Execution submenu: selection, pick, scope, and pre-start rows. */
    public Menu executionMenu(String id, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> executionButtons(id, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(messages, text("execution-title", "Execution")),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(gui, backName(), self)),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> executionButtons(String id, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        buttons.add(choiceRow(id, self, "selection",
                store.selection(id), "IN_ORDER",
                ModifiedGlow.behaviorSelection(store, id),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    selectionPatch(id, cycle(store.selection(id), "IN_ORDER", "PICK_RANDOM"));
                    sounds.playNeutralSound(player);
                },
                () -> selectionPatch(id, null)));
        buttons.add(leafRow(id, self, "pick-count", null, pickCountText(id),
                null, null, ModifiedGlow.behaviorPickCount(store, id),
                player -> fieldPrompt(player, self.get(), "Pick count", pickCountRaw(id), true,
                        raw -> submitPickCount(id, raw)),
                () -> pickCountPatch(id, null)));
        buttons.add(choiceRow(id, self, "pick-scope",
                store.pickBehavior(id), "PER_INVOKE",
                ModifiedGlow.behaviorPickScope(store, id),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    patch(id, entry -> ensurePickRandom(entry).setBehavior(cycle(
                            store.pickBehavior(id), "PER_INVOKE", "PER_EXECUTOR")));
                    sounds.playNeutralSound(player);
                },
                () -> patch(id, entry -> ensurePickRandom(entry).setBehavior(null))));
        buttons.add(choiceRow(id, self, "pre-start",
                store.preStartOrder(id), "IN_ORDER",
                ModifiedGlow.behaviorPreStart(store, id),
                player -> cyclePreStart(player, id),
                () -> patch(id, entry -> {
                    ModifierBehavior behavior = ModifierStore.ensureBehavior(entry);
                    if (behavior.getOnStart() != null) {
                        behavior.getOnStart().setPreStartOrder(null);
                    }
                })));
        return buttons;
    }

    /** Success-chance submenu: chance and scope rows. */
    public Menu chanceMenu(String id, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> chanceButtons(id, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(messages, text("chance-title", "Success Chance")),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(gui, backName(), self)),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> chanceButtons(String id, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        buttons.add(leafRow(id, self, "chance", null, chanceText(id),
                null, null, ModifiedGlow.behaviorChance(store, id),
                player -> fieldPrompt(player, self.get(), "Chance", chanceRaw(id), true,
                        raw -> submitChance(id, raw)),
                () -> chancePatch(id, null)));
        buttons.add(choiceRow(id, self, "chance-scope",
                store.chanceBehavior(id), "PER_INVOKE",
                ModifiedGlow.behaviorChanceScope(store, id),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    patch(id, entry -> ensureChance(entry).setBehavior(cycle(
                            store.chanceBehavior(id), "PER_INVOKE", "PER_EXECUTOR")));
                    sounds.playNeutralSound(player);
                },
                () -> patch(id, entry -> ensureChance(entry).setBehavior(null))));
        return buttons;
    }

    /**
     * One option leaf in the shared settings schema. A null label
     * keeps the descriptor label; the Runs On row overrides it with
     * its configured title.
     */
    private MenuButton leafRow(String id, Supplier<Menu> self, String optionKey, String label,
            String value, List<String> options, Set<String> marked, boolean glow,
            Consumer<Player> click, Runnable clear) {
        ModifierOptionDescriptors.Descriptor descriptor =
                ModifierOptionDescriptors.byKey(optionKey);
        FieldLore.Field field = new FieldLore.Field(
                descriptor.description(), value,
                "modifiers." + id + "." + descriptor.pathSuffix(),
                ModifierOptionDescriptors.typeName(descriptor.kind()),
                descriptor.allowed(), options, marked,
                descriptor.defaultText(), hintFor(descriptor.kind()));
        return FieldButtons.field(messages, descriptor.icon(),
                label == null ? descriptor.label() : label,
                FieldLore.lines(messages, field), glow,
                click, player -> resetLeaf(player, id, self, optionKey, value, clear));
    }

    /** Choice leaf: bullets from the descriptor, effective option marked. */
    private MenuButton choiceRow(String id, Supplier<Menu> self, String optionKey,
            String raw, String fallback, boolean glow,
            Consumer<Player> click, Runnable clear) {
        ModifierOptionDescriptors.Descriptor descriptor =
                ModifierOptionDescriptors.byKey(optionKey);
        String effective = effectiveOption(raw, descriptor.options(), fallback);
        return leafRow(id, self, optionKey, null,
                raw == null ? "Default (" + fallback + ")" : raw,
                descriptor.options(), Set.of(effective), glow, click, clear);
    }

    private String hintFor(ModifierOptionDescriptors.Kind kind) {
        return switch (kind) {
            case NUMBER, INTEGER ->
                    messages.string("manhunt-gui.setting-hint-edit", "Click to edit");
            case CHOICE ->
                    messages.string("manhunt-gui.setting-hint-cycle", "Click to cycle");
            case TRIGGERS -> text("editor-click-open", "Click to open");
        };
    }

    /**
     * Right-click reset behind a confirm panel, mirroring the settings
     * flow: already-default rows refuse in chat instead of opening
     * a panel for nothing.
     */
    private void resetLeaf(Player player, String id, Supplier<Menu> self, String optionKey,
            String value, Runnable clear) {
        if (denied(player)) {
            return;
        }
        if (!leafModified(id, optionKey)) {
            messages.message(player, "modifiers-gui.editor-already-default");
            return;
        }
        ModifierOptionDescriptors.Descriptor descriptor =
                ModifierOptionDescriptors.byKey(optionKey);
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages, text("editor-reset-title", "Reset {name}?")
                        .replace("{name}", descriptor.label())),
                Material.PAPER, null,
                GuiTexts.lore(messages, List.of(value + " -> " + descriptor.defaultText())),
                GuiTexts.name(messages, text("cancel", "Cancel"), "Cancel"),
                back -> gui.navigate(back, self.get()),
                GuiTexts.name(messages, text("confirm", "Confirm"), "Confirm"),
                done -> {
                    clear.run();
                    if (sounds != null) {
                        sounds.playNeutralSound(done);
                    }
                    gui.navigate(done, self.get());
                },
                self);
        gui.navigate(player, confirm);
        if (sounds != null) {
            sounds.playSound(player, "compass.left-click");
        }
    }

    private boolean leafModified(String id, String optionKey) {
        return switch (optionKey) {
            case "delay" -> ModifiedGlow.behaviorDelay(store, id);
            case "interval" -> ModifiedGlow.behaviorInterval(store, id);
            case "deviation" -> ModifiedGlow.behaviorDeviation(store, id);
            case "interval-scope" -> ModifiedGlow.behaviorIntervalScope(store, id);
            case "selection" -> ModifiedGlow.behaviorSelection(store, id);
            case "pick-count" -> ModifiedGlow.behaviorPickCount(store, id);
            case "pick-scope" -> ModifiedGlow.behaviorPickScope(store, id);
            case "pre-start" -> ModifiedGlow.behaviorPreStart(store, id);
            case "chance" -> ModifiedGlow.behaviorChance(store, id);
            case "chance-scope" -> ModifiedGlow.behaviorChanceScope(store, id);
            case "runs-on" -> ModifiedGlow.behaviorRunsOn(store, id);
            default -> false;
        };
    }

    /** Stored option matched case-blindly, or the fallback default. */
    private static String effectiveOption(String raw, List<String> options, String fallback) {
        if (raw != null) {
            for (String option : options) {
                if (option.equalsIgnoreCase(raw)) {
                    return option;
                }
            }
        }
        return fallback;
    }

    private String runsOnValue(List<String> triggers) {
        if (triggers.isEmpty()) {
            return "Default (ON_START)";
        }
        return text("runs-on-count", "{total} selected")
                .replace("{total}", String.valueOf(triggers.size()));
    }

    /** Stored triggers uppercased for bullet marking. */
    private static Set<String> runsOnMarked(List<String> triggers) {
        Set<String> marked = new HashSet<>();
        for (String trigger : triggers) {
            marked.add(trigger.toUpperCase(Locale.ROOT));
        }
        return marked;
    }

    private void openInterval(Player player, String id, Menu self) {
        if (denied(player)) {
            return;
        }
        if (!hasInterval(id)) {
            invalid(player, text("interval-gated", "Add the INTERVAL trigger in Runs On first."));
            return;
        }
        gui.navigate(player, intervalMenu(id, () -> self));
        sounds.playSound(player, "compass.left-click");
    }

    private boolean hasInterval(String id) {
        return store.runsOn(id).stream().anyMatch("INTERVAL"::equalsIgnoreCase);
    }

    private void applyRunsOn(Player player, String id, List<String> previous,
            Set<String> checked, Menu self) {
        List<String> merged = new ArrayList<>();
        for (String trigger : previous) {
            if (ModifierTriggers.KNOWN.stream().noneMatch(trigger::equalsIgnoreCase)) {
                merged.add(trigger);
            }
        }
        merged.addAll(checked);
        patch(id, entry -> ModifierStore.ensureBehavior(entry)
                .setRunsOn(new ArrayList<>(merged)));
        gui.navigate(player, self);
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
            onStart.setPreStartOrder(cycle(store.preStartOrder(id), "IN_ORDER", "AFTER"));
        });
        sounds.playNeutralSound(player);
    }

    private void fieldPrompt(Player player, Menu self, String label, String current,
            boolean clearable, FieldPrompts.Submit submit) {
        FieldPrompts.prompt(dialogs, gui, messages, sounds, player, self,
                text("editor-prompt-title", "Edit {label}").replace("{label}", label),
                current, clearable, submit);
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

    private void selectionPatch(String id, String selection) {
        patch(id, entry -> ModifierStore
                .ensureExecution(ModifierStore.ensureOptions(
                        ModifierStore.ensureBehavior(entry))).setSelection(selection));
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

    private String orUnset(String value) {
        return value == null || value.isBlank()
                ? text("editor-unset", "Not set") : value;
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

    private Component backName() {
        return GuiTexts.name(messages, text("back", "Back"), "Back");
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
