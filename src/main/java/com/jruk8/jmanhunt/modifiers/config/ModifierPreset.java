package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import java.util.List;

/** One named preset: display metadata plus member modifier ids. */
@SuppressWarnings("FieldMayBeFinal")
public class ModifierPreset extends OkaeriConfig {

    private ModifierMeta meta;
    private List<String> modifiers;

    public ModifierMeta getMeta() {
        return meta;
    }

    public void setMeta(ModifierMeta meta) {
        this.meta = meta;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<String> modifiers) {
        this.modifiers = modifiers;
    }
}
