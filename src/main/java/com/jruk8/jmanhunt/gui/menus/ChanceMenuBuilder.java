package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.MenuLayout;
import com.jruk8.jmanhunt.gui.Panels;
import com.jruk8.jmanhunt.gui.ScalingLayout;
import com.jruk8.jmanhunt.modifiers.ModifierFieldEdits;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierChance;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Success-chance submenu behind {@link BehaviorOptionsMenus}. */
final class ChanceMenuBuilder {
    private final ModifierStore store;
    private final BehaviorOptionsMenus.BehaviorTexts texts;
    private final BehaviorOptionsMenus.BehaviorDeps deps;
    private final BehaviorRows rows;

    ChanceMenuBuilder(ModifierStore store, BehaviorOptionsMenus.BehaviorTexts texts,
            BehaviorOptionsMenus.BehaviorDeps deps, BehaviorRows rows) {
        this.store = store;
        this.texts = texts;
        this.deps = deps;
        this.rows = rows;
    }

    public Menu chanceMenu(String id, int index, Supplier<Menu> parent) {
        final Menu[] self = new Menu[1];
        Supplier<List<MenuButton>> content = () -> chanceButtons(id, index, () -> self[0]);
        MenuLayout layout = ScalingLayout.layout(ScalingLayout.rowsFor(content.get().size()));
        self[0] = new Menu(
                GuiTexts.title(texts.messages(), texts.modifiersGui().getChanceTitle()),
                layout, () -> Map.of(ScalingLayout.backSlot(layout.rowCount()),
                        Panels.backButton(deps.gui(), rows.backName(), self)),
                content::get, parent);
        return self[0];
    }

    List<MenuButton> chanceButtons(String id, int index, Supplier<Menu> self) {
        List<MenuButton> buttons = new ArrayList<>();
        buttons.add(rows.leafRow(id, index, self, "chance", null, chanceText(id, index),
                null, null, ModifiedGlow.behaviorChance(store, id, index),
                player -> rows.fieldPrompt(player, self, "Chance", chanceRaw(id, index), true,
                        raw -> submitChance(id, index, raw)),
                () -> chancePatch(id, index, null)));
        buttons.add(rows.choiceRow(id, index, self, "chance-scope",
                store.chanceBehavior(id, index), "PER_INVOKE",
                ModifiedGlow.behaviorChanceScope(store, id, index),
                player -> {
                    if (rows.denied(player)) {
                        return;
                    }
                    rows.patch(id, entry -> ensureChance(entry, index).setBehavior(rows.cycle(
                            store.chanceBehavior(id, index), "PER_INVOKE", "PER_EXECUTOR")));
                    texts.sounds().playNeutralSound(player);
                },
                () -> rows.patch(id, entry -> ensureChance(entry, index).setBehavior(null))));
        return buttons;
    }

    String submitChance(String id, int index, String raw) {
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

    void chancePatch(String id, int index, Double chance) {
        rows.patch(id, entry -> ensureChance(entry, index).setChance(chance));
    }

    static ModifierChance ensureChance(ModifierEntry entry, int index) {
        ModifierOptions options =
                ModifierStore.ensureOptions(ModifierStore.ensureBehavior(entry, index));
        ModifierChance chance = options.getSuccessChance();
        if (chance == null) {
            chance = new ModifierChance();
            options.setSuccessChance(chance);
        }
        return chance;
    }

    Double chanceOf(String id, int index) {
        ModifierOptions options = rows.rawOptions(id, index);
        return options == null || options.getSuccessChance() == null
                ? null : options.getSuccessChance().getChance();
    }

    String chanceText(String id, int index) {
        Double chance = chanceOf(id, index);
        return chance == null ? "Default (100%)" : Math.round(chance * 100) + "%";
    }

    String chanceRaw(String id, int index) {
        Double chance = chanceOf(id, index);
        return chance == null ? "" : String.valueOf(chance);
    }
}
