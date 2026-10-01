package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.Setter;

/** Interval cadence in seconds, plus jitter and timer behavior. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ModifierInterval extends OkaeriConfig {

    private Double interval;
    private Double deviation;
    private String behavior;

}
