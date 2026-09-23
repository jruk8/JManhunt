package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Modifier toggles ({@code /manhunt modifiers ...}). Bare runs print
 * every modifier and preset with its state; setmod flips one bundle
 * and setpreset flips everything inside a preset at once. Players
 * and console alike; no match needs to run.
 */
public final class ModifiersCommand {
    private final ConfigService config;
    private final MessageService messages;

    public ModifiersCommand(ConfigService config, MessageService messages) {
        this.config = config;
        this.messages = messages;
    }

    /** Runs one modifiers action; args[0] is the action when present. */
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Phase 5 opens the menu GUI here for players; console keeps the text list.
            return list(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setmod" -> setModifier(sender, args);
            case "setpreset" -> setPreset(sender, args);
            default -> {
                messages.message(sender, "modifiers.usage");
                yield true;
            }
        };
    }

    /** Modifier ids, alphabetically, for tab completion. */
    public List<String> modifierNameOptions() {
        List<String> names = new ArrayList<>(config.modifierNames());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Preset ids, alphabetically, for tab completion. */
    public List<String> presetIdOptions() {
        List<String> names = new ArrayList<>(config.presetNames());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Parses a toggle state; strict true/false like the config drill. Pure for tests. */
    static Boolean parseState(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase("true")) {
            return true;
        }
        if (trimmed.equalsIgnoreCase("false")) {
            return false;
        }
        return null;
    }

    private boolean list(CommandSender sender) {
        Set<String> names = config.modifierNames();
        if (names.isEmpty()) {
            messages.message(sender, "modifiers.list-empty");
            return true;
        }
        messages.message(sender, "modifiers.list-header");
        for (String name : names) {
            messages.message(sender, config.modifierEnabled(name)
                    ? "modifiers.list-entry-on" : "modifiers.list-entry-off", Map.of("name", name));
        }
        Set<String> presets = config.presetNames();
        if (!presets.isEmpty()) {
            messages.message(sender, "modifiers.list-presets-header");
            for (String id : presets) {
                messages.message(sender, config.presetEnabled(id)
                        ? "modifiers.list-entry-on" : "modifiers.list-entry-off", Map.of("name", id));
            }
        }
        return true;
    }

    private boolean setModifier(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.message(sender, "modifiers.setmod-usage");
            return true;
        }
        String name = args[1];
        if (!config.modifierNames().contains(name)) {
            messages.message(sender, "modifiers.unknown-modifier",
                    Map.of("name", name, "valid", ListFormatter.joinOxford(modifierNameOptions())));
            return true;
        }
        Boolean value = parseState(args[2]);
        if (value == null) {
            messages.message(sender, "modifiers.invalid-state");
            return true;
        }
        config.setModifierEnabled(name, value);
        messages.message(sender, "modifiers.setmod-success",
                Map.of("name", name, "state", value ? "on" : "off"));
        return true;
    }

    private boolean setPreset(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messages.message(sender, "modifiers.setpreset-usage");
            return true;
        }
        String id = args[1];
        if (!config.presetNames().contains(id)) {
            messages.message(sender, "modifiers.unknown-preset",
                    Map.of("name", id, "valid", ListFormatter.joinOxford(presetIdOptions())));
            return true;
        }
        Boolean value = parseState(args[2]);
        if (value == null) {
            messages.message(sender, "modifiers.invalid-state");
            return true;
        }
        config.setPreset(id, value);
        messages.message(sender, "modifiers.setpreset-success",
                Map.of("name", id, "state", value ? "on" : "off",
                        "count", String.valueOf(config.presetMembers(id).size())));
        return true;
    }
}
