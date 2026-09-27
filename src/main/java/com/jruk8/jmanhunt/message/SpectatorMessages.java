package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Spectator toolbar buttons, browser menus, and feedback. */
@SuppressWarnings("FieldMayBeFinal")
public class SpectatorMessages extends OkaeriConfig {

    @CustomKey("toolbar-back-name")
    private String toolbarBackName = "Back to Lobby";

    @CustomKey("toolbar-back-lore")
    private String toolbarBackLore = "Return to your lobby\\nLeave spectator mode";

    @CustomKey("toolbar-lobbies-name")
    private String toolbarLobbiesName = "Browse Matches";

    @CustomKey("toolbar-lobbies-lore")
    private String toolbarLobbiesLore = "Right-click to spectate\\nanother match";

    @CustomKey("toolbar-players-name")
    private String toolbarPlayersName = "Spectate Player";

    @CustomKey("toolbar-players-lore")
    private String toolbarPlayersLore = "Teleport to a player\\nand follow them";

    @CustomKey("lobbies-title")
    private String lobbiesTitle = "Running Matches";

    @CustomKey("lobbies-entry-name")
    private String lobbiesEntryName = "{label}";

    @CustomKey("lobbies-entry-lore")
    private String lobbiesEntryLore = "{info}\\n{hint}";

    @CustomKey("lobbies-entry-info")
    private String lobbiesEntryInfo = "{runners} runners, {hunters} hunters";

    @CustomKey("lobbies-entry-hint")
    private String lobbiesEntryHint = "Click to spectate";

    @CustomKey("lobbies-current-hint")
    private String lobbiesCurrentHint = "You are here";

    @CustomKey("players-title")
    private String playersTitle = "Spectate Player";

    @CustomKey("players-entry-hint")
    private String playersEntryHint = "Click to teleport and follow";

    @CustomKey("match-gone")
    private String matchGone = "{prefix}<red>That match is no longer running.";

    @CustomKey("no-matches")
    private String noMatches = "{prefix}<yellow>No matches are running right now.";

    @CustomKey("no-players")
    private String noPlayers = "{prefix}<yellow>No speedrunners or hunters to spectate right now.";

    @CustomKey("now-spectating")
    private String nowSpectating = "{prefix}<green>Now spectating [{role}<green>] <white>{player}";

    @CustomKey("following-actionbar")
    private String followingActionbar = "<gray>Following [{role}<gray>] <white>{player} <gray>• shift to exit";

    @CustomKey("follow-exited")
    private String followExited = "{prefix}<gray>Stopped following <white>{player}<gray>.";

    @CustomKey("scroll-up")
    private String scrollUp = "Scroll up";

    @CustomKey("scroll-down")
    private String scrollDown = "Scroll down";

    @CustomKey("back")
    private String back = "Back";
}
