package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.WinConditionEngine;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.placeholders.PlaceholderConfig;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.stats.StatsManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** Built-in PlaceholderAPI expansion, intentionally shaped like an eCloud expansion. */
public final class JManhuntExpansion extends PlaceholderExpansion {
    private final JManhuntPlugin plugin;
    private final JManhuntPlaceholders placeholders;

    public JManhuntExpansion(JManhuntPlugin plugin, StatsManager stats, MessageService messages,
            GameManager game, PlayerStateStore playerStates, WinConditionEngine winConditions,
            PlaceholderConfig placeholderConfig) {
        this.plugin = plugin;
        this.placeholders = new JManhuntPlaceholders(stats, messages, game, playerStates,
                winConditions, placeholderConfig, plugin.configService());
    }

    @Override public String getIdentifier() { return "jmanhunt"; }
    @Override public String getAuthor() { return "jruk8"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public boolean canRegister() { return true; }

    @Override public String onRequest(OfflinePlayer player, String params) {
        return placeholders.resolve(player, params);
    }
}
