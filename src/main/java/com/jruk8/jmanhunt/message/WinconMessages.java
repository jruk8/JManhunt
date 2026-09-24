package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Win-condition sentence fragments for the status-win lines. */
@SuppressWarnings("FieldMayBeFinal")
public class WinconMessages extends OkaeriConfig {

    @CustomKey("eliminate-hunters")
    private String eliminateHunters = "eliminate all hunters";

    @CustomKey("eliminate-speedrunners")
    private String eliminateSpeedrunners = "eliminate all speedrunners";

    @CustomKey("credits")
    private String credits = "credits screen";

    @CustomKey("survive")
    private String survive = "survive {time}";

    @CustomKey("acquired")
    private String acquired = "{item} acquired";

    @CustomKey("advancement")
    private String advancement = "advancement {advancement}";

    @CustomKey("killed")
    private String killed = "{mob} killed";

    @CustomKey("time-limit")
    private String timeLimit = "time limit {time}";
}
