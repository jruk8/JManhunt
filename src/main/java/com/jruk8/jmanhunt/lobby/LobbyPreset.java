package com.jruk8.jmanhunt.lobby;

/**
 * Lobby-world generation preset. Every preset pastes only: no block is
 * ever built in code, so each preset needs its .nbt in
 * settings/world-engine/lobby-schematics/.
 */
public enum LobbyPreset {
    EMPTY,
    DEFAULT,
    ADVANCED;

    /** Parses leniently; unknown values fall back to DEFAULT. */
    public static LobbyPreset parse(String raw) {
        if (raw != null) {
            for (LobbyPreset preset : values()) {
                if (preset.name().equalsIgnoreCase(raw.trim())) {
                    return preset;
                }
            }
        }
        return DEFAULT;
    }
}
