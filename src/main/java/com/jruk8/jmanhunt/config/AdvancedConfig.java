package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;

/** Advanced controls: match lifecycle, lobbies, and misc power toggles. */
@SuppressWarnings("FieldMayBeFinal")
public class AdvancedConfig extends OkaeriConfig {

    @CustomKey("advanced-match-controls")
    @Comment({
            "Match lifecycle settings: end-of-match delay, pre-start reminders,",
            "disconnect rules, end-screen statistics, and game rules."
    })
    private MatchConfig advancedMatchControls = new MatchConfig();

    @Comment({
            "Lobby queues. Each lobby runs its own queue, autostart countdown, and match.",
            "Players join the default lobby on login and keep it until they join another",
            "lobby or leave the server."
    })
    private LobbiesConfig lobbies = new LobbiesConfig();

    @Comment("Power-user toggles that rarely need changing.")
    private MiscConfig misc = new MiscConfig();

    public MatchConfig getAdvancedMatchControls() {
        return advancedMatchControls;
    }

    public void setAdvancedMatchControls(MatchConfig advancedMatchControls) {
        this.advancedMatchControls = advancedMatchControls;
    }

    public LobbiesConfig getLobbies() {
        return lobbies;
    }

    public void setLobbies(LobbiesConfig lobbies) {
        this.lobbies = lobbies;
    }

    public MiscConfig getMisc() {
        return misc;
    }

    public void setMisc(MiscConfig misc) {
        this.misc = misc;
    }
}
