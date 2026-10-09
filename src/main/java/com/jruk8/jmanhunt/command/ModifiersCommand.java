package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.match.ModifierTestService;
import com.jruk8.jmanhunt.match.ModifierTriggers;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.ListFormatter;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersMessages;
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

    /** Menu opener, main menu factory, and dry-run pipeline. Nulls allowed per path. */
    public record ModifiersDeps(GuiService gui, Function<Player, Menu> mainMenu,
            ModifierTestService testService) {
    }

    /** Message bus, modifier/command texts, and sounds. */
    public record ModifiersTexts(MessageService messages, ModifiersMessages modifiers,
            CommandMessages command, SoundService sounds) {
    }

    private final ConfigService config;
    private final ModifiersDeps deps;
    private final ModifiersTexts texts;

    public ModifiersCommand(ConfigService config, ModifiersDeps deps, ModifiersTexts texts) {
        this.config = config;
        this.deps = deps;
        this.texts = texts;
    }

    /** Runs one texts.modifiers() action; args[0] is the action when present. */
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                deps.gui().clearOverrideLobby(player);
                deps.gui().open(player, deps.mainMenu().apply(player));
                texts.sounds().playNeutralSound(player);
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
            case "test" -> testCommand(sender, args);
            default -> {
                texts.messages().messageRaw(sender, texts.modifiers().getUsage());
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
            texts.messages().messageRaw(sender, texts.modifiers().getListEmpty());
            return true;
        }
        texts.messages().messageRaw(sender, texts.modifiers().getListHeader());
        for (String name : names) {
            texts.messages().messageRaw(sender, config.modifierEnabled(name)
                    ? texts.modifiers().getListEntryOn() : texts.modifiers().getListEntryOff(), Map.of("name", name));
        }
        Set<String> presets = config.presetNames();
        if (!presets.isEmpty()) {
            texts.messages().messageRaw(sender, texts.modifiers().getListPresetsHeader());
            for (String id : presets) {
                texts.messages().messageRaw(sender, config.presetEnabled(id)
                        ? texts.modifiers().getListEntryOn() : texts.modifiers().getListEntryOff(), Map.of("name", id));
            }
        }
        return true;
    }

    private boolean setModifier(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            texts.messages().messageRaw(sender, texts.command().getNoPermission());
            return true;
        }
        if (args.length < 3) {
            texts.messages().messageRaw(sender, texts.modifiers().getSetmodUsage());
            return true;
        }
        String name = args[1];
        if (!config.hasModifier(name)) {
            texts.messages().messageRaw(sender, texts.modifiers().getUnknownModifier(),
                    Map.of("name", name, "valid", ListFormatter.joinOxford(modifierNameOptions())));
            return true;
        }
        Boolean value = parseState(args[2]);
        if (value == null) {
            texts.messages().messageRaw(sender, texts.modifiers().getInvalidState());
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
            texts.messages().messageRaw(sender, texts.command().getNoPermission());
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
        String display = config.modifiers().metaName(name);
        texts.messages().messageRaw(sender, texts.modifiers().getSetmodSuccess(),
                Map.of("name", display, "state", value ? "on" : "off"));
        SettingFeedback.announceSettingChange(texts.messages(),
                config.server().isAnnounceConfigChanges(),
                sender, texts.modifiers().getToggleAnnounced(), "modifier " + display,
                value ? "on" : "off");
        return true;
    }

    private boolean setPreset(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            texts.messages().messageRaw(sender, texts.command().getNoPermission());
            return true;
        }
        if (args.length < 3) {
            texts.messages().messageRaw(sender, texts.modifiers().getSetpresetUsage());
            return true;
        }
        String id = args[1];
        if (!config.hasPreset(id)) {
            texts.messages().messageRaw(sender, texts.modifiers().getUnknownPreset(),
                    Map.of("name", id, "valid", ListFormatter.joinOxford(presetIdOptions())));
            return true;
        }
        Boolean value = parseState(args[2]);
        if (value == null) {
            texts.messages().messageRaw(sender, texts.modifiers().getInvalidState());
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
            texts.messages().messageRaw(sender, texts.command().getNoPermission());
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
     * when the preset is unknown or every member is missing (the
     * refuse line still shows, even when quiet).
     */
    private boolean applyPresetToggle(CommandSender sender, String id, boolean value,
            boolean quiet) {
        if (!config.hasPreset(id)) {
            return false;
        }
        List<String> members = config.presetMembers(id);
        int missing = config.presetMissing(id).size();
        if (!members.isEmpty() && missing == members.size()) {
            texts.messages().messageRaw(sender, texts.modifiers().getPresetAllMissing(),
                    Map.of("name", config.modifiers().presetName(id)));
            if (sender instanceof Player player) {
                texts.sounds().playAngrySound(player);
            }
            return false;
        }
        config.setPreset(id, value);
        if (quiet) {
            return true;
        }
        String display = config.modifiers().presetName(id);
        texts.messages().messageRaw(sender, texts.modifiers().getSetpresetSuccess(),
                Map.of("name", display, "state", value ? "on" : "off",
                        "count", String.valueOf(members.size() - missing)));
        SettingFeedback.announceSettingChange(texts.messages(),
                config.server().isAnnounceConfigChanges(),
                sender, texts.modifiers().getToggleAnnounced(), "preset " + display,
                value ? "on" : "off");
        return true;
    }

    private boolean exportCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || parseEntryType(args[1]) == null) {
            texts.messages().messageRaw(sender, texts.modifiers().getExportUsage());
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
            texts.messages().messageRaw(sender, texts.command().getNoPermission());
            return false;
        }
        String payload;
        String name;
        if (type.equals("preset")) {
            ModifierPreset preset =
                    config.modifiers().presetEntry(id);
            if (preset == null) {
                texts.messages().messageRaw(sender, texts.modifiers().getUnknownPreset(),
                        Map.of("name", id, "valid", ListFormatter.joinOxford(presetIdOptions())));
                return false;
            }
            payload = ModifierCodec.exportPreset(id, preset);
            name = config.modifiers().presetName(id);
        } else {
            ModifierEntry entry =
                    config.modifiers().modifierEntry(id);
            if (entry == null) {
                texts.messages().messageRaw(sender, texts.modifiers().getUnknownModifier(),
                        Map.of("name", id, "valid", ListFormatter.joinOxford(modifierNameOptions())));
                return false;
            }
            payload = ModifierCodec.exportModifier(id, entry);
            name = config.modifiers().metaName(id);
        }
        sender.sendMessage(texts.messages().componentRaw(texts.modifiers().getExported(),
                        Map.of("type", type, "name", name))
                .clickEvent(ClickEvent.copyToClipboard(payload)));
        if (sender instanceof Player player) {
            texts.sounds().playNeutralSound(player);
        }
        return true;
    }

    private boolean importCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || parseEntryType(args[1]) == null) {
            texts.messages().messageRaw(sender, texts.modifiers().getImportUsage());
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
            texts.messages().messageRaw(sender, texts.command().getNoPermission());
            return false;
        }
        Optional<ModifierCodec.Imported> decoded =
                ModifierCodec.decode(payload);
        if (decoded.isEmpty()) {
            texts.messages().messageRaw(sender, texts.modifiers().getImportFailed());
            return false;
        }
        ModifierCodec.Imported imported = decoded.get();
        boolean isPreset = imported.kind()
                == ModifierCodec.Kind.PRESET;
        if (!type.equals(isPreset ? "preset" : "modifier")) {
            texts.messages().messageRaw(sender, texts.modifiers().getImportFailed());
            return false;
        }
        String finalId = isPreset
                ? config.modifiers().addPreset(imported.id(), imported.preset())
                : config.modifiers().addModifier(imported.id(), imported.entry());
        String name = isPreset
                ? config.modifiers().presetName(finalId)
                : config.modifiers().metaName(finalId);
        texts.messages().messageRaw(sender, texts.modifiers().getImported(), Map.of("name", name));
        if (!finalId.equals(imported.id())) {
            texts.messages().messageRaw(sender, texts.modifiers().getImportDuplicate(),
                    Map.of("duplicate", imported.id(), "id", finalId));
        }
        if (sender instanceof Player player) {
            texts.sounds().playNeutralSound(player);
        }
        return true;
    }

    private boolean createCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            texts.messages().messageRaw(sender, texts.command().getNoPermission());
            return true;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(rest, config.modifierNames(), texts.modifiers());
        if (!result.success()) {
            texts.messages().messageRaw(sender, result.messageTemplate(), result.params());
            return true;
        }
        ModifierCreateArgs.Plan plan = result.plan();
        String author = plan.author() == null || plan.author().isBlank()
                ? senderName(sender) : plan.author();
        String slug = ModifierNames.kebab(plan.name());
        String finalId;
        if (plan.preset()) {
            finalId = config.modifiers().addPreset(slug.isEmpty() ? "preset" : slug, plan.toPreset(author));
        } else {
            finalId = config.modifiers().addModifier(slug.isEmpty() ? "modifier" : slug, plan.toEntry(author));
        }
        String display = plan.preset()
                ? config.modifiers().presetName(finalId)
                : config.modifiers().metaName(finalId);
        texts.messages().messageRaw(sender, texts.modifiers().getCreateSuccess(),
                Map.of("type", plan.preset() ? "preset" : "modifier", "name", display));
        for (String warning : result.warnings()) {
            texts.messages().messageRaw(sender, texts.modifiers().getCreateCommandWarning(),
                    Map.of("warning", warning));
        }
        if (sender instanceof Player player) {
            texts.sounds().playNeutralSound(player);
        }
        return true;
    }

    /**
     * Parses the test role word; upper-case HUNTER or SPEEDRUNNER, null
     * when anything else. Pure for tests.
     */
    static String parseTestRole(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("HUNTER") || normalized.equals("SPEEDRUNNER")) {
            return normalized;
        }
        return null;
    }

    /**
     * Dry-runs explicit lines for one player under one role with the
     * result reported back. Used by the test texts.command(); the GUI uses
     * {@link #testList} for whole texts.command() lists.
     */
    public boolean testCommands(Player player, String role, List<String> lines) {
        if (!player.hasPermission(MODIFIERS_PERMISSION)) {
            texts.messages().messageRaw(player, texts.command().getNoPermission());
            return true;
        }
        deps.testService().report(player, deps.testService().run(player, role, lines));
        return true;
    }

    /**
     * Dry-runs one texts.command() list for the viewing player: the test role
     * follows the list audience. Used by the editor GUI test clicks.
     */
    public boolean testList(Player viewer, String list, List<String> lines) {
        return testCommands(viewer, deps.testService().roleFor(viewer, list), lines);
    }

    private boolean testCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            texts.messages().messageRaw(sender, texts.command().getNoPermission());
            return true;
        }
        if (!(sender instanceof Player player)) {
            texts.messages().messageRaw(sender, texts.command().getPlayerOnly());
            return true;
        }
        if (args.length < 3 || parseTestRole(args[1]) == null) {
            texts.messages().messageRaw(sender, texts.modifiers().getTestUsage());
            return true;
        }
        List<String> lines =
                ModifierTestService.parseCommandLines(String.join(" ",
                        Arrays.copyOfRange(args, 2, args.length)));
        if (lines.isEmpty()) {
            texts.messages().messageRaw(sender, texts.modifiers().getTestUsage());
            return true;
        }
        return testCommands(player, parseTestRole(args[1]), lines);
    }

    /** Creator name for new entries: player name, or CONSOLE. */
    private static String senderName(CommandSender sender) {
        return sender instanceof Player player ? player.getName() : "CONSOLE";
    }

    private void announceBulkToggle(CommandSender sender, int count, String kind, boolean value) {
        String state = value ? "on" : "off";
        texts.messages().messageRaw(sender, texts.modifiers().getToggleAllSuccess(),
                Map.of("count", String.valueOf(count), "kind", kind, "state", state));
        SettingFeedback.announceSettingChange(texts.messages(),
                config.server().isAnnounceConfigChanges(),
                sender, texts.modifiers().getToggleAllAnnounced(), count + " " + kind, state);
    }

    /** Tab completion for modifiers toggles. Null when inapplicable. */
    public List<String> completeModifiersTab(String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("modifiers")) {
            return CommandSupport.partial(args[1],
                    List.of("setmod", "setpreset", "export", "import", "create", "test"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("modifiers")) {
            if (args[1].equalsIgnoreCase("setmod")) {
                return CommandSupport.partial(args[2], modifierNameOptions());
            }
            if (args[1].equalsIgnoreCase("setpreset")) {
                return CommandSupport.partial(args[2], presetIdOptions());
            }
            if (args[1].equalsIgnoreCase("export") || args[1].equalsIgnoreCase("import")
                    || args[1].equalsIgnoreCase("create")) {
                return CommandSupport.partial(args[2], List.of("modifier", "preset"));
            }
            if (args[1].equalsIgnoreCase("test")) {
                return CommandSupport.partial(args[2], List.of("speedrunner", "hunter"));
            }
            return null;
        }
        if (args.length >= 4 && args[0].equalsIgnoreCase("modifiers")
                && args[1].equalsIgnoreCase("create")) {
            return completeCreateTab(args);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("modifiers")
                && (args[1].equalsIgnoreCase("setmod") || args[1].equalsIgnoreCase("setpreset"))) {
            return CommandSupport.partial(args[3], List.of("true", "false"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("modifiers")
                && args[1].equalsIgnoreCase("export")) {
            if (args[2].equalsIgnoreCase("preset")) {
                return CommandSupport.partial(args[3], presetIdOptions());
            }
            return CommandSupport.partial(args[3], modifierNameOptions());
        }
        return null;
    }

    /** Flag and flag-value completion for modifiers create. */
    private List<String> completeCreateTab(String[] args) {
        boolean preset = args[2].equalsIgnoreCase("preset");
        String current = args[args.length - 1];
        if (current.startsWith("--")) {
            return CommandSupport.partial(current, ModifierCreateArgs.flagsFor(preset));
        }
        String previous = args[args.length - 2].toLowerCase(Locale.ROOT);
        return switch (previous) {
            case "--trigger" -> CommandSupport.partial(current, ModifierTriggers.KNOWN);
            case "--member" -> CommandSupport.partial(current, modifierNameOptions());
            case "--on-start", "--selection" -> CommandSupport.partial(current, List.of("IN_ORDER", "PICK_RANDOM"));
            case "--interval-scope", "--chance-scope", "--pick-scope" ->
                    CommandSupport.partial(current, List.of("PER_INVOKE", "PER_EXECUTOR"));
            default -> null;
        };
    }
}
