package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;

/** Start timing: run commands before or after the pre-start window. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ModifierOnStart extends OkaeriConfig {

    @CustomKey("pre-start-order")
    private String preStartOrder;

}
