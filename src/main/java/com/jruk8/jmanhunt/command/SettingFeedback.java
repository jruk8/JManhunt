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
