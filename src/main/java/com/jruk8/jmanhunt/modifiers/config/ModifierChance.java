package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.Setter;

/** Success chance per activation, from 0.0 to 1.0. Ignored by cleanup. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ModifierChance extends OkaeriConfig {

    private Double chance;
    private String behavior;

}
