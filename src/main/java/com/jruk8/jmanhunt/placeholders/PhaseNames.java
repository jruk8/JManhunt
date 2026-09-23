package com.jruk8.jmanhunt.placeholders;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;

/**
 * Phase names used by game_phase. Field names stay single lowercase
 * words so the yaml keys never depend on a naming strategy.
 */
public class PhaseNames extends OkaeriConfig {

    @Comment("No game is running in the lobby.")
    private String lobby = "LOBBY";

    @Comment("Match started but the pre-start window is still open.")
    private String prestart = "PRESTART";

    @Comment("Match is running.")
    private String inprogress = "IN_PROGRESS";

    @Comment("Match ended and the end delay is running.")
    private String ended = "ENDED";

    public String getLobby() {
        return lobby;
    }

    public void setLobby(String lobby) {
        this.lobby = lobby;
    }

    public String getPrestart() {
        return prestart;
    }

    public void setPrestart(String prestart) {
        this.prestart = prestart;
    }

    public String getInprogress() {
        return inprogress;
    }

    public void setInprogress(String inprogress) {
        this.inprogress = inprogress;
    }

    public String getEnded() {
        return ended;
    }

    public void setEnded(String ended) {
        this.ended = ended;
    }
}
