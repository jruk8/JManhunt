package com.jruk8.jmanhunt.modifiers.files;

import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.FailedFile;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.LoadedModifier;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.LoadedPreset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Previous-load id sets (one per kind) plus content fingerprints.
 * Seeded at enable so the first reload diff is correct. Failed ids
 * stay pinned in the cache: a broken id is never reported removed,
 * and fixing it later never reports it new.
 */
public final class ModifierReloadCache {

    private final Set<String> modifierIds = new LinkedHashSet<>();
    private final Set<String> presetIds = new LinkedHashSet<>();
    private final Map<String, String> fingerprints = new LinkedHashMap<>();

    /** Seeds both id sets (plus failed-id pinning) at enable. */
    public void seed(ModLoadResult result) {
        replace(result);
    }

    /**
     * Diffs a fresh load against the cache. New reads fresh-minus-cached;
     * removed reads cached-minus-fresh excluding fresh failed ids.
     */
    public ReloadDiff diff(ModLoadResult fresh) {
        return new ReloadDiff(
                added(new ArrayList<>(fresh.modifiers().keySet()), modifierIds),
                removed(modifierIds, fresh.modifiers().keySet(), failedIds(fresh, ModFileKind.MODIFIER)),
                added(new ArrayList<>(fresh.presets().keySet()), presetIds),
                removed(presetIds, fresh.presets().keySet(), failedIds(fresh, ModFileKind.PRESET)));
    }

    /**
     * Modifier ids present in both loads whose fingerprint changed
     * (content edits). Call before {@link #replace} overwrites the cache.
     */
    public Set<String> changedModifierIds(ModLoadResult fresh) {
        Set<String> changed = new LinkedHashSet<>();
        for (Map.Entry<String, LoadedModifier> entry : fresh.modifiers().entrySet()) {
            String before = fingerprints.get(fingerprintKey(ModFileKind.MODIFIER, entry.getKey()));
            if (before != null && !before.equals(entry.getValue().fingerprint())) {
                changed.add(entry.getKey());
            }
        }
        return changed;
    }

    /** Replaces the cache from a fresh load, pinning failed ids. */
    public void replace(ModLoadResult fresh) {
        modifierIds.clear();
        modifierIds.addAll(fresh.modifiers().keySet());
        presetIds.clear();
        presetIds.addAll(fresh.presets().keySet());
        for (FailedFile failed : fresh.failedFiles()) {
            String id = stripExtension(failed.filename());
            if (failed.kind() == ModFileKind.MODIFIER) {
                modifierIds.add(id);
            } else {
                presetIds.add(id);
            }
        }
        fingerprints.clear();
        for (Map.Entry<String, LoadedModifier> entry : fresh.modifiers().entrySet()) {
            fingerprints.put(fingerprintKey(ModFileKind.MODIFIER, entry.getKey()),
                    entry.getValue().fingerprint());
        }
        for (Map.Entry<String, LoadedPreset> entry : fresh.presets().entrySet()) {
            fingerprints.put(fingerprintKey(ModFileKind.PRESET, entry.getKey()),
                    entry.getValue().fingerprint());
        }
    }

    private static List<String> added(List<String> freshOrdered, Set<String> cached) {
        List<String> added = new ArrayList<>();
        for (String id : freshOrdered) {
            if (!cached.contains(id)) {
                added.add(id);
            }
        }
        return added;
    }

    private static List<String> removed(Set<String> cachedOrdered, Set<String> freshIds,
            Set<String> failedIds) {
        List<String> removed = new ArrayList<>();
        for (String id : cachedOrdered) {
            if (!freshIds.contains(id) && !failedIds.contains(id)) {
                removed.add(id);
            }
        }
        return removed;
    }

    private static Set<String> failedIds(ModLoadResult result, ModFileKind kind) {
        Set<String> ids = new LinkedHashSet<>();
        for (FailedFile failed : result.failedFiles()) {
            if (failed.kind() == kind) {
                ids.add(stripExtension(failed.filename()));
            }
        }
        return ids;
    }

    private static String stripExtension(String filename) {
        if (filename.toLowerCase(Locale.ROOT).endsWith(".yml")) {
            return filename.substring(0, filename.length() - ".yml".length());
        }
        return filename;
    }

    private static String fingerprintKey(ModFileKind kind, String id) {
        return kind.dirName() + "/" + id;
    }
}
