package com.jruk8.jmanhunt.config;

import java.util.List;
import java.util.Map;

/**
 * One editable scalar: its path, type, canonical default, bounds, and
 * allowed options. Bounds use boxed doubles so int and float settings
 * share one shape; null means unbounded. Dynamic maxima resolve from
 * live values at validation time.
 */
public record SettingDescriptor(
        String path,
        SettingType type,
        String defaultValue,
        Double min,
        Double max,
        List<String> options,
        Map<String, String> aliases,
        boolean minusOneOrMin,
        DynamicBound dynamicBound,
        boolean restartRequired) {

    /** Live-resolved maxima. */
    public enum DynamicBound {
        NONE,
        /** Half of world-engine.cell-size (tp-spread-radius cap). */
        CELL_HALF,
        /** Enabled signal-interference option count, at least 1. */
        ENABLED_OPTION_COUNT
    }

    public SettingDescriptor {
        options = options == null ? List.of() : List.copyOf(options);
        aliases = aliases == null ? Map.of() : Map.copyOf(aliases);
    }
}
