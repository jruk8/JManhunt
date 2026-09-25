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
            assertTrue(!line.contains("duration"), line);
        }
    }
}
