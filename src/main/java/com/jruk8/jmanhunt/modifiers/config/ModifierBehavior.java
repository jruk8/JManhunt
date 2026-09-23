package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.List;

/**
 * Trigger behavior: when commands run and which lines fire. Every
 * section is optional; absent sections stay null so saving never
 * invents blocks the file did not have.
 */
@SuppressWarnings("FieldMayBeFinal")
public class ModifierBehavior extends OkaeriConfig {

    @CustomKey("runs-on")
    private List<String> runsOn;

    @CustomKey("on-start")
    private ModifierOnStart onStart;

    @CustomKey("interval-settings")
    private ModifierInterval intervalSettings;

    @CustomKey("success-chance")
    private ModifierChance successChance;

    private Long delay;

    private ModifierCommands commands;

    public List<String> getRunsOn() {
        return runsOn;
    }

    public void setRunsOn(List<String> runsOn) {
        this.runsOn = runsOn;
    }

    public ModifierOnStart getOnStart() {
        return onStart;
    }

    public void setOnStart(ModifierOnStart onStart) {
        this.onStart = onStart;
    }

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

    public Long getDelay() {
        return delay;
    }

    public void setDelay(Long delay) {
        this.delay = delay;
    }

    public ModifierCommands getCommands() {
        return commands;
    }

    public void setCommands(ModifierCommands commands) {
        this.commands = commands;
    }
}
