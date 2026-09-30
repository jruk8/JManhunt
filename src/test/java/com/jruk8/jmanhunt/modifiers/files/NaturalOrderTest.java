package com.jruk8.jmanhunt.modifiers.files;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalOrderTest {

    @Test
    void digitRunsCompareNumerically() {
        assertTrue(NaturalOrder.compare("mod2", "mod10") < 0);
        assertTrue(NaturalOrder.compare("mod10", "mod9") > 0);
        assertTrue(NaturalOrder.compare("file20", "file3") > 0);
        assertTrue(NaturalOrder.compare("x7", "x8") < 0);
        assertTrue(NaturalOrder.compare("x8", "x70") < 0);
    }

    @Test
    void lettersCompareCaseInsensitively() {
        assertTrue(NaturalOrder.compare("a.yml", "B.yml") < 0);
        assertTrue(NaturalOrder.compare("B.yml", "a.yml") > 0);
        assertTrue(NaturalOrder.compare("apple", "Banana") < 0);
        assertEquals(0, NaturalOrder.compare("same", "same"));
    }

    @Test
    void tiesBreakDeterministically() {
        assertTrue(NaturalOrder.compare("Mod", "mod") != 0);
        assertTrue(NaturalOrder.compare("mod02", "mod2") != 0);
        assertEquals(Integer.signum(NaturalOrder.compare("b", "a")),
                -Integer.signum(NaturalOrder.compare("a", "b")));
    }

    @Test
    void shorterPrefixSortsFirst() {
        assertTrue(NaturalOrder.compare("mod", "mod2") < 0);
        assertTrue(NaturalOrder.compare("mod10", "mod") > 0);
    }

    @Test
    void sortsExplorerStyle() {
        List<String> names = new ArrayList<>(List.of("mod10", "B.yml", "mod2", "Mod1", "a.yml"));
        names.sort(NaturalOrder.comparator());
        assertEquals(List.of("a.yml", "B.yml", "Mod1", "mod2", "mod10"), names);
    }
}
