package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Start timing: run commands before or after the pre-start window. */
@SuppressWarnings("FieldMayBeFinal")
public class ModifierOnStart extends OkaeriConfig {

    @CustomKey("pre-start-order")
    private String preStartOrder;

    public String getPreStartOrder() {
        return preStartOrder;
    }

    public void setPreStartOrder(String preStartOrder) {
        this.preStartOrder = preStartOrder;
    }
}
