package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;

/**
 * Optional behavior tuning: interval cadence, success chance, line
 * selection, and trigger delay. Every section is optional; absent
 * sections stay null so saving never invents blocks the file did
 * not have.
 */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ModifierOptions extends OkaeriConfig {

    @CustomKey("interval-settings")
    private ModifierInterval intervalSettings;

    @CustomKey("success-chance")
    private ModifierChance successChance;

    private ModifierExecution execution;

    private Long delay;

}
