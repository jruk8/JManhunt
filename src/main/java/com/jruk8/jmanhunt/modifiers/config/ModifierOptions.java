package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/**
 * Optional behavior tuning: interval cadence, success chance, line
 * selection, and trigger delay. Every section is optional; absent
 * sections stay null so saving never invents blocks the file did
 * not have.
 */
@SuppressWarnings("FieldMayBeFinal")
public class ModifierOptions extends OkaeriConfig {

    @CustomKey("interval-settings")
    private ModifierInterval intervalSettings;

    @CustomKey("success-chance")
    private ModifierChance successChance;

    private ModifierExecution execution;

    private Long delay;

    public ModifierInterval getIntervalSettings() {
        return intervalSettings;
    }

    public void setIntervalSettings(ModifierInterval intervalSettings) {
        this.intervalSettings = intervalSettings;
    }

    public ModifierChance getSuccessChance() {
        return successChance;
    }

    public void setSuccessChance(ModifierChance successChance) {
        this.successChance = successChance;
    }

    public ModifierExecution getExecution() {
        return execution;
    }

    public void setExecution(ModifierExecution execution) {
        this.execution = execution;
    }

    public Long getDelay() {
        return delay;
    }

    public void setDelay(Long delay) {
        this.delay = delay;
    }
}
