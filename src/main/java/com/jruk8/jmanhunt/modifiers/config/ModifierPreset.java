package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** One named preset: display metadata plus member modifier ids. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ModifierPreset extends OkaeriConfig {

    private ModifierMeta meta;
    private List<String> modifiers;

}
