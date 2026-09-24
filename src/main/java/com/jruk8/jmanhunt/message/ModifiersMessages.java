package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Modifier toggle command responses. */
@SuppressWarnings("FieldMayBeFinal")
public class ModifiersMessages extends OkaeriConfig {

    @CustomKey("usage")
    private String usage = "{prefix}<yellow>Usage: <white>/manhunt modifiers [setmod <name> " +
            "<true|false>|setpreset <id> <true|false>]<yellow>.";

    @CustomKey("setmod-usage")
    private String setmodUsage = "{prefix}<yellow>Usage: <white>/manhunt modifiers setmod <name> " +
            "<true|false><yellow>.";

    @CustomKey("setpreset-usage")
    private String setpresetUsage = "{prefix}<yellow>Usage: <white>/manhunt modifiers setpreset <id> " +
            "<true|false><yellow>.";

    @CustomKey("unknown-modifier")
    private String unknownModifier = "{prefix}<red>Unknown modifier <white>{name}<red>. Use one of: " +
            "<white>{valid}<red>.";

    @CustomKey("unknown-preset")
    private String unknownPreset = "{prefix}<red>Unknown preset <white>{name}<red>. Use one of: " +
            "<white>{valid}<red>.";

    @CustomKey("invalid-state")
    private String invalidState = "{prefix}<red>State must be <white>true<red> or <white>false<red>.";

    @CustomKey("setmod-success")
    private String setmodSuccess = "{prefix}<green>Modifier <white>{name}<green> is now <white>{state}<green>.";

    @CustomKey("setpreset-success")
    private String setpresetSuccess = "{prefix}<green>Preset <white>{name}<green> is now <white>{state}<green> " +
            "(<white>{count}<green> modifiers).";

    @CustomKey("list-header")
    private String listHeader = "\n{prefix}\n<#de7766>Modifiers:";

    @CustomKey("list-entry-on")
    private String listEntryOn = "<white>» {name} <green>[on]";

    @CustomKey("list-entry-off")
    private String listEntryOff = "<white>» {name} <gray>[off]";

    @CustomKey("list-presets-header")
    private String listPresetsHeader = "\n<#de7766>Presets:";

    @CustomKey("list-empty")
    private String listEmpty = "{prefix}<yellow>No modifiers defined.";

    @CustomKey("toggle-announced")
    private String toggleAnnounced = "{prefix}<white>{player} <yellow>set <gray>{key}</gray> to " +
            "<white>{value}</white>.</yellow></white>";

    @CustomKey("toggle-all-success")
    private String toggleAllSuccess = "{prefix}<green>Toggled all <white>{count} {kind}</white> to " +
            "<white>{state}</white>.";

    @CustomKey("toggle-all-announced")
    private String toggleAllAnnounced = "{prefix}<white>{player} <yellow>toggled all <gray>{count} {kind}</gray> " +
            "to <white>{state}</white>.</yellow></white>";

    @CustomKey("export-usage")
    private String exportUsage = "{prefix}<yellow>Usage: /manhunt modifiers export <modifier|preset> <id>";

    @CustomKey("import-usage")
    private String importUsage = "{prefix}<yellow>Usage: /manhunt modifiers import <modifier|preset> <payload>";

    @CustomKey("exported")
    private String exported = "{prefix}<green><underlined>Click to copy <white>{type} {name}</white> " +
            "to clipboard</underlined>";

    @CustomKey("imported")
    private String imported = "{prefix}<green>Imported <white>{name}<green>.";

    @CustomKey("import-failed")
    private String importFailed = "{prefix}<red>That import string is invalid or corrupted.";

    @CustomKey("create-usage")
    private String createUsage = "{prefix}<yellow>Usage: /manhunt modifiers create <modifier|preset> " +
            "<name> [flags...]\n{prefix}<gray>Modifier flags: --desc --item --author --trigger --on-start " +
            "--interval --deviation --interval-scope --chance --chance-scope --selection --pick-count " +
            "--pick-scope --delay --console --player --hunter --speedrunner --console-cleanup " +
            "--player-cleanup\n{prefix}<gray>Preset flags: --desc --item --member";

    @CustomKey("create-unknown-flag")
    private String createUnknownFlag = "{prefix}<red>Unknown flag <white>{flag}</white>.";

    @CustomKey("create-missing-value")
    private String createMissingValue = "{prefix}<red>Flag <white>{flag}</white> needs a value.";

    @CustomKey("create-bad-number")
    private String createBadNumber = "{prefix}<red>Flag <white>{flag}</white> got a bad number: " +
            "<white>{value}</white>.";

    @CustomKey("create-bad-enum")
    private String createBadEnum = "{prefix}<red>Flag <white>{flag}</white> wants {valid}, got " +
            "<white>{value}</white>.";

    @CustomKey("create-unknown-trigger")
    private String createUnknownTrigger = "{prefix}<red>Unknown trigger <white>{value}</white>. Valid: " +
            "<gray>{valid}</gray>.";

    @CustomKey("create-unknown-member")
    private String createUnknownMember = "{prefix}<red>Unknown modifier <white>{value}</white>.";

    @CustomKey("create-bad-item")
    private String createBadItem = "{prefix}<red>Unknown item <white>{value}</white>.";

    @CustomKey("create-bad-command")
    private String createBadCommand = "{prefix}<red>Bad command in <white>{list}</white>: {error}";

    @CustomKey("create-deviation-range")
    private String createDeviationRange = "{prefix}<red>Deviation ({value}) needs --interval and " +
            "must not exceed it.";

    @CustomKey("create-success")
    private String createSuccess = "{prefix}<green>Created {type} <white>{name}<green>.";

    @CustomKey("create-command-warning")
    private String createCommandWarning = "{prefix}<yellow>Warning: {warning}";

    @CustomKey("edit-invalid")
    private String editInvalid = "{prefix}<red>Invalid value: {error}";

    @CustomKey("edit-renamed")
    private String editRenamed = "{prefix}<green>Renamed to <white>{name}<green>.";

    @CustomKey("edit-deleted")
    private String editDeleted = "{prefix}<green>Deleted <white>{name}<green>.";
}
