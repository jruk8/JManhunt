package com.jruk8.jmanhunt.command;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderCheatsheetTest {

    @Test
    void listsEveryEngineTagExactlyOnce() {
        List<String> lines = PlaceholderCheatsheet.lines();

        assertEquals(CommandSyntax.knownTags().size(), lines.size());
        for (String tag : CommandSyntax.knownTags()) {
            long count = lines.stream()
                    .filter(line -> line.contains("<" + tag + ">")
                            || line.contains("<" + tag + ":"))
                    .count();
            assertEquals(1, count, tag);
        }
    }

    @Test
    void everyLineUsesTheDoubleArrowMarker() {
        for (String line : PlaceholderCheatsheet.lines()) {
            assertTrue(line.contains("»"), line);
        }
    }

    @Test
    void omitsCompassOnlyDurationTag() {
        for (String line : PlaceholderCheatsheet.lines()) {
            assertTrue(!line.contains("<duration"), line);
        }
    }

    @Test
    void signaturesRenderWhiteWithGreenMarkerAndGrayNote() {
        for (String line : PlaceholderCheatsheet.lines()) {
            assertTrue(line.startsWith("<green>»</green> <white>"), line);
            assertTrue(line.contains("</white> <gray>"), line);
            assertTrue(line.endsWith("</gray>"), line);
        }
    }

    @Test
    void commandDialogLeadsWithGuidanceShortlistAndDocsPointer() {
        List<String> lines = PlaceholderCheatsheet.commandDialogLines();

        assertTrue(lines.get(0).contains("give @p cooked_beef 8"), lines.get(0));
        assertTrue(lines.get(1).contains("runs as one command"), lines.get(1));
        assertTrue(lines.stream().anyMatch(line -> line.contains("<p>")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.contains("<pmessage:text>")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.contains("<random-player>")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.contains("<random-num:min,max>")),
                lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.contains("<random-pick:a,b>")),
                lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.contains("Modifiers docs page")),
                lines.toString());
        assertEquals(9 + PlaceholderCheatsheet.lines().size(), lines.size());
        assertEquals(PlaceholderCheatsheet.lines(),
                lines.subList(9, lines.size()));
    }
}
