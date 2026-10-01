package com.jruk8.jmanhunt.config;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.DrillResolve;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Config drill-down: resolving path segments against the setting
 * registry, listing drill children for completion, and rendering
 * dir-style entry lists. Shared by config and override settings.
 */
public final class ConfigDrill {

    private ConfigDrill() {
    }

    /**
     * Resolves drill segments against the setting registry: each segment
     * matches a child case-insensitively, list indices descend into list
     * entries, and anything past a leaf is the value remainder.
     */
    public static DrillResolve resolveDrill(List<String> segments,
            Function<String, List<String>> lists) {
        String current = "";
        int consumed = 0;
        for (String segment : segments) {
            String match = matchChild(current, segment, lists);
            if (match == null) {
                break;
            }
            current = current.isEmpty() ? match : current + "." + match;
            consumed++;
            if (SettingRegistry.byPath(current) != null || isIndexPath(current, lists)) {
                break;
            }
        }
        if (consumed == 0) {
            return null;
        }
        boolean leaf = SettingRegistry.byPath(current) != null || isIndexPath(current, lists);
        boolean section = !leaf
                && (SettingRegistry.isSection(current) || SettingRegistry.isListPath(current));
        return new DrillResolve(current, leaf, section,
                List.copyOf(segments.subList(consumed, segments.size())));
    }

    private static String matchChild(String parent, String segment,
            Function<String, List<String>> lists) {
        if (!parent.isEmpty() && SettingRegistry.isListPath(parent)) {
            List<String> entries = lists == null ? null : lists.apply(parent);
            if (entries == null) {
                return null;
            }
            try {
                int index = Integer.parseInt(segment.trim());
                return index >= 0 && index < entries.size() ? String.valueOf(index) : null;
            } catch (NumberFormatException expected) {
                return null;
            }
        }
        List<String> candidates = new ArrayList<>();
        if (parent.isEmpty()) {
            candidates.addAll(SettingRegistry.topCategories());
        } else {
            SettingRegistry.DrillChildren children = SettingRegistry.children(parent);
            candidates.addAll(children.sections());
            candidates.addAll(children.leaves());
        }
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(segment)) {
                return candidate;
            }
        }
        return null;
    }

    /** True when the path addresses one list entry with a live index. */
    private static boolean isIndexPath(String path, Function<String, List<String>> lists) {
        int dot = path.lastIndexOf('.');
        if (dot == -1 || !SettingRegistry.isListPath(path.substring(0, dot))) {
            return false;
        }
        List<String> entries = lists == null ? null : lists.apply(path.substring(0, dot));
        if (entries == null) {
            return false;
        }
        try {
            int index = Integer.parseInt(path.substring(dot + 1).trim());
            return index >= 0 && index < entries.size();
        } catch (NumberFormatException expected) {
            return false;
        }
    }

    /**
     * Next-level completion options for the given prefix: child sections
     * and leaves, or list indices with add and remove under lists, so
     * completion never suggests dead ends.
     */
    public static List<String> drillChildren(List<String> prefix,
            Function<String, List<String>> lists) {
        String current = "";
        for (String segment : prefix) {
            String match = matchChild(current, segment, lists);
            if (match == null) {
                return List.of();
            }
            current = current.isEmpty() ? match : current + "." + match;
            if (SettingRegistry.byPath(current) != null || isIndexPath(current, lists)) {
                return List.of();
            }
        }
        List<String> options = new ArrayList<>();
        if (current.isEmpty()) {
            options.addAll(SettingRegistry.topCategories());
        } else if (SettingRegistry.isListPath(current)) {
            List<String> entries = lists == null ? null : lists.apply(current);
            if (entries != null) {
                for (int index = 0; index < entries.size(); index++) {
                    options.add(String.valueOf(index));
                }
            }
            options.add("add");
            options.add("remove");
            options.add("reset");
        } else {
            SettingRegistry.DrillChildren children = SettingRegistry.children(current);
            options.addAll(children.sections());
            options.addAll(children.leaves());
        }
        options.sort(String.CASE_INSENSITIVE_ORDER);
        return options;
    }

    /** Renders dir-style entries into one templated blob. Pure for tests. */
    public static String renderEntries(Map<String, String> entries, String entryTemplate) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            out.append(entryTemplate.replace("{key}", entry.getKey()).replace("{suffix}", entry.getValue()));
        }
        return out.toString();
    }

    /**
     * Next-level completion for drill args: list indices under remove,
     * bool/option values or the leaf default under leaves, else drill
     * children. Shared by config and override settings completion.
     */
    public static List<String> completeDrill(String[] args, ConfigService config) {
        List<String> prefix = new ArrayList<>();
        for (int i = 1; i < args.length - 1; i++) {
            prefix.add(args[i]);
        }
        String completing = args[args.length - 1];
        Function<String, List<String>> lists = config::getStringList;
        if (!prefix.isEmpty() && prefix.get(prefix.size() - 1).equalsIgnoreCase("remove")) {
            DrillResolve parent = resolveDrill(
                    prefix.subList(0, prefix.size() - 1), lists);
            if (parent != null && SettingRegistry.isListPath(parent.path())
                    && parent.remainder().isEmpty()) {
                List<String> indices = new ArrayList<>();
                for (int index = 0; index < config.getStringList(parent.path()).size(); index++) {
                    indices.add(String.valueOf(index));
                }
                return CommandSupport.partial(completing, indices);
            }
            return List.of();
        }
        if (prefix.isEmpty()) {
            return CommandSupport.partial(completing, SettingRegistry.topCategories());
        }
        DrillResolve resolved = resolveDrill(prefix, lists);
        if (resolved != null && resolved.leaf() && resolved.remainder().isEmpty()) {
            SettingDescriptor descriptor = config.describe(resolved.path());
            if (descriptor != null && descriptor.type() == SettingType.BOOL) {
                return CommandSupport.partial(completing, List.of("true", "false"));
            }
            if (descriptor != null && descriptor.type() == SettingType.OPTION) {
                return CommandSupport.partial(completing, descriptor.options());
            }
            Object defaultValue = config.defaultValue(resolved.path());
            if (defaultValue != null) {
                return CommandSupport.partial(completing, List.of(ConfigService.displayValue(defaultValue)));
            }
            return List.of();
        }
        return CommandSupport.partial(completing, drillChildren(prefix, lists));
    }
}
