package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.match.ModifierTestService;
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

    private final ConfigService config;
    private final MessageService messages;
    private final ModifiersMessages modifiers;
    private final CommandMessages command;
    private final GuiService gui;
    private final Function<Player, Menu> mainMenu;
    private final SoundService sounds;
    private final ModifierTestService testService;

    /**
     * @param gui menu opener, main menu factory, and sounds; all are only
     *        touched on the bare-player path, so tests may pass nulls
     * @param testService dry-run pipeline behind test runs; only the test
     *        path touches it, so tests for other paths may pass null
     */
    public ModifiersCommand(ConfigService config, MessageService messages,
            ModifiersMessages modifiers, CommandMessages command,
            GuiService gui, Function<Player, Menu> mainMenu, SoundService sounds,
            ModifierTestService testService) {
        this.config = config;
        this.messages = messages;
        this.modifiers = modifiers;
        this.command = command;
        this.gui = gui;
        this.mainMenu = mainMenu;
        this.sounds = sounds;
        this.testService = testService;
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
            case "test" -> testCommand(sender, args);
            default -> {
                messages.messageRaw(sender, modifiers.getUsage());
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
            messages.messageRaw(sender, modifiers.getListEmpty());
            return true;
        }
        messages.messageRaw(sender, modifiers.getListHeader());
        for (String name : names) {
            messages.messageRaw(sender, config.modifierEnabled(name)
                    ? modifiers.getListEntryOn() : modifiers.getListEntryOff(), Map.of("name", name));
        }
        Set<String> presets = config.presetNames();
        if (!presets.isEmpty()) {
            messages.messageRaw(sender, modifiers.getListPresetsHeader());
            for (String id : presets) {
                messages.messageRaw(sender, config.presetEnabled(id)
                        ? modifiers.getListEntryOn() : modifiers.getListEntryOff(), Map.of("name", id));
            }
        }
        return true;
    }

    private boolean setModifier(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            messages.messageRaw(sender, command.getNoPermission());
            return true;
        }
        if (args.length < 3) {
            messages.messageRaw(sender, modifiers.getSetmodUsage());
            return true;
        }
        String name = args[1];
        if (!config.hasModifier(name)) {
            messages.messageRaw(sender, modifiers.getUnknownModifier(),
                    Map.of("name", name, "valid", ListFormatter.joinOxford(modifierNameOptions())));
            return true;
        }
        Boolean value = parseState(args[2]);
        if (value == null) {
            messages.messageRaw(sender, modifiers.getInvalidState());
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
            messages.messageRaw(sender, command.getNoPermission());
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
        messages.messageRaw(sender, modifiers.getSetmodSuccess(),
                Map.of("name", name, "state", value ? "on" : "off"));
        ManhuntCommand.announceSettingChange(messages,
                config.getBoolean("settings.server.announce-config-changes", false),
                sender, modifiers.getToggleAnnounced(), "modifier " + name, value ? "on" : "off");
        return true;
    }

    private boolean setPreset(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            messages.messageRaw(sender, command.getNoPermission());
            return true;
        }
        if (args.length < 3) {
            messages.messageRaw(sender, modifiers.getSetpresetUsage());
            return true;
        }
        String id = args[1];
        if (!config.hasPreset(id)) {
            messages.messageRaw(sender, modifiers.getUnknownPreset(),
                    Map.of("name", id, "valid", ListFormatter.joinOxford(presetIdOptions())));
            return true;
        }
        Boolean value = parseState(args[2]);
        if (value == null) {
            messages.messageRaw(sender, modifiers.getInvalidState());
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
            messages.messageRaw(sender, command.getNoPermission());
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
        messages.messageRaw(sender, modifiers.getSetpresetSuccess(),
                Map.of("name", id, "state", value ? "on" : "off",
                        "count", String.valueOf(config.presetMembers(id).size())));
        ManhuntCommand.announceSettingChange(messages,
                config.getBoolean("settings.server.announce-config-changes", false),
                sender, modifiers.getToggleAnnounced(), "preset " + id, value ? "on" : "off");
        return true;
    }

    private boolean exportCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || parseEntryType(args[1]) == null) {
            messages.messageRaw(sender, modifiers.getExportUsage());
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
            messages.messageRaw(sender, command.getNoPermission());
            return false;
        }
        String payload;
        String name;
        if (type.equals("preset")) {
            ModifierPreset preset =
                    config.modifiers().presetEntry(id);
            if (preset == null) {
                messages.messageRaw(sender, modifiers.getUnknownPreset(),
                        Map.of("name", id, "valid", ListFormatter.joinOxford(presetIdOptions())));
                return false;
            }
            payload = ModifierCodec.exportPreset(id, preset);
            name = config.modifiers().presetName(id);
        } else {
            ModifierEntry entry =
                    config.modifiers().modifierEntry(id);
            if (entry == null) {
                messages.messageRaw(sender, modifiers.getUnknownModifier(),
                        Map.of("name", id, "valid", ListFormatter.joinOxford(modifierNameOptions())));
                return false;
            }
            payload = ModifierCodec.exportModifier(id, entry);
            name = config.modifiers().metaName(id);
        }
        sender.sendMessage(messages.componentRaw(modifiers.getExported(),
                        Map.of("type", type, "name", name))
                .clickEvent(ClickEvent.copyToClipboard(payload)));
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
        return true;
    }

    private boolean importCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || parseEntryType(args[1]) == null) {
            messages.messageRaw(sender, modifiers.getImportUsage());
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
            messages.messageRaw(sender, command.getNoPermission());
            return false;
        }
        Optional<ModifierCodec.Imported> decoded =
                ModifierCodec.decode(payload);
        if (decoded.isEmpty()) {
            messages.messageRaw(sender, modifiers.getImportFailed());
            return false;
        }
        ModifierCodec.Imported imported = decoded.get();
        boolean isPreset = imported.kind()
                == ModifierCodec.Kind.PRESET;
        if (!type.equals(isPreset ? "preset" : "modifier")) {
            messages.messageRaw(sender, modifiers.getImportFailed());
            return false;
        }
        String finalId = isPreset
                ? config.modifiers().addPreset(imported.id(), imported.preset())
                : config.modifiers().addModifier(imported.id(), imported.entry());
        String name = isPreset
                ? config.modifiers().presetName(finalId)
                : config.modifiers().metaName(finalId);
        messages.messageRaw(sender, modifiers.getImported(), Map.of("name", name));
        if (!finalId.equals(imported.id())) {
            messages.messageRaw(sender, modifiers.getImportDuplicate(),
                    Map.of("duplicate", imported.id(), "id", finalId));
        }
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
        return true;
    }

    private boolean createCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            messages.messageRaw(sender, command.getNoPermission());
            return true;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        ModifierCreateArgs.Result result = ModifierCreateArgs.parse(rest, config.modifierNames(), modifiers);
        if (!result.success()) {
            messages.messageRaw(sender, result.messageTemplate(), result.params());
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
        messages.messageRaw(sender, modifiers.getCreateSuccess(),
                Map.of("type", plan.preset() ? "preset" : "modifier", "name", display));
        for (String warning : result.warnings()) {
            messages.messageRaw(sender, modifiers.getCreateCommandWarning(), Map.of("warning", warning));
        }
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
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
     * result reported back. Used by the test command; the GUI uses
     * {@link #testList} for whole command lists.
     */
    public boolean testCommands(Player player, String role, List<String> lines) {
        if (!player.hasPermission(MODIFIERS_PERMISSION)) {
            messages.messageRaw(player, command.getNoPermission());
            return true;
        }
        testService.report(player, testService.run(player, role, lines));
        return true;
    }

    /**
     * Dry-runs one command list for the viewing player: the test role
     * follows the list audience. Used by the editor GUI test clicks.
     */
    public boolean testList(Player viewer, String list, List<String> lines) {
        return testCommands(viewer, testService.roleFor(viewer, list), lines);
    }

    private boolean testCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission(MODIFIERS_PERMISSION)) {
            messages.messageRaw(sender, command.getNoPermission());
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.messageRaw(sender, command.getPlayerOnly());
            return true;
        }
        if (args.length < 3 || parseTestRole(args[1]) == null) {
            messages.messageRaw(sender, modifiers.getTestUsage());
            return true;
        }
        List<String> lines =
                ModifierTestService.parseCommandLines(String.join(" ",
                        Arrays.copyOfRange(args, 2, args.length)));
        if (lines.isEmpty()) {
            messages.messageRaw(sender, modifiers.getTestUsage());
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
        messages.messageRaw(sender, modifiers.getToggleAllSuccess(),
                Map.of("count", String.valueOf(count), "kind", kind, "state", state));
        ManhuntCommand.announceSettingChange(messages,
                config.getBoolean("settings.server.announce-config-changes", false),
                sender, modifiers.getToggleAllAnnounced(), count + " " + kind, state);
    }
}
