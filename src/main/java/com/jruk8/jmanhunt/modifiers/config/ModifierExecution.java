package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;

/** Shared line-selection settings for one commands block. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ModifierExecution extends OkaeriConfig {

    private String selection;

    @CustomKey("pick-random")
    private ModifierPickRandom pickRandom;

}
