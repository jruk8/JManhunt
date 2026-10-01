package com.jruk8.jmanhunt.modifiers.files;

import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.FailedFile;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.LoadedModifier;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.LoadedPreset;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierReloadCacheTest {

    @Test
    void newAndRemoved() {
        ModifierReloadCache cache = new ModifierReloadCache();
        cache.seed(result(Map.of("a", "fp-a", "b", "fp-b"), Map.of("p", "fp-p"), List.of()));

        ReloadDiff diff = cache.diff(result(Map.of("b", "fp-b", "c", "fp-c"),
                Map.of("p", "fp-p", "q", "fp-q"), List.of()));

        assertEquals(List.of("c"), diff.newModifiers());
        assertEquals(List.of("a"), diff.removedModifiers());
        assertEquals(List.of("q"), diff.newPresets());
        assertTrue(diff.removedPresets().isEmpty());
    }

    @Test
    void renameIsOneRemovedPlusOneNew() {
        ModifierReloadCache cache = new ModifierReloadCache();
        cache.seed(result(Map.of("old", "fp"), Map.of(), List.of()));

        ReloadDiff diff = cache.diff(result(Map.of("new", "fp"), Map.of(), List.of()));

        assertEquals(List.of("new"), diff.newModifiers());
        assertEquals(List.of("old"), diff.removedModifiers());
    }

    @Test
    void editsAreInvisibleInDiffButVisibleAsChanged() {
        ModifierReloadCache cache = new ModifierReloadCache();
        cache.seed(result(Map.of("a", "fp-1"), Map.of(), List.of()));
        ModLoadResult fresh = result(Map.of("a", "fp-2"), Map.of(), List.of());

        ReloadDiff diff = cache.diff(fresh);

        assertTrue(diff.newModifiers().isEmpty());
        assertTrue(diff.removedModifiers().isEmpty());
        assertEquals(Set.of("a"), cache.changedModifierIds(fresh));

        cache.replace(fresh);
        assertTrue(cache.changedModifierIds(fresh).isEmpty());
    }

    @Test
    void duplicatesNeverCount() {
        ModifierReloadCache cache = new ModifierReloadCache();
        cache.seed(result(Map.of("x", "fp"), Map.of(), List.of()));
        ModLoadResult fresh = withDuplicates(result(Map.of("x", "fp"), Map.of(), List.of()));

        assertTrue(cache.diff(fresh).newModifiers().isEmpty());
        assertTrue(cache.diff(fresh).removedModifiers().isEmpty());
    }

    @Test
    void knownIdBreakingIsNeitherRemovedNorLaterNew() {
        ModifierReloadCache cache = new ModifierReloadCache();
        cache.seed(result(Map.of("a", "fp-a", "b", "fp-b"), Map.of(), List.of()));
        ModLoadResult broken = result(Map.of("b", "fp-b"), Map.of(),
                List.of(failed("a.yml", ModFileKind.MODIFIER)));

        assertTrue(cache.diff(broken).removedModifiers().isEmpty());
        cache.replace(broken);

        ReloadDiff fixed = cache.diff(result(Map.of("a", "fp-a", "b", "fp-b"), Map.of(), List.of()));
        assertTrue(fixed.newModifiers().isEmpty());
        assertTrue(fixed.removedModifiers().isEmpty());
    }

    @Test
    void brandNewBrokenFileIsOnlyEverFailed() {
        ModifierReloadCache cache = new ModifierReloadCache();
        cache.seed(empty());
        ModLoadResult broken = result(Map.of(), Map.of(),
                List.of(failed("new.yml", ModFileKind.MODIFIER)));

        assertTrue(cache.diff(broken).newModifiers().isEmpty());
        assertTrue(cache.diff(broken).removedModifiers().isEmpty());
        cache.replace(broken);

        ReloadDiff fixed = cache.diff(result(Map.of("new", "fp"), Map.of(), List.of()));
        assertTrue(fixed.newModifiers().isEmpty());
        assertTrue(fixed.removedModifiers().isEmpty());
    }

    private static ModLoadResult empty() {
        return result(Map.of(), Map.of(), List.of());
    }

    private static ModLoadResult result(Map<String, String> modifiers, Map<String, String> presets,
            List<FailedFile> failed) {
        Map<String, LoadedModifier> loadedModifiers = new LinkedHashMap<>();
        modifiers.forEach((id, print) -> loadedModifiers.put(id,
                new LoadedModifier(new ModifierEntry(), Path.of(id), print)));
        Map<String, LoadedPreset> loadedPresets = new LinkedHashMap<>();
        presets.forEach((id, print) -> loadedPresets.put(id,
                new LoadedPreset(new ModifierPreset(), Path.of(id), print)));
        return new ModLoadResult(new ModLoadResult.Loaded(loadedModifiers, loadedPresets),
                new ModLoadResult.Problems(List.of(), failed, List.of(), List.of()));
    }

    private static ModLoadResult withDuplicates(ModLoadResult base) {
        return new ModLoadResult(new ModLoadResult.Loaded(base.modifiers(), base.presets()),
                new ModLoadResult.Problems(base.unknownFiles(), base.failedFiles(),
                        List.of(new ModLoadResult.DuplicateId("x", ModFileKind.MODIFIER, Path.of("winner"),
                        List.of(Path.of("skipped")))), base.unknownMembers()));
    }

    private static FailedFile failed(String filename, ModFileKind kind) {
        return new FailedFile(filename, kind, "boom", Path.of(filename));
    }
}
