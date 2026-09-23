package com.jruk8.jmanhunt.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jruk8.jmanhunt.message.MessageService;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

class GuiTextsTest {

    private final MessageService messages = new MessageService();

    private static Component plain(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    @Test
    void titleKeepsDefaultColorAndNeverBlank() {
        Component expected = Component.text("Modifiers").decoration(TextDecoration.ITALIC, false);
        Component blank = Component.text(" ").decoration(TextDecoration.ITALIC, false);
        assertEquals(expected, GuiTexts.title(messages, "Modifiers"));
        assertEquals(blank, GuiTexts.title(messages, ""));
        assertEquals(blank, GuiTexts.title(messages, null));
    }

    @Test
    void nameHonorsInlineColorAndFallsBackWhenBlank() {
        assertEquals(plain("Beef", NamedTextColor.GREEN), GuiTexts.name(messages, "&aBeef", "Mod"));
        assertEquals(plain("Pie", NamedTextColor.RED),
                GuiTexts.name(messages, "<red>Pie</red>", "Mod"));
        assertEquals(plain("Mod", NamedTextColor.WHITE), GuiTexts.name(messages, "  ", "Mod"));
    }

    @Test
    void brokenMarkupRendersLiterally() {
        assertEquals(plain("<red", NamedTextColor.WHITE), GuiTexts.name(messages, "<red", "Mod"));
        assertEquals(plain("oops", NamedTextColor.RED), GuiTexts.name(messages, "<red>oops", "Mod"));
    }

    @Test
    void loreSplitsLinesAndAppendsAuthor() {
        List<Component> lore = GuiTexts.loreWithAuthor(messages, "line1\nline2", "Okaeri");

        assertEquals(List.of(plain("line1", NamedTextColor.GRAY),
                plain("line2", NamedTextColor.GRAY), Component.text(" "),
                plain("by Okaeri", NamedTextColor.GRAY)), lore);
    }

    @Test
    void loreSplitsLiteralBreaksAndDropsTrailingEmpties() {
        assertEquals(List.of(plain("a", NamedTextColor.GRAY), plain("b", NamedTextColor.GRAY)),
                GuiTexts.lore(messages, "a\\nb\n"));

        List<Component> middle = GuiTexts.lore(messages, "a\n\nb");
        assertEquals(3, middle.size());
        assertEquals(plain("a", NamedTextColor.GRAY), middle.get(0));
        assertEquals(plain("b", NamedTextColor.GRAY), middle.get(2));
    }

    @Test
    void loreOmitsSeparatorWithoutDescription() {
        assertEquals(List.of(plain("by Okaeri", NamedTextColor.GRAY)),
                GuiTexts.loreWithAuthor(messages, "  ", "Okaeri"));
        assertEquals(List.of(), GuiTexts.lore(messages, null));
    }

    @Test
    void sortKeyStripsTagsAndLowercases() {
        assertEquals("beef pie", GuiTexts.sortKey("&aBeef <red>Pie</red>"));
        assertEquals("", GuiTexts.sortKey(null));
    }
}
