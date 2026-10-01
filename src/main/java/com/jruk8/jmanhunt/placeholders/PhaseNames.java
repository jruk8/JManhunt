package com.jruk8.jmanhunt.placeholders;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;

/**
 * Phase names used by game_phase. Field names stay single lowercase
 * words so the yaml keys never depend on a naming strategy.
 */
@Getter
@Setter
public class PhaseNames extends OkaeriConfig {

    @Comment("No game is running in the lobby.")
    private String lobby = "LOBBY";

    @Comment("Match started but the pre-start window is still open.")
    private String prestart = "PRESTART";

    @Comment("Match is running.")
    private String inprogress = "IN_PROGRESS";

    @Comment("Match ended and the end delay is running.")
    private String ended = "ENDED";

}
