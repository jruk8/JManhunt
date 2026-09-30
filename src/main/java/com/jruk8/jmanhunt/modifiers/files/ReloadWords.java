package com.jruk8.jmanhunt.modifiers.files;

/** Resolved reload report templates and word variants, in one bundle. */
public record ReloadWords(String changesNew, String changesRemoved, String changesJoin,
        String unknownLine, String failedLine, String duplicateLine,
        String itemModifier, String itemModifiers, String itemPreset, String itemPresets,
        String itemBoth, String labelUnknownFile, String labelUnknownFiles,
        String labelDuplicateId, String labelDuplicateIds, String extra) {
}
