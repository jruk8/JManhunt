package com.jruk8.jmanhunt;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingValueParserTest {

    @Test
    void parsesBoolean() {
        List<Object> applied = new ArrayList<>();
        assertTrue(SettingValueParser.parse(Boolean.TRUE, "false", (oldValue, newValue) -> applied.add(newValue)));
        assertEquals(List.of(Boolean.FALSE), applied);
    }

    @Test
    void rejectsInvalidBoolean() {
        assertFalse(SettingValueParser.parse(Boolean.TRUE, "not-a-bool", (oldValue, newValue) -> { }));
    }

    @Test
    void parsesInteger() {
        List<Object> applied = new ArrayList<>();
        assertTrue(SettingValueParser.parse(42, "137", (oldValue, newValue) -> applied.add(newValue)));
        assertEquals(List.of(137), applied);
    }

    @Test
    void rejectsInvalidInteger() {
        assertFalse(SettingValueParser.parse(42, "abc", (oldValue, newValue) -> { }));
    }

    @Test
    void parsesDouble() {
        List<Object> applied = new ArrayList<>();
        assertTrue(SettingValueParser.parse(10.0, "25.5", (oldValue, newValue) -> applied.add(newValue)));
        assertEquals(List.of(25.5), applied);
    }

    @Test
    void rejectsInvalidDouble() {
        assertFalse(SettingValueParser.parse(10.0, "oops", (oldValue, newValue) -> { }));
    }

    @Test
    void parsesFloat() {
        List<Object> applied = new ArrayList<>();
        assertTrue(SettingValueParser.parse(1.0f, "2.5", (oldValue, newValue) -> applied.add(newValue)));
        assertEquals(List.of(2.5f), applied);
    }

    @Test
    void parsesStringVerbatim() {
        List<Object> applied = new ArrayList<>();
        assertTrue(SettingValueParser.parse(
                "some-string", "FORCE_START", (oldValue, newValue) -> applied.add(newValue)));
        assertEquals(List.of("FORCE_START"), applied);
    }

    @Test
    void trimsStringInput() {
        List<Object> applied = new ArrayList<>();
        assertTrue(SettingValueParser.parse("a", "  hello  ", (oldValue, newValue) -> applied.add(newValue)));
        assertEquals(List.of("hello"), applied);
    }

    @Test
    void rejectsNullRawInput() {
        assertFalse(SettingValueParser.parse("x", null, (oldValue, newValue) -> { }));
    }

    @Test
    void rejectsUnsupportedTypes() {
        assertFalse(SettingValueParser.parse(List.of("a"), "b", (oldValue, newValue) -> { }));
    }
}