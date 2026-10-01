package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.ModifiersCommand;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.dialog.SettingDialog;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.MessagesConfig;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Meta quad layout over a fake target: name, description, live icon,
 * and author on the middle row with rename on the name right-click.
 */
class MetaQuadTest {

    private MetaQuad meta;
    private MessageService messages;
    private MessagesConfig texts;

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** Expected Current line: gray prefix with a white value. */
    private Component currentLine(String value) {
        return messages.nonItalic(messages.parse("<gray>Current: <white>" + value));
    }

    /** Expected id line: gray prefix with a white id. */
    private Component idLine(String value) {
        return messages.nonItalic(messages.parse("<gray>Id: <white>" + value));
    }

    private static String textOf(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static MetaTarget target() {
        return new FakeTarget();
    }

    /** Fixed-value target fake for the quad layout test. */
    private static final class FakeTarget implements MetaTarget {
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
    }

    @BeforeEach
    void setup() {
        messages = new MessageService();
        texts = new MessagesConfig();
        messages.reload(texts);
        meta = new MetaQuad(
                new MetaQuad.MetaTexts(messages, texts.getModifiersGui(), texts.getModifiers(),
                        texts.getCommand(), null),
                null, null);
    }

    @Test
    void metaQuadShowsAllFourFields() {
        Menu menu = meta.menu(target(), null, id -> null);

        assertEquals(27, menu.layout().size());

        MenuButton name = menu.buttonAt(10);
        assertEquals(Material.NAME_TAG, name.material());
        assertEquals(List.of(currentLine("Pack"), idLine("pack"),
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
        assertNotNull(author.metaTweak());

        assertEquals(Material.PAPER, menu.buttonAt(22).material());
    }

    @Test
    @SuppressWarnings("unchecked")
    void idRenameMessageShowsIdNotDisplayName() {
        SettingDialog dialogs = mock(SettingDialog.class);
        MetaQuad quad = new MetaQuad(
                new MetaQuad.MetaTexts(messages, texts.getModifiersGui(), texts.getModifiers(),
                        texts.getCommand(), mock(SoundService.class)),
                mock(GuiService.class), dialogs);
        Menu menu = quad.menu(target(), null, id -> null);
        Player player = mock(Player.class);
        when(player.hasPermission(ModifiersCommand.MODIFIERS_PERMISSION)).thenReturn(true);

        menu.buttonAt(10).rightAction().accept(player);

        ArgumentCaptor<Consumer<String>> submit = ArgumentCaptor.forClass(Consumer.class);
        verify(dialogs).prompt(eq(player), anyString(), eq("pack"), anyList(),
                submit.capture(), any(Runnable.class));
        submit.getValue().accept("new-pack");

        ArgumentCaptor<Component> sent = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(sent.capture());
        String text = textOf(sent.getValue());
        assertTrue(text.contains("new-pack"), text);
        assertFalse(text.contains("Pack"), text);
    }

    @Test
    void iconEditShowsSpriteWhileOtherFieldsStayPlain() {
        SettingDialog dialogs = mock(SettingDialog.class);
        MetaQuad quad = new MetaQuad(
                new MetaQuad.MetaTexts(messages, texts.getModifiersGui(), texts.getModifiers(),
                        texts.getCommand(), mock(SoundService.class)),
                mock(GuiService.class), dialogs);
        Menu menu = quad.menu(target(), null, id -> null);
        Player player = mock(Player.class);

        menu.buttonAt(14).action().accept(player);
        menu.buttonAt(12).action().accept(player);

        verify(dialogs).promptWithIcon(eq(player), anyString(), eq("CHEST"), anyList(),
                any(), any());
        verify(dialogs).prompt(eq(player), anyString(), eq("Everything on"), anyList(),
                any(), any());
    }
}
