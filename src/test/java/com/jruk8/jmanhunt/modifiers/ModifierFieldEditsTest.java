package com.jruk8.jmanhunt.modifiers;

import java.util.Set;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifierFieldEditsTest {

    @Test
    void nameTrimsAndRejectsBlank() {
        assertTrue(ModifierFieldEdits.name("  Beef ").ok());
        assertEquals("Beef", ModifierFieldEdits.name("  Beef ").value());
        assertFalse(ModifierFieldEdits.name("   ").ok());
    }

    @Test
    void itemParsesLenientlyRejectsAirAndBogus() {
        assertEquals(Material.COOKED_BEEF, ModifierFieldEdits.item("cooked beef").value());
        assertEquals(Material.STONE, ModifierFieldEdits.item("minecraft:stone").value());
        assertFalse(ModifierFieldEdits.item("air").ok());
        assertFalse(ModifierFieldEdits.item("bogus_item").ok());
        assertFalse(ModifierFieldEdits.item("").ok());
    }

    @Test
    void idChecksShapeAndTaken() {
        assertTrue(ModifierFieldEdits.id("good-id_1", Set.of("taken")).ok());
        assertFalse(ModifierFieldEdits.id("taken", Set.of("taken")).ok());
        assertFalse(ModifierFieldEdits.id("has space", Set.of()).ok());
        assertFalse(ModifierFieldEdits.id("-lead", Set.of()).ok());
        assertFalse(ModifierFieldEdits.id("", Set.of()).ok());
    }

    @Test
    void numberChecksParseAndRange() {
        assertEquals(0.5, ModifierFieldEdits.number("Chance", "0.5", 0.0, 1.0).value());
        assertFalse(ModifierFieldEdits.number("Chance", "nope", 0.0, 1.0).ok());
        assertFalse(ModifierFieldEdits.number("Chance", "2", 0.0, 1.0).ok());
        assertFalse(ModifierFieldEdits.number("Chance", "NaN", 0.0, 1.0).ok());
        assertTrue(ModifierFieldEdits.number("Interval", "30", 0.0, null).ok());
        assertFalse(ModifierFieldEdits.number("Interval", "-1", 0.0, null).ok());
    }

    @Test
    void wholeChecksParseAndRange() {
        assertEquals(100L, ModifierFieldEdits.whole("Delay", "100", 0L, null).value());
        assertFalse(ModifierFieldEdits.whole("Delay", "1.5", 0L, null).ok());
        assertFalse(ModifierFieldEdits.whole("Delay", "-1", 0L, null).ok());
        assertFalse(ModifierFieldEdits.whole("Count", "0", 1L, null).ok());
    }
}
