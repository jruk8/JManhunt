package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single funnel for plugin console output. Level methods behave exactly like
 * the wrapped logger; debug lines additionally fan out to every debug
 * recipient instead of only the console log. Throwable overloads log the
 * full trace to the console log and a truncated single line to debug
 * recipients; callers rethrow after logging so nothing is swallowed.
 */
public final class JManhuntLogger {

    /** Max plain-text chars of a logger line shown to console/chat recipients. */
    static final int MAX_RECIPIENT_CHARS = 500;

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

    /** Logs the message plus trace, and fans out a truncated line to debug recipients. */
    public void info(String message, Throwable error) {
        log(Level.INFO, message, error);
    }

    /** Logs the message plus trace, and fans out a truncated line to debug recipients. */
    public void warning(String message, Throwable error) {
        log(Level.WARNING, message, error);
    }

    /** Logs the message plus trace, and fans out a truncated line to debug recipients. */
    public void severe(String message, Throwable error) {
        log(Level.SEVERE, message, error);
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
        // The debug prefix leads, then the level tag, then the body: the
        // templates carry {debug-prefix} themselves, so render the body
        // with it blanked to avoid doubling the prefix.
        Component rendered = messages.component("debug.prefix")
                .append(messages.miniMessage(levelTag(level)))
                .append(messages.renderLiteral(
                        messages.string(key, key).replace("{debug-prefix}", ""), values));
        sendRendered(level, rendered);
    }

    private void log(Level level, String message, Throwable error) {
        console.log(level, message, error);
        if (!debug.hasRecipients()) {
            return;
        }
        DebugLevel debugLevel = debugLevel(level);
        Component rendered = messages.component("debug.prefix")
                .append(messages.miniMessage(levelTag(debugLevel)))
                .append(Component.text(truncate(singleLine(message, error))));
        sendRendered(debugLevel, rendered);
    }

    /**
     * Colored level tag: severe red, warn yellow, info gray, brackets
     * included and the color closed so the body keeps its own colors.
     */
    private static String levelTag(DebugLevel level) {
        return switch (level) {
            case SEVERE -> "<red>[SEVERE] </red>";
            case WARN -> "<yellow>[WARN] </yellow>";
            case INFO -> "<gray>[INFO] </gray>";
        };
    }

    private void sendRendered(DebugLevel level, Component rendered) {
        Component capped = capLength(rendered);
        DebugLevel consoleLevel = debug.consoleLevel();
        if (consoleLevel != null && consoleLevel.shows(level)) {
            sink.sendToConsole(capped);
        }
        for (Map.Entry<UUID, DebugLevel> entry : debug.playerLevels().entrySet()) {
            if (entry.getValue().shows(level)) {
                sink.sendToPlayer(entry.getKey(), capped);
            }
        }
    }

    /** Maps a console log level onto a debug level: severe, warning, else info. Pure. */
    static DebugLevel debugLevel(Level level) {
        if (level != null && level.intValue() >= Level.SEVERE.intValue()) {
            return DebugLevel.SEVERE;
        }
        if (level != null && level.intValue() >= Level.WARNING.intValue()) {
            return DebugLevel.WARN;
        }
        return DebugLevel.INFO;
    }

    /** Collapses a message plus throwable into one chat-safe line. Pure. */
    static String singleLine(String message, Throwable error) {
        String base = message == null ? "" : message;
        if (error != null) {
            base = base.isEmpty() ? String.valueOf(error) : base + ": " + error;
        }
        return base.replaceAll("\\s+", " ").trim();
    }

    /**
     * Caps a recipient line at 500 chars, appending "..." past the cap.
     * Null reads empty. Pure.
     */
    static String truncate(String line) {
        if (line == null) {
            return "";
        }
        if (line.length() <= MAX_RECIPIENT_CHARS) {
            return line;
        }
        return line.substring(0, MAX_RECIPIENT_CHARS) + "...";
    }

    /**
     * Caps a rendered line at 500 plain-text chars; over-long lines fall
     * back to truncated plain text. Pure.
     */
    static Component capLength(Component rendered) {
        String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
        if (plain.length() <= MAX_RECIPIENT_CHARS) {
            return rendered;
        }
        return Component.text(truncate(plain));
    }
}
