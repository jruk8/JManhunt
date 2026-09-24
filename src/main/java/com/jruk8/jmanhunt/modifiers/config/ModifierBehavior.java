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

    private ModifierOptions options;

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

    public ModifierOptions getOptions() {
        return options;
    }

    public void setOptions(ModifierOptions options) {
        this.options = options;
    }

    public ModifierCommands getCommands() {
        return commands;
    }

    public void setCommands(ModifierCommands commands) {
        this.commands = commands;
    }
}
