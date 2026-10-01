package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigDrill;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.config.SettingType;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Per-lobby overrides ({@code /manhunt override <lobby> ...}). Settings
 * drill exactly like {@code /manhunt config} but read effective values
 * and write overrides; modifiers force one lobby's enabled flags.
 * Players and console alike; no match needs to run.
 */
public final class OverrideCommand {

    /** Message bus, manhunt/modifier texts, and sounds. */
    public record OverrideTexts(MessageService messages, ManhuntMessages manhunt,
            ModifiersMessages modifiers, SoundService sounds) {
    }

    private final OverrideService overrides;
    private final ConfigService config;
    private final LobbyService lobbies;
    private final OverrideTexts texts;
    private final SettingFeedback feedback;
    private final PendingConfirmations confirms = new PendingConfirmations();

    public OverrideCommand(OverrideService overrides, ConfigService config,
            OverrideTexts texts, SettingFeedback feedback, LobbyService lobbies) {
        this.overrides = overrides;
        this.config = config;
        this.texts = texts;
        this.feedback = feedback;
        this.lobbies = lobbies;
    }

    /** Runs one override action; args[0] is "override". */
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            return usage(sender);
        }
        OptionalInt lobby = OverrideService.parseLobbyId(args[1]);
        if (lobby.isEmpty()) {
            texts.messages().messageRaw(sender, texts.manhunt().getOverrideInvalidLobby(),
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
        if (confirmRequired(sender, lobby, "settings|" + lobby + "|" + resolved.path(),
                "overrides under " + resolved.path())) {
            return true;
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
                texts.messages().messageRaw(sender, texts.modifiers().getListPresetsHeader());
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
            texts.messages().messageRaw(sender, texts.modifiers().getInvalidState());
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
            return modifiersClearAll(sender, lobby);
        }
        String id = rest.get(0);
        if (config.modifierNames().contains(id)) {
            return modifiersClearOne(sender, lobby, id);
        }
        if (config.presetNames().contains(id)) {
            return modifiersClearPreset(sender, lobby, id);
        }
        return unknownModifier(sender, id);
    }

    /** Clears every modifier override after the rerun confirm. */
    private boolean modifiersClearAll(CommandSender sender, int lobby) {
        if (confirmRequired(sender, lobby, "modifiers|" + lobby + "|all",
                "all modifier overrides")) {
            return true;
        }
        int removed = 0;
        for (String name : config.modifierNames()) {
            if (overrides.clearModifierOverride(lobby, name)) {
                removed++;
            }
        }
        feedback.overrideCleared(sender, lobby, "modifiers", removed);
        return true;
    }

    /** Clears one modifier override after the rerun confirm. */
    private boolean modifiersClearOne(CommandSender sender, int lobby, String id) {
        if (confirmRequired(sender, lobby, "modifiers|" + lobby + "|" + id,
                "the " + id + " modifier override")) {
            return true;
        }
        if (overrides.clearModifierOverride(lobby, id)) {
            feedback.overrideModifierCleared(sender, lobby, id);
        } else {
            feedback.overrideCleared(sender, lobby, "modifiers." + id, 0);
        }
        return true;
    }

    /** Clears one preset's member overrides after the rerun confirm. */
    private boolean modifiersClearPreset(CommandSender sender, int lobby, String id) {
        if (confirmRequired(sender, lobby, "preset|" + lobby + "|" + id,
                "the " + id + " preset overrides")) {
            return true;
        }
        int removed = 0;
        for (String member : config.presetMembers(id)) {
            if (overrides.clearModifierOverride(lobby, member)) {
                removed++;
            }
        }
        feedback.overrideCleared(sender, lobby, "preset." + id, removed);
        return true;
    }

    private boolean clearLobby(CommandSender sender, int lobby) {
        if (confirmRequired(sender, lobby, "lobby|" + lobby, "every override")) {
            return true;
        }
        int removed = overrides.clearLobby(lobby);
        feedback.overrideLobbyCleared(sender, lobby, removed);
        return true;
    }

    /**
     * True when the clear must wait for an explicit rerun: the first
     * run arms the action and asks, the identical rerun within 10
     * seconds proceeds. Keyed by sender name so console works too.
     */
    private boolean confirmRequired(CommandSender sender, int lobby, String key, String what) {
        if (confirms.confirm(sender.getName() + "|" + key)) {
            return false;
        }
        texts.messages().messageRaw(sender, texts.manhunt().getOverrideClearConfirm(),
                Map.of("lobby", String.valueOf(lobby), "what", what));
        return true;
    }

    /** Effective value plus its source: override or global. */
    private void showSetting(CommandSender sender, int lobby, String path) {
        boolean overridden = isIndexPath(lobby, path)
                ? overrides.hasListOverride(lobby, path.substring(0, path.lastIndexOf('.')))
                : overrides.hasSettingOverride(lobby, path);
        texts.messages().messageRaw(sender, texts.manhunt().getOverrideSettingShown(), Map.of("setting", path,
                "value", ConfigService.displayValue(
                        overrides.effectiveValue(lobby, path)),
                "source", source(overridden)));
        neutralSound(sender);
    }

    private void showList(CommandSender sender, int lobby, String listPath) {
        List<String> entries = overrides.getStringList(lobby, listPath);
        if (entries.isEmpty()) {
            texts.messages().messageRaw(sender, texts.manhunt().getOverrideSettingShown(), Map.of("setting",
                    listPath, "value", "(empty)", "source", source(false)));
            neutralSound(sender);
            return;
        }
        String mark = source(overrides.hasListOverride(lobby, listPath));
        for (int index = 0; index < entries.size(); index++) {
            texts.messages().messageRaw(sender, texts.manhunt().getOverrideSettingShown(), Map.of("setting",
                    listPath + "." + index, "value", entries.get(index), "source", mark));
        }
        neutralSound(sender);
    }

    private void showModifier(CommandSender sender, int lobby, String name) {
        texts.messages().messageRaw(sender, texts.manhunt().getOverrideModifierShown(), Map.of("modifier", name,
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
        texts.messages().messageRaw(sender, texts.manhunt().getOverrideModifierShown(), Map.of("modifier", id,
                "state", overrides.presetEnabled(lobby, id) ? "on" : "off",
                "source", source(anyOverridden)));
    }

    private String source(boolean overridden) {
        return overridden
                ? texts.manhunt().getOverrideSourceOverride()
                : texts.manhunt().getOverrideSourceGlobal();
    }

    /**
     * Lists entries dir-style under one header, mirroring the config
     * drill so browsing never spams one prefixed line per entry.
     */
    private void listEntries(CommandSender sender, String key, Map<String, String> entries) {
        String template = texts.manhunt().getConfigEntry();
        texts.messages().messageRaw(sender, texts.manhunt().getConfigList(),
                Map.of("key", key, "entries",
                        ConfigDrill.renderEntries(entries, template)));
    }

    private DrillResolve resolveDrill(int lobby, List<String> segments) {
        return ConfigDrill.resolveDrill(segments,
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
        texts.messages().messageRaw(sender, texts.modifiers().getUnknownModifier(),
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
        if (texts.sounds() != null && sender instanceof Player player) {
            texts.sounds().playNeutralSound(player);
        }
    }

    private boolean usage(CommandSender sender) {
        texts.messages().messageRaw(sender, texts.manhunt().getOverrideUsage());
        return true;
    }

    private boolean invalid(CommandSender sender) {
        texts.messages().messageRaw(sender, texts.manhunt().getSettingInvalid());
        return true;
    }

    /** Tab completion for per-lobby overrides. Null when inapplicable. */
    public List<String> completeOverrideTab(String[] args) {
        if (!args[0].equalsIgnoreCase("override")) {
            return null;
        }
        if (args.length == 2) {
            return CommandSupport.partial(args[1], overrideLobbyOptions());
        }
        if (args.length == 3) {
            return CommandSupport.partial(args[2], List.of("settings", "modifiers", "clear"));
        }
        if (args[2].equalsIgnoreCase("clear")) {
            return List.of();
        }
        if (args.length == 4) {
            if (args[2].equalsIgnoreCase("settings") || args[2].equalsIgnoreCase("modifiers")) {
                return CommandSupport.partial(args[3], List.of("get", "set", "clear"));
            }
            return List.of();
        }
        if (args[2].equalsIgnoreCase("modifiers")) {
            return completeOverrideModifiersTab(args);
        }
        if (args[2].equalsIgnoreCase("settings")) {
            return completeOverrideSettingsTab(args);
        }
        return List.of();
    }

    /** Id and state completion for override modifiers verbs. */
    private List<String> completeOverrideModifiersTab(String[] args) {
        String verb = args[3].toLowerCase(Locale.ROOT);
        if (!verb.equals("get") && !verb.equals("set") && !verb.equals("clear")) {
            return null;
        }
        if (args.length == 5) {
            List<String> ids = new ArrayList<>(sortedNames(config.modifierNames()));
            ids.addAll(sortedNames(config.presetNames()));
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            return CommandSupport.partial(args[4], ids);
        }
        if (args.length == 6 && verb.equals("set")) {
            return CommandSupport.partial(args[5], List.of("true", "false"));
        }
        return List.of();
    }

    /** Drill completion for override settings verbs, reusing the config drill. */
    private List<String> completeOverrideSettingsTab(String[] args) {
        String verb = args[3].toLowerCase(Locale.ROOT);
        if (!verb.equals("get") && !verb.equals("set") && !verb.equals("clear")) {
            return null;
        }
        List<String> segments = new ArrayList<>();
        for (int index = 4; index < args.length - 1; index++) {
            segments.add(args[index]);
        }
        String completing = args[args.length - 1];
        if (verb.equals("get") || verb.equals("clear")) {
            if (segments.isEmpty()) {
                return CommandSupport.partial(completing, SettingRegistry.topCategories());
            }
            return CommandSupport.partial(completing, ConfigDrill.drillChildren(segments, config::getStringList));
        }
        String[] shifted = new String[segments.size() + 2];
        shifted[0] = "config";
        for (int index = 0; index < segments.size(); index++) {
            shifted[index + 1] = segments.get(index);
        }
        shifted[shifted.length - 1] = completing;
        List<String> options = ConfigDrill.completeDrill(shifted, config);
        DrillResolve parent = segments.isEmpty() ? null
                : ConfigDrill.resolveDrill(segments, config::getStringList);
        if (parent != null && SettingRegistry.isListPath(parent.path())
                && parent.remainder().isEmpty()) {
            options = options.stream()
                    .filter(option -> !option.equalsIgnoreCase("reset")).toList();
        }
        return options;
    }

    /** Lobby id completion: live ids plus override holders, sorted. */
    private List<String> overrideLobbyOptions() {
        Set<Integer> ids = new HashSet<>();
        for (String raw : CommandSupport.lobbyIdOptions(lobbies)) {
            try {
                ids.add(Integer.parseInt(raw.trim()));
            } catch (NumberFormatException expected) {
                // Live ids are always numeric; ignore anything else.
            }
        }
        if (overrides != null) {
            ids.addAll(overrides.overrideLobbyIds());
        }
        List<Integer> sorted = new ArrayList<>(ids);
        sorted.sort(Integer::compareTo);
        return sorted.stream().map(String::valueOf).toList();
    }
}
