package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** One modifier: its toggle, display metadata, and trigger behaviors. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ModifierEntry extends OkaeriConfig {

    private boolean enabled = false;
    private ModifierMeta meta;
    private Map<String, ModifierBehavior> behavior;

}
