package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Developer schematic tool responses. */
@SuppressWarnings("FieldMayBeFinal")
public class DevMessages extends OkaeriConfig {

    @CustomKey("usage")
    private String usage = "{prefix}<yellow>Usage: <white>/manhunt dev schem <pos1|pos2|save|load|list><yellow>.";

    @CustomKey("pos1")
    private String pos1 = "{prefix}<green>Position 1 set to <white>{pos}<green>.";

    @CustomKey("pos2")
    private String pos2 = "{prefix}<green>Position 2 set to <white>{pos}<green>.";

    @CustomKey("need-selection")
    private String needSelection = "{prefix}<red>Set both corners first with <white>pos1<red> and " +
            "<white>pos2<red>.";

    @CustomKey("world-mismatch")
    private String worldMismatch = "{prefix}<red>Both corners must be in the same world.";

    @CustomKey("invalid-name")
    private String invalidName = "{prefix}<red>Invalid schematic name.";

    @CustomKey("saved")
    private String saved = "{prefix}<green>Saved schematic <white>{name}<green> ({size}).";

    @CustomKey("save-failed")
    private String saveFailed = "{prefix}<red>Could not save schematic <white>{name}<red>.";

    @CustomKey("pasted")
    private String pasted = "{prefix}<green>Pasted schematic <white>{name}<green>.";

    @CustomKey("load-missing")
    private String loadMissing = "{prefix}<red>No schematic named <white>{name}<red>.";

    @CustomKey("load-failed")
    private String loadFailed = "{prefix}<red>Could not paste schematic <white>{name}<red>.";

    @CustomKey("list")
    private String list = "{prefix}<green>Schematics: <white>{value}";

    @CustomKey("list-empty")
    private String listEmpty = "{prefix}<yellow>No schematics saved yet.";
}
