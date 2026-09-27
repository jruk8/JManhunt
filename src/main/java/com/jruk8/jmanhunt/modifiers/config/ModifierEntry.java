package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import java.util.Map;

/** One modifier: its toggle, display metadata, and trigger behaviors. */
@SuppressWarnings("FieldMayBeFinal")
public class ModifierEntry extends OkaeriConfig {

    private boolean enabled = false;
    private ModifierMeta meta;
    private Map<String, ModifierBehavior> behavior;

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

    public Map<String, ModifierBehavior> getBehavior() {
        return behavior;
    }

    public void setBehavior(Map<String, ModifierBehavior> behavior) {
        this.behavior = behavior;
    }
}
