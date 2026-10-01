package com.jruk8.jmanhunt.modifiers.files;

import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.DuplicateId;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.FailedFile;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.LoadedModifier;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.LoadedPreset;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.UnknownFile;
import com.jruk8.jmanhunt.modifiers.files.ModLoadResult.UnknownMember;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Single-pass loader over both mods dirs. Modifiers load fully
 * before presets; problems (unknown, failed, duplicate) are
 * collected during the walk in load order. A load never rewrites
 * files and never throws: IO problems warn and skip.
 */
public final class ModsLoader {

    private final ModsSeeder seeder;
    private final Logger log;

    public ModsLoader(ModsSeeder seeder, Logger log) {
        this.seeder = seeder;
        this.log = log;
    }

    /** Loads both kind dirs under {@code modsRoot} in one pass. */
    public ModLoadResult load(Path modsRoot) {
        Acc acc = new Acc();
        for (ModFileKind kind : ModFileKind.values()) {
            loadKind(modsRoot, kind, acc);
        }
        filterUnknownMembers(acc);
        return acc.result();
    }

    private void loadKind(Path modsRoot, ModFileKind kind, Acc acc) {
        Path dir = modsRoot.resolve(kind.dirName());
        if (!ensureDir(kind, dir)) {
            return;
        }
        List<Path> files;
        try {
            files = ModFileWalk.walk(dir);
        } catch (IOException exception) {
            log.warning("Could not list " + display(modsRoot, dir) + ": " + exception.getMessage());
            return;
        }
        for (Path file : files) {
            loadFile(modsRoot, kind, file, acc);
        }
    }

    private boolean ensureDir(ModFileKind kind, Path dir) {
        if (Files.isDirectory(dir)) {
            return true;
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException exception) {
            log.warning("Could not create " + dir + ": " + exception.getMessage());
            return false;
        }
        try {
            seeder.seed(kind, dir);
        } catch (IOException | RuntimeException exception) {
            log.warning("Could not seed " + dir + ": " + exception.getMessage());
        }
        return true;
    }

    private void loadFile(Path modsRoot, ModFileKind kind, Path file, Acc acc) {
        String name = file.getFileName().toString();
        if (!isYaml(name)) {
            acc.unknownFiles.add(new UnknownFile(name, file));
            return;
        }
        String id = name.substring(0, name.length() - ".yml".length());
        if (kind == ModFileKind.MODIFIER) {
            loadModifier(modsRoot, id, file, acc);
        } else {
            loadPreset(modsRoot, id, file, acc);
        }
    }

    private void loadModifier(Path modsRoot, String id, Path file, Acc acc) {
        LoadedModifier seen = acc.modifiers.get(id);
        if (seen != null) {
            // Duplicate check is "id already loaded": a failed file never
            // occupies its id, so a later same-id file can still load,
            // and a same-id file after a loaded one is never parsed.
            acc.skippedModifiers.computeIfAbsent(id, key -> new ArrayList<>()).add(file);
            log.warning("Duplicate modifier id '" + id + "': '" + display(modsRoot, seen.path())
                    + "' wins, skipping '" + display(modsRoot, file) + "'.");
            return;
        }
        try {
            ModifierEntry entry = ModifierFileCodec.readModifier(file);
            String fingerprint = ModifierFileCodec.fingerprint(ModifierFileCodec.serializeModifier(entry));
            acc.modifiers.put(id, new LoadedModifier(entry, file, fingerprint));
        } catch (ModFileException exception) {
            String filename = file.getFileName().toString();
            acc.failedFiles.add(new FailedFile(filename, ModFileKind.MODIFIER, exception.getMessage(), file));
            log.warning(display(modsRoot, file) + ": " + exception.getMessage());
        }
    }

    private void loadPreset(Path modsRoot, String id, Path file, Acc acc) {
        LoadedPreset seen = acc.presets.get(id);
        if (seen != null) {
            acc.skippedPresets.computeIfAbsent(id, key -> new ArrayList<>()).add(file);
            log.warning("Duplicate preset id '" + id + "': '" + display(modsRoot, seen.path())
                    + "' wins, skipping '" + display(modsRoot, file) + "'.");
            return;
        }
        try {
            ModifierPreset preset = ModifierFileCodec.readPreset(file);
            String fingerprint = ModifierFileCodec.fingerprint(ModifierFileCodec.serializePreset(preset));
            acc.presets.put(id, new LoadedPreset(preset, file, fingerprint));
        } catch (ModFileException exception) {
            String filename = file.getFileName().toString();
            acc.failedFiles.add(new FailedFile(filename, ModFileKind.PRESET, exception.getMessage(), file));
            log.warning(display(modsRoot, file) + ": " + exception.getMessage());
        }
    }

    private void filterUnknownMembers(Acc acc) {
        for (Map.Entry<String, LoadedPreset> entry : acc.presets.entrySet()) {
            List<String> members = entry.getValue().preset().getModifiers();
            if (members == null || members.isEmpty()) {
                continue;
            }
            List<String> kept = new ArrayList<>(members.size());
            for (String member : members) {
                if (acc.modifiers.containsKey(member)) {
                    kept.add(member);
                } else {
                    acc.unknownMembers.add(new UnknownMember(entry.getKey(), member));
                    log.warning("Preset '" + entry.getKey() + "' references unknown modifier '"
                            + member + "'; skipping.");
                }
            }
            // In memory only: the preset file keeps its member list so a
            // later reload picks members up once their files exist.
            if (kept.size() != members.size()) {
                entry.getValue().preset().setModifiers(kept);
            }
        }
    }

    private static boolean isYaml(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".yml");
    }

    private static String display(Path modsRoot, Path path) {
        try {
            Path base = modsRoot.toAbsolutePath().normalize();
            Path absolute = path.toAbsolutePath().normalize();
            if (absolute.startsWith(base)) {
                return base.relativize(absolute).toString();
            }
        } catch (RuntimeException fallback) {
            // Fall through to the raw path below.
        }
        return path.toString();
    }

    /** Mutable per-pass accumulator; result() freezes it into load order. */
    private static final class Acc {
        private final Map<String, LoadedModifier> modifiers = new LinkedHashMap<>();
        private final Map<String, LoadedPreset> presets = new LinkedHashMap<>();
        private final List<UnknownFile> unknownFiles = new ArrayList<>();
        private final List<FailedFile> failedFiles = new ArrayList<>();
        private final Map<String, List<Path>> skippedModifiers = new LinkedHashMap<>();
        private final Map<String, List<Path>> skippedPresets = new LinkedHashMap<>();
        private final List<UnknownMember> unknownMembers = new ArrayList<>();

        private ModLoadResult result() {
            return new ModLoadResult(new ModLoadResult.Loaded(modifiers, presets),
                    new ModLoadResult.Problems(unknownFiles, failedFiles, duplicates(),
                            unknownMembers));
        }

        private List<DuplicateId> duplicates() {
            List<DuplicateId> all = new ArrayList<>();
            for (Map.Entry<String, LoadedModifier> winner : modifiers.entrySet()) {
                collectDuplicate(all, winner.getKey(), ModFileKind.MODIFIER,
                        winner.getValue().path(), skippedModifiers.get(winner.getKey()));
            }
            for (Map.Entry<String, LoadedPreset> winner : presets.entrySet()) {
                collectDuplicate(all, winner.getKey(), ModFileKind.PRESET,
                        winner.getValue().path(), skippedPresets.get(winner.getKey()));
            }
            return all;
        }

        private void collectDuplicate(List<DuplicateId> all, String id, ModFileKind kind,
                Path winner, List<Path> skipped) {
            if (skipped != null) {
                all.add(new DuplicateId(id, kind, winner, List.copyOf(skipped)));
            }
        }
    }
}
