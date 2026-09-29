package com.jruk8.jmanhunt.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Backslash escapes: structural sentinels, verbatim rest, restore. */
class EngineEscapesTest {

    @Test
    void roundTripsAllStructuralChars() {
        String structured = "<>,\"'\\:@~?&|!=+-*/%()[]{} \t";
        assertEquals(27, structured.length());
        for (int index = 0; index < structured.length(); index++) {
            char letter = structured.charAt(index);
            String substituted = EngineEscapes.substitute("\\" + letter);
            assertEquals(1, substituted.length(), "at index " + index);
            assertEquals(String.valueOf(letter), EngineEscapes.restore(substituted));
        }
    }

    @Test
    void doubleBackslashCollapses() {
        assertEquals("\\", EngineEscapes.restore(EngineEscapes.substitute("\\\\")));
        assertEquals("a\\b", EngineEscapes.restore(EngineEscapes.substitute("a\\\\b")));
    }

    @Test
    void trailingBackslashStays() {
        assertEquals("abc\\", EngineEscapes.substitute("abc\\"));
    }

    @Test
    void nonStructuralEmitsVerbatim() {
        assertEquals("n", EngineEscapes.substitute("\\n"));
        assertEquals("q5.", EngineEscapes.substitute("\\q\\5\\."));
        assertEquals("and", EngineEscapes.substitute("\\a\\n\\d"));
    }

    @Test
    void substitutionIsIdempotent() {
        String once = EngineEscapes.substitute("a\\<b\\>c\\\\d\\,e");
        assertEquals(once, EngineEscapes.substitute(once));
    }

    @Test
    void nullSafe() {
        assertNull(EngineEscapes.substitute(null));
        assertNull(EngineEscapes.restore(null));
    }

    @Test
    void restoringWrapper() {
        List<String> out = new ArrayList<>();
        Consumer<String> sink = EngineEscapes.restoring(out::add);

        sink.accept(EngineEscapes.substitute("\\<hi\\>"));
        sink.accept("plain");
        assertEquals(List.of("<hi>", "plain"), out);
        assertTrue(out.stream().noneMatch(text -> text.indexOf(0xE000) >= 0));
    }
}
