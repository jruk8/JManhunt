package com.jruk8.jmanhunt.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Every editable scalar in config.yml: path, type, default, bounds, and
 * options. Lists, maps, and sections are not editable. This registry
 * drives typed validation for the config command and the settings GUI,
 * plus the drill-down structure both browse. Defaults mirror the schema;
 * a unit test pins every entry against a fresh schema instance.
 */
public final class SettingRegistry {

    private static final Map<String, SettingDescriptor> BY_PATH = build();
    private static final Set<String> NAMES = Set.copyOf(BY_PATH.keySet());
    private static final Set<String> LIST_PATHS = Set.of(
            "advanced.advanced-match-controls.end-statistics",
            "settings.compass.analysis.debuffs.commands.player",
            "settings.compass.analysis.debuffs.commands.speedrunner",
            "settings.compass.analysis.debuffs.commands.hunter",
            "settings.compass.signal-interference.weather.interfere-during",
            "settings.compass.signal-interference.biome.interfere-in",
            "world-engine.preloading.commands",
            "settings.server.team-chat.prefixes",
            "settings.server.anti-spawn-camp.monitored-roles",
            "world-engine.spawnpoint-algorithm.spawn-close-to-structure.structures",
            "advanced.advanced-match-controls.game-rules.rules");

    private SettingRegistry() {
    }

    /** All editable paths. */
    public static Set<String> settingNames() {
        return NAMES;
    }

    /** Descriptor for the path, case-insensitively, or null when unknown. */
    public static SettingDescriptor byPath(String path) {
        if (path == null) {
            return null;
        }
        SettingDescriptor direct = BY_PATH.get(path);
        if (direct != null) {
            return direct;
        }
        for (Map.Entry<String, SettingDescriptor> entry : BY_PATH.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(path)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Canonical path for case-insensitive input, or null when unknown. */
    public static String canonicalPath(String path) {
        SettingDescriptor descriptor = byPath(path);
        return descriptor == null ? null : descriptor.path();
    }

    /** Top-level drill categories, alphabetically. */
    public static List<String> topCategories() {
        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String path : BY_PATH.keySet()) {
            categories.add(path.substring(0, path.indexOf('.')));
        }
        for (String path : LIST_PATHS) {
            categories.add(path.substring(0, path.indexOf('.')));
        }
        return List.copyOf(categories);
    }

    /** All editable string-list paths. */
    public static Set<String> listPaths() {
        return LIST_PATHS;
    }

    /** True when the path names an editable string list. */
    public static boolean isListPath(String path) {
        if (path == null) {
            return false;
        }
        for (String list : LIST_PATHS) {
            if (list.equalsIgnoreCase(path)) {
                return true;
            }
        }
        return false;
    }

    /** Canonical list path for case-insensitive input, or null when unknown. */
    public static String canonicalListPath(String path) {
        if (path == null) {
            return null;
        }
        for (String list : LIST_PATHS) {
            if (list.equalsIgnoreCase(path)) {
                return list;
            }
        }
        return null;
    }

    /** Next drill level under a section path. */
    public static DrillChildren children(String path) {
        Set<String> sections = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> leaves = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        String prefix = path + ".";
        for (String setting : BY_PATH.keySet()) {
            if (setting.length() <= prefix.length()
                    || !setting.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            String rest = setting.substring(prefix.length());
            int dot = rest.indexOf('.');
            if (dot == -1) {
                leaves.add(rest);
            } else {
                sections.add(rest.substring(0, dot));
            }
        }
        for (String list : LIST_PATHS) {
            if (list.length() <= prefix.length()
                    || !list.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            String rest = list.substring(prefix.length());
            int dot = rest.indexOf('.');
            if (dot == -1) {
                sections.add(rest);
            } else {
                sections.add(rest.substring(0, dot));
            }
        }
        return new DrillChildren(List.copyOf(sections), List.copyOf(leaves));
    }

    /** Next drill level: sub-sections plus terminal leaves. */
    public record DrillChildren(List<String> sections, List<String> leaves) {
    }

    /** True when the path is a section with registered settings below it. */
    public static boolean isSection(String path) {
        String prefix = path + ".";
        for (String setting : BY_PATH.keySet()) {
            if (setting.length() > prefix.length()
                    && setting.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return true;
            }
        }
        return false;
    }

    /** Validated parse result: canonical value or a message key plus slots. */
    public record ValidationOutcome(boolean ok, Object value, String errorKey,
            Map<String, String> slots) {
        public static ValidationOutcome ok(Object value) {
            return new ValidationOutcome(true, value, null, Map.of());
        }

        public static ValidationOutcome fail(String errorKey, Map<String, String> slots) {
            return new ValidationOutcome(false, null, errorKey, slots);
        }
    }

    /**
     * Parses raw input against the descriptor. The lookup resolves live
     * values for dynamic bounds (cell size, enabled option count).
     */
    public static ValidationOutcome validate(SettingDescriptor descriptor, String raw,
            Function<String, Object> lookup) {
        if (descriptor == null || raw == null) {
            return ValidationOutcome.fail("manhunt.setting-invalid", Map.of());
        }
        String trimmed = raw.trim();
        return switch (descriptor.type()) {
            case BOOL -> validateBool(trimmed);
            case INT -> validateInt(descriptor, trimmed, lookup);
            case FLOAT -> validateFloat(descriptor, trimmed, lookup);
            case STRING -> validateString(descriptor, trimmed);
            case OPTION -> validateOption(descriptor, trimmed);
        };
    }

    private static ValidationOutcome validateBool(String trimmed) {
        if (trimmed.equalsIgnoreCase("true") || trimmed.equalsIgnoreCase("false")) {
            return ValidationOutcome.ok(Boolean.parseBoolean(trimmed));
        }
        return ValidationOutcome.fail("manhunt.setting-invalid-value", Map.of());
    }

    private static ValidationOutcome validateInt(SettingDescriptor descriptor, String trimmed,
            Function<String, Object> lookup) {
        int value;
        try {
            value = Integer.parseInt(trimmed);
        } catch (NumberFormatException exception) {
            return ValidationOutcome.fail("manhunt.setting-invalid-number", Map.of());
        }
        Double max = resolveMax(descriptor, lookup);
        if (!inBounds(value, descriptor.min(), max, descriptor.minusOneOrMin())) {
            return outOfRange(descriptor, max);
        }
        return ValidationOutcome.ok(value);
    }

    private static ValidationOutcome validateFloat(SettingDescriptor descriptor, String trimmed,
            Function<String, Object> lookup) {
        double value;
        try {
            value = Double.parseDouble(trimmed);
        } catch (NumberFormatException exception) {
            return ValidationOutcome.fail("manhunt.setting-invalid-number", Map.of());
        }
        if (!Double.isFinite(value)) {
            return ValidationOutcome.fail("manhunt.setting-invalid-number", Map.of());
        }
        Double max = resolveMax(descriptor, lookup);
        if (!inBounds(value, descriptor.min(), max, descriptor.minusOneOrMin())) {
            return outOfRange(descriptor, max);
        }
        return ValidationOutcome.ok(value);
    }

    private static ValidationOutcome validateString(SettingDescriptor descriptor, String trimmed) {
        if (descriptor.min() == null && descriptor.max() == null) {
            return ValidationOutcome.ok(trimmed);
        }
        int length = trimmed.length();
        int min = descriptor.min() == null ? 0 : descriptor.min().intValue();
        int max = descriptor.max() == null ? Integer.MAX_VALUE : descriptor.max().intValue();
        if (length >= min && length <= max) {
            return ValidationOutcome.ok(trimmed);
        }
        return ValidationOutcome.fail("manhunt.setting-out-of-range",
                Map.of("bounds", boundsText(descriptor, descriptor.max())));
    }

    private static ValidationOutcome validateOption(SettingDescriptor descriptor, String trimmed) {
        for (String option : descriptor.options()) {
            if (option.equalsIgnoreCase(trimmed)) {
                return ValidationOutcome.ok(option);
            }
        }
        String alias = descriptor.aliases().get(trimmed.toLowerCase(Locale.ROOT));
        if (alias != null) {
            return ValidationOutcome.ok(alias);
        }
        return ValidationOutcome.fail("manhunt.setting-invalid-option",
                Map.of("valid", String.join(", ", descriptor.options())));
    }

    private static boolean inBounds(double value, Double min, Double max, boolean minusOneOrMin) {
        if (minusOneOrMin && value == -1.0) {
            return true;
        }
        if (min != null && value < min) {
            return false;
        }
        return max == null || value <= max;
    }

    private static ValidationOutcome outOfRange(SettingDescriptor descriptor, Double max) {
        return ValidationOutcome.fail("manhunt.setting-out-of-range",
                Map.of("bounds", boundsText(descriptor, max)));
    }

    /** True when the setting shows an allowed-bounds line: numbers always, strings when bounded. */
    public static boolean hasBounds(SettingDescriptor descriptor) {
        return descriptor.type() == SettingType.INT || descriptor.type() == SettingType.FLOAT
                || (descriptor.type() == SettingType.STRING
                        && (descriptor.min() != null || descriptor.max() != null));
    }

    /** Human bounds text, with dynamic maxima resolved. */
    public static String boundsText(SettingDescriptor descriptor, Double resolvedMax) {
        Double max = resolvedMax == null ? descriptor.max() : resolvedMax;
        Double min = descriptor.min();
        if (descriptor.type() == SettingType.STRING && (min != null || max != null)) {
            int low = min == null ? 0 : min.intValue();
            int high = max == null ? Integer.MAX_VALUE : max.intValue();
            if (low == high) {
                return "exactly " + low + " characters";
            }
            return "between " + low + " and " + high + " characters";
        }
        if (descriptor.minusOneOrMin() && min != null) {
            return "-1 or at least " + displayNumber(min);
        }
        if (min != null && max != null) {
            return displayNumber(min) + " to " + displayNumber(max);
        }
        if (min != null) {
            return "at least " + displayNumber(min);
        }
        if (max != null) {
            return "at most " + displayNumber(max);
        }
        return "any value";
    }

    /** Bounds text with the live maximum resolved through the lookup. */
    public static String boundsText(SettingDescriptor descriptor, Function<String, Object> lookup) {
        return boundsText(descriptor, resolveMax(descriptor, lookup));
    }

    private static Double resolveMax(SettingDescriptor descriptor, Function<String, Object> lookup) {
        if (descriptor.dynamicBound() == SettingDescriptor.DynamicBound.NONE || lookup == null) {
            return descriptor.max();
        }
        return switch (descriptor.dynamicBound()) {
            case NONE -> descriptor.max();
            case CELL_HALF -> (double) Math.max(0, toInt(lookup.apply("world-engine.cell-size"), 10000) / 2);
            case ENABLED_OPTION_COUNT -> (double) Math.max(1, enabledInterferenceOptions(lookup));
        };
    }

    private static int enabledInterferenceOptions(Function<String, Object> lookup) {
        int count = 0;
        String base = "settings.compass.signal-interference.";
        for (String option : List.of("light-level", "underground", "underwater",
                "altitude", "weather", "biome", "line-of-sight")) {
            if (Boolean.TRUE.equals(lookup.apply(base + option + ".enabled"))) {
                count++;
            }
        }
        return count;
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    private static String displayNumber(Double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf(value.longValue());
        }
        return String.valueOf(value);
    }

    private static SettingDescriptor bool(String path, boolean def) {
        return new SettingDescriptor(path, SettingType.BOOL, String.valueOf(def),
                null, null, List.of(), Map.of(), false,
                SettingDescriptor.DynamicBound.NONE, false);
    }

    private static SettingDescriptor string(String path, String def) {
        return new SettingDescriptor(path, SettingType.STRING, def,
                null, null, List.of(), Map.of(), false,
                SettingDescriptor.DynamicBound.NONE, false);
    }

    private static SettingDescriptor stringLength(String path, String def, int length) {
        return new SettingDescriptor(path, SettingType.STRING, def,
                (double) length, (double) length, List.of(), Map.of(), false,
                SettingDescriptor.DynamicBound.NONE, false);
    }

    private static SettingDescriptor option(String path, String def, String... options) {
        return new SettingDescriptor(path, SettingType.OPTION, def,
                null, null, List.of(options), Map.of(), false,
                SettingDescriptor.DynamicBound.NONE, false);
    }

    private static SettingDescriptor optionAliases(String path, String def,
            Map<String, String> aliases, String... options) {
        return new SettingDescriptor(path, SettingType.OPTION, def,
                null, null, List.of(options), aliases, false,
                SettingDescriptor.DynamicBound.NONE, false);
    }

    private static SettingDescriptor intVal(String path, int def, Integer min, Integer max) {
        return new SettingDescriptor(path, SettingType.INT, String.valueOf(def),
                min == null ? null : min.doubleValue(), max == null ? null : max.doubleValue(),
                List.of(), Map.of(), false, SettingDescriptor.DynamicBound.NONE, false);
    }

    private static SettingDescriptor intMinusOne(String path, int def, int min) {
        return new SettingDescriptor(path, SettingType.INT, String.valueOf(def),
                (double) min, null, List.of(), Map.of(), true,
                SettingDescriptor.DynamicBound.NONE, false);
    }

    private static SettingDescriptor intDynamic(String path, int def, Integer min,
            SettingDescriptor.DynamicBound bound) {
        return new SettingDescriptor(path, SettingType.INT, String.valueOf(def),
                min == null ? null : min.doubleValue(), null,
                List.of(), Map.of(), false, bound, false);
    }

    private static SettingDescriptor floatVal(String path, double def, Double min, Double max) {
        return new SettingDescriptor(path, SettingType.FLOAT, String.valueOf(def),
                min, max, List.of(), Map.of(), false,
                SettingDescriptor.DynamicBound.NONE, false);
    }

    private static SettingDescriptor floatMinusOne(String path, double def, double min) {
        return new SettingDescriptor(path, SettingType.FLOAT, String.valueOf(def),
                min, null, List.of(), Map.of(), true,
                SettingDescriptor.DynamicBound.NONE, false);
    }

    private static SettingDescriptor restart(SettingDescriptor descriptor) {
        return new SettingDescriptor(descriptor.path(), descriptor.type(), descriptor.defaultValue(),
                descriptor.min(), descriptor.max(), descriptor.options(), descriptor.aliases(),
                descriptor.minusOneOrMin(), descriptor.dynamicBound(), true);
    }

    private static Map<String, SettingDescriptor> build() {
        List<SettingDescriptor> entries = new ArrayList<>();
        addCoreEntries(entries);
        addStatisticsEntries(entries);
        addAdvancedMatchControlsEntries(entries);
        addAdvancedLobbiesEntries(entries);
        addAdvancedMiscEntries(entries);
        addSettingsMatchEntries(entries);
        addWinConditionEntries(entries);
        addGameBoostsEntries(entries);
        addCompassEntries(entries);
        addSignalInterferenceEntries(entries);
        addPlayerEntries(entries);
        addServerEntries(entries);
        addWorldEngineEntries(entries);

        Map<String, SettingDescriptor> byPath = new LinkedHashMap<>();
        for (SettingDescriptor entry : entries) {
            byPath.put(entry.path(), entry);
        }
        return byPath;
    }

    private static void addCoreEntries(List<SettingDescriptor> entries) {
        entries.add(bool("update-checker.enabled", true));
        entries.add(bool("update-checker.releases.major", true));
        entries.add(bool("update-checker.releases.minor", true));
        entries.add(bool("update-checker.releases.hotfix", false));
        entries.add(bool("debug.enabled", false));
    }

    private static void addStatisticsEntries(List<SettingDescriptor> entries) {
        entries.add(restart(bool("statistics.enabled", true)));
        entries.add(restart(optionAliases("statistics.type", "sqlite",
                Map.of("postgres", "postgres"), "sqlite", "postgresql")));
        entries.add(restart(string("statistics.sqlite.file", "statistics.db")));
        entries.add(restart(string("statistics.postgresql.host", "localhost")));
        entries.add(restart(intVal("statistics.postgresql.port", 5432, 1, 65535)));
        entries.add(restart(string("statistics.postgresql.database", "jmanhunt")));
        entries.add(restart(string("statistics.postgresql.username", "jmanhunt")));
        entries.add(restart(string("statistics.postgresql.password", "change-me")));
        entries.add(restart(bool("statistics.postgresql.ssl", false)));
        entries.add(restart(intVal("statistics.pool-size", 4, 1, null)));
    }

    private static void addAdvancedMatchControlsEntries(List<SettingDescriptor> entries) {
        String root = "advanced.advanced-match-controls.";
        entries.add(bool(root + "game-rules.enabled", true));
        entries.add(floatVal(root + "end-delay", 10.0, null, null));
        entries.add(floatMinusOne(root + "start-reminder-interval", 30.0, 0.0));
        entries.add(intVal(root + "disconnect-handling.speedrunner.reconnect-grace-seconds", 60, null, null));
        entries.add(intVal(root + "disconnect-handling.speedrunner.max-strikes", 3, 1, null));
        entries.add(intVal(root + "disconnect-handling.hunter.reconnect-grace-seconds", 60, null, null));
        entries.add(intVal(root + "disconnect-handling.hunter.max-strikes", 3, 1, null));
    }

    private static void addAdvancedLobbiesEntries(List<SettingDescriptor> entries) {
        String root = "advanced.lobbies.";
        entries.add(intVal(root + "default-lobby-id", 0, null, null));
        entries.add(bool(root + "join-teleports-to-lobby", true));
        entries.add(option(root + "announce-lobby-changes", "ALL", "ALL", "SELF", "MEMBERS", "NONE"));
        entries.add(intVal(root + "bounds.exit-lobby-id", -1, null, null));
        entries.add(option(root + "bounds.exit-behavior", "KEEP_IN_LOBBY",
                "KEEP_IN_LOBBY", "EXIT_LOBBY"));
        entries.add(option(root + "mid-match-setplayer", "SUBLOBBY_WITH_SPECTATORS",
                "HOLD", "JOIN_ANY", "JOIN_SPECTATORS", "SUBLOBBY", "SUBLOBBY_WITH_SPECTATORS"));
        entries.add(intMinusOne(root + "queue-caps.speedrunner", -1, 1));
        entries.add(intMinusOne(root + "queue-caps.hunter", -1, 1));
        entries.add(string(root + "lobby-world-name", "jmh_lobby"));
        entries.add(bool(root + "disable-player-collisions", true));
    }

    private static void addAdvancedMiscEntries(List<SettingDescriptor> entries) {
        String root = "advanced.misc.";
        entries.add(bool(root + "interop.disable-worldedit-navwand", true));
        entries.add(bool(root + "interop.validate-modifier-editor-commands", true));
    }

    private static void addSettingsMatchEntries(List<SettingDescriptor> entries) {
        entries.add(bool("settings.match.autostart.enabled", true));
        entries.add(intVal("settings.match.autostart.countdown-seconds", 45, 0, null));
        entries.add(intVal("settings.match.autostart.minimums.hunter", 1, 1, null));
        entries.add(intVal("settings.match.autostart.minimums.speedrunner", 1, 1, null));
        entries.add(bool("settings.match.autostart.broadcast-requirements.enabled", false));
        entries.add(intVal("settings.match.autostart.broadcast-requirements.interval-seconds", 60, 1, null));
        entries.add(bool("settings.match.start-on-speedrunner-damage.enabled", true));
        entries.add(intMinusOne("settings.match.start-on-speedrunner-damage.delay-seconds", 45, 5));
        entries.add(option("settings.match.start-on-speedrunner-damage.on-expire", "FORCE_START",
                "CANCEL", "FORCE_START"));
        entries.add(bool("settings.match.start-on-speedrunner-damage.start-in-adventure-mode", true));
        entries.add(bool("settings.match.headstarts.speedrunner.enabled", false));
        entries.add(intVal("settings.match.headstarts.speedrunner.delay-seconds", 30, 0, null));
        entries.add(bool("settings.match.headstarts.hunter.enabled", false));
        entries.add(intVal("settings.match.headstarts.hunter.delay-seconds", 30, 0, null));
        entries.add(option("settings.match.game-leave.destination", "SPECTATOR", "SPECTATOR", "LOBBY"));
    }

    private static void addGameBoostsEntries(List<SettingDescriptor> entries) {
        entries.add(restart(bool("settings.match.game-boosts.nether-structures.enabled", false)));
        entries.add(restart(bool("settings.match.game-boosts.overworld-structures.enabled", false)));
        entries.add(bool("settings.match.game-boosts.disable-brutes.enabled", true));
        entries.add(bool("settings.match.game-boosts.custom-piglin-barter", true));
    }

    private static void addWinConditionEntries(List<SettingDescriptor> entries) {
        entries.add(bool("settings.match.win-conditions.speedrunner.exit-end.enabled", true));
        entries.add(bool("settings.match.win-conditions.speedrunner.survive-time.enabled", false));
        entries.add(floatVal("settings.match.win-conditions.speedrunner.survive-time.time", 3600.0, 0.0, null));
        entries.add(bool("settings.match.win-conditions.speedrunner.acquire-item.enabled", false));
        entries.add(string("settings.match.win-conditions.speedrunner.acquire-item.item",
                "minecraft:netherite_ingot"));
        entries.add(bool("settings.match.win-conditions.speedrunner.reach-advancement.enabled", false));
        entries.add(string("settings.match.win-conditions.speedrunner.reach-advancement.advancement",
                "minecraft:story/enter_the_nether"));
        entries.add(bool("settings.match.win-conditions.speedrunner.kill-mob.enabled", false));
        entries.add(string("settings.match.win-conditions.speedrunner.kill-mob.mob", "minecraft:wither"));
        entries.add(bool("settings.match.win-conditions.hunter.survive-time.enabled", false));
        entries.add(floatVal("settings.match.win-conditions.hunter.survive-time.time", 3600.0, 0.0, null));
        entries.add(bool("settings.match.win-conditions.hunter.acquire-item.enabled", false));
        entries.add(string("settings.match.win-conditions.hunter.acquire-item.item",
                "minecraft:netherite_ingot"));
        entries.add(bool("settings.match.win-conditions.hunter.reach-advancement.enabled", false));
        entries.add(string("settings.match.win-conditions.hunter.reach-advancement.advancement",
                "minecraft:story/enter_the_nether"));
        entries.add(bool("settings.match.win-conditions.hunter.kill-mob.enabled", false));
        entries.add(string("settings.match.win-conditions.hunter.kill-mob.mob", "minecraft:ender_dragon"));
        entries.add(bool("settings.match.win-conditions.cancel.survived-time.enabled", true));
        entries.add(floatVal("settings.match.win-conditions.cancel.survived-time.time", 28800.0, 0.0, null));
    }

    private static void addCompassEntries(List<SettingDescriptor> entries) {
        entries.add(bool("settings.compass.given-to.hunters", true));
        entries.add(bool("settings.compass.given-to.speedrunners", false));
        entries.add(string("settings.compass.item", "compass"));
        entries.add(bool("settings.compass.must-be-inventory.enabled", true));
        entries.add(bool("settings.compass.drop-on-death.enabled", true));
        entries.add(floatMinusOne("settings.compass.refresh-interval", 10.0, 0.0));
        entries.add(bool("settings.compass.right-click.refresh-on-right-click", true));
        entries.add(floatVal("settings.compass.click.click-cooldown", 3.0, -1.0, null));
        entries.add(bool("settings.compass.left-click.enabled", true));
        entries.add(intVal("settings.compass.left-click.max-targets", 5, 1, 20));
        entries.add(floatVal("settings.compass.left-click.scroll-cooldown", 0.5, 0.0, null));
        entries.add(bool("settings.compass.teammates.enabled", true));
        entries.add(floatVal("settings.compass.teammates.switch-cooldown", 0.5, 0.0, null));
        entries.add(bool("settings.compass.chat-messages.enabled", true));
        entries.add(bool("settings.compass.hunter.min-distance.enabled", true));
        entries.add(floatVal("settings.compass.hunter.min-distance.distance", 25.0, 0.0, null));
        entries.add(bool("settings.compass.hunter.max-distance.enabled", true));
        entries.add(floatVal("settings.compass.hunter.max-distance.distance", -1.0, -1.0, null));
        entries.add(bool("settings.compass.speedrunner.min-distance.enabled", true));
        entries.add(floatVal("settings.compass.speedrunner.min-distance.distance", 25.0, 0.0, null));
        entries.add(bool("settings.compass.speedrunner.max-distance.enabled", true));
        entries.add(floatVal("settings.compass.speedrunner.max-distance.distance", -1.0, -1.0, null));
        addCompassAnalysisEntries(entries);
    }

    private static void addCompassAnalysisEntries(List<SettingDescriptor> entries) {
        String root = "settings.compass.analysis.";
        entries.add(bool(root + "right-click", false));
        entries.add(bool(root + "auto", false));
        entries.add(floatVal(root + "delay-seconds", 1.0, 0.0, null));
        entries.add(floatVal(root + "delay-deviation-seconds", 0.0, 0.0, null));
        entries.add(floatVal(root + "sound-interval-seconds", 0.5, 0.05, 3.0));
        entries.add(bool(root + "debuffs.enabled", true));
        addCompassActionbarEntries(entries);
    }

    private static void addCompassActionbarEntries(List<SettingDescriptor> entries) {
        String root = "settings.compass.actionbar.";
        entries.add(intVal(root + "refresh-ticks", 1, 1, null));
        entries.add(bool(root + "show-distance-delta.enabled", true));
        entries.add(string(root + "show-distance-delta.further-format", "<green>▲{distance}m"));
        entries.add(string(root + "show-distance-delta.closer-format", "<red>▼{distance}m"));
        entries.add(floatVal(root + "show-distance-delta.max-distance", 200.0, 0.0, null));
        entries.add(floatVal(root + "show-distance-delta.min-delta-to-show", 5.0, 0.0, null));
    }

    private static void addSignalInterferenceEntries(List<SettingDescriptor> entries) {
        entries.add(bool("settings.compass.signal-interference.enabled", false));
        entries.add(intDynamic("settings.compass.signal-interference.required-to-fail", 1, 1,
                SettingDescriptor.DynamicBound.ENABLED_OPTION_COUNT));
        entries.add(bool("settings.compass.signal-interference.two-way", false));
        entries.add(floatVal("settings.compass.signal-interference.chance-to-bypass", 0.0, 0.0, 1.0));
        entries.add(bool("settings.compass.signal-interference.show-reason-in-actionbar", true));
        entries.add(bool("settings.compass.signal-interference.light-level.enabled", false));
        entries.add(intVal("settings.compass.signal-interference.light-level.min-sky-light", 10, 0, 15));
        entries.add(intVal("settings.compass.signal-interference.light-level.min-block-light", 5, 0, 15));
        entries.add(option("settings.compass.signal-interference.light-level.interfere-when", "ONE_UNMET",
                "ONE_UNMET", "BOTH_UNMET"));
        entries.add(bool("settings.compass.signal-interference.underground.enabled", true));
        entries.add(intVal("settings.compass.signal-interference.underground.max-blocks-above", 3, 1, 380));
        entries.add(bool("settings.compass.signal-interference.underground.ignore-transparent", true));
        entries.add(bool("settings.compass.signal-interference.underwater.enabled", true));
        entries.add(intVal("settings.compass.signal-interference.underwater.max-blocks-above", 2, 1, 380));
        entries.add(bool("settings.compass.signal-interference.altitude.enabled", false));
        entries.add(intVal("settings.compass.signal-interference.altitude.min-y", -20, -64, 319));
        entries.add(intVal("settings.compass.signal-interference.altitude.max-y", 120, -64, 319));
        entries.add(bool("settings.compass.signal-interference.weather.enabled", false));
        entries.add(bool("settings.compass.signal-interference.biome.enabled", false));
        entries.add(bool("settings.compass.signal-interference.line-of-sight.enabled", false));
        entries.add(option("settings.compass.signal-interference.line-of-sight.interfere-when", "VISIBLE",
                "VISIBLE", "NOT_VISIBLE"));
        entries.add(intVal("settings.compass.signal-interference.line-of-sight.max-ray-distance",
                300, 1, 1000));
    }

    private static void addPlayerEntries(List<SettingDescriptor> entries) {
        entries.add(bool("settings.players.roles.reset-on-game-end.enabled", true));
        entries.add(bool("settings.players.roles.reset-on-leave.enabled", true));
        entries.add(bool("settings.players.roles.turn-nones-spectator.enabled", false));
        entries.add(bool("settings.players.respawn.hunter.enabled", true));
        entries.add(intVal("settings.players.respawn.hunter.delay-seconds", 15, null, null));
        entries.add(intMinusOne("settings.players.respawn.hunter.lives", -1, 0));
        entries.add(bool("settings.players.respawn.speedrunner.enabled", false));
        entries.add(intVal("settings.players.respawn.speedrunner.delay-seconds", 60, null, null));
        entries.add(intMinusOne("settings.players.respawn.speedrunner.lives", 1, 0));
        entries.add(bool("settings.players.friendly-fire.speedrunner", true));
        entries.add(bool("settings.players.friendly-fire.hunter", true));
        entries.add(bool("settings.players.friendly-fire.broadcast-kills", true));
        entries.add(bool("settings.players.invulnerability.on-game-end.enabled", true));
        entries.add(bool("settings.players.invulnerability.none-players.enabled", true));
        entries.add(stringLength("settings.players.spectator.toolbar.layout", "cp######b", 9));
        entries.add(bool("settings.players.spectator.toolbar.lock-on", true));
        entries.add(intVal("settings.players.spectator.toolbar.tp-distance", 25, 1, null));
        entries.add(bool("settings.players.spectator.travel.enabled", true));
        entries.add(floatVal("settings.players.spectator.travel.max-distance", 125.0, 0.0, null));
        entries.add(bool("settings.players.announce-roles.chat.enabled", true));
        entries.add(bool("settings.players.announce-roles.title.enabled", true));
        entries.add(floatVal("settings.players.announce-roles.title.fade-in-seconds", 0.5, 0.0, null));
        entries.add(floatVal("settings.players.announce-roles.title.stay-seconds", 3.0, 0.0, null));
        entries.add(floatVal("settings.players.announce-roles.title.fade-out-seconds", 0.5, 0.0, null));
    }

    private static void addServerEntries(List<SettingDescriptor> entries) {
        entries.add(bool("settings.server.announce-config-changes", false));
        entries.add(bool("settings.server.announce-role-changes", false));
        entries.add(bool("settings.server.anti-spawn-camp.enabled", true));
        entries.add(intVal("settings.server.anti-spawn-camp.kills", 3, 1, null));
        entries.add(floatVal("settings.server.anti-spawn-camp.window-seconds", 90.0, 0.0, null));
        entries.add(optionAliases("settings.server.anti-spawn-camp.punishment", "KILL",
                Map.of("gear_wipe", "GEAR-WIPE"), "KILL", "GEAR-WIPE"));
        entries.add(bool("settings.server.status.show-win-conditions", false));
        entries.add(bool("settings.server.status.show-elapsed-time", false));
        entries.add(bool("settings.server.status.show-modifiers", false));
        entries.add(bool("settings.server.status.show-ids", true));
        entries.add(bool("settings.server.team-chat.enabled", true));
        entries.add(bool("settings.server.team-chat.spectators-see", true));
    }

    private static void addWorldEngineEntries(List<SettingDescriptor> entries) {
        entries.add(restart(bool("world-engine.enabled", false)));
        entries.add(string("world-engine.world-name", "world"));

        entries.add(bool("world-engine.role-pads.enabled", true));
        entries.add(bool("world-engine.role-pads.silent-role-assignment", false));
        entries.add(string("world-engine.role-pads.blocks.speedrunner", "LIME_CONCRETE"));
        entries.add(string("world-engine.role-pads.blocks.hunter", "RED_CONCRETE"));
        entries.add(string("world-engine.role-pads.blocks.afk", "YELLOW_CONCRETE"));
        entries.add(string("world-engine.role-pads.blocks.spectator", "LIGHT_GRAY_CONCRETE"));
        entries.add(string("world-engine.role-pads.blocks.none", "GRAY_CONCRETE"));
        entries.add(intVal("world-engine.cell-size", 10000, 1, 50000));
        entries.add(intDynamic("world-engine.tp-spread-radius", 5, 0,
                SettingDescriptor.DynamicBound.CELL_HALF));
        addSpawnpointEntries(entries);
        entries.add(intVal("world-engine.preloading.cell-buffer.stored-cells-buffer", 3, 1, null));
        entries.add(option("world-engine.preloading.cell-buffer.increment-when", "ALWAYS",
                "ALWAYS", "NO_MATCH_RUNNING"));
        entries.add(bool("world-engine.world-border.enabled", true));
        entries.add(floatVal("world-engine.world-border.damage.buffer", 5.0, 0.0, null));
        entries.add(floatVal("world-engine.world-border.damage.amount", 1.0, 0.0, null));
        entries.add(bool("world-engine.world-border.start-border.enabled", true));
        entries.add(intVal("world-engine.world-border.start-border.radius", 10, null, null));
        entries.add(intVal("world-engine.world-border.start-border.fadeout-time", 5, -1, null));
        entries.add(option("world-engine.world-border.particles.type", "DUST",
                "DUST", "SMOKE", "LARGE_SMOKE", "HEART", "HAPPY_VILLAGER", "ANGRY_VILLAGER",
                "WITCH", "FIREWORK", "FLASH", "FLAME", "SMALL_FLAME", "EFFECT",
                "INSTANT_EFFECT", "PORTAL", "END_ROD", "NOTE", "SOUL", "ENCHANT"));
        entries.add(intVal("world-engine.world-border.particles.particle-spacing", 2, 1, 8));
        entries.add(string("world-engine.world-border.particles.color", "#de7766"));
        entries.add(floatVal("world-engine.world-border.particles.render-radius", 10.0, 0.0, null));
        entries.add(option("world-engine.world-border.particles.pulse-mode", "INTERVAL",
                "INTERVAL", "SINE_WAVE"));
        entries.add(floatVal("world-engine.world-border.particles.interval", 0.5, 0.0, 3.0));
        entries.add(floatVal("world-engine.world-border.particles.wave-direction-angle", 0.0, 0.0, 360.0));
        entries.add(floatVal("world-engine.world-border.particles.wave-length", 8.0, 2.0, 64.0));
        entries.add(floatVal("world-engine.world-border.particles.wave-speed", 0.5, 0.05, 3.0));
        entries.add(intVal("world-engine.world-border.particles.max-particles-per-player", 1000, 1, null));
        entries.add(string("world-engine.end.base-name", "jmh_end"));
        entries.add(intVal("world-engine.end.buffer", 3, 1, null));
    }

    private static void addSpawnpointEntries(List<SettingDescriptor> entries) {
        entries.add(bool("world-engine.spawnpoint-algorithm.enabled", true));
        entries.add(intVal("world-engine.spawnpoint-algorithm.max-retries", 8, 0, null));
        entries.add(intVal("world-engine.spawnpoint-algorithm.y-tolerance", 7, 0, null));
        entries.add(bool("world-engine.spawnpoint-algorithm.spawn-close-to-structure.enabled", false));
        entries.add(intVal("world-engine.spawnpoint-algorithm.spawn-close-to-structure.attempts", 3, 1, 5));
        entries.add(intVal("world-engine.spawnpoint-algorithm.spawn-close-to-structure.max-distance",
                125, 50, 200));
    }
}
