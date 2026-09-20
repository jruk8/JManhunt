package com.jruk8.jmanhunt;

import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Single funnel for plugin console output. Level methods behave exactly like
 * the wrapped logger; debug lines additionally fan out to every debug
 * recipient instead of only the console log.
 */
public final class JManhuntLogger {
    /** Delivers rendered debug lines; a Bukkit implementation lives in {@link BukkitDebugSink}. */
    public interface DebugSink {
        void sendToConsole(Component message);
        void sendToPlayer(UUID playerId, Component message);
    }

    private final Logger console;
    private final DebugService debug;
    private final MessageService messages;
    private final DebugSink sink;

    public JManhuntLogger(Logger console, DebugService debug, MessageService messages, DebugSink sink) {
        this.console = console;
        this.debug = debug;
        this.messages = messages;
        this.sink = sink;
    }

    public void info(String message) {
        console.info(message);
    }

    public void warning(String message) {
        console.warning(message);
    }

    public void severe(String message) {
        console.severe(message);
    }

    public void fine(String message) {
        console.fine(message);
    }

    /** Sends a debug.* message to every debug recipient; silent when none exist. */
    public void debug(String key) {
        debug(key, Map.of());
    }

    /** Sends a debug.* message to every debug recipient; silent when none exist. */
    public void debug(String key, Map<String, String> values) {
        if (!debug.hasRecipients()) {
            return;
        }
        Component rendered = messages.component(key, values);
        if (debug.isConsoleEnabled()) {
            sink.sendToConsole(rendered);
        }
        for (UUID playerId : debug.debugPlayerIds()) {
            sink.sendToPlayer(playerId, rendered);
        }
    }
}
