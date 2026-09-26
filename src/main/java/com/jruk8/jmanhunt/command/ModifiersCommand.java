package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.function.Function;
import com.jruk8.jmanhunt.modifiers.ModifierCodec;
import com.jruk8.jmanhunt.modifiers.ModifierNames;
import com.jruk8.jmanhunt.modifiers.config.ModifierEntry;
import com.jruk8.jmanhunt.modifiers.config.ModifierPreset;
import java.util.Optional;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Arrays;
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
    private final Function<Player, Menu> mainMenu;
    private final SoundService sounds;

    /**
     * @param gui menu opener, main menu factory, and sounds; all are only
     *        touched on the bare-player path, so tests may pass nulls
     */
    public ModifiersCommand(ConfigService config, MessageService messages,
            GuiService gui, Function<Player, Menu> mainMenu, SoundService sounds) {
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
                gui.clearOverrideLobby(player);
                gui.open(player, mainMenu.apply(player));
                sounds.playNeutralSound(player);
                return true;
            }
            return list(sender);
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "setmod" -> setModifier(sender, args);
            case "setpreset" -> setPreset(sender, args);
            case "export" -> exportCommand(sender, args);
            case "import" -> importCommand(sender, args);
            case "create" -> createCommand(sender, args);
            default -> {
                messages.message(sender, "modifiers.usage");
                yield true;
            }
        };
    }

    /** Parses the modifier-or-preset type word; null when invalid. Pure for tests. */
    static String parseEntryType(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("modifier") || normalized.equals("preset")) {
            return normalized;
        }
        return null;
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
                config.getBoolean("settings.server.announce-config-changes", false),
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
                config.getBoolean("settings.server.announce-config-changes", false),
                sender, "modifiers.toggle-announced", "preset " + id, value ? "on" : "off");
        return true;
    }

    private boolean exportCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || parseEntryType(args[1]) == null) {
            messages.message(sender, "modifiers.export-usage");
            return true;
        }
        exportEntry(sender, parseEntryType(args[1]), args[2]);
        return true;
    }

    /**
     * Sends a click-to-copy share string for one entry. Used by the
     * export loom as well as the CLI. Returns false when unknown.
     */
    public boolean exportEntry(CommandSender sender, String type, String id) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            messages.message(sender, "command.no-permission");
            return false;
        }
        String payload;
        String name;
        if (type.equals("preset")) {
            ModifierPreset preset =
                    config.modifiers().presetEntry(id);
            if (preset == null) {
                messages.message(sender, "modifiers.unknown-preset",
                        Map.of("name", id, "valid", ListFormatter.joinOxford(presetIdOptions())));
                return false;
            }
            payload = ModifierCodec.exportPreset(id, preset);
            name = config.modifiers().presetName(id);
        } else {
            ModifierEntry entry =
                    config.modifiers().modifierEntry(id);
            if (entry == null) {
                messages.message(sender, "modifiers.unknown-modifier",
                        Map.of("name", id, "valid", ListFormatter.joinOxford(modifierNameOptions())));
                return false;
            }
            payload = ModifierCodec.exportModifier(id, entry);
            name = config.modifiers().metaName(id);
        }
        sender.sendMessage(messages.component("modifiers.exported",
                        Map.of("type", type, "name", name))
                .clickEvent(ClickEvent.copyToClipboard(payload)));
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
        return true;
    }

    private boolean importCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || parseEntryType(args[1]) == null) {
            messages.message(sender, "modifiers.import-usage");
            return true;
        }
        importEntry(sender, parseEntryType(args[1]), args[2]);
        return true;
    }

    /**
     * Imports one share string, bumping the name when the id is taken.
     * Used by the import button as well as the CLI. Returns false when
     * the payload is corrupt, off-schema, or of the wrong kind.
     */
    public boolean importEntry(CommandSender sender, String type, String payload) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            messages.message(sender, "command.no-permission");
            return false;
        }
        Optional<ModifierCodec.Imported> decoded =
                ModifierCodec.decode(payload);
        if (decoded.isEmpty()) {
            messages.message(sender, "modifiers.import-failed");
            return false;
        }
        ModifierCodec.Imported imported = decoded.get();
        boolean isPreset = imported.kind()
                == ModifierCodec.Kind.PRESET;
        if (!type.equals(isPreset ? "preset" : "modifier")) {
            messages.message(sender, "modifiers.import-failed");
            return false;
        }
        String finalId = isPreset
                ? config.modifiers().addPreset(imported.id(), imported.preset())
                : config.modifiers().addModifier(imported.id(), imported.entry());
        String name = isPreset
                ? config.modifiers().presetName(finalId)
                : config.modifiers().metaName(finalId);
        messages.message(sender, "modifiers.imported", Map.of("name", name));
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
        return true;
    }

    private boolean createCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            messages.message(sender, "command.no-permission");
            return true;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(rest, config.modifierNames());
        if (!result.success()) {
            messages.message(sender, result.messageKey(), result.params());
            return true;
        }
        ModifierCreateArgs.Plan plan = result.plan();
        String slug = ModifierNames.kebab(plan.name());
        String finalId;
        if (plan.preset()) {
            finalId = config.modifiers().addPreset(slug.isEmpty() ? "preset" : slug, plan.toPreset());
        } else {
            finalId = config.modifiers().addModifier(slug.isEmpty() ? "modifier" : slug, plan.toEntry());
        }
        String display = plan.preset()
                ? config.modifiers().presetName(finalId)
                : config.modifiers().metaName(finalId);
        messages.message(sender, "modifiers.create-success",
                Map.of("type", plan.preset() ? "preset" : "modifier", "name", display));
        for (String warning : result.warnings()) {
            messages.message(sender, "modifiers.create-command-warning", Map.of("warning", warning));
        }
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
        return true;
    }

    private void announceBulkToggle(CommandSender sender, int count, String kind, boolean value) {
        String state = value ? "on" : "off";
        messages.message(sender, "modifiers.toggle-all-success",
                Map.of("count", String.valueOf(count), "kind", kind, "state", state));
        ManhuntCommand.announceSettingChange(messages,
                config.getBoolean("settings.server.announce-config-changes", false),
                sender, "modifiers.toggle-all-announced", count + " " + kind, state);
    }
}
