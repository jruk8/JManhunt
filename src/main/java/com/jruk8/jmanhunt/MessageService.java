package com.jruk8.jmanhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public final class MessageService {
    private FileConfiguration messages;
    private String format = "minimessage";

    public void reload(FileConfiguration configuration, String textFormat) {
        messages = configuration;
        format = textFormat;
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    public Component component(String key, Map<String, String> values) {
        String raw = messages.getString(key, key).replace("{prefix}", messages.getString("prefix", ""));
        for (Map.Entry<String, String> entry : values.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return parse(raw);
    }

    public Component parse(String raw) {
        return "legacy".equalsIgnoreCase(format)
                ? LegacyComponentSerializer.legacyAmpersand().deserialize(raw)
                : MiniMessage.miniMessage().deserialize(raw);
    }

    public String formatPlaceholder(String raw) {
        if ("legacy".equalsIgnoreCase(format)) {
            return LegacyComponentSerializer.legacySection().serialize(
                    LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
        }
        return LegacyComponentSerializer.legacySection().serialize(
                MiniMessage.miniMessage().deserialize(raw));
    }

    public String addSeparators(String text) {
        String separator = string("game.separator", "");
        return separator + "\n" + text + "\n" + separator;
    }

    public Component nonItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public String string(String path, String fallback) {
        return messages.getString(path, fallback);
    }

    public java.util.List<String> strings(String path) {
        return messages.getStringList(path);
    }

    public void broadcast(String key) { Bukkit.broadcast(component(key)); }
    public void broadcast(String key, Map<String, String> values) { Bukkit.broadcast(component(key, values)); }

    public void message(CommandSender sender, String key) { sender.sendMessage(component(key)); }
    public void message(CommandSender sender, String key, Map<String, String> values) {
        sender.sendMessage(component(key, values));
    }
}
