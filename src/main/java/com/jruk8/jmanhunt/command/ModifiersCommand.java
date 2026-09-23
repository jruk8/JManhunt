package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.function.Supplier;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
    /** Single permission node gating the modifiers GUI and chat fallback. */
    public static final String MODIFIERS_PERMISSION = "jmanhunt.modifiers";

    private final ConfigService config;
    private final MessageService messages;
    private final GuiService gui;
    private final Supplier<Menu> mainMenu;
    private final SoundService sounds;

    /**
     * @param gui menu opener, main menu supplier, and sounds; all are only
     *        touched on the bare-player path, so tests may pass nulls
     */
    public ModifiersCommand(ConfigService config, MessageService messages,
            GuiService gui, Supplier<Menu> mainMenu, SoundService sounds) {
        this.config = config;
        this.messages = messages;
        this.gui = gui;
        this.mainMenu = mainMenu;
        this.sounds = sounds;
    }

    /** Runs one modifiers action; args[0] is the action when present. */
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                gui.open(player, mainMenu.get());
                sounds.playNeutralSound(player);
                return true;
            }
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
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            messages.message(sender, "command.no-permission");
            return true;
        }
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
        applyModifierToggle(sender, name, value, false);
        return true;
    }

    /**
     * Flips every listed modifier at once with one summary line and one
     * bulk announce. Used by the GUI toggle-all button.
     */
    public boolean toggleAllModifiers(CommandSender sender, List<String> ids, boolean value) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            messages.message(sender, "command.no-permission");
            return true;
        }
        int flipped = 0;
        for (String id : ids) {
            if (applyModifierToggle(sender, id, value, true)) {
                flipped++;
            }
        }
        announceBulkToggle(sender, flipped, "modifiers", value);
        return true;
    }

    /**
     * Flips one modifier, optionally quiet for bulk runs. Returns false
     * when the modifier is unknown.
     */
    private boolean applyModifierToggle(CommandSender sender, String name, boolean value,
            boolean quiet) {
        if (!config.setModifierEnabled(name, value)) {
            return false;
        }
        if (quiet) {
            return true;
        }
        messages.message(sender, "modifiers.setmod-success",
                Map.of("name", name, "state", value ? "on" : "off"));
        ManhuntCommand.announceSettingChange(messages,
                config.getBoolean("settings.announce-config-changes", false),
                sender, "modifiers.toggle-announced", "modifier " + name, value ? "on" : "off");
        return true;
    }

    private boolean setPreset(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            messages.message(sender, "command.no-permission");
            return true;
        }
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
        applyPresetToggle(sender, id, value, false);
        return true;
    }

    /**
     * Flips every listed preset at once with one summary line and one
     * bulk announce. Used by the GUI toggle-all button.
     */
    public boolean toggleAllPresets(CommandSender sender, List<String> ids, boolean value) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            messages.message(sender, "command.no-permission");
            return true;
        }
        int flipped = 0;
        for (String id : ids) {
            if (applyPresetToggle(sender, id, value, true)) {
                flipped++;
            }
        }
        announceBulkToggle(sender, flipped, "presets", value);
        return true;
    }

    /**
     * Flips one preset, optionally quiet for bulk runs. Returns false
     * when the preset is unknown.
     */
    private boolean applyPresetToggle(CommandSender sender, String id, boolean value,
            boolean quiet) {
        if (!config.setPreset(id, value)) {
            return false;
        }
        if (quiet) {
            return true;
        }
        messages.message(sender, "modifiers.setpreset-success",
                Map.of("name", id, "state", value ? "on" : "off",
                        "count", String.valueOf(config.presetMembers(id).size())));
        ManhuntCommand.announceSettingChange(messages,
                config.getBoolean("settings.announce-config-changes", false),
                sender, "modifiers.toggle-announced", "preset " + id, value ? "on" : "off");
        return true;
    }

    private void announceBulkToggle(CommandSender sender, int count, String kind, boolean value) {
        String state = value ? "on" : "off";
        messages.message(sender, "modifiers.toggle-all-success",
                Map.of("count", String.valueOf(count), "kind", kind, "state", state));
        ManhuntCommand.announceSettingChange(messages,
                config.getBoolean("settings.announce-config-changes", false),
                sender, "modifiers.toggle-all-announced", count + " " + kind, state);
    }
}
