package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Typed root of the internal Core/dev.yml: lobby presets for fresh
 * lobby-world generation. Jar-loaded and never user editable; the file
 * in the jar is the source of truth, so preset changes ship with builds.
 */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class DevConfig extends OkaeriConfig {

    @CustomKey("lobby-presets")
    @Comment({
            "Per-preset generation. schematic is a bundled dev/lobby-schematics/",
            "entry (.jmhlobby wins over .nbt), pasted with its midpoint at",
            "0,64,0. commands run as console after the paste and before",
            "the first teleport lands ({world} and {preset} are substituted).",
            "Do not touch unless you know what you are doing."
    })
    private Map<String, LobbyPresetEntry> lobbyPresets = defaultPresets();

    /** Preset keys in file order, for completion and error text. */
    public Set<String> presetKeys() {
        if (lobbyPresets == null) {
            return Set.of();
        }
        return new LinkedHashSet<>(lobbyPresets.keySet());
    }

    /** Schematic name for the preset, blank when the preset is unknown. */
    public String schematicFor(String preset) {
        LobbyPresetEntry entry = entryFor(preset);
        return entry == null || entry.getSchematic() == null ? "" : entry.getSchematic();
    }

    /** Console commands for the preset, empty when the preset is unknown. */
    public List<String> commandsFor(String preset) {
        LobbyPresetEntry entry = entryFor(preset);
        if (entry == null || entry.getCommands() == null) {
            return List.of();
        }
        return new ArrayList<>(entry.getCommands());
    }

    private LobbyPresetEntry entryFor(String preset) {
        if (lobbyPresets == null || preset == null) {
            return null;
        }
        LobbyPresetEntry exact = lobbyPresets.get(preset);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, LobbyPresetEntry> candidate : lobbyPresets.entrySet()) {
            if (candidate.getKey().equalsIgnoreCase(preset)) {
                return candidate.getValue();
            }
        }
        return null;
    }

    private static Map<String, LobbyPresetEntry> defaultPresets() {
        Map<String, LobbyPresetEntry> presets = new LinkedHashMap<>();
        presets.put("EMPTY", LobbyPresetEntry.of("empty-lobby"));
        presets.put("DEFAULT", LobbyPresetEntry.of("default-lobby"));
        presets.put("ADVANCED", LobbyPresetEntry.of("advanced-lobby"));
        return presets;
    }

    /** One lobby preset: schematic plus console commands. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class LobbyPresetEntry extends OkaeriConfig {
        private String schematic = "";
        private List<String> commands = new ArrayList<>();

        public static LobbyPresetEntry of(String schematic) {
            LobbyPresetEntry entry = new LobbyPresetEntry();
            entry.setSchematic(schematic);
            return entry;
        }

    }
}
