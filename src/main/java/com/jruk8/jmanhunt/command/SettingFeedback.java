package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.Map;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Chat feedback for setting writes, shared by the config command and the
 * settings GUI so validation messages, announces, and sounds are identical
 * on both paths. Failures stay silent apart from the error message; callers
 * add their own failure sound when their surface needs one.
 */
public final class SettingFeedback {

    private final MessageService messages;
    private final ConfigService config;
    private final SoundService sounds;

    public SettingFeedback(MessageService messages, ConfigService config,
            SoundService sounds) {
        this.messages = messages;
        this.config = config;
        this.sounds = sounds;
    }

    /** Reports a failed write with its validation message. */
    public void failed(CommandSender sender, ConfigService.SetOutcome outcome) {
        messages.message(sender, outcome.errorKey(), outcome.slots());
    }

    /** Reports a scalar write with the restart nudge, announce, and sound. */
    public void scalarUpdated(CommandSender sender, String setting,
            ConfigService.SetOutcome outcome) {
        if (unchanged(outcome)) {
            messages.message(sender, "manhunt.setting-unchanged", Map.of("setting", setting,
                    "value", ConfigService.displayValue(outcome.newValue())));
            return;
        }
        messages.message(sender, "manhunt.setting-updated", Map.of("setting", setting,
                "value", ConfigService.displayValue(outcome.newValue()),
                "old-value", ConfigService.displayValue(outcome.oldValue())));
        if (outcome.descriptor() != null && outcome.descriptor().restartRequired()) {
            messages.message(sender, "manhunt.setting-restart-required");
        }
        announce(sender, setting, ConfigService.displayValue(outcome.newValue()));
        neutralSound(sender);
    }

    /** Reports a list append with announce and sound. */
    public void listAdded(CommandSender sender, String listPath,
            ConfigService.SetOutcome outcome) {
        messages.message(sender, "manhunt.setting-list-added", Map.of("setting", listPath,
                "value", ConfigService.displayValue(outcome.newValue())));
        announce(sender, listPath, ConfigService.displayValue(outcome.newValue()));
        neutralSound(sender);
    }

    /** Reports a list removal with announce and sound. */
    public void listRemoved(CommandSender sender, String listPath,
            ConfigService.SetOutcome outcome) {
        messages.message(sender, "manhunt.setting-list-removed", Map.of("setting", listPath,
                "value", ConfigService.displayValue(outcome.oldValue())));
        announce(sender, listPath, "-");
        neutralSound(sender);
    }

    /** Reports a list reset, or the nothing-changed line when already default. */
    public void listReset(CommandSender sender, String listPath,
            ConfigService.SetOutcome outcome) {
        if (unchanged(outcome)) {
            messages.message(sender, "manhunt.setting-unchanged", Map.of("setting", listPath,
                    "value", ConfigService.displayValue(outcome.newValue())));
            return;
        }
        messages.message(sender, "manhunt.setting-list-reset", Map.of("setting", listPath));
        announce(sender, listPath, ConfigService.displayValue(outcome.newValue()));
        neutralSound(sender);
    }

    /** Reports a scalar override write with announce and sound. */
    public void overrideScalarUpdated(CommandSender sender, int lobby, String setting,
            ConfigService.SetOutcome outcome) {
        if (unchanged(outcome)) {
            messages.message(sender, "manhunt.override-setting-unchanged", Map.of("lobby",
                    String.valueOf(lobby), "setting", setting,
                    "value", ConfigService.displayValue(outcome.newValue())));
            return;
        }
        messages.message(sender, "manhunt.override-setting-updated", Map.of("lobby",
                String.valueOf(lobby), "setting", setting,
                "value", ConfigService.displayValue(outcome.newValue()),
                "old-value", ConfigService.displayValue(outcome.oldValue())));
        announce(sender, "lobby." + lobby + "." + setting,
                ConfigService.displayValue(outcome.newValue()));
        neutralSound(sender);
    }

    /** Reports an override list append with announce and sound. */
    public void overrideListAdded(CommandSender sender, int lobby, String listPath,
            ConfigService.SetOutcome outcome) {
        messages.message(sender, "manhunt.override-list-added", Map.of("lobby",
                String.valueOf(lobby), "setting", listPath,
                "value", ConfigService.displayValue(outcome.newValue())));
        announce(sender, "lobby." + lobby + "." + listPath,
                ConfigService.displayValue(outcome.newValue()));
        neutralSound(sender);
    }

    /** Reports an override list removal with announce and sound. */
    public void overrideListRemoved(CommandSender sender, int lobby, String listPath,
            ConfigService.SetOutcome outcome) {
        messages.message(sender, "manhunt.override-list-removed", Map.of("lobby",
                String.valueOf(lobby), "setting", listPath,
                "value", ConfigService.displayValue(outcome.oldValue())));
        announce(sender, "lobby." + lobby + "." + listPath, "-");
        neutralSound(sender);
    }

    /** Reports clearing overrides, or the nothing-stored line when empty. */
    public void overrideCleared(CommandSender sender, int lobby, String path, int removed) {
        if (removed <= 0) {
            messages.message(sender, "manhunt.override-nothing-to-clear", Map.of("lobby",
                    String.valueOf(lobby), "setting", path));
            return;
        }
        if (removed == 1) {
            messages.message(sender, "manhunt.override-removed", Map.of("lobby",
                    String.valueOf(lobby), "setting", path));
        } else {
            messages.message(sender, "manhunt.override-cleared", Map.of("lobby",
                    String.valueOf(lobby), "setting", path,
                    "count", String.valueOf(removed)));
        }
        announce(sender, "lobby." + lobby + "." + path, "cleared");
        neutralSound(sender);
    }

    /** Reports a modifier override write with announce and sound. */
    public void overrideModifierSet(CommandSender sender, int lobby, String id, boolean value) {
        messages.message(sender, "manhunt.override-modifier-set", Map.of("lobby",
                String.valueOf(lobby), "modifier", id, "state", value ? "on" : "off"));
        announce(sender, "lobby." + lobby + ".modifiers." + id, value ? "on" : "off");
        neutralSound(sender);
    }

    /** Reports a removed modifier override with announce and sound. */
    public void overrideModifierCleared(CommandSender sender, int lobby, String id) {
        messages.message(sender, "manhunt.override-modifier-cleared", Map.of("lobby",
                String.valueOf(lobby), "modifier", id));
        announce(sender, "lobby." + lobby + ".modifiers." + id, "cleared");
        neutralSound(sender);
    }

    /** Reports a preset override write with announce and sound. */
    public void overridePresetSet(CommandSender sender, int lobby, String id, boolean value,
            int count) {
        messages.message(sender, "manhunt.override-preset-set", Map.of("lobby",
                String.valueOf(lobby), "preset", id, "state", value ? "on" : "off",
                "count", String.valueOf(count)));
        announce(sender, "lobby." + lobby + ".preset." + id, value ? "on" : "off");
        neutralSound(sender);
    }

    /** Reports a bulk override write with announce and sound. */
    public void overrideBulkSet(CommandSender sender, int lobby, String kind, int count,
            boolean value) {
        messages.message(sender, "manhunt.override-bulk-set", Map.of("lobby",
                String.valueOf(lobby), "kind", kind, "count", String.valueOf(count),
                "state", value ? "on" : "off"));
        announce(sender, "lobby." + lobby + "." + kind, value ? "on" : "off");
        neutralSound(sender);
    }

    /** Reports clearing a whole lobby, or the empty line when none exist. */
    public void overrideLobbyCleared(CommandSender sender, int lobby, int removed) {
        if (removed <= 0) {
            messages.message(sender, "manhunt.override-lobby-empty",
                    Map.of("lobby", String.valueOf(lobby)));
            return;
        }
        messages.message(sender, "manhunt.override-lobby-cleared", Map.of("lobby",
                String.valueOf(lobby), "count", String.valueOf(removed)));
        announce(sender, "lobby." + lobby, "cleared");
        neutralSound(sender);
    }

    /** True when a write left the canonical value untouched. */
    private static boolean unchanged(ConfigService.SetOutcome outcome) {
        return ConfigService.displayValue(outcome.oldValue())
                .equals(ConfigService.displayValue(outcome.newValue()));
    }

    private void announce(CommandSender sender, String keySlot, String valueSlot) {
        ManhuntCommand.announceSettingChange(messages,
                config.getBoolean("settings.server.announce-config-changes", false),
                sender, "manhunt.setting-change-announced", keySlot, valueSlot);
    }

    private void neutralSound(CommandSender sender) {
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
    }
}
