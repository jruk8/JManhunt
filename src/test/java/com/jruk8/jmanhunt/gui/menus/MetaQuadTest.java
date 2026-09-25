package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Meta quad layout over a fake target: name, description, live icon,
 * and author on the middle row with rename on the name right-click.
 */
class MetaQuadTest {

    private MetaQuad meta;

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static String textOf(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static MetaTarget target() {
        return new MetaTarget() {
            @Override
            public String id() {
                return "pack";
            }

            @Override
            public String name() {
                return "Pack";
            }

            @Override
            public String description() {
                return "Everything on";
            }

            @Override
            public Material item() {
                return Material.CHEST;
            }

            @Override
            public String author() {
                return null;
            }

            @Override
            public void patchName(String name) {
            }

            @Override
            public void patchDescription(String description) {
            }

            @Override
            public void patchItem(Material item) {
            }

            @Override
            public void patchAuthor(String author) {
            }

            @Override
            public Set<String> takenIds() {
                return new HashSet<>(Set.of("other"));
            }

            @Override
            public void rename(String newId) {
            }

            @Override
            public String displayName(String renamedId) {
                return "Pack";
            }
        };
    }

    @BeforeEach
    void setup() {
        MessageService messages = new MessageService();
        messages.reload(new MessagesConfig());
        meta = new MetaQuad(messages, null, null, null);
    }

    @Test
    void metaQuadShowsAllFourFields() {
        Menu menu = meta.menu(target(), null, id -> null);

        assertEquals(27, menu.layout().size());

        MenuButton name = menu.buttonAt(10);
        assertEquals(Material.NAME_TAG, name.material());
        assertEquals(List.of(plain("Current: Pack", NamedTextColor.GRAY),
                plain("Click to edit", NamedTextColor.GRAY),
                plain("Right-click to rename id", NamedTextColor.GRAY)), name.lore());
        assertNotNull(name.action());
        assertNotNull(name.rightAction());

        MenuButton description = menu.buttonAt(12);
        assertEquals(Material.BOOK, description.material());
        assertEquals("Current: Everything on", textOf(description.lore().get(0)));
        assertNotNull(description.action());
        assertNull(description.rightAction());

        MenuButton icon = menu.buttonAt(14);
        assertEquals(Material.CHEST, icon.material());
        assertEquals("Current: CHEST", textOf(icon.lore().get(0)));
        assertNotNull(icon.action());

        MenuButton author = menu.buttonAt(16);
        assertEquals(Material.PLAYER_HEAD, author.material());
        assertEquals("Current: Not set", textOf(author.lore().get(0)));
        assertNotNull(author.action());

        assertEquals(Material.PAPER, menu.buttonAt(22).material());
    }
}
