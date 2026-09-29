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
    public Menu optionsMenu(String id, int index, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> optionButtons(id, index, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(messages, text("behavior-options-title", "Behavior Options")),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(gui, backName(), self)),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> optionButtons(String id, int index, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        buttons.add(runsOnRow(id, index, self));
        buttons.add(EditorButtons.actionButton(messages, Material.REPEATER,
                text("interval-settings-title", "Interval Settings"),
                List.of(text("interval-settings-lore", "Cadence, jitter, and scope"),
                        text("editor-click-open", "Click to open")),
                ModifiedGlow.behaviorIntervalGroup(store, id, index),
                player -> openInterval(player, id, index, self.get())).silent());
        buttons.add(EditorButtons.actionButton(messages, Material.BELL,
                text("execution-title", "Execution"),
                List.of(text("execution-lore", "Line selection and order"),
                        text("editor-click-open", "Click to open")),
                ModifiedGlow.behaviorExecutionGroup(store, id, index),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    gui.navigate(player, executionMenu(id, index, self::get));
                }));
        buttons.add(leafRow(id, index, self, "delay", null, delayText(id, index),
                null, null, ModifiedGlow.behaviorDelay(store, id, index),
                player -> fieldPrompt(player, self, "Delay", delayRaw(id, index), true,
                        raw -> submitDelay(id, index, raw)),
                () -> delayPatch(id, index, null)));
        buttons.add(EditorButtons.actionButton(messages, Material.HOPPER,
                text("chance-title", "Success Chance"),
                List.of(text("chance-lore", "Roll chance and scope"),
                        text("editor-click-open", "Click to open")),
                ModifiedGlow.behaviorChanceGroup(store, id, index),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    gui.navigate(player, chanceMenu(id, index, self::get));
                }));
        return buttons;
    }

    private MenuButton runsOnRow(String id, int index, Supplier<Menu> self) {
        List<String> triggers = store.runsOn(id, index);
        return leafRow(id, index, self, "runs-on",
                text("runs-on-title", "Runs On"), runsOnValue(triggers),
                ModifierTriggers.KNOWN, runsOnMarked(triggers),
                ModifiedGlow.behaviorRunsOn(store, id, index),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    modifierDialogs.openRunsOn(player, triggers,
                            checked -> applyRunsOn(player, id, index, triggers, checked, self.get()),
                            () -> gui.navigate(player, self.get()));
                },
                () -> patch(id, entry -> ModifierStore.ensureBehavior(entry, index).setRunsOn(null)));
    }

    /** Interval submenu: cadence, jitter, and scope rows. */
    public Menu intervalMenu(String id, int index, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> intervalButtons(id, index, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(messages, text("interval-settings-title", "Interval Settings")),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(gui, backName(), self)),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> intervalButtons(String id, int index, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        buttons.add(leafRow(id, index, self, "interval", null, intervalText(id, index),
                null, null, ModifiedGlow.behaviorInterval(store, id, index),
                player -> fieldPrompt(player, self, "Interval", intervalRaw(id, index), true,
                        raw -> submitInterval(id, index, raw)),
                () -> intervalPatch(id, index, null, deviationOf(id, index))));
        buttons.add(leafRow(id, index, self, "deviation", null, deviationText(id, index),
                null, null, ModifiedGlow.behaviorDeviation(store, id, index),
                player -> fieldPrompt(player, self, "Deviation", deviationRaw(id, index), true,
                        raw -> submitDeviation(id, index, raw)),
                () -> intervalPatch(id, index, intervalOf(id, index), null)));
        buttons.add(choiceRow(id, index, self, "interval-scope",
                store.intervalBehavior(id, index), "PER_INVOKE",
                ModifiedGlow.behaviorIntervalScope(store, id, index),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    patch(id, entry -> ensureInterval(entry, index).setBehavior(cycle(
                            store.intervalBehavior(id, index), "PER_INVOKE", "PER_EXECUTOR")));
                    sounds.playNeutralSound(player);
                },
                () -> patch(id, entry -> ensureInterval(entry, index).setBehavior(null))));
        return buttons;
    }

    /** Execution submenu: selection, pick, scope, and pre-start rows. */
    public Menu executionMenu(String id, int index, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> executionButtons(id, index, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(messages, text("execution-title", "Execution")),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(gui, backName(), self)),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> executionButtons(String id, int index, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        buttons.add(choiceRow(id, index, self, "selection",
                store.selection(id, index), "IN_ORDER",
                ModifiedGlow.behaviorSelection(store, id, index),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    selectionPatch(id, index, cycle(store.selection(id, index), "IN_ORDER", "PICK_RANDOM"));
                    sounds.playNeutralSound(player);
                },
                () -> selectionPatch(id, index, null)));
        buttons.add(leafRow(id, index, self, "pick-count", null, pickCountText(id, index),
                null, null, ModifiedGlow.behaviorPickCount(store, id, index),
                player -> fieldPrompt(player, self, "Pick count", pickCountRaw(id, index), true,
                        raw -> submitPickCount(id, index, raw)),
                () -> pickCountPatch(id, index, null)));
        buttons.add(choiceRow(id, index, self, "pick-scope",
                store.pickBehavior(id, index), "PER_INVOKE",
                ModifiedGlow.behaviorPickScope(store, id, index),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    patch(id, entry -> ensurePickRandom(entry, index).setBehavior(cycle(
                            store.pickBehavior(id, index), "PER_INVOKE", "PER_EXECUTOR")));
                    sounds.playNeutralSound(player);
                },
                () -> patch(id, entry -> ensurePickRandom(entry, index).setBehavior(null))));
        buttons.add(choiceRow(id, index, self, "pre-start",
                store.preStartOrder(id, index), "BEFORE",
                ModifiedGlow.behaviorPreStart(store, id, index),
                player -> cyclePreStart(player, id, index),
                () -> patch(id, entry -> {
                    ModifierBehavior behavior = ModifierStore.ensureBehavior(entry, index);
                    if (behavior.getOnStart() != null) {
                        behavior.getOnStart().setPreStartOrder(null);
                    }
                })));
        return buttons;
    }

    /** Success-chance submenu: chance and scope rows. */
    public Menu chanceMenu(String id, int index, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> chanceButtons(id, index, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(messages, text("chance-title", "Success Chance")),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(gui, backName(), self)),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> chanceButtons(String id, int index, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        buttons.add(leafRow(id, index, self, "chance", null, chanceText(id, index),
                null, null, ModifiedGlow.behaviorChance(store, id, index),
                player -> fieldPrompt(player, self, "Chance", chanceRaw(id, index), true,
                        raw -> submitChance(id, index, raw)),
                () -> chancePatch(id, index, null)));
        buttons.add(choiceRow(id, index, self, "chance-scope",
                store.chanceBehavior(id, index), "PER_INVOKE",
                ModifiedGlow.behaviorChanceScope(store, id, index),
                player -> {
                    if (denied(player)) {
                        return;
                    }
                    patch(id, entry -> ensureChance(entry, index).setBehavior(cycle(
                            store.chanceBehavior(id, index), "PER_INVOKE", "PER_EXECUTOR")));
                    sounds.playNeutralSound(player);
                },
                () -> patch(id, entry -> ensureChance(entry, index).setBehavior(null))));
        return buttons;
    }

    /**
     * One option leaf in the shared settings schema. A null label
     * keeps the descriptor label; the Runs On row overrides it with
     * its configured title.
     */
    private MenuButton leafRow(String id, int index, Supplier<Menu> self, String optionKey, String label,
            String value, List<String> options, Set<String> marked, boolean glow,
            Consumer<Player> click, Runnable clear) {
        ModifierOptionDescriptors.Descriptor descriptor =
                ModifierOptionDescriptors.byKey(optionKey);
        FieldLore.Field field = new FieldLore.Field(
                descriptor.description(), value,
                behaviorPath(id, index, descriptor.pathSuffix()),
                ModifierOptionDescriptors.typeName(descriptor.kind()),
                descriptor.allowed(), options, marked,
                descriptor.defaultText(), hintFor(descriptor.kind()), false);
        return FieldButtons.field(messages, descriptor.icon(),
                label == null ? descriptor.label() : label,
                FieldLore.lines(messages, field), glow,
                click, player -> resetLeaf(player, id, index, self, optionKey, value, clear));
    }

    /** Choice leaf: bullets from the descriptor, effective option marked. */
    private MenuButton choiceRow(String id, int index, Supplier<Menu> self, String optionKey,
            String raw, String fallback, boolean glow,
            Consumer<Player> click, Runnable clear) {
        ModifierOptionDescriptors.Descriptor descriptor =
                ModifierOptionDescriptors.byKey(optionKey);
        String effective = effectiveOption(raw, descriptor.options(), fallback);
        return leafRow(id, index, self, optionKey, null,
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
    private void resetLeaf(Player player, String id, int index, Supplier<Menu> self, String optionKey,
            String value, Runnable clear) {
        if (denied(player)) {
            return;
        }
        if (!leafModified(id, index, optionKey)) {
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

    private boolean leafModified(String id, int index, String optionKey) {
        return switch (optionKey) {
            case "delay" -> ModifiedGlow.behaviorDelay(store, id, index);
            case "interval" -> ModifiedGlow.behaviorInterval(store, id, index);
            case "deviation" -> ModifiedGlow.behaviorDeviation(store, id, index);
            case "interval-scope" -> ModifiedGlow.behaviorIntervalScope(store, id, index);
            case "selection" -> ModifiedGlow.behaviorSelection(store, id, index);
            case "pick-count" -> ModifiedGlow.behaviorPickCount(store, id, index);
            case "pick-scope" -> ModifiedGlow.behaviorPickScope(store, id, index);
            case "pre-start" -> ModifiedGlow.behaviorPreStart(store, id, index);
            case "chance" -> ModifiedGlow.behaviorChance(store, id, index);
            case "chance-scope" -> ModifiedGlow.behaviorChanceScope(store, id, index);
            case "runs-on" -> ModifiedGlow.behaviorRunsOn(store, id, index);
            default -> false;
        };
    }

    /** Config path for one behavior leaf, with the behavior index scoped in. */
    private static String behaviorPath(String id, int index, String pathSuffix) {
        String scoped = pathSuffix.startsWith("behavior.")
                ? "behavior." + index + "." + pathSuffix.substring("behavior.".length())
                : pathSuffix;
        return "modifiers." + id + "." + scoped;
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

    private void openInterval(Player player, String id, int index, Menu self) {
        if (denied(player)) {
            return;
        }
        if (!hasInterval(id, index)) {
            invalid(player, text("interval-gated", "Add the INTERVAL trigger in Runs On first."));
            return;
        }
        gui.navigate(player, intervalMenu(id, index, () -> self));
        sounds.playSound(player, "compass.left-click");
    }

    private boolean hasInterval(String id, int index) {
        return store.runsOn(id, index).stream().anyMatch("INTERVAL"::equalsIgnoreCase);
    }

    private void applyRunsOn(Player player, String id, int index, List<String> previous,
            Set<String> checked, Menu self) {
        List<String> merged = new ArrayList<>();
        for (String trigger : previous) {
            if (ModifierTriggers.KNOWN.stream().noneMatch(trigger::equalsIgnoreCase)) {
                merged.add(trigger);
            }
        }
        merged.addAll(checked);
        patch(id, entry -> ModifierStore.ensureBehavior(entry, index)
                .setRunsOn(new ArrayList<>(merged)));
        gui.navigate(player, self);
    }

    private void cyclePreStart(Player player, String id, int index) {
        if (denied(player)) {
            return;
        }
        patch(id, entry -> {
            ModifierBehavior behavior = ModifierStore.ensureBehavior(entry, index);
            ModifierOnStart onStart = behavior.getOnStart();
            if (onStart == null) {
                onStart = new ModifierOnStart();
                behavior.setOnStart(onStart);
            }
            onStart.setPreStartOrder(cycle(store.preStartOrder(id, index), "BEFORE", "AFTER"));
        });
        sounds.playNeutralSound(player);
    }

    private void fieldPrompt(Player player, Supplier<Menu> reopen, String label, String current,
            boolean clearable, FieldPrompts.Submit submit) {
        FieldPrompts.prompt(dialogs, gui, messages, sounds, player, reopen,
                text("editor-prompt-title", "Edit {label}").replace("{label}", label),
                current, clearable, submit);
    }

    private void patch(String id, Consumer<ModifierEntry> patch) {
        store.updateModifier(id, patch);
    }

    private String submitInterval(String id, int index, String raw) {
        if (raw == null) {
            intervalPatch(id, index, null, null);
            return null;
        }
        ModifierFieldEdits.Parsed<Double> parsed =
                ModifierFieldEdits.number("Interval", raw, 0.0, null);
        if (!parsed.ok()) {
            return parsed.error();
        }
        Double deviation = deviationOf(id, index);
        if (deviation != null && deviation > parsed.value()) {
            return "Deviation (" + deviation + "s) exceeds the new interval.";
        }
        intervalPatch(id, index, parsed.value(), deviation);
        return null;
    }

    private String submitDeviation(String id, int index, String raw) {
        if (raw == null) {
            intervalPatch(id, index, intervalOf(id, index), null);
            return null;
        }
        ModifierFieldEdits.Parsed<Double> parsed =
                ModifierFieldEdits.number("Deviation", raw, 0.0, null);
        if (!parsed.ok()) {
            return parsed.error();
        }
        Double interval = intervalOf(id, index);
        if (interval == null) {
            return "Deviation needs an interval first.";
        }
        if (parsed.value() > interval) {
            return "Deviation must not exceed the interval (" + interval + "s).";
        }
        intervalPatch(id, index, interval, parsed.value());
        return null;
    }

    private String submitChance(String id, int index, String raw) {
        if (raw == null) {
            chancePatch(id, index, null);
            return null;
        }
        ModifierFieldEdits.Parsed<Double> parsed =
                ModifierFieldEdits.number("Chance", raw, 0.0, 1.0);
        if (!parsed.ok()) {
            return parsed.error();
        }
        chancePatch(id, index, parsed.value());
        return null;
    }

    private String submitPickCount(String id, int index, String raw) {
        if (raw == null) {
            pickCountPatch(id, index, null);
            return null;
        }
        ModifierFieldEdits.Parsed<Long> parsed =
                ModifierFieldEdits.whole("Pick count", raw, 1L, null);
        if (!parsed.ok()) {
            return parsed.error();
        }
        pickCountPatch(id, index, parsed.value().intValue());
        return null;
    }

    private String submitDelay(String id, int index, String raw) {
        if (raw == null) {
            delayPatch(id, index, null);
            return null;
        }
        ModifierFieldEdits.Parsed<Long> parsed =
                ModifierFieldEdits.whole("Delay", raw, 0L, null);
        if (!parsed.ok()) {
            return parsed.error();
        }
        delayPatch(id, index, parsed.value());
        return null;
    }

    private void intervalPatch(String id, int index, Double interval, Double deviation) {
        patch(id, entry -> {
            ModifierInterval settings = ensureInterval(entry, index);
            settings.setInterval(interval);
            settings.setDeviation(deviation);
        });
    }

    private void chancePatch(String id, int index, Double chance) {
        patch(id, entry -> ensureChance(entry, index).setChance(chance));
    }

    private void pickCountPatch(String id, int index, Integer count) {
        patch(id, entry -> ensurePickRandom(entry, index).setCount(count));
    }

    private void selectionPatch(String id, int index, String selection) {
        patch(id, entry -> ModifierStore
                .ensureExecution(ModifierStore.ensureOptions(
                        ModifierStore.ensureBehavior(entry, index))).setSelection(selection));
    }

    private void delayPatch(String id, int index, Long delay) {
        patch(id, entry -> ModifierStore
                .ensureOptions(ModifierStore.ensureBehavior(entry, index)).setDelay(delay));
    }

    private static ModifierInterval ensureInterval(ModifierEntry entry, int index) {
        ModifierOptions options =
                ModifierStore.ensureOptions(ModifierStore.ensureBehavior(entry, index));
        ModifierInterval settings = options.getIntervalSettings();
        if (settings == null) {
            settings = new ModifierInterval();
            options.setIntervalSettings(settings);
        }
        return settings;
    }

    private static ModifierChance ensureChance(ModifierEntry entry, int index) {
        ModifierOptions options =
                ModifierStore.ensureOptions(ModifierStore.ensureBehavior(entry, index));
        ModifierChance chance = options.getSuccessChance();
        if (chance == null) {
            chance = new ModifierChance();
            options.setSuccessChance(chance);
        }
        return chance;
    }

    private static ModifierPickRandom ensurePickRandom(ModifierEntry entry, int index) {
        ModifierExecution execution = ModifierStore.ensureExecution(
                ModifierStore.ensureOptions(ModifierStore.ensureBehavior(entry, index)));
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

    private Double intervalOf(String id, int index) {
        ModifierOptions options = rawOptions(id, index);
        return options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getInterval();
    }

    private Double deviationOf(String id, int index) {
        ModifierOptions options = rawOptions(id, index);
        return options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getDeviation();
    }

    private Double chanceOf(String id, int index) {
        ModifierOptions options = rawOptions(id, index);
        return options == null || options.getSuccessChance() == null
                ? null : options.getSuccessChance().getChance();
    }

    private Integer pickCountOf(String id, int index) {
        ModifierOptions options = rawOptions(id, index);
        if (options == null || options.getExecution() == null
                || options.getExecution().getPickRandom() == null) {
            return null;
        }
        return options.getExecution().getPickRandom().getCount();
    }

    private Long delayOf(String id, int index) {
        ModifierOptions options = rawOptions(id, index);
        return options == null ? null : options.getDelay();
    }

    private ModifierOptions rawOptions(String id, int index) {
        ModifierEntry entry = store.modifierEntry(id);
        return entry == null ? null : rawOptions(entry, index);
    }

    private static ModifierOptions rawOptions(ModifierEntry entry, int index) {
        if (entry.getBehavior() == null) {
            return null;
        }
        ModifierBehavior behavior = entry.getBehavior().get(String.valueOf(index));
        return behavior == null ? null : behavior.getOptions();
    }

    private String intervalText(String id, int index) {
        Double interval = intervalOf(id, index);
        return interval == null ? orUnset(null) : interval + "s";
    }

    private String intervalRaw(String id, int index) {
        Double interval = intervalOf(id, index);
        return interval == null ? "" : String.valueOf(interval);
    }

    private String deviationText(String id, int index) {
        Double deviation = deviationOf(id, index);
        return deviation == null ? orUnset(null) : deviation + "s";
    }

    private String deviationRaw(String id, int index) {
        Double deviation = deviationOf(id, index);
        return deviation == null ? "" : String.valueOf(deviation);
    }

    private String chanceText(String id, int index) {
        Double chance = chanceOf(id, index);
        return chance == null ? "Default (100%)" : Math.round(chance * 100) + "%";
    }

    private String chanceRaw(String id, int index) {
        Double chance = chanceOf(id, index);
        return chance == null ? "" : String.valueOf(chance);
    }

    private String pickCountText(String id, int index) {
        Integer count = pickCountOf(id, index);
        return count == null ? "Default (1)" : String.valueOf(count);
    }

    private String pickCountRaw(String id, int index) {
        Integer count = pickCountOf(id, index);
        return count == null ? "" : String.valueOf(count);
    }

    private String delayText(String id, int index) {
        Long delay = delayOf(id, index);
        return delay == null ? orUnset(null) : delay + " ticks";
    }

    private String delayRaw(String id, int index) {
        Long delay = delayOf(id, index);
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
