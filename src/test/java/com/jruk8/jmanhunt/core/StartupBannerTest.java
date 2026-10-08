package com.jruk8.jmanhunt.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Startup banner: versioned, compact, console colors only. */
class StartupBannerTest {

    @Test
    void linesCarryTheVersion() {
        List<String> lines = StartupBanner.lines("5.0.0");

        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("JManhunt"));
        assertTrue(lines.get(0).contains("5.0.0"));
    }

    @Test
    void linesUseNoMiniMessageOrEmDashes() {
        for (String line : StartupBanner.lines("5.0.0")) {
            assertFalse(line.contains("<"), line);
            assertFalse(line.contains(">"), line);
            assertFalse(line.contains("\u2014"), line);
        }
    }

    @Test
    void printPadsWithTwoBlankLinesAroundThreeColoredLines() {
        List<Component> sent = new ArrayList<>();

        StartupBanner.print(sent::add, "5.0.0");

        assertEquals(7, sent.size());
        assertEquals(Component.empty(), sent.get(0));
        assertEquals(Component.empty(), sent.get(1));
        assertEquals(Component.empty(), sent.get(5));
        assertEquals(Component.empty(), sent.get(6));
        String first = LegacyComponentSerializer.legacySection().serialize(sent.get(2));
        assertTrue(first.contains("\u00A7d"), first);
        String body = sent.subList(2, 5).stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .collect(Collectors.joining("\n"));
        assertTrue(body.contains("JManhunt"));
        assertTrue(body.contains("5.0.0"));
    }
}
