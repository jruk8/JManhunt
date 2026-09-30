package com.jruk8.jmanhunt.modifiers.files;

import com.jruk8.jmanhunt.message.Plurals;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Composes the 0..4 reload report lines from a diff, a load result,
 * and resolved templates: changes, unknown files, parse failures,
 * duplicate ids, in that order. No bullets here; the caller prefixes
 * them. Blank lines (e.g. emptied templates) are omitted. Pure.
 */
public final class ReloadReportComposer {

    private ReloadReportComposer() {
    }

    /** Raw report lines in fixed order, skipping blanks. Pure. */
    public static List<String> compose(ReloadDiff diff, ModLoadResult result, ReloadWords words) {
        List<String> lines = new ArrayList<>(4);
        String changes = changesLine(diff, words);
        if (!changes.isBlank()) {
            lines.add(changes);
        }
        if (!result.unknownFiles().isEmpty()) {
            addIfPresent(lines, unknownLine(result, words));
        }
        if (!result.failedFiles().isEmpty()) {
            addIfPresent(lines, failedLine(result, words));
        }
        if (!result.duplicates().isEmpty()) {
            addIfPresent(lines, duplicateLine(result, words));
        }
        return lines;
    }

    private static void addIfPresent(List<String> lines, String line) {
        if (!line.isBlank()) {
            lines.add(line);
        }
    }

    private static String changesLine(ReloadDiff diff, ReloadWords words) {
        List<String> parts = new ArrayList<>(2);
        if (diff.newTotal() > 0) {
            parts.add(fill(words.changesNew(), Map.of(
                    "total", String.valueOf(diff.newTotal()),
                    "items", items(diff.newKinds(), diff.newTotal(), words))));
        }
        if (diff.removedTotal() > 0) {
            parts.add(fill(words.changesRemoved(), Map.of(
                    "total", String.valueOf(diff.removedTotal()),
                    "items", items(diff.removedKinds(), diff.removedTotal(), words))));
        }
        parts.removeIf(String::isBlank);
        return String.join(words.changesJoin(), parts);
    }

    private static String unknownLine(ModLoadResult result, ReloadWords words) {
        int total = result.unknownFiles().size();
        return fill(words.unknownLine(), Map.of(
                "label", Plurals.pick(total, words.labelUnknownFile(), words.labelUnknownFiles()),
                "filename", result.unknownFiles().get(0).filename(),
                "extra", extra(total, words)));
    }

    private static String failedLine(ModLoadResult result, ReloadWords words) {
        int total = result.failedFiles().size();
        return fill(words.failedLine(), Map.of(
                "items", items(failedKinds(result), total, words),
                "filename", result.failedFiles().get(0).filename(),
                "extra", extra(total, words)));
    }

    private static String duplicateLine(ModLoadResult result, ReloadWords words) {
        int total = result.duplicates().size();
        return fill(words.duplicateLine(), Map.of(
                "label", Plurals.pick(total, words.labelDuplicateId(), words.labelDuplicateIds()),
                "id", result.duplicates().get(0).id(),
                "extra", extra(total, words)));
    }

    private static String items(Set<ModFileKind> kinds, int total, ReloadWords words) {
        if (kinds.size() > 1) {
            return words.itemBoth();
        }
        if (kinds.contains(ModFileKind.MODIFIER)) {
            return Plurals.pick(total, words.itemModifier(), words.itemModifiers());
        }
        return Plurals.pick(total, words.itemPreset(), words.itemPresets());
    }

    private static Set<ModFileKind> failedKinds(ModLoadResult result) {
        Set<ModFileKind> kinds = EnumSet.noneOf(ModFileKind.class);
        for (var failed : result.failedFiles()) {
            kinds.add(failed.kind());
        }
        return kinds;
    }

    private static String extra(int total, ReloadWords words) {
        if (total <= 1) {
            return "";
        }
        return words.extra().replace("{n}", String.valueOf(total - 1));
    }

    private static String fill(String template, Map<String, String> values) {
        String line = template;
        for (Map.Entry<String, String> value : values.entrySet()) {
            line = line.replace("{" + value.getKey() + "}", value.getValue());
        }
        return line;
    }
}
