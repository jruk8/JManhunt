package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifiersCommandTest {

    @Test
    void parseStateAcceptsStrictBooleans() {
        assertTrue(ModifiersCommand.parseState("true"));
        assertTrue(ModifiersCommand.parseState("  TRUE  "));
        assertFalse(ModifiersCommand.parseState("false"));
        assertFalse(ModifiersCommand.parseState("False"));
    }

    @Test
    void parseStateRejectsAnythingElse() {
        assertNull(ModifiersCommand.parseState("on"));
        assertNull(ModifiersCommand.parseState("off"));
        assertNull(ModifiersCommand.parseState("1"));
        assertNull(ModifiersCommand.parseState("yes"));
        assertNull(ModifiersCommand.parseState(""));
        assertNull(ModifiersCommand.parseState(null));
    }

    @Test
    void optionsSortCaseInsensitively() {
        ModifiersConfig config = new ModifiersConfig();
        for (String name : List.of("zeta", "Alpha", "mike")) {
            config.getModifiers().put(name, new ModifierEntry());
        }
        for (String id : List.of("zulu", "apple")) {
            config.getPresets().put(id, new ModifierPreset());
        }
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        // Nulls are never touched: options read the store, messages unused.
        ModifiersCommand command = new ModifiersCommand(
                new ConfigService(null, new ModifierStore(config, log)), null);

        assertEquals(List.of("Alpha", "mike", "zeta"), command.modifierNameOptions());
        assertEquals(List.of("apple", "zulu"), command.presetIdOptions());
    }
}
