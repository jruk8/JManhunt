package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.Setter;

/** How many lines PICK_RANDOM draws, and per whom it draws them. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ModifierPickRandom extends OkaeriConfig {

    private Integer count;
    private String behavior;

}
