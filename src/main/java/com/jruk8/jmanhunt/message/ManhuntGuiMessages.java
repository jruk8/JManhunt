package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Settings browser, confirm panel, and dialog labels. */
@SuppressWarnings("FieldMayBeFinal")
public class ManhuntGuiMessages extends OkaeriConfig {

    @CustomKey("back")
    private String back = "Back";

    @CustomKey("cancel")
    private String cancel = "Cancel";

    @CustomKey("confirm")
    private String confirm = "Confirm";

    @CustomKey("scroll-up")
    private String scrollUp = "Scroll up";

    @CustomKey("scroll-down")
    private String scrollDown = "Scroll down";

    @CustomKey("dialog-submit")
    private String dialogSubmit = "Submit";

    @CustomKey("dialog-cancel")
    private String dialogCancel = "Cancel";

    @CustomKey("dialog-current")
    private String dialogCurrent = "Current value: <white>{value}";

    @CustomKey("dialog-bounds")
    private String dialogBounds = "Allowed: <white>{bounds}";

    @CustomKey("dialog-too-long")
    private String dialogTooLong = "{prefix}<red>That value is too long to edit here " +
            "(<white>{length}</white> of max <white>{max}</white>). Edit it in the config file instead.";

    @CustomKey("dialog-title-edit")
    private String dialogTitleEdit = "Edit {name}";

    @CustomKey("setting-value")
    private String settingValue = "Value: <white>{value}";

    @CustomKey("setting-path")
    private String settingPath = "Path: <white>{path}";

    @CustomKey("setting-type")
    private String settingType = "Type: <white>{type}";

    @CustomKey("setting-default")
    private String settingDefault = "Default: <white>{value}";

    @CustomKey("setting-hint-toggle")
    private String settingHintToggle = "Click to toggle";

    @CustomKey("setting-hint-cycle")
    private String settingHintCycle = "Click to cycle";

    @CustomKey("setting-hint-edit")
    private String settingHintEdit = "Click to edit";

    @CustomKey("setting-hint-reset")
    private String settingHintReset = "Right-click to reset";

    @CustomKey("setting-already-default")
    private String settingAlreadyDefault = "{prefix}<yellow>That setting is already the default.";

    @CustomKey("setting-reset-title")
    private String settingResetTitle = "Reset {name}?";

    @CustomKey("title-root")
    private String titleRoot = "JManhunt";

    @CustomKey("title-settings")
    private String titleSettings = "Settings";

    @CustomKey("title-category")
    private String titleCategory = "{name} Settings";

    @CustomKey("to-settings")
    private String toSettings = "Settings";

    @CustomKey("to-settings-lore")
    private String toSettingsLore = "Match, Compass, Players, Server";

    @CustomKey("to-history")
    private String toHistory = "History";

    @CustomKey("to-modifiers")
    private String toModifiers = "Modifiers";

    @CustomKey("to-modifiers-lore")
    private String toModifiersLore = "Toggle modifiers and presets";

    @CustomKey("category-lore")
    private String categoryLore = "{count} entries";

    @CustomKey("list-hint-open")
    private String listHintOpen = "Click to open";

    @CustomKey("list-entry-name")
    private String listEntryName = "#{index}";

    @CustomKey("list-hint-edit")
    private String listHintEdit = "Click to edit";

    @CustomKey("list-hint-delete")
    private String listHintDelete = "Right-click to delete";

    @CustomKey("list-add-name")
    private String listAddName = "Add entry";

    @CustomKey("list-add-lore")
    private String listAddLore = "Click to append";

    @CustomKey("dialog-title-edit-entry")
    private String dialogTitleEditEntry = "Edit entry {index}";

    @CustomKey("dialog-title-add-entry")
    private String dialogTitleAddEntry = "Add entry";

    @CustomKey("dialog-title-delete-entry")
    private String dialogTitleDeleteEntry = "Delete entry {index}?";

    @CustomKey("history-line-matches")
    private String historyLineMatches = "Matches played: <white>{value}";

    @CustomKey("history-line-kills")
    private String historyLineKills = "Total kills: <white>{value}";

    @CustomKey("history-line-hunter-kills")
    private String historyLineHunterKills = "Hunter kills: <white>{value}";

    @CustomKey("history-line-speedrunner-kills")
    private String historyLineSpeedrunnerKills = "Speedrunner kills: <white>{value}";

    @CustomKey("history-line-hunter-wins")
    private String historyLineHunterWins = "Hunter wins: <white>{value}";

    @CustomKey("history-line-speedrunner-wins")
    private String historyLineSpeedrunnerWins = "Speedrunner wins: <white>{value}";

    @CustomKey("history-line-damage")
    private String historyLineDamage = "Damage dealt: <white>{value}";

    @CustomKey("history-line-playtime")
    private String historyLinePlaytime = "Total playtime: <white>{value}";

    @CustomKey("setup-first-title")
    private String setupFirstTitle = "First-Time Setup";

    @CustomKey("setup-first-line1")
    private String setupFirstLine1 = "JManhunt is not set up yet.";

    @CustomKey("setup-first-line2")
    private String setupFirstLine2 = "Start the interactive setup guide?";

    @CustomKey("setup-first-confirm")
    private String setupFirstConfirm = "<green>Start setup";

    @CustomKey("setup-first-cancel")
    private String setupFirstCancel = "<red>Skip forever (not recommended)";
}
