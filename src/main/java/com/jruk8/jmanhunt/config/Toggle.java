package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;

/** Enabled-only toggle shared by every section. */
@SuppressWarnings("FieldMayBeFinal")
public class Toggle extends OkaeriConfig {
    private boolean enabled;

    public Toggle() {
        this(false);
    }

    public Toggle(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}