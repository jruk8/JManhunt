package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** One color tag per role. */
@SuppressWarnings("FieldMayBeFinal")
public class RoleColorsMessages extends OkaeriConfig {

    @CustomKey("speedrunner")
    private String speedrunner = "<#74de66>";

    @CustomKey("hunter")
    private String hunter = "<#de666e>";

    @CustomKey("spectator")
    private String spectator = "<#6e728a>";

    @CustomKey("afk")
    private String afk = "<#a18e68>";

    @CustomKey("none")
    private String none = "<#7d7d7d>";
}
