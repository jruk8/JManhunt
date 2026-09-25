package com.jruk8.jmanhunt.command;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandValidationTest {

    private static final Set<String> ROOTS = Set.of("give", "effect");
    private static final Predicate<String> KNOWN_APPLE =
            token -> token.equalsIgnoreCase("golden_apple");
    private static final Set<String> NAMES = Set.of("golden_apple", "diamond_sword");

    @Test
    void flagOffSkipsRootAndItemChecksButKeepsPlaceholderErrors() {
        assertTrue(CommandValidation
                .validateLine("asd asd asd asd", false, ROOTS, KNOWN_APPLE, NAMES).isEmpty());
        assertTrue(CommandValidation
                .validateLine("give <p> gulden_apple", false, ROOTS, KNOWN_APPLE, NAMES).isEmpty());
        assertTrue(CommandValidation
                .validateLine("give <p", false, ROOTS, KNOWN_APPLE, NAMES).isPresent());
        assertTrue(CommandValidation
                .validateLine("", false, ROOTS, KNOWN_APPLE, NAMES).isPresent());
    }

    @Test
    void flagOnRunsPlaceholderRootAndItemChecksInOrder() {
        assertEquals(Optional.of("Unknown command 'asd'."),
                CommandValidation.validateLine("asd asd asd asd", true, ROOTS, KNOWN_APPLE,
                        NAMES));
        assertEquals(Optional.of("Unknown item 'gulden_apple'. Did you mean 'golden_apple'?"),
                CommandValidation.validateLine("give <p> gulden_apple", true, ROOTS, KNOWN_APPLE,
                        NAMES));
        assertEquals(Optional.of("Unclosed '<' tag in command."),
                CommandValidation.validateLine("give <p", true, ROOTS, KNOWN_APPLE, NAMES));
        assertTrue(CommandValidation
                .validateLine("give <p> golden_apple", true, ROOTS, KNOWN_APPLE, NAMES).isEmpty());
    }

    @Test
    void warningsStayUntouchedByValidation() {
        assertEquals(List.of("Unknown tag '<bogus>', left untouched at runtime."),
                CommandSyntax.warnings("give <bogus> apple"));
    }
}
