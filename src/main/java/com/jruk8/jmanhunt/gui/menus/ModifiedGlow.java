package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.modifiers.ModifierStore;

/**
 * Recursive modified glow for every Scaling Menu.
 *
 * <p>A leaf glows when its current value differs from its default; a
 * category glows when ANY of its immediate children (leaf or
 * subcategory) glows. Settings compare against {@link SettingRegistry}
 * defaults, lists against their configured defaults, and behavior
 * leaves against the engine's unset defaults.
 */
public final class ModifiedGlow {

    private ModifiedGlow() {
    }

    /** True when one scalar setting differs from its default. */
    public static boolean leafSetting(ConfigService config, String path) {
        SettingDescriptor descriptor = SettingRegistry.byPath(path);
        SettingRegistry.ValidationOutcome parsed = SettingRegistry.validate(
                descriptor, descriptor.defaultValue(), config::getValue);
        String canonical = parsed.ok()
                ? ConfigService.displayValue(parsed.value())
                : descriptor.defaultValue();
        return !ConfigService.displayValue(config.getValue(descriptor.path())).equals(canonical);
    }

    /** True when one string list differs from its configured default. */
    public static boolean leafList(ConfigService config, String listPath) {
        return config.isListModified(listPath);
    }

    /**
     * True when ANY immediate child of one settings section glows:
     * terminal leaves, string lists, and nested subsections alike.
     */
    public static boolean section(ConfigService config, String path) {
        SettingRegistry.DrillChildren children = SettingRegistry.children(path);
        for (String leaf : children.leaves()) {
            if (leafSetting(config, path + "." + leaf)) {
                return true;
            }
        }
        for (String section : children.sections()) {
            String child = path + "." + section;
            if (SettingRegistry.isListPath(child)) {
                if (leafList(config, child)) {
                    return true;
                }
            } else if (section(config, child)) {
                return true;
            }
        }
        return false;
    }

    /** True when the modifier is enabled (new modifiers start disabled). */
    public static boolean behaviorEnabled(ModifierStore store, String id) {
        return store.isEnabled(id);
    }

    /** True when runs-on carries any trigger. */
    public static boolean behaviorRunsOn(ModifierStore store, String id, int index) {
        return !store.runsOn(id, index).isEmpty();
    }

    /** True when the interval seconds differ from 60. */
    public static boolean behaviorInterval(ModifierStore store, String id, int index) {
        return store.intervalSeconds(id, index) != 60.0;
    }

    /** True when the deviation seconds differ from 0. */
    public static boolean behaviorDeviation(ModifierStore store, String id, int index) {
        return store.intervalDeviation(id, index) != 0.0;
    }

    /** True when the interval scope differs from PER_INVOKE. */
    public static boolean behaviorIntervalScope(ModifierStore store, String id, int index) {
        return explicitScope(store.intervalBehavior(id, index));
    }

    /** True when ANY interval leaf glows. */
    public static boolean behaviorIntervalGroup(ModifierStore store, String id, int index) {
        return behaviorInterval(store, id, index)
                || behaviorDeviation(store, id, index)
                || behaviorIntervalScope(store, id, index);
    }

    /** True when the selection differs from IN_ORDER. */
    public static boolean behaviorSelection(ModifierStore store, String id, int index) {
        String selection = store.selection(id, index);
        return selection != null && !"IN_ORDER".equalsIgnoreCase(selection);
    }

    /** True when the pick count differs from 1. */
    public static boolean behaviorPickCount(ModifierStore store, String id, int index) {
        return store.pickCount(id, index) != 1;
    }

    /** True when the pick scope differs from PER_INVOKE. */
    public static boolean behaviorPickScope(ModifierStore store, String id, int index) {
        return explicitScope(store.pickBehavior(id, index));
    }

    /** True when pre-start order defers past the pre-start hit. */
    public static boolean behaviorPreStart(ModifierStore store, String id, int index) {
        String order = store.preStartOrder(id, index);
        return order != null && "AFTER".equalsIgnoreCase(order.trim());
    }

    /** True when ANY execution leaf glows. */
    public static boolean behaviorExecutionGroup(ModifierStore store, String id, int index) {
        return behaviorSelection(store, id, index)
                || behaviorPickCount(store, id, index)
                || behaviorPickScope(store, id, index)
                || behaviorPreStart(store, id, index);
    }

    /** True when the delay ticks differ from 0. */
    public static boolean behaviorDelay(ModifierStore store, String id, int index) {
        return store.delayTicks(id, index) != 0L;
    }

    /** True when the success chance differs from 100%. */
    public static boolean behaviorChance(ModifierStore store, String id, int index) {
        return store.chance(id, index) != 1.0;
    }

    /** True when the chance scope differs from PER_INVOKE. */
    public static boolean behaviorChanceScope(ModifierStore store, String id, int index) {
        return explicitScope(store.chanceBehavior(id, index));
    }

    /** True when ANY success-chance leaf glows. */
    public static boolean behaviorChanceGroup(ModifierStore store, String id, int index) {
        return behaviorChance(store, id, index) || behaviorChanceScope(store, id, index);
    }

    /** True when ANY behavior leaf of one modifier glows. */
    public static boolean behaviorAny(ModifierStore store, String id) {
        if (behaviorEnabled(store, id)) {
            return true;
        }
        for (int index : store.behaviorIndexes(id)) {
            if (behaviorRunsOn(store, id, index)
                    || behaviorIntervalGroup(store, id, index)
                    || behaviorExecutionGroup(store, id, index)
                    || behaviorDelay(store, id, index)
                    || behaviorChanceGroup(store, id, index)) {
                return true;
            }
        }
        return false;
    }

    private static boolean explicitScope(String raw) {
        return raw != null && !"PER_INVOKE".equalsIgnoreCase(raw.trim());
    }
}
