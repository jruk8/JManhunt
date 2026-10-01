package com.jruk8.jmanhunt.modifiers.files;

import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import java.io.IOException;
import lombok.Getter;
import lombok.Setter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * File-backed modifier and preset container: live entry maps plus
 * the id-to-path index from the load pass. Every mutation persists
 * only its touched file, atomically. A null root means in-memory
 * (unit tests): maps work, file writes are skipped.
 */
public final class ModifierFiles {

    private final Path root;
    /** Live modifier map, in load order. */
    @Getter
    @Setter
    private Map<String, ModifierEntry> modifiers = new LinkedHashMap<>();
    /** Live preset map, in load order. */
    @Getter
    @Setter
    private Map<String, ModifierPreset> presets = new LinkedHashMap<>();
    private final Map<String, Path> modifierPaths = new LinkedHashMap<>();
    private final Map<String, Path> presetPaths = new LinkedHashMap<>();

    private ModifierFiles(Path root) {
        this.root = root;
    }

    /** In-memory container: no root, file writes skipped. */
    public static ModifierFiles inMemory() {
        return new ModifierFiles(null);
    }

    /** Empty container rooted at {@code root} (the mods dir). */
    public static ModifierFiles atRoot(Path root) {
        return new ModifierFiles(root);
    }

    /** Container holding one load pass, rooted at {@code root}. */
    public static ModifierFiles fromLoad(Path root, ModLoadResult result) {
        ModifierFiles files = new ModifierFiles(root);
        files.replaceAll(result);
        return files;
    }

    /** Mods root, or null when in-memory. */
    public Path root() {
        return root;
    }

    /** Known source file for one modifier, or null. */
    public Path modifierPath(String id) {
        return modifierPaths.get(id);
    }

    /** Known source file for one preset, or null. */
    public Path presetPath(String id) {
        return presetPaths.get(id);
    }

    /** Swaps contents from a fresh load pass (reload). */
    public void replaceAll(ModLoadResult result) {
        modifiers = new LinkedHashMap<>();
        presets = new LinkedHashMap<>();
        modifierPaths.clear();
        presetPaths.clear();
        for (Map.Entry<String, ModLoadResult.LoadedModifier> entry : result.modifiers().entrySet()) {
            modifiers.put(entry.getKey(), entry.getValue().entry());
            modifierPaths.put(entry.getKey(), entry.getValue().path());
        }
        for (Map.Entry<String, ModLoadResult.LoadedPreset> entry : result.presets().entrySet()) {
            presets.put(entry.getKey(), entry.getValue().preset());
            presetPaths.put(entry.getKey(), entry.getValue().path());
        }
    }

    /** Persists one modifier to its file, atomically. Logs on failure. */
    public void saveModifier(String id, Logger log) {
        ModifierEntry entry = modifiers.get(id);
        Path target = entry == null ? null : targetFor(ModFileKind.MODIFIER, id);
        if (target == null) {
            return;
        }
        try {
            AtomicFiles.writeString(target, ModifierFileCodec.serializeModifier(entry));
            modifierPaths.put(id, target);
        } catch (ModFileException | IOException exception) {
            log.warning("Could not save " + display(target) + ": " + exception.getMessage());
        }
    }

    /** Persists one preset to its file, atomically. Logs on failure. */
    public void savePreset(String id, Logger log) {
        ModifierPreset preset = presets.get(id);
        Path target = preset == null ? null : targetFor(ModFileKind.PRESET, id);
        if (target == null) {
            return;
        }
        try {
            AtomicFiles.writeString(target, ModifierFileCodec.serializePreset(preset));
            presetPaths.put(id, target);
        } catch (ModFileException | IOException exception) {
            log.warning("Could not save " + display(target) + ": " + exception.getMessage());
        }
    }

    /** Deletes one modifier file. Logs on failure. */
    public void deleteModifierFile(String id, Logger log) {
        deleteFile(modifierPaths.remove(id), rootFile(ModFileKind.MODIFIER, id), log);
    }

    /** Deletes one preset file. Logs on failure. */
    public void deletePresetFile(String id, Logger log) {
        deleteFile(presetPaths.remove(id), rootFile(ModFileKind.PRESET, id), log);
    }

    /** Moves one modifier file within its directory. Logs on failure. */
    public void moveModifierFile(String oldId, String newId, Logger log) {
        moveFile(ModFileKind.MODIFIER, modifierPaths, oldId, newId, log);
    }

    /** Moves one preset file within its directory. Logs on failure. */
    public void movePresetFile(String oldId, String newId, Logger log) {
        moveFile(ModFileKind.PRESET, presetPaths, oldId, newId, log);
    }

    private void deleteFile(Path known, Path fallback, Logger log) {
        Path target = known != null ? known : fallback;
        if (target == null) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException exception) {
            log.warning("Could not delete " + display(target) + ": " + exception.getMessage());
        }
    }

    private void moveFile(ModFileKind kind, Map<String, Path> paths, String oldId,
            String newId, Logger log) {
        Path from = paths.remove(oldId);
        Path dir = from != null && from.getParent() != null ? from.getParent() : kindDir(kind);
        if (dir == null || from == null) {
            return;
        }
        Path target = dir.resolve(newId + ".yml");
        try {
            AtomicFiles.move(from, target);
        } catch (IOException exception) {
            log.warning("Could not rename " + display(from) + ": " + exception.getMessage());
        }
        paths.put(newId, target);
    }

    private Path targetFor(ModFileKind kind, String id) {
        Map<String, Path> paths = kind == ModFileKind.MODIFIER ? modifierPaths : presetPaths;
        Path known = paths.get(id);
        return known != null ? known : rootFile(kind, id);
    }

    private Path kindDir(ModFileKind kind) {
        return root == null ? null : root.resolve(kind.dirName());
    }

    private Path rootFile(ModFileKind kind, String id) {
        Path dir = kindDir(kind);
        return dir == null ? null : dir.resolve(id + ".yml");
    }

    private String display(Path target) {
        if (root != null) {
            try {
                Path base = root.toAbsolutePath().normalize();
                Path absolute = target.toAbsolutePath().normalize();
                if (absolute.startsWith(base)) {
                    return base.relativize(absolute).toString();
                }
            } catch (RuntimeException fallback) {
                // Fall through to the raw path below.
            }
        }
        return target.toString();
    }
}
