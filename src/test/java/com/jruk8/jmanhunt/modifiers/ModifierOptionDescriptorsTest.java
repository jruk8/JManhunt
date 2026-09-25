package com.jruk8.jmanhunt.modifiers;

import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Modifier option registry: every row key carries complete display
 * data, choices carry options, and numbers carry bounds.
 */
class ModifierOptionDescriptorsTest {

    @Test
    void registryCoversAllOptionRows() {
        assertEquals(Set.of("delay", "interval", "deviation", "interval-scope",
                "selection", "pick-count", "pick-scope", "pre-start", "chance",
                "chance-scope", "runs-on"), ModifierOptionDescriptors.keys());
    }

    @Test
    void everyKeyHasCompleteDisplayData() {
        for (String key : ModifierOptionDescriptors.keys()) {
            ModifierOptionDescriptors.Descriptor descriptor =
                    ModifierOptionDescriptors.byKey(key);
            assertNotNull(descriptor, key);
            assertFalse(descriptor.label().isBlank(), key);
            assertNotNull(descriptor.icon(), key);
            assertFalse(descriptor.description().isBlank(), key);
            assertFalse(descriptor.defaultText().isBlank(), key);
            assertFalse(descriptor.pathSuffix().isBlank(), key);
            if (descriptor.kind() == ModifierOptionDescriptors.Kind.CHOICE) {
                assertTrue(descriptor.options().size() >= 2, key);
            }
            if (descriptor.kind() == ModifierOptionDescriptors.Kind.NUMBER
                    || descriptor.kind() == ModifierOptionDescriptors.Kind.INTEGER) {
                assertNotNull(descriptor.allowed(), key);
            }
        }
    }

    @Test
    void typeNamesMatchSettingsVocabulary() {
        assertEquals("Number",
                ModifierOptionDescriptors.typeName(ModifierOptionDescriptors.Kind.NUMBER));
        assertEquals("Integer",
                ModifierOptionDescriptors.typeName(ModifierOptionDescriptors.Kind.INTEGER));
        assertEquals("Choice",
                ModifierOptionDescriptors.typeName(ModifierOptionDescriptors.Kind.CHOICE));
        assertEquals("Choice",
                ModifierOptionDescriptors.typeName(ModifierOptionDescriptors.Kind.TRIGGERS));
    }
}
