package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.match.ModifierTriggers;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierBehavior;
import com.jruk8.jmanhunt.modifiers.config.ModifierChance;
import com.jruk8.jmanhunt.modifiers.config.ModifierCommands;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierExecution;
import com.jruk8.jmanhunt.modifiers.config.ModifierInterval;
import com.jruk8.jmanhunt.modifiers.config.ModifierMeta;
import com.jruk8.jmanhunt.modifiers.config.ModifierOnStart;
import com.jruk8.jmanhunt.modifiers.config.ModifierOptions;
import com.jruk8.jmanhunt.modifiers.config.ModifierPickRandom;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;

/**
 * Parses {@code modifiers create} arguments into a build plan. Values run
 * greedily until the next known flag so commands keep their spaces
 * without quoting; every field validates against the same rules the
 * runner and the codec use. Pure apart from the member-id lookup set,
 * so unit tests cover it without a server.
 */
public final class ModifierCreateArgs {
    /** Command list flags, in runner order. */
    public static final List<String> LIST_FLAGS = List.of("--console", "--player", "--hunter",
            "--speedrunner", "--console-cleanup", "--player-cleanup");

    private static final Set<String> MODIFIER_FLAGS = Set.of("--desc", "--item", "--author",
            "--trigger", "--on-start", "--interval", "--deviation", "--interval-scope",
            "--chance", "--chance-scope", "--selection", "--pick-count", "--pick-scope",
            "--delay", "--console", "--player", "--hunter", "--speedrunner",
            "--console-cleanup", "--player-cleanup");
    private static final Set<String> PRESET_FLAGS = Set.of("--desc", "--item", "--member");
    private static final Set<String> SCOPES = Set.of("PER_INVOKE", "PER_EXECUTOR");
    private static final Set<String> ORDERS = Set.of("IN_ORDER", "PICK_RANDOM");

    private ModifierCreateArgs() {
    }

    /** Flag vocabulary for one create type, for tab completion. */
    public static List<String> flagsFor(boolean preset) {
        List<String> flags = new ArrayList<>(preset ? PRESET_FLAGS : MODIFIER_FLAGS);
        flags.sort(String.CASE_INSENSITIVE_ORDER);
        return flags;
    }

    /** Build plan; every optional field stays null when its flag is absent. */
    public record Plan(boolean preset, String name, String description, String item, String author,
            List<String> triggers, String onStartOrder, Double interval, Double deviation,
            String intervalBehavior, Double chance, String chanceBehavior, String selection,
            Integer pickCount, String pickBehavior, Long delay,
            Map<String, List<String>> commands, List<String> members) {
        /** Builds a disabled entry with only the flagged sections set. */
        public ModifierEntry toEntry() {
            ModifierEntry entry = new ModifierEntry();
            ModifierMeta meta = new ModifierMeta();
            meta.setName(name);
            meta.setDescription(description);
            meta.setItem(item);
            meta.setAuthor(author);
            entry.setMeta(meta);
            ModifierBehavior behavior = new ModifierBehavior();
            boolean touched = false;
            if (!triggers.isEmpty()) {
                behavior.setRunsOn(new ArrayList<>(triggers));
                touched = true;
            }
            if (onStartOrder != null) {
                ModifierOnStart onStart = new ModifierOnStart();
                onStart.setPreStartOrder(onStartOrder);
                behavior.setOnStart(onStart);
                touched = true;
            }
            ModifierOptions options = buildOptions();
            if (options != null) {
                behavior.setOptions(options);
                touched = true;
            }
            if (!commands.isEmpty()) {
                ModifierCommands lists = new ModifierCommands();
                for (Map.Entry<String, List<String>> list : commands.entrySet()) {
                    lists.getLists().put(list.getKey(), new ArrayList<>(list.getValue()));
                }
                behavior.setCommands(lists);
                touched = true;
            }
            entry.setBehavior(touched ? behavior : null);
            return entry;
        }

        private ModifierOptions buildOptions() {
            ModifierOptions options = new ModifierOptions();
            boolean touched = false;
            if (interval != null || deviation != null || intervalBehavior != null) {
                ModifierInterval settings = new ModifierInterval();
                settings.setInterval(interval);
                settings.setDeviation(deviation);
                settings.setBehavior(intervalBehavior);
                options.setIntervalSettings(settings);
                touched = true;
            }
            if (chance != null || chanceBehavior != null) {
                ModifierChance success = new ModifierChance();
                success.setChance(chance);
                success.setBehavior(chanceBehavior);
                options.setSuccessChance(success);
                touched = true;
            }
            if (selection != null || pickCount != null || pickBehavior != null) {
                ModifierExecution execution = new ModifierExecution();
                execution.setSelection(selection);
                if (pickCount != null || pickBehavior != null) {
                    ModifierPickRandom pick = new ModifierPickRandom();
                    pick.setCount(pickCount);
                    pick.setBehavior(pickBehavior);
                    execution.setPickRandom(pick);
                }
                options.setExecution(execution);
                touched = true;
            }
            if (delay != null) {
                options.setDelay(delay);
                touched = true;
            }
            return touched ? options : null;
        }

        /** Builds a preset with the flagged display data and members. */
        public ModifierPreset toPreset() {
            ModifierPreset preset = new ModifierPreset();
            preset.setName(name);
            preset.setDescription(description);
            preset.setItem(item);
            preset.setModifiers(new ArrayList<>(members));
            return preset;
        }
    }

    /** Either a plan plus command warnings, or a message key with params. */
    public record Result(Plan plan, String messageKey, Map<String, String> params,
            List<String> warnings) {
        static Result ok(Plan plan, List<String> warnings) {
            return new Result(plan, null, Map.of(), warnings);
        }

        static Result fail(String messageKey, Map<String, String> params) {
            return new Result(null, messageKey, params, List.of());
        }

        public boolean success() {
            return plan != null;
        }
    }

    /**
     * Parses the words after {@code create}; the first is the
     * modifier-or-preset type word. Member ids validate against the
     * given known set.
     */
    public static Result parse(String[] args, Set<String> knownModifierIds) {
        if (args.length == 0 || ModifiersCommand.parseEntryType(args[0]) == null) {
            return Result.fail("modifiers.create-usage", Map.of());
        }
        boolean preset = args[0].equalsIgnoreCase("preset");
        Set<String> allowed = preset ? PRESET_FLAGS : MODIFIER_FLAGS;
        int cursor = 1;
        List<String> nameWords = new ArrayList<>();
        while (cursor < args.length && !allowed.contains(args[cursor].toLowerCase(Locale.ROOT))) {
            if (args[cursor].startsWith("--")) {
                return Result.fail("modifiers.create-unknown-flag", Map.of("flag", args[cursor]));
            }
            nameWords.add(args[cursor]);
            cursor++;
        }
        if (nameWords.isEmpty()) {
            return Result.fail("modifiers.create-usage", Map.of());
        }
        Builder builder = new Builder(preset, String.join(" ", nameWords));
        while (cursor < args.length) {
            String flag = args[cursor].toLowerCase(Locale.ROOT);
            if (!allowed.contains(flag)) {
                return Result.fail("modifiers.create-unknown-flag", Map.of("flag", args[cursor]));
            }
            cursor++;
            List<String> valueWords = new ArrayList<>();
            while (cursor < args.length && !allowed.contains(args[cursor].toLowerCase(Locale.ROOT))) {
                valueWords.add(args[cursor]);
                cursor++;
            }
            if (valueWords.isEmpty()) {
                return Result.fail("modifiers.create-missing-value", Map.of("flag", flag));
            }
            Result failure = builder.apply(flag, String.join(" ", valueWords), knownModifierIds);
            if (failure != null) {
                return failure;
            }
        }
        return builder.build();
    }

    /** Mutable accumulator behind the immutable plan. */
    private static final class Builder {
        private final boolean preset;
        private final String name;
        private String description;
        private String item;
        private String author;
        private final List<String> triggers = new ArrayList<>();
        private String onStartOrder;
        private Double interval;
        private Double deviation;
        private String intervalBehavior;
        private Double chance;
        private String chanceBehavior;
        private String selection;
        private Integer pickCount;
        private String pickBehavior;
        private Long delay;
        private final Map<String, List<String>> commands = new LinkedHashMap<>();
        private final List<String> members = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        private Builder(boolean preset, String name) {
            this.preset = preset;
            this.name = name;
        }

        private Result apply(String flag, String value, Set<String> knownModifierIds) {
            return switch (flag) {
                case "--desc" -> {
                    description = value;
                    yield null;
                }
                case "--item" -> item(value);
                case "--author" -> {
                    author = value;
                    yield null;
                }
                case "--trigger" -> trigger(value);
                case "--member" -> member(value, knownModifierIds);
                case "--on-start" -> {
                    Result failure = checkEnum(flag, value, ORDERS);
                    if (failure == null) {
                        onStartOrder = value.trim().toUpperCase(Locale.ROOT);
                    }
                    yield failure;
                }
                case "--interval" -> number(value, checkDouble(flag, value, 0.0, null),
                        parsed -> interval = parsed);
                case "--deviation" -> number(value, checkDouble(flag, value, 0.0, null),
                        parsed -> deviation = parsed);
                default -> applyOptions(flag, value);
            };
        }

        private Result applyOptions(String flag, String value) {
            return switch (flag) {
                case "--interval-scope" -> scope(value, checkEnum(flag, value, SCOPES),
                        parsed -> intervalBehavior = parsed);
                case "--chance" -> number(value, checkDouble(flag, value, 0.0, 1.0),
                        parsed -> chance = parsed);
                case "--chance-scope" -> scope(value, checkEnum(flag, value, SCOPES),
                        parsed -> chanceBehavior = parsed);
                case "--selection" -> scope(value, checkEnum(flag, value, ORDERS),
                        parsed -> selection = parsed);
                case "--pick-count" -> {
                    Result failure = checkLong(flag, value, 1L, (long) Integer.MAX_VALUE);
                    if (failure == null) {
                        pickCount = Integer.valueOf(value.trim());
                    }
                    yield failure;
                }
                case "--pick-scope" -> scope(value, checkEnum(flag, value, SCOPES),
                        parsed -> pickBehavior = parsed);
                case "--delay" -> {
                    Result failure = checkLong(flag, value, 0L, null);
                    if (failure == null) {
                        delay = Long.valueOf(value.trim());
                    }
                    yield failure;
                }
                default -> command(flag, value);
            };
        }

        private static Result number(String value, Result failure,
                java.util.function.Consumer<Double> setter) {
            if (failure == null) {
                setter.accept(Double.parseDouble(value.trim()));
            }
            return failure;
        }

        private static Result scope(String value, Result failure,
                java.util.function.Consumer<String> setter) {
            if (failure == null) {
                setter.accept(value.trim().toUpperCase(Locale.ROOT));
            }
            return failure;
        }

        private Result item(String value) {
            Material material = ModifierStore.parseMaterial(value);
            if (material == null || material == Material.AIR) {
                return Result.fail("modifiers.create-bad-item", Map.of("value", value));
            }
            item = material.name();
            return null;
        }

        private Result trigger(String value) {
            String canonical = value.trim().toUpperCase(Locale.ROOT);
            if (!ModifierTriggers.KNOWN.contains(canonical)) {
                return Result.fail("modifiers.create-unknown-trigger",
                        Map.of("value", value, "valid", String.join(", ", ModifierTriggers.KNOWN)));
            }
            if (!triggers.contains(canonical)) {
                triggers.add(canonical);
            }
            return null;
        }

        private Result member(String value, Set<String> knownModifierIds) {
            if (!knownModifierIds.contains(value)) {
                return Result.fail("modifiers.create-unknown-member", Map.of("value", value));
            }
            if (!members.contains(value)) {
                members.add(value);
            }
            return null;
        }

        private Result command(String flag, String value) {
            String list = flag.substring("--".length());
            java.util.Optional<String> problem = CommandSyntax.error(value);
            if (problem.isPresent()) {
                return Result.fail("modifiers.create-bad-command",
                        Map.of("list", list, "error", problem.get()));
            }
            commands.computeIfAbsent(list, ignored -> new ArrayList<>()).add(value);
            warnings.addAll(CommandSyntax.warnings(value));
            return null;
        }

        private Result build() {
            if (!preset && deviation != null && interval != null && deviation > interval) {
                return Result.fail("modifiers.create-deviation-range", Map.of("value", String.valueOf(deviation)));
            }
            // Deviation without an interval means nothing to the runner;
            // keep the plan clean by refusing rather than writing a
            // dangling value.
            if (!preset && deviation != null && interval == null) {
                return Result.fail("modifiers.create-deviation-range", Map.of("value", String.valueOf(deviation)));
            }
            return Result.ok(new Plan(preset, name, description, item, author,
                    List.copyOf(triggers), onStartOrder, interval, deviation,
                    intervalBehavior, chance, chanceBehavior, selection,
                    pickCount, pickBehavior, delay, Map.copyOf(commands), List.copyOf(members)),
                    List.copyOf(warnings));
        }

        private static Result checkEnum(String flag, String value, Set<String> valid) {
            if (!valid.contains(value.trim().toUpperCase(Locale.ROOT))) {
                return Result.fail("modifiers.create-bad-enum",
                        Map.of("flag", flag, "value", value, "valid", String.join("/", valid)));
            }
            return null;
        }

        private static Result checkDouble(String flag, String value, Double min, Double max) {
            double parsed;
            try {
                parsed = Double.parseDouble(value.trim());
            } catch (NumberFormatException unparseable) {
                return Result.fail("modifiers.create-bad-number", Map.of("flag", flag, "value", value));
            }
            if (!Double.isFinite(parsed) || (min != null && parsed < min) || (max != null && parsed > max)) {
                return Result.fail("modifiers.create-bad-number", Map.of("flag", flag, "value", value));
            }
            return null;
        }

        private static Result checkLong(String flag, String value, Long min, Long max) {
            long parsed;
            try {
                parsed = Long.parseLong(value.trim());
            } catch (NumberFormatException unparseable) {
                return Result.fail("modifiers.create-bad-number", Map.of("flag", flag, "value", value));
            }
            if ((min != null && parsed < min) || (max != null && parsed > max)) {
                return Result.fail("modifiers.create-bad-number", Map.of("flag", flag, "value", value));
            }
            return null;
        }
    }
}
