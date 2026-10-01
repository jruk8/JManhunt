package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Getter;
import lombok.Setter;

/** Display metadata for menus: name, description, icon, and author. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class ModifierMeta extends OkaeriConfig {

    private String name;
    private String description;
    private String item;
    private String author;

}
