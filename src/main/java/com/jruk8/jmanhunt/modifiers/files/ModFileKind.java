package com.jruk8.jmanhunt.modifiers.files;

/**
 * The two mod file namespaces. Declaration order is load order:
 * modifiers load fully before presets so preset member validation
 * can resolve every modifier id.
 */
public enum ModFileKind {
    MODIFIER("modifiers"),
    PRESET("presets");

    private final String dirName;

    ModFileKind(String dirName) {
        this.dirName = dirName;
    }

    /** Data-folder subdirectory name for this kind. */
    public String dirName() {
        return dirName;
    }
}
