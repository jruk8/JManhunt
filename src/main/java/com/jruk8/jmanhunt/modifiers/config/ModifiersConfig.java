package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * modifiers.yml root: the file version, the modifier entries by id,
 * and the named presets grouping them. Entry data lives only in the
 * yaml file; these fields declare structure with empty defaults so a
 * missing or damaged file loads safe fallbacks.
 */
@SuppressWarnings("FieldMayBeFinal")
@Header({
        "Named command bundles and the presets that group them.",
        "Toggle bundles in-game with the /mh modifiers menu or",
        "/mh modifiers setmod <name> true|false (presets: setpreset).",
        "Display fields live under meta: (name, description, item, author);",
        "behavior holds triggers and commands. commands: is always last.",
        "All commands run when the match starts (or on interval if configured),",
        "except console-cleanup and player-cleanup which run when the match ends.",
        "Need help writing commands? Generate up-to-date ones at https://mcstacker.net/",
        ""
})
public class ModifiersConfig extends OkaeriConfig {

    @CustomKey("modifiers-version")
    private int modifiersVersion = 1;

    @Comment("Modifier entries by id. enabled flips the bundle; meta holds display data.")
    private Map<String, ModifierEntry> modifiers = new LinkedHashMap<>();

    @Comment({
            "Named groups of modifiers. A preset has no state of its own: it reads",
            "as on when every modifier inside is enabled, and toggling it flips",
            "everything inside at once. Lists hold modifier ids from above."
    })
    private Map<String, ModifierPreset> presets = new LinkedHashMap<>();

    public int getModifiersVersion() {
        return modifiersVersion;
    }

    public void setModifiersVersion(int modifiersVersion) {
        this.modifiersVersion = modifiersVersion;
    }

    public Map<String, ModifierEntry> getModifiers() {
        return modifiers;
    }

    public void setModifiers(Map<String, ModifierEntry> modifiers) {
        this.modifiers = modifiers;
    }

    public Map<String, ModifierPreset> getPresets() {
        return presets;
    }

    public void setPresets(Map<String, ModifierPreset> presets) {
        this.presets = presets;
    }
}
