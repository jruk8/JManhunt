package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Editor button shapes: value buttons carry exactly one Current line and
 * stay silent, action buttons carry plain lore and click centrally.
 */
class EditorButtonsTest {

    private MessageService messages;

    @BeforeEach
    void setup() {
        messages = new MessageService();
        messages.reload(new MessagesConfig());
    }

    @Test
    void valueButtonShowsOneCurrentLineAndStaysSilent() {
        MenuButton button = EditorButtons.valueButton(messages, Material.NAME_TAG,
                "Name", "Speedy", "Click to edit", player -> {});

        assertEquals(List.of("Current: Speedy", "Click to edit"), plain(button.lore()));
        assertEquals(MenuButton.SoundPolicy.SILENT, button.soundPolicy());
        assertFalse(button.glow());
    }

    @Test
    void valueButtonHonorsGlow() {
        MenuButton button = EditorButtons.valueButton(messages, Material.LEVER,
                "Enabled", "Enabled", "Click to toggle", true, player -> {});

        assertTrue(button.glow());
        assertEquals(MenuButton.SoundPolicy.SILENT, button.soundPolicy());
    }

    @Test
    void actionButtonShowsPlainLoreAndClicks() {
        MenuButton button = EditorButtons.actionButton(messages, Material.ANVIL,
                "Rename Id", List.of("Current id: vanilla-plus"), player -> {});

        assertEquals(List.of("Current id: vanilla-plus"), plain(button.lore()));
        assertEquals(MenuButton.SoundPolicy.CLICK, button.soundPolicy());
    }

    @Test
    void actionButtonNeverAddsACurrentPrefix() {
        MenuButton button = EditorButtons.actionButton(messages, Material.LOOM,
                "Export", List.of("Copy a share string", "Click to copy"), player -> {});

        assertEquals(List.of("Copy a share string", "Click to copy"), plain(button.lore()));
    }

    private static List<String> plain(List<Component> lore) {
        return lore.stream()
                .map(PlainTextComponentSerializer.plainText()::serialize)
                .toList();
    }
}
