package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.Setter;

/** Enabled-only toggle shared by every section. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class Toggle extends OkaeriConfig {
    private boolean enabled;

    public Toggle() {
        this(false);
    }

    public Toggle(boolean enabled) {
        this.enabled = enabled;
    }

}
