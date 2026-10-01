package com.jruk8.jmanhunt.placeholders;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;

/** One placeholder definition: toggle, format, type, and description. */
@Getter
@Setter
public class PlaceholderEntry extends OkaeriConfig {

    @Comment("When false, the placeholder resolves to an empty string.")
    private boolean enabled = true;

    @Comment("{value} is the built-in value.")
    private String format = "{value}";

    @Comment("Value kind: INTEGER, LONG, DECIMAL, or TEXT. Reference only.")
    private String type = "TEXT";

    @Comment("What the placeholder resolves to.")
    private String description = "";

    public PlaceholderEntry() {
    }

    public PlaceholderEntry(String type, String description) {
        this.type = type;
        this.description = description;
    }

}
