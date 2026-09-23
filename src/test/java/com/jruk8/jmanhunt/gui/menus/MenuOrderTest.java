package com.jruk8.jmanhunt.gui.menus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MenuOrderTest {

    @Test
    void sortsEnabledFirstThenFileOrderThenName() {
        Map<String, String> names = Map.of(
                "zebra", "Zulu",
                "apple", "&aApple",
                "mike", "<red>Mike</red>",
                "amber", "apple");
        Map<String, Integer> fileOrder = Map.of(
                "mike", 0, "zebra", 1, "apple", 2, "amber", 3);
        Set<String> enabled = Set.of("zebra", "amber");
        List<String> ids = new ArrayList<>(names.keySet());

        ids.sort(MenuOrder.modifiers(names::get, enabled::contains, fileOrder::get));

        assertEquals(List.of("zebra", "amber", "mike", "apple"), ids);
    }

    @Test
    void unknownOrderFallsBackToAlphabetical() {
        Map<String, String> names = Map.of(
                "zebra", "Zulu", "apple", "Apple", "mike", "Mike");
        Map<String, Integer> fileOrder = Map.of("mike", 0);
        List<String> ids = new ArrayList<>(names.keySet());

        ids.sort(MenuOrder.modifiers(names::get, id -> false, fileOrder::get));

        assertEquals(List.of("mike", "apple", "zebra"), ids);
    }

    @Test
    void presetOrderMatchesModifierShape() {
        Map<String, String> names = Map.of("b-preset", "Beta", "a-preset", "alpha");
        Map<String, Integer> fileOrder = Map.of("b-preset", 0, "a-preset", 1);
        Set<String> allOn = Set.of("a-preset");
        List<String> ids = new ArrayList<>(names.keySet());

        ids.sort(MenuOrder.presets(names::get, allOn::contains, fileOrder::get));

        assertEquals(List.of("a-preset", "b-preset"), ids);
    }
}
