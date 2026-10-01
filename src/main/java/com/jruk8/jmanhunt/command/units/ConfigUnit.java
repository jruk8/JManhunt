package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.config.ConfigDrill;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.config.SettingType;
import com.jruk8.jmanhunt.command.DrillResolve;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The config verb: browse and edit settings by drill-down path. */
public final class ConfigUnit implements SubcommandUnit {
    public record ConfigDeps(GameManager game, ConfigService config, SettingFeedback feedback,
            Runnable observeWorldEngine) {
    }

    public record ConfigTexts(ManhuntMessages manhunt, CommandSupport support) {
    }

    private final GameManager game;
    private final ConfigService config;
    private final SettingFeedback feedback;
    private final Runnable observeWorldEngine;
    private final ManhuntMessages texts;
    private final CommandSupport support;

    public ConfigUnit(ConfigDeps deps, ConfigTexts texts) {
        this.game = deps.game();
        this.config = deps.config();
        this.feedback = deps.feedback();
        this.observeWorldEngine = deps.observeWorldEngine();
        this.texts = texts.manhunt();
        this.support = texts.support();
    }

    /** Drill segments; empty means list the top categories. */
    public record ConfigArgs(List<String> segments) {
    }

    public static ConfigArgs parse(String[] args) {
        List<String> segments = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            segments.add(args[i]);
        }
        return new ConfigArgs(segments);
    }

    @Override public String primaryName() {
        return "config";
    }

    @Override public Set<String> aliases() {
        return Set.of();
    }

    @Override public boolean execute(CommandSender sender, String[] args) {
        return executeParsed(sender, parse(args));
    }

    public boolean executeParsed(CommandSender sender, ConfigArgs args) {
        List<String> segments = args.segments();
        if (segments.isEmpty()) {
            Map<String, String> categories = new LinkedHashMap<>();
            for (String category : SettingRegistry.topCategories()) {
                categories.put(category, "");
            }
            listEntries(sender, "config", categories);
            support.neutralSound(sender);
            return true;
        }
        DrillResolve resolved = ConfigDrill.resolveDrill(segments, config::getStringList);
        if (resolved == null) {
            return support.message(sender, texts.getSettingInvalid());
        }
        if (resolved.leaf()) {
            return showOrUpdateSetting(sender, resolved.path(), resolved.remainder());
        }
        if (SettingRegistry.isListPath(resolved.path())) {
            return listCommand(sender, resolved.path(), resolved.remainder());
        }
        if (!resolved.section() || !resolved.remainder().isEmpty()) {
            return support.message(sender, texts.getConfigUsage());
        }
        SettingRegistry.DrillChildren listing = SettingRegistry.children(resolved.path());
        Map<String, String> entries = new LinkedHashMap<>();
        for (String section : listing.sections()) {
            entries.put(section, "");
        }
        for (String leaf : listing.leaves()) {
            entries.put(leaf, ": " + ConfigService.displayValue(
                    game.getSettingValue(resolved.path() + "." + leaf)));
        }
        listEntries(sender, resolved.path(), entries);
        support.neutralSound(sender);
        return true;
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("config")) {
            return ConfigDrill.completeDrill(args, config);
        }
        return null;
    }

    /**
     * Lists entries dir-style under one header, so browsing never spams one
     * prefixed line per entry.
     */
    private void listEntries(CommandSender sender, String key, Map<String, String> entries) {
        String template = texts.getConfigEntry();
        support.message(sender, texts.getConfigList(),
                Map.of("key", key, "entries", ConfigDrill.renderEntries(entries, template)));
    }

    /**
     * Browses or edits one string list: bare lists entries by index with the
     * add, remove, and reset forms; {@code add} appends, {@code remove}
     * deletes, {@code reset} restores the schema defaults.
     */
    private boolean listCommand(CommandSender sender, String listPath, List<String> args) {
        if (args.isEmpty()) {
            return listListEntries(sender, listPath);
        }
        if (args.get(0).equalsIgnoreCase("add")) {
            return listAddEntry(sender, listPath, args);
        }
        if (args.get(0).equalsIgnoreCase("remove") && args.size() == 2) {
            return listRemoveEntry(sender, listPath, args.get(1));
        }
        if (args.get(0).equalsIgnoreCase("reset") && args.size() == 1) {
            return listResetEntries(sender, listPath);
        }
        return support.message(sender, texts.getConfigUsage());
    }

    private boolean listListEntries(CommandSender sender, String listPath) {
        List<String> entries = config.getStringList(listPath);
        Map<String, String> rows = new LinkedHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            rows.put(String.valueOf(index), ": " + entries.get(index));
        }
        rows.put("add <value>", "");
        rows.put("remove <index>", "");
        rows.put("reset", "");
        listEntries(sender, listPath, rows);
        support.neutralSound(sender);
        return true;
    }

    private boolean listAddEntry(CommandSender sender, String listPath, List<String> args) {
        if (args.size() < 2) {
            return support.message(sender, texts.getConfigUsage());
        }
        ConfigService.SetOutcome outcome = config.listAdd(
                listPath, String.join(" ", args.subList(1, args.size())));
        if (!outcome.ok()) {
            feedback.failed(sender, outcome);
            return true;
        }
        feedback.listAdded(sender, listPath, outcome);
        return true;
    }

    private boolean listRemoveEntry(CommandSender sender, String listPath, String rawIndex) {
        int index;
        try {
            index = Integer.parseInt(rawIndex.trim());
        } catch (NumberFormatException expected) {
            support.message(sender, texts.getSettingIndexInvalid(), Map.of("setting", listPath,
                    "index", rawIndex.trim(),
                    "size", String.valueOf(config.getStringList(listPath).size())));
            return true;
        }
        ConfigService.SetOutcome outcome = config.listRemove(listPath, index);
        if (!outcome.ok()) {
            feedback.failed(sender, outcome);
            return true;
        }
        feedback.listRemoved(sender, listPath, outcome);
        return true;
    }

    private boolean listResetEntries(CommandSender sender, String listPath) {
        ConfigService.SetOutcome outcome = config.listReset(listPath);
        if (!outcome.ok()) {
            feedback.failed(sender, outcome);
            return true;
        }
        feedback.listReset(sender, listPath, outcome);
        return true;
    }

    private boolean showOrUpdateSetting(CommandSender sender, String setting, List<String> values) {
        Object oldValue = game.getSettingValue(setting);
        if (values.isEmpty()) {
            support.message(sender, texts.getSettingStatus(),
                    Map.of("setting", setting, "value", ConfigService.displayValue(oldValue)));
            support.neutralSound(sender);
            return true;
        }
        SettingDescriptor descriptor = config.describe(setting);
        boolean freeform = (descriptor != null && descriptor.type() == SettingType.STRING)
                || config.isIndexPath(setting);
        String raw;
        if (freeform) {
            raw = String.join(" ", values);
        } else if (values.size() > 1) {
            return support.message(sender, texts.getConfigUsage());
        } else {
            raw = values.get(0);
        }
        ConfigService.SetOutcome outcome = game.setSetting(setting, raw);
        if (!outcome.ok()) {
            feedback.failed(sender, outcome);
            return true;
        }
        feedback.scalarUpdated(sender, setting, outcome);
        if (setting.equals("world-engine.enabled")) {
            observeWorldEngine.run();
        }
        return true;
    }
}
