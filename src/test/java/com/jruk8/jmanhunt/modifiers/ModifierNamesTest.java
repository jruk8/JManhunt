package com.jruk8.jmanhunt.modifiers;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModifierNamesTest {

    @Test
    void kebabDerivesLowercaseIds() {
        assertEquals("my-modifier", ModifierNames.kebab("My Modifier"));
        assertEquals("hello-world", ModifierNames.kebab("Hello, World!"));
        assertEquals("a-b", ModifierNames.kebab("  a  b  "));
        assertEquals("gear-dice-2", ModifierNames.kebab("Gear Dice 2"));
        assertEquals("", ModifierNames.kebab("!!!"));
        assertEquals("", ModifierNames.kebab(null));
    }

    @Test
    void uniqueNameAppendsNextAvailableCounter() {
        assertEquals("Gear", ModifierNames.uniqueName("Gear", Set.of()));
        assertEquals("Gear 2", ModifierNames.uniqueName("Gear", Set.of("Gear")));
        assertEquals("Gear 3", ModifierNames.uniqueName("Gear", Set.of("Gear", "Gear 2")));
        assertEquals("Gear 2", ModifierNames.uniqueName("Gear", Set.of("Gear", "Gear 3")));
    }
}
