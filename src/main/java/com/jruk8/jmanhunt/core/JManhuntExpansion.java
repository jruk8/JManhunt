package com.jruk8.jmanhunt.core;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** Built-in PlaceholderAPI expansion, intentionally shaped like an eCloud expansion. */
public final class JManhuntExpansion extends PlaceholderExpansion {
    private final String pluginVersion;
    private final JManhuntPlaceholders placeholders;

    public JManhuntExpansion(String pluginVersion, JManhuntPlaceholders placeholders) {
        this.pluginVersion = pluginVersion;
        this.placeholders = placeholders;
    }

    @Override public String getIdentifier() { return "jmanhunt"; }
    @Override public String getAuthor() { return "jruk8"; }
    @Override public String getVersion() { return pluginVersion; }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override public String onRequest(OfflinePlayer player, String params) {
        return placeholders.resolve(player, params);
    }
}
