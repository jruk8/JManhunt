package com.jruk8.jmanhunt.gui.menus;

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
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.ManhuntGuiMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersGuiMessages;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.modifiers.ModifierFieldEdits;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierExecution;
import com.jruk8.jmanhunt.modifiers.config.ModifierOnStart;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import com.jruk8.jmanhunt.modifiers.config.ModifierPickRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
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

    /** Message bus, gui texts, modifier/command texts, and sounds. */
    public record BehaviorTexts(MessageService messages, ModifiersGuiMessages modifiersGui,
            ManhuntGuiMessages manhuntGui, ModifiersMessages modifiers,
            CommandMessages command, SoundService sounds) {
    }

    /** Gui plus setting and modifier dialogs; nulls tolerated per action. */
    public record BehaviorDeps(GuiService gui, SettingDialogs dialogs,
            ModifierDialog modifierDialogs) {
    }

    private final ModifierStore store;
    private final BehaviorTexts texts;
    private final BehaviorDeps deps;
    private final BehaviorRows rows;
    private final ChanceMenuBuilder chance;
    private final IntervalMenuBuilder intervals;

    public BehaviorOptionsMenus(ModifierStore store, BehaviorTexts texts, BehaviorDeps deps) {
        this.store = store;
        this.texts = texts;
        this.deps = deps;
        this.rows = new BehaviorRows(store, texts, deps);
        this.chance = new ChanceMenuBuilder(store, texts, deps, rows);
        this.intervals = new IntervalMenuBuilder(store, texts, deps, rows);
    }

    /** Success-chance submenu: chance and scope rows. */
    public Menu chanceMenu(String id, int index, Supplier<Menu> parent) {
        return chance.chanceMenu(id, index, parent);
    }

    /** Interval submenu: interval, deviation, and scope rows. */
    public Menu intervalMenu(String id, int index, Supplier<Menu> parent) {
        return intervals.intervalMenu(id, index, parent);
    }

    /** Five-entry options menu for one modifier. */
    public Menu optionsMenu(String id, int index, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> optionButtons(id, index, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(texts.messages(), texts.modifiersGui().getBehaviorOptionsTitle()),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(deps.gui(), rows.backName(), self)),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> optionButtons(String id, int index, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        buttons.add(runsOnRow(id, index, self));
        buttons.add(EditorButtons.actionButton(texts.messages(), Material.REPEATER,
                texts.modifiersGui().getIntervalSettingsTitle(),
                List.of(texts.modifiersGui().getIntervalSettingsLore(),
                        texts.modifiersGui().getEditorClickOpen()),
                ModifiedGlow.behaviorIntervalGroup(store, id, index),
                player -> intervals.openInterval(player, id, index, self.get())).silent());
        buttons.add(EditorButtons.actionButton(texts.messages(), Material.BELL,
                texts.modifiersGui().getExecutionTitle(),
                List.of(texts.modifiersGui().getExecutionLore(),
                        texts.modifiersGui().getEditorClickOpen()),
                ModifiedGlow.behaviorExecutionGroup(store, id, index),
                player -> {
                    if (rows.denied(player)) {
                        return;
                    }
                    deps.gui().navigate(player, executionMenu(id, index, self::get));
                }));
        buttons.add(rows.leafRow(id, index, self, "delay", null, delayText(id, index),
                null, null, ModifiedGlow.behaviorDelay(store, id, index),
                player -> rows.fieldPrompt(player, self, "Delay", delayRaw(id, index), true,
                        raw -> submitDelay(id, index, raw)),
                () -> delayPatch(id, index, null)));
        buttons.add(EditorButtons.actionButton(texts.messages(), Material.HOPPER,
                texts.modifiersGui().getChanceTitle(),
                List.of(texts.modifiersGui().getChanceLore(),
                        texts.modifiersGui().getEditorClickOpen()),
                ModifiedGlow.behaviorChanceGroup(store, id, index),
                player -> {
                    if (rows.denied(player)) {
                        return;
                    }
                    deps.gui().navigate(player, chanceMenu(id, index, self::get));
                }));
        return buttons;
    }

    private MenuButton runsOnRow(String id, int index, Supplier<Menu> self) {
        List<String> triggers = store.runsOn(id, index);
        return rows.leafRow(id, index, self, "runs-on",
                texts.modifiersGui().getRunsOnTitle(), runsOnValue(triggers),
                ModifierTriggers.KNOWN, runsOnMarked(triggers),
                ModifiedGlow.behaviorRunsOn(store, id, index),
                player -> {
                    if (rows.denied(player)) {
                        return;
                    }
                    deps.modifierDialogs().openRunsOn(player, triggers,
                            checked -> applyRunsOn(player, id, index, triggers, checked, self.get()),
                            () -> deps.gui().navigate(player, self.get()));
                },
                () -> rows.patch(id, entry -> ModifierStore.ensureBehavior(entry, index).setRunsOn(null)));
    }

    /** Interval submenu: cadence, jitter, and scope rows. */
    /** Execution submenu: selection, pick, scope, and pre-start rows. */
    public Menu executionMenu(String id, int index, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> executionButtons(id, index, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(texts.messages(), texts.modifiersGui().getExecutionTitle()),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(deps.gui(), rows.backName(), self)),
                content::get, parent);
        return self[0];
    }

    private List<MenuButton> executionButtons(String id, int index, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        buttons.add(rows.choiceRow(id, index, self, "selection",
                store.selection(id, index), "IN_ORDER",
                ModifiedGlow.behaviorSelection(store, id, index),
                player -> {
                    if (rows.denied(player)) {
                        return;
                    }
                    selectionPatch(id, index, rows.cycle(store.selection(id, index), "IN_ORDER", "PICK_RANDOM"));
                    texts.sounds().playNeutralSound(player);
                },
                () -> selectionPatch(id, index, null)));
        buttons.add(rows.leafRow(id, index, self, "pick-count", null, pickCountText(id, index),
                null, null, ModifiedGlow.behaviorPickCount(store, id, index),
                player -> rows.fieldPrompt(player, self, "Pick count", pickCountRaw(id, index), true,
                        raw -> submitPickCount(id, index, raw)),
                () -> pickCountPatch(id, index, null)));
        buttons.add(rows.choiceRow(id, index, self, "pick-scope",
                store.pickBehavior(id, index), "PER_INVOKE",
                ModifiedGlow.behaviorPickScope(store, id, index),
                player -> {
                    if (rows.denied(player)) {
                        return;
                    }
                    rows.patch(id, entry -> ensurePickRandom(entry, index).setBehavior(rows.cycle(
                            store.pickBehavior(id, index), "PER_INVOKE", "PER_EXECUTOR")));
                    texts.sounds().playNeutralSound(player);
                },
                () -> rows.patch(id, entry -> ensurePickRandom(entry, index).setBehavior(null))));
        buttons.add(rows.choiceRow(id, index, self, "pre-start",
                store.preStartOrder(id, index), "BEFORE",
                ModifiedGlow.behaviorPreStart(store, id, index),
                player -> cyclePreStart(player, id, index),
                () -> rows.patch(id, entry -> {
                    ModifierBehavior behavior = ModifierStore.ensureBehavior(entry, index);
                    if (behavior.getOnStart() != null) {
                        behavior.getOnStart().setPreStartOrder(null);
                    }
                })));
        return buttons;
    }

    /** Success-chance submenu: chance and scope rows. */
    /** Choice leaf: bullets from the descriptor, effective option marked. */
    /** Config path for one behavior leaf, with the behavior index scoped in. */
    /** Stored option matched case-blindly, or the fallback default. */
    private String runsOnValue(List<String> triggers) {
        if (triggers.isEmpty()) {
            return "Default (ON_START)";
        }
        return texts.modifiersGui().getRunsOnCount().replace("{total}", String.valueOf(triggers.size()));
    }

    /** Stored triggers uppercased for bullet marking. */
    private static Set<String> runsOnMarked(List<String> triggers) {
        Set<String> marked = new HashSet<>();
        for (String trigger : triggers) {
            marked.add(trigger.toUpperCase(Locale.ROOT));
        }
        return marked;
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
        rows.patch(id, entry -> ModifierStore.ensureBehavior(entry, index)
                .setRunsOn(new ArrayList<>(merged)));
        deps.gui().navigate(player, self);
    }

    private void cyclePreStart(Player player, String id, int index) {
        if (rows.denied(player)) {
            return;
        }
        rows.patch(id, entry -> {
            ModifierBehavior behavior = ModifierStore.ensureBehavior(entry, index);
            ModifierOnStart onStart = behavior.getOnStart();
            if (onStart == null) {
                onStart = new ModifierOnStart();
                behavior.setOnStart(onStart);
            }
            onStart.setPreStartOrder(rows.cycle(store.preStartOrder(id, index), "BEFORE", "AFTER"));
        });
        texts.sounds().playNeutralSound(player);
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

    private void pickCountPatch(String id, int index, Integer count) {
        rows.patch(id, entry -> ensurePickRandom(entry, index).setCount(count));
    }

    private void selectionPatch(String id, int index, String selection) {
        rows.patch(id, entry -> ModifierStore
                .ensureExecution(ModifierStore.ensureOptions(
                        ModifierStore.ensureBehavior(entry, index))).setSelection(selection));
    }

    private void delayPatch(String id, int index, Long delay) {
        rows.patch(id, entry -> ModifierStore
                .ensureOptions(ModifierStore.ensureBehavior(entry, index)).setDelay(delay));
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
    private Integer pickCountOf(String id, int index) {
        ModifierOptions options = rows.rawOptions(id, index);
        if (options == null || options.getExecution() == null
                || options.getExecution().getPickRandom() == null) {
            return null;
        }
        return options.getExecution().getPickRandom().getCount();
    }

    private Long delayOf(String id, int index) {
        ModifierOptions options = rows.rawOptions(id, index);
        return options == null ? null : options.getDelay();
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
        return delay == null ? rows.orUnset(null) : delay + " ticks";
    }

    private String delayRaw(String id, int index) {
        Long delay = delayOf(id, index);
        return delay == null ? "" : String.valueOf(delay);
    }


}
