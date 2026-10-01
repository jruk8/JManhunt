package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.Panels;
import com.jruk8.jmanhunt.gui.ScalingLayout;
import com.jruk8.jmanhunt.modifiers.ModifierFieldEdits;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import com.jruk8.jmanhunt.modifiers.config.ModifierInterval;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.bukkit.entity.Player;

/** Interval submenu behind {@link BehaviorOptionsMenus}. */
final class IntervalMenuBuilder {
    private final ModifierStore store;
    private final BehaviorOptionsMenus.BehaviorTexts texts;
    private final BehaviorOptionsMenus.BehaviorDeps deps;
    private final BehaviorRows rows;

    IntervalMenuBuilder(ModifierStore store, BehaviorOptionsMenus.BehaviorTexts texts,
            BehaviorOptionsMenus.BehaviorDeps deps, BehaviorRows rows) {
        this.store = store;
        this.texts = texts;
        this.deps = deps;
        this.rows = rows;
    }

    public Menu intervalMenu(String id, int index, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> intervalButtons(id, index, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(texts.messages(), texts.modifiersGui().getIntervalSettingsTitle()),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(deps.gui(), rows.backName(), self)),
                content::get, parent);
        return self[0];
    }

    List<MenuButton> intervalButtons(String id, int index, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        buttons.add(rows.leafRow(id, index, self, "interval", null, intervalText(id, index),
                null, null, ModifiedGlow.behaviorInterval(store, id, index),
                player -> rows.fieldPrompt(player, self, "Interval", intervalRaw(id, index), true,
                        raw -> submitInterval(id, index, raw)),
                () -> intervalPatch(id, index, null, deviationOf(id, index))));
        buttons.add(rows.leafRow(id, index, self, "deviation", null, deviationText(id, index),
                null, null, ModifiedGlow.behaviorDeviation(store, id, index),
                player -> rows.fieldPrompt(player, self, "Deviation", deviationRaw(id, index), true,
                        raw -> submitDeviation(id, index, raw)),
                () -> intervalPatch(id, index, intervalOf(id, index), null)));
        buttons.add(rows.choiceRow(id, index, self, "interval-scope",
                store.intervalBehavior(id, index), "PER_INVOKE",
                ModifiedGlow.behaviorIntervalScope(store, id, index),
                player -> {
                    if (rows.denied(player)) {
                        return;
                    }
                    rows.patch(id, entry -> ensureInterval(entry, index).setBehavior(rows.cycle(
                            store.intervalBehavior(id, index), "PER_INVOKE", "PER_EXECUTOR")));
                    texts.sounds().playNeutralSound(player);
                },
                () -> rows.patch(id, entry -> ensureInterval(entry, index).setBehavior(null))));
        return buttons;
    }

    void openInterval(Player player, String id, int index, Menu self) {
        if (rows.denied(player)) {
            return;
        }
        if (!hasInterval(id, index)) {
            rows.invalid(player, texts.modifiersGui().getIntervalGated());
            return;
        }
        deps.gui().navigate(player, intervalMenu(id, index, () -> self));
        texts.sounds().playSound(player, "compass.left-click");
    }

    boolean hasInterval(String id, int index) {
        return store.runsOn(id, index).stream().anyMatch("INTERVAL"::equalsIgnoreCase);
    }

    String submitInterval(String id, int index, String raw) {
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

    String submitDeviation(String id, int index, String raw) {
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

    void intervalPatch(String id, int index, Double interval, Double deviation) {
        rows.patch(id, entry -> {
            ModifierInterval settings = ensureInterval(entry, index);
            settings.setInterval(interval);
            settings.setDeviation(deviation);
        });
    }

    static ModifierInterval ensureInterval(ModifierEntry entry, int index) {
        ModifierOptions options =
                ModifierStore.ensureOptions(ModifierStore.ensureBehavior(entry, index));
        ModifierInterval settings = options.getIntervalSettings();
        if (settings == null) {
            settings = new ModifierInterval();
            options.setIntervalSettings(settings);
        }
        return settings;
    }

    Double intervalOf(String id, int index) {
        ModifierOptions options = rows.rawOptions(id, index);
        return options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getInterval();
    }

    Double deviationOf(String id, int index) {
        ModifierOptions options = rows.rawOptions(id, index);
        return options == null || options.getIntervalSettings() == null
                ? null : options.getIntervalSettings().getDeviation();
    }

    String intervalText(String id, int index) {
        Double interval = intervalOf(id, index);
        return interval == null ? rows.orUnset(null) : interval + "s";
    }

    String intervalRaw(String id, int index) {
        Double interval = intervalOf(id, index);
        return interval == null ? "" : String.valueOf(interval);
    }

    String deviationText(String id, int index) {
        Double deviation = deviationOf(id, index);
        return deviation == null ? rows.orUnset(null) : deviation + "s";
    }

    String deviationRaw(String id, int index) {
        Double deviation = deviationOf(id, index);
        return deviation == null ? "" : String.valueOf(deviation);
    }
}
