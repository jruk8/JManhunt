package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.message.MessageService;
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

    /** Sends a debug.* message to recipients whose level shows it; silent when none exist. */
    public void debug(DebugLevel level, String key) {
        debug(level, key, Map.of());
    }

    /** Sends a debug.* message to recipients whose level shows it; silent when none exist. */
    public void debug(DebugLevel level, String key, Map<String, String> values) {
        if (!debug.hasRecipients()) {
            return;
        }
        Component rendered = Component.text("[" + level.name() + "] ")
                .append(messages.component(key, values));
        DebugLevel consoleLevel = debug.consoleLevel();
        if (consoleLevel != null && consoleLevel.shows(level)) {
            sink.sendToConsole(rendered);
        }
        for (Map.Entry<UUID, DebugLevel> entry : debug.playerLevels().entrySet()) {
            if (entry.getValue().shows(level)) {
                sink.sendToPlayer(entry.getKey(), rendered);
            }
        }
    }
}
