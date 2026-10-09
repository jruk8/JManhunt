package com.jruk8.jmanhunt.command;

import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.Map;
import org.bukkit.Bukkit;
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
    private final ManhuntMessages manhunt;
    private final ConfigService config;
    private final SoundService sounds;

    public SettingFeedback(MessageService messages, ManhuntMessages manhunt, ConfigService config,
            SoundService sounds) {
        this.messages = messages;
        this.manhunt = manhunt;
        this.config = config;
        this.sounds = sounds;
    }

    /** Reports a failed write with its validation message. */
    public void failed(CommandSender sender, ConfigService.SetOutcome outcome) {
        messages.messageRaw(sender, errorTemplate(outcome.errorKey()), outcome.slots());
    }

    /**
     * Maps framework failure keys to templates. The keys stay in the
     * exempt settings framework; this switch is their single typed edge.
     */
    private String errorTemplate(String errorKey) {
        return switch (errorKey) {
            case "manhunt.setting-invalid-number" -> manhunt.getSettingInvalidNumber();
            case "manhunt.setting-invalid-value" -> manhunt.getSettingInvalidValue();
            case "manhunt.setting-invalid-option" -> manhunt.getSettingInvalidOption();
            case "manhunt.setting-out-of-range" -> manhunt.getSettingOutOfRange();
            case "manhunt.setting-index-invalid" -> manhunt.getSettingIndexInvalid();
            default -> manhunt.getSettingInvalid();
        };
    }

    /** Reports a scalar write with the restart nudge, announce, and sound. */
    public void scalarUpdated(CommandSender sender, String setting,
            ConfigService.SetOutcome outcome) {
        if (unchanged(outcome)) {
            messages.messageRaw(sender, manhunt.getSettingUnchanged(), Map.of("setting", setting,
                    "value", ConfigService.displayValue(outcome.newValue())));
            return;
        }
        messages.messageRaw(sender, manhunt.getSettingUpdated(), Map.of("setting", setting,
                "value", ConfigService.displayValue(outcome.newValue()),
                "old-value", ConfigService.displayValue(outcome.oldValue())));
        if (outcome.descriptor() != null && outcome.descriptor().restartRequired()) {
            messages.messageRaw(sender, manhunt.getSettingRestartRequired());
        }
        announce(sender, setting, ConfigService.displayValue(outcome.newValue()));
        neutralSound(sender);
    }

    /** Reports a list append with announce and sound. */
    public void listAdded(CommandSender sender, String listPath,
            ConfigService.SetOutcome outcome) {
        messages.messageRaw(sender, manhunt.getSettingListAdded(), Map.of("setting", listPath,
                "value", ConfigService.displayValue(outcome.newValue())));
        announce(sender, listPath, ConfigService.displayValue(outcome.newValue()));
        neutralSound(sender);
    }

    /** Reports a list removal with announce and sound. */
    public void listRemoved(CommandSender sender, String listPath,
            ConfigService.SetOutcome outcome) {
        messages.messageRaw(sender, manhunt.getSettingListRemoved(), Map.of("setting", listPath,
                "value", ConfigService.displayValue(outcome.oldValue())));
        announce(sender, listPath, "-");
        neutralSound(sender);
    }

    /** Reports a list reset, or the nothing-changed line when already default. */
    public void listReset(CommandSender sender, String listPath,
            ConfigService.SetOutcome outcome) {
        if (unchanged(outcome)) {
            messages.messageRaw(sender, manhunt.getSettingUnchanged(), Map.of("setting", listPath,
                    "value", ConfigService.displayValue(outcome.newValue())));
            return;
        }
        messages.messageRaw(sender, manhunt.getSettingListReset(), Map.of("setting", listPath));
        announce(sender, listPath, ConfigService.displayValue(outcome.newValue()));
        neutralSound(sender);
    }

    /** Reports a scalar override write with announce and sound. */
    public void overrideScalarUpdated(CommandSender sender, int lobby, String setting,
            ConfigService.SetOutcome outcome) {
        if (unchanged(outcome)) {
            messages.messageRaw(sender, manhunt.getOverrideSettingUnchanged(), Map.of("lobby",
                    String.valueOf(lobby), "setting", setting,
                    "value", ConfigService.displayValue(outcome.newValue())));
            return;
        }
        messages.messageRaw(sender, manhunt.getOverrideSettingUpdated(), Map.of("lobby",
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
        messages.messageRaw(sender, manhunt.getOverrideListAdded(), Map.of("lobby",
                String.valueOf(lobby), "setting", listPath,
                "value", ConfigService.displayValue(outcome.newValue())));
        announce(sender, "lobby." + lobby + "." + listPath,
                ConfigService.displayValue(outcome.newValue()));
        neutralSound(sender);
    }

    /** Reports an override list removal with announce and sound. */
    public void overrideListRemoved(CommandSender sender, int lobby, String listPath,
            ConfigService.SetOutcome outcome) {
        messages.messageRaw(sender, manhunt.getOverrideListRemoved(), Map.of("lobby",
                String.valueOf(lobby), "setting", listPath,
                "value", ConfigService.displayValue(outcome.oldValue())));
        announce(sender, "lobby." + lobby + "." + listPath, "-");
        neutralSound(sender);
    }

    /** Reports clearing overrides, or the nothing-stored line when empty. */
    public void overrideCleared(CommandSender sender, int lobby, String path, int removed) {
        if (removed <= 0) {
            messages.messageRaw(sender, manhunt.getOverrideNothingToClear(), Map.of("lobby",
                    String.valueOf(lobby), "setting", path));
            return;
        }
        if (removed == 1) {
            messages.messageRaw(sender, manhunt.getOverrideRemoved(), Map.of("lobby",
                    String.valueOf(lobby), "setting", path));
        } else {
            messages.messageRaw(sender, manhunt.getOverrideCleared(), Map.of("lobby",
                    String.valueOf(lobby), "setting", path,
                    "count", String.valueOf(removed)));
        }
        announce(sender, "lobby." + lobby + "." + path, "cleared");
        neutralSound(sender);
    }

    /** Reports a modifier override write with announce and sound. */
    public void overrideModifierSet(CommandSender sender, int lobby, String id, boolean value) {
        messages.messageRaw(sender, manhunt.getOverrideModifierSet(), Map.of("lobby",
                String.valueOf(lobby), "modifier", config.modifiers().metaName(id),
                "state", value ? "on" : "off"));
        announce(sender, "lobby." + lobby + ".modifiers." + id, value ? "on" : "off");
        neutralSound(sender);
    }

    /** Reports a removed modifier override with announce and sound. */
    public void overrideModifierCleared(CommandSender sender, int lobby, String id) {
        messages.messageRaw(sender, manhunt.getOverrideModifierCleared(), Map.of("lobby",
                String.valueOf(lobby), "modifier", id));
        announce(sender, "lobby." + lobby + ".modifiers." + id, "cleared");
        neutralSound(sender);
    }

    /** Reports a preset override write with announce and sound. */
    public void overridePresetSet(CommandSender sender, int lobby, String id, boolean value,
            int count) {
        messages.messageRaw(sender, manhunt.getOverridePresetSet(), Map.of("lobby",
                String.valueOf(lobby), "preset", config.modifiers().presetName(id),
                "state", value ? "on" : "off",
                "count", String.valueOf(count)));
        announce(sender, "lobby." + lobby + ".preset." + id, value ? "on" : "off");
        neutralSound(sender);
    }

    /** Reports a bulk override write with announce and sound. */
    public void overrideBulkSet(CommandSender sender, int lobby, String kind, int count,
            boolean value) {
        messages.messageRaw(sender, manhunt.getOverrideBulkSet(), Map.of("lobby",
                String.valueOf(lobby), "kind", kind, "count", String.valueOf(count),
                "state", value ? "on" : "off"));
        announce(sender, "lobby." + lobby + "." + kind, value ? "on" : "off");
        neutralSound(sender);
    }

    /** Reports clearing a whole lobby, or the empty line when none exist. */
    public void overrideLobbyCleared(CommandSender sender, int lobby, int removed) {
        if (removed <= 0) {
            messages.messageRaw(sender, manhunt.getOverrideLobbyEmpty(),
                    Map.of("lobby", String.valueOf(lobby)));
            return;
        }
        messages.messageRaw(sender, manhunt.getOverrideLobbyCleared(), Map.of("lobby",
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
        announceSettingChange(messages,
                config.server().isAnnounceConfigChanges(),
                sender, manhunt.getSettingChangeAnnounced(), keySlot, valueSlot);
    }

    private void neutralSound(CommandSender sender) {
        if (sender instanceof Player player) {
            sounds.playNeutralSound(player);
        }
    }

    /**
     * Broadcasts a config change to online players except the changer.
     * Console changes reach everyone.
     */
    public static void announceSettingChange(MessageService messages, boolean announceEnabled,
            CommandSender sender, String template, String keySlot, String valueSlot) {
        if (!announceEnabled) {
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player changer && online.getUniqueId().equals(changer.getUniqueId())) {
                continue;
            }
            messages.messageRaw(online, template,
                    Map.of("player", sender.getName(), "key", keySlot, "value", valueSlot));
        }
    }
}
