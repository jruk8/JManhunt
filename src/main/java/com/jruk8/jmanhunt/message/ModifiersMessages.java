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
}
