package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;

/** Debug output default. */
@SuppressWarnings("FieldMayBeFinal")
public class DebugConfig extends OkaeriConfig {

    private boolean enabled = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}