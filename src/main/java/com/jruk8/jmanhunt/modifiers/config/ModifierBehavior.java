package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Trigger behavior: when commands run and which lines fire. Every
 * section is optional; absent sections stay null so saving never
 * invents blocks the file did not have.
 */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ModifierBehavior extends OkaeriConfig {

    @CustomKey("runs-on")
    private List<String> runsOn;

    @CustomKey("on-start")
    private ModifierOnStart onStart;

    private ModifierOptions options;

    private ModifierCommands commands;

}
