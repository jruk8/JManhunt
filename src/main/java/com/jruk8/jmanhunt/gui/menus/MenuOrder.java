package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.gui.GuiTexts;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Menu entry order: enabled first, then file order, then stripped display
 * name, then id.
 *
 * <p>Both menus share the shape; only the enabled test differs (a modifier
 * flag versus a preset all-on check), so callers supply it as a lambda.
 * Ids missing from the order map sort last and fall back to alphabetical.
 * Pure and Bukkit-free for direct unit tests.
 */
public final class MenuOrder {

    private MenuOrder() {
    }

    /** Modifier ids: enabled first, then file order, then display name. */
    public static Comparator<String> modifiers(Function<String, String> displayName,
            Predicate<String> enabled, Function<String, Integer> order) {
        return byState(displayName, enabled, order);
    }

    /** Preset ids: all-on first, then file order, then display name. */
    public static Comparator<String> presets(Function<String, String> displayName,
            Predicate<String> allOn, Function<String, Integer> order) {
        return byState(displayName, allOn, order);
    }

    private static Comparator<String> byState(Function<String, String> displayName,
            Predicate<String> enabled, Function<String, Integer> order) {
        return Comparator.comparing((String id) -> enabled.test(id) ? 0 : 1)
                .thenComparing(id -> orderIndex(order, id))
                .thenComparing(id -> GuiTexts.sortKey(displayName.apply(id)))
                .thenComparing(id -> id);
    }

    private static int orderIndex(Function<String, Integer> order, String id) {
        Integer index = order.apply(id);
        return index == null ? Integer.MAX_VALUE : index;
    }
}
