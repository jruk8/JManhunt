package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import com.jruk8.jmanhunt.modifiers.config.ModifiersConfig;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
                new ConfigService(null, new ModifierStore(config, log)), null, null, null, null);

        assertEquals(List.of("Alpha", "mike", "zeta"), command.modifierNameOptions());
        assertEquals(List.of("apple", "zulu"), command.presetIdOptions());
    }

    @Test
    void togglesWithoutPermissionChangeNothing() {
        ModifiersConfig config = new ModifiersConfig();
        config.getModifiers().put("beef", new ModifierEntry());
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService service = new ConfigService(null, new ModifierStore(config, log));
        MessageService messages = new MessageService();
        messages.reload(new YamlConfiguration());
        ModifiersCommand command = new ModifiersCommand(service, messages, null, null, null);
        FakeSender sender = new FakeSender();
        boolean before = service.modifierEnabled("beef");

        assertTrue(command.execute(sender, new String[]{"setmod", "beef", "true"}));
        assertTrue(command.execute(sender, new String[]{"setpreset", "speed", "true"}));

        Component denied = messages.component("command.no-permission");
        assertEquals(List.of(denied, denied), sender.received);
        assertEquals(before, service.modifierEnabled("beef"));
    }

    /** Records Adventure messages; every permission check fails. */
    private static final class FakeSender implements CommandSender {
        private final List<Component> received = new ArrayList<>();

        @Override
        public void sendMessage(String message) {
        }

        @Override
        public void sendMessage(String[] messages) {
        }

        @Override
        public void sendMessage(UUID sender, String message) {
        }

        @Override
        public void sendMessage(UUID sender, String[] messages) {
        }

        @Override
        public Server getServer() {
            return null;
        }

        @Override
        public String getName() {
            return "tester";
        }

        @Override
        public Spigot spigot() {
            return null;
        }

        @Override
        public boolean isPermissionSet(String name) {
            return false;
        }

        @Override
        public boolean isPermissionSet(Permission perm) {
            return false;
        }

        @Override
        public boolean hasPermission(String name) {
            return false;
        }

        @Override
        public boolean hasPermission(Permission perm) {
            return false;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return null;
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
        }

        @Override
        public void recalculatePermissions() {
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return Set.of();
        }

        @Override
        public boolean isOp() {
            return false;
        }

        @Override
        public void setOp(boolean value) {
        }

        @Override
        public void sendMessage(Component message) {
            received.add(message);
        }

        @Override
        public Component name() {
            return Component.text("tester");
        }

        @Override
        public Audience filterAudience(Predicate<? super Audience> filter) {
            return this;
        }

        @Override
        public void forEachAudience(Consumer<? super Audience> action) {
            action.accept(this);
        }
    }
}
