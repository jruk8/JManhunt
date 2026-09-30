package com.jruk8.jmanhunt.modifiers.files;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Id diff between the cached and fresh loads, each list in load order. */
public record ReloadDiff(List<String> newModifiers, List<String> removedModifiers,
        List<String> newPresets, List<String> removedPresets) {

    /** Combined new count for the changes line. */
    public int newTotal() {
        return newModifiers.size() + newPresets.size();
    }

    /** Combined removed count for the changes line. */
    public int removedTotal() {
        return removedModifiers.size() + removedPresets.size();
    }

    /** Kinds present among new ids, for {items} selection. */
    public Set<ModFileKind> newKinds() {
        return kinds(newModifiers, newPresets);
    }

    /** Kinds present among removed ids, for {items} selection. */
    public Set<ModFileKind> removedKinds() {
        return kinds(removedModifiers, removedPresets);
    }

    private static Set<ModFileKind> kinds(List<String> modifiers, List<String> presets) {
        Set<ModFileKind> kinds = EnumSet.noneOf(ModFileKind.class);
        if (!modifiers.isEmpty()) {
            kinds.add(ModFileKind.MODIFIER);
        }
        if (!presets.isEmpty()) {
            kinds.add(ModFileKind.PRESET);
        }
        return kinds;
    }
}
