package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.message.DebugMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JManhuntLoggerTest {

    private record RecordingSink(List<Component> console, List<UUID> players,
                                 List<Component> playerLines) implements DebugSink {
        @Override
        public void sendToConsole(Component message) {
            console.add(message);
        }

        @Override
        public void sendToPlayer(UUID playerId, Component message) {
            players.add(playerId);
            playerLines.add(message);
        }
    }

    @Test
    void levelMethodsPassThroughToLogger() {
        List<LogRecord> records = new ArrayList<>();
        Logger jul = Logger.getAnonymousLogger();
        jul.setUseParentHandlers(false);
        jul.setLevel(Level.ALL);
        jul.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        JManhuntLogger logger = logger(jul, new DebugService(), sink());

        logger.info("i");
        logger.warning("w");
        logger.severe("s");
        logger.fine("f");

        assertEquals(4, records.size());
        assertEquals(Level.INFO, records.get(0).getLevel());
        assertEquals("i", records.get(0).getMessage());
        assertEquals(Level.WARNING, records.get(1).getLevel());
        assertEquals(Level.SEVERE, records.get(2).getLevel());
        assertEquals(Level.FINE, records.get(3).getLevel());
    }

    @Test
    void debugIsSilentWithoutRecipients() {
        RecordingSink sink = sink();
        JManhuntLogger logger = logger(Logger.getAnonymousLogger(), new DebugService(), sink);

        logger.debug(DebugLevel.INFO, DebugMessages::getCellFetched, Map.of("value", "7"));

        assertTrue(sink.console().isEmpty());
        assertTrue(sink.players().isEmpty());
    }

    @Test
    void debugFansOutToConsoleAndPlayers() {
        RecordingSink sink = sink();
        DebugService debug = new DebugService();
        UUID player = UUID.randomUUID();
        debug.setConsoleLevel(DebugLevel.INFO);
        debug.setPlayerLevel(player, DebugLevel.INFO);
        FixtureText fixture = fixtureTexts();
        MessageService messages = fixture.service();
        JManhuntLogger logger = new JManhuntLogger(Logger.getAnonymousLogger(), debug, messages,
                fixture.texts().getDebug(), sink);

        logger.debug(DebugLevel.INFO, DebugMessages::getCellFetched, Map.of("value", "7"));

        assertEquals(1, sink.console().size());
        assertEquals("[D] [INFO] value 7.", plain(sink.console().get(0)));
        assertEquals(List.of(player), sink.players());
        assertEquals(1, sink.playerLines().size());
        assertEquals("[D] [INFO] value 7.", plain(sink.playerLines().get(0)));
    }

    @Test
    void debugTagsEachLevel() {
        RecordingSink sink = sink();
        DebugService debug = new DebugService();
        debug.setConsoleLevel(DebugLevel.INFO);
        JManhuntLogger logger = logger(Logger.getAnonymousLogger(), debug, sink);

        logger.debug(DebugLevel.WARN, DebugMessages::getCellFetched, Map.of("value", "7"));
        logger.debug(DebugLevel.SEVERE, DebugMessages::getCellFetched, Map.of("value", "7"));

        assertEquals(2, sink.console().size());
        assertEquals("[D] [WARN] value 7.", plain(sink.console().get(0)));
        assertEquals("[D] [SEVERE] value 7.", plain(sink.console().get(1)));
    }

    @Test
    void debugTagsCarryLevelColors() {
        RecordingSink sink = sink();
        DebugService debug = new DebugService();
        debug.setConsoleLevel(DebugLevel.INFO);
        JManhuntLogger logger = logger(Logger.getAnonymousLogger(), debug, sink);

        logger.debug(DebugLevel.INFO, DebugMessages::getCellFetched, Map.of("value", "7"));
        logger.debug(DebugLevel.WARN, DebugMessages::getCellFetched, Map.of("value", "7"));
        logger.debug(DebugLevel.SEVERE, DebugMessages::getCellFetched, Map.of("value", "7"));

        assertEquals(3, sink.console().size());
        assertEquals(NamedTextColor.GRAY, tagColor(sink.console().get(0), "[INFO] "));
        assertEquals(NamedTextColor.YELLOW, tagColor(sink.console().get(1), "[WARN] "));
        assertEquals(NamedTextColor.RED, tagColor(sink.console().get(2), "[SEVERE] "));
    }

    @Test
    void debugFiltersPerRecipientLevel() {
        RecordingSink sink = sink();
        DebugService debug = new DebugService();
        UUID severePlayer = UUID.randomUUID();
        UUID infoPlayer = UUID.randomUUID();
        debug.setConsoleLevel(DebugLevel.WARN);
        debug.setPlayerLevel(severePlayer, DebugLevel.SEVERE);
        debug.setPlayerLevel(infoPlayer, DebugLevel.INFO);
        JManhuntLogger logger = logger(Logger.getAnonymousLogger(), debug, sink);

        logger.debug(DebugLevel.INFO, DebugMessages::getCellFetched, Map.of("value", "7"));
        assertTrue(sink.console().isEmpty());
        assertEquals(List.of(infoPlayer), sink.players());

        logger.debug(DebugLevel.WARN, DebugMessages::getCellFetched, Map.of("value", "7"));
        assertEquals(1, sink.console().size());
        assertEquals(List.of(infoPlayer, infoPlayer), sink.players());

        logger.debug(DebugLevel.SEVERE, DebugMessages::getCellFetched, Map.of("value", "7"));
        assertEquals(2, sink.console().size());
        assertEquals(4, sink.players().size());
        assertTrue(sink.players().contains(severePlayer));
    }

    @Test
    void throwableOverloadsFanOutTruncatedLineAfterPrefix() {
        RecordingSink sink = sink();
        DebugService debug = new DebugService();
        debug.setConsoleLevel(DebugLevel.INFO);
        JManhuntLogger logger = logger(Logger.getAnonymousLogger(), debug, sink);

        logger.severe("kaboom", new RuntimeException("bad"));

        assertEquals(1, sink.console().size());
        assertEquals("[D] [SEVERE] kaboom: java.lang.RuntimeException: bad",
                plain(sink.console().get(0)));
    }

    @Test
    void throwableFanOutTruncatesLongLines() {
        RecordingSink sink = sink();
        DebugService debug = new DebugService();
        debug.setConsoleLevel(DebugLevel.INFO);
        JManhuntLogger logger = logger(Logger.getAnonymousLogger(), debug, sink);

        logger.warning("w".repeat(600), null);

        assertEquals(1, sink.console().size());
        String capped = plain(sink.console().get(0));
        assertEquals(503, capped.length());
        assertTrue(capped.endsWith("..."));
    }

    @Test
    void truncateCapsLongLines() {
        assertEquals("", JManhuntLogger.truncate(null));
        assertEquals("short", JManhuntLogger.truncate("short"));
        String exact = "x".repeat(500);
        assertEquals(exact, JManhuntLogger.truncate(exact));
        assertEquals("y".repeat(500) + "...", JManhuntLogger.truncate("y".repeat(501)));
    }

    @Test
    void singleLineCollapsesWhitespace() {
        assertEquals("", JManhuntLogger.singleLine(null, null));
        assertEquals("boom", JManhuntLogger.singleLine("boom", null));
        assertEquals("java.lang.RuntimeException: bad",
                JManhuntLogger.singleLine(null, new RuntimeException("bad")));
        assertEquals("ctx: java.lang.RuntimeException: bad",
                JManhuntLogger.singleLine("ctx", new RuntimeException("bad")));
        assertEquals("a b c", JManhuntLogger.singleLine("a\n\t b\r\nc", null));
    }

    @Test
    void debugLevelMapsConsoleLevels() {
        assertEquals(DebugLevel.SEVERE, JManhuntLogger.debugLevel(Level.SEVERE));
        assertEquals(DebugLevel.WARN, JManhuntLogger.debugLevel(Level.WARNING));
        assertEquals(DebugLevel.INFO, JManhuntLogger.debugLevel(Level.INFO));
        assertEquals(DebugLevel.INFO, JManhuntLogger.debugLevel(Level.FINE));
        assertEquals(DebugLevel.INFO, JManhuntLogger.debugLevel(null));
    }

    @Test
    void capLengthKeepsShortAndTruncatesLong() {
        assertEquals("short", plain(JManhuntLogger.capLength(Component.text("short"))));
        assertEquals("z".repeat(500) + "...",
                plain(JManhuntLogger.capLength(Component.text("z".repeat(600)))));
    }

    private static String plain(Component component) {
        StringBuilder text = new StringBuilder(
                component instanceof TextComponent textComponent ? textComponent.content() : "");
        for (Component child : component.children()) {
            text.append(plain(child));
        }
        return text.toString();
    }

    private static TextColor tagColor(Component root, String tag) {
        if (root instanceof TextComponent text && text.content().equals(tag)) {
            return text.color();
        }
        for (Component child : root.children()) {
            TextColor found = tagColor(child, tag);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static JManhuntLogger logger(Logger jul, DebugService debug, RecordingSink sink) {
        MessagesConfig texts = new MessagesConfig();
        ConfigPathMapper.set(texts, "debug.prefix", "<gray>[D]</gray> ");
        ConfigPathMapper.set(texts, "debug.cell-fetched",
                "{debug-prefix}<gray>value <white>{value}<gray>.");
        MessageService service = new MessageService();
        service.reload(texts);
        return new JManhuntLogger(jul, debug, service, texts.getDebug(), sink);
    }

    private static RecordingSink sink() {
        return new RecordingSink(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private record FixtureText(MessageService service, MessagesConfig texts) {
    }

    private static FixtureText fixtureTexts() {
        MessagesConfig config = new MessagesConfig();
        ConfigPathMapper.set(config, "prefix", "<gray>[T]</gray> ");
        ConfigPathMapper.set(config, "debug.prefix", "<gray>[D]</gray> ");
        ConfigPathMapper.set(config, "debug.cell-fetched",
                "{debug-prefix}<gray>value <white>{value}<gray>.");
        MessageService messages = new MessageService();
        messages.reload(config);
        return new FixtureText(messages, config);
    }
}
