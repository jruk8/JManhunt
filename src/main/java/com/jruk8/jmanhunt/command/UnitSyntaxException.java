package com.jruk8.jmanhunt.command;

import java.util.Map;

/**
 * Pure parse failure carrying the template and placeholders to render.
 * Units whose parsing cannot fit the valid-flag record style throw this
 * from parse; execute renders it identically to a usage error.
 */
public final class UnitSyntaxException extends Exception {
    private final String template;
    private final Map<String, String> placeholders;

    public UnitSyntaxException(String template, Map<String, String> placeholders) {
        super(template);
        this.template = template;
        this.placeholders = Map.copyOf(placeholders);
    }

    public String template() {
        return template;
    }

    public Map<String, String> placeholders() {
        return placeholders;
    }
}
