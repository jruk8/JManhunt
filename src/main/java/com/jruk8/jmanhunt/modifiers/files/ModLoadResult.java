package com.jruk8.jmanhunt.modifiers.files;

import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One full load pass over both mods dirs: loaded entries in load
 * order plus every file problem collected during the single walk,
 * each in load (or walk encounter) order.
 */
public final class ModLoadResult {

    /** One loaded modifier: entry, source file, content fingerprint. */
    public record LoadedModifier(ModifierEntry entry, Path path, String fingerprint) {
    }

    /** One loaded preset: preset, source file, content fingerprint. */
    public record LoadedPreset(ModifierPreset preset, Path path, String fingerprint) {
    }

    /** A non-yml file under either dir, with its real extension. */
    public record UnknownFile(String filename, Path path) {
    }

    /** A yml file that failed to parse or validate. */
    public record FailedFile(String filename, ModFileKind kind, String error, Path path) {
    }

    /** One duplicated id: winner plus every skipped copy. */
    public record DuplicateId(String id, ModFileKind kind, Path winner, List<Path> skipped) {
    }

    /** One preset member id with no loaded modifier. */
    public record UnknownMember(String presetId, String memberId) {
    }

    /** Loaded entries by id, in load order. */
    public record Loaded(Map<String, LoadedModifier> modifiers,
            Map<String, LoadedPreset> presets) {
    }

    /** File problems in load (or walk encounter) order. */
    public record Problems(List<UnknownFile> unknownFiles, List<FailedFile> failedFiles,
            List<DuplicateId> duplicates, List<UnknownMember> unknownMembers) {
    }

    private final Map<String, LoadedModifier> modifiers;
    private final Map<String, LoadedPreset> presets;
    private final List<UnknownFile> unknownFiles;
    private final List<FailedFile> failedFiles;
    private final List<DuplicateId> duplicates;
    private final List<UnknownMember> unknownMembers;

    public ModLoadResult(Loaded loaded, Problems problems) {
        this.modifiers = new LinkedHashMap<>(loaded.modifiers());
        this.presets = new LinkedHashMap<>(loaded.presets());
        this.unknownFiles = new ArrayList<>(problems.unknownFiles());
        this.failedFiles = new ArrayList<>(problems.failedFiles());
        this.duplicates = new ArrayList<>(problems.duplicates());
        this.unknownMembers = new ArrayList<>(problems.unknownMembers());
    }

    /** Empty result: no entries, no problems. */
    public static ModLoadResult empty() {
        return new ModLoadResult(new Loaded(Map.of(), Map.of()),
                new Problems(List.of(), List.of(), List.of(), List.of()));
    }

    /** Loaded modifiers by id, in load order. */
    public Map<String, LoadedModifier> modifiers() {
        return Collections.unmodifiableMap(modifiers);
    }

    /** Loaded presets by id, in load order. */
    public Map<String, LoadedPreset> presets() {
        return Collections.unmodifiableMap(presets);
    }

    /** Unknown files in walk encounter order. */
    public List<UnknownFile> unknownFiles() {
        return Collections.unmodifiableList(unknownFiles);
    }

    /** Failed files in load order. */
    public List<FailedFile> failedFiles() {
        return Collections.unmodifiableList(failedFiles);
    }

    /** Duplicate ids ordered by winner load position. */
    public List<DuplicateId> duplicates() {
        return Collections.unmodifiableList(duplicates);
    }

    /** Unknown preset members in load order. */
    public List<UnknownMember> unknownMembers() {
        return Collections.unmodifiableList(unknownMembers);
    }
}
