package com.jruk8.jmanhunt.core;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure span expansion behind the placeholder pass. */
class PlaceholderPassTest {

    private static String expand(String text, Map<String, String> values) {
        return PlaceholderPass.expandSpans(text, values::get);
    }

    @Test
    void expandsSpansAndKeepsUnknownVerbatim() {
        Map<String, String> values = Map.of("a", "1", "b", "2");
        assertEquals("1 and 2", expand("%a% and %b%", values));
        assertEquals("1 and %zzz%", expand("%a% and %zzz%", values));
    }

    @Test
    void ignoresUnbalancedAndEmptySpans() {
        Map<String, String> values = Map.of("a", "1");
        assertEquals("100% beef", expand("100% beef", values));
        assertEquals("%%", expand("%%", values));
        assertEquals("%a", expand("%a", values));
    }

    @Test
    void valuesSurviveReplacementEscapes() {
        Map<String, String> values = Map.of("a", "$1\\dollar");
        assertEquals("$1\\dollar", expand("%a%", values));
    }
}
