package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.config.SettingType;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Per-lobby overrides ({@code /manhunt override <lobby> ...}). Settings
 * drill exactly like {@code /manhunt config} but read effective values
 * and write overrides; modifiers force one lobby's enabled flags.
 * Players and console alike; no match needs to run.
 */
public final class OverrideCommand {

    private final OverrideService overrides;
    private final ConfigService config;
    private final MessageService messages;
    private final SettingFeedback feedback;
    private final SoundService sounds;

    /**
     * @param sounds get-path blips, null only in unit tests that never
     *        address players
     */
    public OverrideCommand(OverrideService overrides, ConfigService config,
            MessageService messages, SettingFeedback feedback, SoundService sounds) {
        this.overrides = overrides;
        this.config = config;
        this.messages = messages;
        this.feedback = feedback;
        this.sounds = sounds;
    }

    /** Runs one override action; args[0] is "override". */
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return usage(sender);
        }
        OptionalInt lobby = OverrideService.parseLobbyId(args[1]);
        if (lobby.isEmpty()) {
            messages.message(sender, "manhunt.override-invalid-lobby",
                    Map.of("lobby", args[1]));
            return true;
        }
        if (args.length < 3) {
            return usage(sender);
        }
        return switch (args[2].toLowerCase(Locale.ROOT)) {
            case "settings" -> settings(sender, lobby.getAsInt(), tail(args, 3));
            case "modifiers" -> modifiers(sender, lobby.getAsInt(), tail(args, 3));
            case "clear" -> args.length == 3
                    ? clearLobby(sender, lobby.getAsInt()) : usage(sender);
            default -> usage(sender);
        };
    }

    private boolean settings(CommandSender sender, int lobby, String[] rest) {
        if (rest.length < 1) {
            return usage(sender);
        }
        List<String> segments = tailList(rest, 1);
        return switch (rest[0].toLowerCase(Locale.ROOT)) {
            case "get" -> settingsGet(sender, lobby, segments);
            case "set" -> settingsSet(sender, lobby, segments);
            case "clear" -> settingsClear(sender, lobby, segments);
            default -> usage(sender);
        };
    }

    private boolean settingsGet(CommandSender sender, int lobby, List<String> segments) {
        if (segments.isEmpty()) {
            return usage(sender);
        }
        DrillResolve resolved = resolveDrill(lobby, segments);
        if (resolved == null) {
            return invalid(sender);
        }
        if (!resolved.remainder().isEmpty()) {
            return usage(sender);
        }
        if (SettingRegistry.byPath(resolved.path()) != null) {
            showSetting(sender, lobby, resolved.path());
            return true;
        }
        if (isIndexPath(lobby, resolved.path())) {
            showSetting(sender, lobby, resolved.path());
            return true;
        }
        if (SettingRegistry.isListPath(resolved.path())) {
            showList(sender, lobby, resolved.path());
            return true;
        }
        if (!resolved.section()) {
            return usage(sender);
        }
        SettingRegistry.DrillChildren listing = SettingRegistry.children(resolved.path());
        Map<String, String> entries = new LinkedHashMap<>();
        for (String section : listing.sections()) {
            entries.put(section, "");
        }
        for (String leaf : listing.leaves()) {
            entries.put(leaf, ": " + ConfigService.displayValue(
                    overrides.effectiveValue(lobby, resolved.path() + "." + leaf)));
        }
        listEntries(sender, "lobby." + lobby + "." + resolved.path(), entries);
        neutralSound(sender);
        return true;
    }

    private boolean settingsSet(CommandSender sender, int lobby, List<String> segments) {
        if (segments.isEmpty()) {
            return usage(sender);
        }
        DrillResolve resolved = resolveDrill(lobby, segments);
        if (resolved == null) {
            return invalid(sender);
        }
        if (SettingRegistry.byPath(resolved.path()) != null) {
            return setScalar(sender, lobby, resolved.path(), resolved.remainder());
        }
        if (isIndexPath(lobby, resolved.path())) {
            return setIndex(sender, lobby, resolved.path(), resolved.remainder());
        }
        if (SettingRegistry.isListPath(resolved.path())) {
            return setList(sender, lobby, resolved.path(), resolved.remainder());
        }
        return usage(sender);
    }

    private boolean settingsClear(CommandSender sender, int lobby, List<String> segments) {
        if (segments.isEmpty()) {
            return usage(sender);
        }
        DrillResolve resolved = resolveDrill(lobby, segments);
        if (resolved == null) {
            return invalid(sender);
        }
        if (!resolved.remainder().isEmpty()) {
            return usage(sender);
        }
        int removed = overrides.clearOverrides(lobby, resolved.path());
        feedback.overrideCleared(sender, lobby, resolved.path(), removed);
        return true;
    }

    private boolean setScalar(CommandSender sender, int lobby, String path, List<String> values) {
        if (values.isEmpty()) {
            return usage(sender);
        }
        SettingDescriptor descriptor = config.describe(path);
        boolean freeform = descriptor != null && descriptor.type() == SettingType.STRING;
        String raw;
        if (freeform) {
            raw = String.join(" ", values);
        } else if (values.size() > 1) {
            return usage(sender);
        } else {
            raw = values.get(0);
        }
        ConfigService.SetOutcome outcome = overrides.setSettingOverride(lobby, path, raw);
        if (!outcome.ok()) {
            feedback.failed(sender, outcome);
            return true;
        }
        feedback.overrideScalarUpdated(sender, lobby, path, outcome);
        return true;
    }

    private boolean setIndex(CommandSender sender, int lobby, String path, List<String> values) {
        if (values.isEmpty()) {
            return usage(sender);
        }
        int dot = path.lastIndexOf('.');
        String listPath = path.substring(0, dot);
        int index = Integer.parseInt(path.substring(dot + 1));
        ConfigService.SetOutcome outcome = overrides.listSetOverride(lobby, listPath, index,
                String.join(" ", values));
        if (!outcome.ok()) {
            feedback.failed(sender, outcome);
            return true;
        }
        feedback.overrideScalarUpdated(sender, lobby, path, outcome);
        return true;
    }

    private boolean setList(CommandSender sender, int lobby, String listPath,
            List<String> remainder) {
        if (remainder.size() < 2) {
            return usage(sender);
        }
        if (remainder.get(0).equalsIgnoreCase("add")) {
            ConfigService.SetOutcome outcome = overrides.listAddOverride(lobby, listPath,
                    String.join(" ", remainder.subList(1, remainder.size())));
            if (!outcome.ok()) {
                feedback.failed(sender, outcome);
                return true;
            }
            feedback.overrideListAdded(sender, lobby, listPath, outcome);
            return true;
        }
        if (remainder.get(0).equalsIgnoreCase("remove") && remainder.size() == 2) {
            int index;
            try {
                index = Integer.parseInt(remainder.get(1).trim());
            } catch (NumberFormatException expected) {
                return usage(sender);
            }
            ConfigService.SetOutcome outcome =
                    overrides.listRemoveOverride(lobby, listPath, index);
            if (!outcome.ok()) {
                feedback.failed(sender, outcome);
                return true;
            }
            feedback.overrideListRemoved(sender, lobby, listPath, outcome);
            return true;
        }
        return usage(sender);
    }

    private boolean modifiers(CommandSender sender, int lobby, String[] rest) {
        if (rest.length < 1) {
            return usage(sender);
        }
        return switch (rest[0].toLowerCase(Locale.ROOT)) {
            case "get" -> modifiersGet(sender, lobby, tailList(rest, 1));
            case "set" -> modifiersSet(sender, lobby, tailList(rest, 1));
            case "clear" -> modifiersClear(sender, lobby, tailList(rest, 1));
            default -> usage(sender);
        };
    }

    private boolean modifiersGet(CommandSender sender, int lobby, List<String> rest) {
        if (rest.isEmpty()) {
            for (String name : sortedNames(config.modifierNames())) {
                showModifier(sender, lobby, name);
            }
            if (!config.presetNames().isEmpty()) {
                messages.message(sender, "modifiers.list-presets-header");
                for (String id : sortedNames(config.presetNames())) {
                    showPreset(sender, lobby, id);
                }
            }
            neutralSound(sender);
            return true;
        }
        if (rest.size() > 1) {
            return usage(sender);
        }
        String id = rest.get(0);
        if (config.modifierNames().contains(id)) {
            showModifier(sender, lobby, id);
            neutralSound(sender);
            return true;
        }
        if (config.presetNames().contains(id)) {
            showPreset(sender, lobby, id);
            neutralSound(sender);
            return true;
        }
        return unknownModifier(sender, id);
    }

    private boolean modifiersSet(CommandSender sender, int lobby, List<String> rest) {
        if (rest.size() != 2) {
            return usage(sender);
        }
        String id = rest.get(0);
        Boolean value = ModifiersCommand.parseState(rest.get(1));
        if (value == null) {
            messages.message(sender, "modifiers.invalid-state");
            return true;
        }
        if (config.modifierNames().contains(id)) {
            overrides.setModifierOverride(lobby, id, value);
            feedback.overrideModifierSet(sender, lobby, id, value);
            return true;
        }
        if (config.presetNames().contains(id)) {
            List<String> members = config.presetMembers(id);
            for (String member : members) {
                overrides.setModifierOverride(lobby, member, value);
            }
            feedback.overridePresetSet(sender, lobby, id, value, members.size());
            return true;
        }
        return unknownModifier(sender, id);
    }

    private boolean modifiersClear(CommandSender sender, int lobby, List<String> rest) {
        if (rest.size() > 1) {
            return usage(sender);
        }
        if (rest.isEmpty()) {
            int removed = 0;
            for (String name : config.modifierNames()) {
                if (overrides.clearModifierOverride(lobby, name)) {
                    removed++;
                }
            }
            feedback.overrideCleared(sender, lobby, "modifiers", removed);
            return true;
        }
        String id = rest.get(0);
        if (config.modifierNames().contains(id)) {
            if (overrides.clearModifierOverride(lobby, id)) {
                feedback.overrideModifierCleared(sender, lobby, id);
            } else {
                feedback.overrideCleared(sender, lobby, "modifiers." + id, 0);
            }
            return true;
        }
        if (config.presetNames().contains(id)) {
            int removed = 0;
            for (String member : config.presetMembers(id)) {
                if (overrides.clearModifierOverride(lobby, member)) {
                    removed++;
                }
            }
            feedback.overrideCleared(sender, lobby, "preset." + id, removed);
            return true;
        }
        return unknownModifier(sender, id);
    }

    private boolean clearLobby(CommandSender sender, int lobby) {
        int removed = overrides.clearLobby(lobby);
        feedback.overrideLobbyCleared(sender, lobby, removed);
        return true;
    }

    /** Effective value plus its source: override or global. */
    private void showSetting(CommandSender sender, int lobby, String path) {
        boolean overridden = isIndexPath(lobby, path)
                ? overrides.hasListOverride(lobby, path.substring(0, path.lastIndexOf('.')))
                : overrides.hasSettingOverride(lobby, path);
        messages.message(sender, "manhunt.override-setting-shown", Map.of("setting", path,
                "value", ConfigService.displayValue(
                        overrides.effectiveValue(lobby, path)),
                "source", source(overridden)));
        neutralSound(sender);
    }

    private void showList(CommandSender sender, int lobby, String listPath) {
        List<String> entries = overrides.getStringList(lobby, listPath);
        if (entries.isEmpty()) {
            messages.message(sender, "manhunt.override-setting-shown", Map.of("setting",
                    listPath, "value", "(empty)", "source", source(false)));
            neutralSound(sender);
            return;
        }
        String mark = source(overrides.hasListOverride(lobby, listPath));
        for (int index = 0; index < entries.size(); index++) {
            messages.message(sender, "manhunt.override-setting-shown", Map.of("setting",
                    listPath + "." + index, "value", entries.get(index), "source", mark));
        }
        neutralSound(sender);
    }

    private void showModifier(CommandSender sender, int lobby, String name) {
        messages.message(sender, "manhunt.override-modifier-shown", Map.of("modifier", name,
                "state", overrides.modifierEnabled(lobby, name) ? "on" : "off",
                "source", source(overrides.hasModifierOverride(lobby, name))));
    }

    private void showPreset(CommandSender sender, int lobby, String id) {
        boolean anyOverridden = false;
        for (String member : config.presetMembers(id)) {
            if (overrides.hasModifierOverride(lobby, member)) {
                anyOverridden = true;
                break;
            }
        }
        messages.message(sender, "manhunt.override-modifier-shown", Map.of("modifier", id,
                "state", overrides.presetEnabled(lobby, id) ? "on" : "off",
                "source", source(anyOverridden)));
    }

    private String source(boolean overridden) {
        return overridden
                ? messages.string("manhunt.override-source-override", "<gray>(override)</gray>")
                : messages.string("manhunt.override-source-global", "<gray>(global)</gray>");
    }

    /**
     * Lists entries dir-style under one header, mirroring the config
     * drill so browsing never spams one prefixed line per entry.
     */
    private void listEntries(CommandSender sender, String key, Map<String, String> entries) {
        String template = messages.string("manhunt.config-entry",
                "\n<green>» <white>{key}</white><gray>{suffix}</gray></white>");
        messages.message(sender, "manhunt.config-list",
                Map.of("key", key, "entries",
                        ManhuntCommand.renderEntries(entries, template)));
    }

    private DrillResolve resolveDrill(int lobby, List<String> segments) {
        return ManhuntCommand.resolveDrill(segments,
                path -> overrides.getStringList(lobby, path));
    }

    /** True when the path addresses one effective list entry. */
    private boolean isIndexPath(int lobby, String path) {
        int dot = path.lastIndexOf('.');
        if (dot == -1 || !SettingRegistry.isListPath(path.substring(0, dot))) {
            return false;
        }
        try {
            int index = Integer.parseInt(path.substring(dot + 1).trim());
            return index >= 0
                    && index < overrides.getStringList(lobby, path.substring(0, dot)).size();
        } catch (NumberFormatException expected) {
            return false;
        }
    }

    private boolean unknownModifier(CommandSender sender, String id) {
        messages.message(sender, "modifiers.unknown-modifier",
                Map.of("name", id, "valid",
                        ListFormatter.joinOxford(sortedNames(config.modifierNames()))));
        return true;
    }

    private static List<String> sortedNames(Iterable<String> names) {
        List<String> sorted = new ArrayList<>();
        names.forEach(sorted::add);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    private static String[] tail(String[] args, int from) {
        String[] rest = new String[Math.max(0, args.length - from)];
        System.arraycopy(args, from, rest, 0, rest.length);
        return rest;
    }

    private static List<String> tailList(String[] rest, int from) {
        List<String> out = new ArrayList<>();
        for (int index = from; index < rest.length; index++) {
            out.add(rest[index]);
        }
        return out;
    }

    private void neutralSound(CommandSender sender) {
        if (sounds != null && sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
    }

    private boolean usage(CommandSender sender) {
        messages.message(sender, "manhunt.override-usage");
        return true;
    }

    private boolean invalid(CommandSender sender) {
        messages.message(sender, "manhunt.setting-invalid");
        return true;
    }
}
