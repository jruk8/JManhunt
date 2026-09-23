package com.jruk8.jmanhunt.core;

import com.jruk8.jmanhunt.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.configuration.file.YamlConfiguration;
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

        logger.debug("debug.probe", Map.of("value", "7"));

        assertTrue(sink.console().isEmpty());
        assertTrue(sink.players().isEmpty());
    }

    @Test
    void debugFansOutToConsoleAndPlayers() {
        RecordingSink sink = sink();
        DebugService debug = new DebugService();
        UUID player = UUID.randomUUID();
        debug.setConsoleEnabled(true);
        debug.setPlayerEnabled(player, true);
        MessageService messages = messages();
        JManhuntLogger logger = new JManhuntLogger(Logger.getAnonymousLogger(), debug, messages, sink);

        logger.debug("debug.probe", Map.of("value", "7"));

        assertEquals(1, sink.console().size());
        assertEquals("[D] value 7.", plain(sink.console().get(0)));
        assertEquals(List.of(player), sink.players());
        assertEquals(1, sink.playerLines().size());
        assertEquals("[D] value 7.", plain(sink.playerLines().get(0)));
    }

    private static String plain(Component component) {
        StringBuilder text = new StringBuilder(
                component instanceof TextComponent textComponent ? textComponent.content() : "");
        for (Component child : component.children()) {
            text.append(plain(child));
        }
        return text.toString();
    }

    private static JManhuntLogger logger(Logger jul, DebugService debug, RecordingSink sink) {
        return new JManhuntLogger(jul, debug, messages(), sink);
    }

    private static RecordingSink sink() {
        return new RecordingSink(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    private static MessageService messages() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("prefix", "<gray>[T]</gray> ");
        config.set("debug.prefix", "<gray>[D]</gray> ");
        config.set("debug.probe", "{debug-prefix}<gray>value <white>{value}<gray>.");
        MessageService messages = new MessageService();
        messages.reload(config);
        return messages;
    }
}
