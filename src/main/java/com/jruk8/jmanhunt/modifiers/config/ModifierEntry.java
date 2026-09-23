package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;

/** One modifier: its toggle, display metadata, and trigger behavior. */
@SuppressWarnings("FieldMayBeFinal")
public class ModifierEntry extends OkaeriConfig {

    private boolean enabled = false;
    private ModifierMeta meta;
    private ModifierBehavior behavior;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ModifierMeta getMeta() {
        return meta;
    }

    public void setMeta(ModifierMeta meta) {
        this.meta = meta;
    }

    public ModifierBehavior getBehavior() {
        return behavior;
    }

    public void setBehavior(ModifierBehavior behavior) {
        this.behavior = behavior;
    }
}
