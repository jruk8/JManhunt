package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Shared command responses. */
@SuppressWarnings("FieldMayBeFinal")
public class CommandMessages extends OkaeriConfig {

    @CustomKey("player-only")
    private String playerOnly = "{prefix}<red>Only players can use this command.";

    @CustomKey("no-permission")
    private String noPermission = "{prefix}<red>You do not have permission to use this command.";

    @CustomKey("invalid")
    private String invalid = "{prefix}<red>Unknown command. Use <yellow>/manhunt help<red>.";

    @CustomKey("no-targets")
    private String noTargets = "{prefix}<yellow>No players matched that selector.";
}
