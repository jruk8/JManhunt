package com.jruk8.jmanhunt.placeholders;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;

/** One placeholder definition: toggle, format, type, and description. */
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
