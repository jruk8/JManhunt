package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.config.SettingType;
import com.jruk8.jmanhunt.gui.ConfirmMenu;
import com.jruk8.jmanhunt.gui.GuiConfig;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.MenuButton;
import com.jruk8.jmanhunt.gui.dialog.SettingDialog;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Setting buttons: category icon, prettified name, description, value,
 * path, type, and bounds or option bullets, glowing when modified.
 * Booleans toggle and options cycle on click; numbers and text open
 * the value dialog; right-click resets through the confirm panel.
 */
public final class SettingButtons {

    private final ConfigService config;
    private final GuiConfig guiData;
    private final MessageService messages;
    private final SettingDialog dialogs;
    private final GuiService gui;
    private final SettingFeedback feedback;
    private final SoundService sounds;

    /**
     * @param sounds failure blips for toggle and cycle writes; success
     *        sounds come from the shared feedback, null only in unit
     *        tests that never invoke actions
     */
    public SettingButtons(ConfigService config, GuiConfig guiData, MessageService messages,
            SettingDialog dialogs, GuiService gui, SettingFeedback feedback,
            SoundService sounds) {
        this.config = config;
        this.guiData = guiData;
        this.messages = messages;
        this.dialogs = dialogs;
        this.gui = gui;
        this.feedback = feedback;
        this.sounds = sounds;
    }

    /** Leaf segment to title words: countdown-seconds becomes Countdown Seconds. */
    public static String prettify(String leaf) {
        String[] words = leaf.split("-");
        StringBuilder pretty = new StringBuilder();
        for (String word : words) {
            if (pretty.length() > 0) {
                pretty.append(' ');
            }
            if (word.isEmpty()) {
                continue;
            }
            pretty.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                pretty.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return pretty.toString();
    }

    /** Next option after the current value, wrapping around. */
    public static String nextOption(SettingDescriptor descriptor, String current) {
        List<String> options = descriptor.options();
        int index = options.indexOf(current);
        return options.get((index + 1) % options.size());
    }

    /** Flipped boolean as setter text, defaulting unknown values to true. */
    public static String toggledValue(Object current) {
        return current instanceof Boolean bool && bool ? "false" : "true";
    }

    /**
     * Full setting button with click and right-click actions. Silent: bool
     * and option commits play neutral through the shared feedback and
     * numbers and text open the dialog, which plays neutral on enter.
     *
     * @param path full setting path
     * @param caller rebuilds the menu buttons return to
     */
    public MenuButton settingButton(String path, Supplier<Menu> caller) {
        SettingDescriptor descriptor = SettingRegistry.byPath(path);
        Material icon = guiData.sectionItem(path);
        Component name = GuiTexts.name(messages, prettify(leaf(path)), prettify(leaf(path)));
        return new MenuButton(icon, name, lore(descriptor), modified(descriptor),
                false, clickAction(descriptor, caller),
                player -> resetConfirm(player, descriptor, caller)).silent();
    }

    private Consumer<Player> clickAction(SettingDescriptor descriptor, Supplier<Menu> caller) {
        return switch (descriptor.type()) {
            case BOOL -> player -> toggle(player, descriptor);
            case OPTION -> player -> cycle(player, descriptor);
            case INT, FLOAT, STRING -> player -> dialogs.openSetting(player, descriptor,
                    GuiTexts.title(messages, dialogTitle(descriptor)), caller);
        };
    }

    private List<Component> lore(SettingDescriptor descriptor) {
        String allowed = null;
        if (descriptor.type() == SettingType.INT || descriptor.type() == SettingType.FLOAT) {
            allowed = SettingRegistry.boundsText(descriptor, config::getValue);
        }
        List<String> options = null;
        if (descriptor.type() == SettingType.OPTION) {
            options = descriptor.options();
        }
        FieldLore.Field field = new FieldLore.Field(
                guiData.description(descriptor.path()),
                displayCurrent(descriptor),
                descriptor.path().replaceFirst("^settings\\.", ""),
                typeName(descriptor.type()),
                allowed, options, Set.of(displayCurrent(descriptor)),
                displayDefault(descriptor),
                hint(descriptor.type()));
        return GuiTexts.lore(messages, FieldLore.lines(messages, field));
    }

    private String displayCurrent(SettingDescriptor descriptor) {
        Object value = config.getValue(descriptor.path());
        if (descriptor.type() == SettingType.BOOL && value instanceof Boolean bool) {
            return bool ? "<green>Enabled</green>" : "<red>Disabled</red>";
        }
        return MiniMessage.miniMessage().escapeTags(ConfigService.displayValue(value));
    }

    private String displayDefault(SettingDescriptor descriptor) {
        String raw = descriptor.defaultValue();
        if (descriptor.type() == SettingType.BOOL) {
            return Boolean.parseBoolean(raw) ? "Enabled" : "Disabled";
        }
        return raw;
    }

    private boolean modified(SettingDescriptor descriptor) {
        return ModifiedGlow.leafSetting(config, descriptor.path());
    }

    private void toggle(Player player, SettingDescriptor descriptor) {
        ConfigService.SetOutcome outcome = config.setValue(descriptor.path(),
                toggledValue(config.getValue(descriptor.path())));
        if (!outcome.ok()) {
            feedback.failed(player, outcome);
            angry(player);
        } else {
            feedback.scalarUpdated(player, descriptor.path(), outcome);
        }
    }

    private void cycle(Player player, SettingDescriptor descriptor) {
        String current = ConfigService.displayValue(config.getValue(descriptor.path()));
        ConfigService.SetOutcome outcome =
                config.setValue(descriptor.path(), nextOption(descriptor, current));
        if (!outcome.ok()) {
            feedback.failed(player, outcome);
            angry(player);
        } else {
            feedback.scalarUpdated(player, descriptor.path(), outcome);
        }
    }

    private void angry(Player player) {
        if (sounds != null) {
            sounds.playAngrySound(player);
        }
    }

    private void resetConfirm(Player player, SettingDescriptor descriptor, Supplier<Menu> caller) {
        // Already at default: resetting would be a no-op, so say so in
        // chat instead of opening a confirm panel for nothing.
        if (!modified(descriptor)) {
            messages.message(player, "manhunt-gui.setting-already-default");
            return;
        }
        String oldValue = MiniMessage.miniMessage()
                .escapeTags(ConfigService.displayValue(config.getValue(descriptor.path())));
        Menu confirm = ConfirmMenu.create(
                GuiTexts.title(messages, resetTitle(descriptor)),
                Material.PAPER, null,
                GuiTexts.lore(messages, List.of(oldValue + " -> " + descriptor.defaultValue())),
                GuiTexts.name(messages, text("cancel", "Cancel"), "Cancel"),
                back -> gui.navigate(back, caller.get()),
                GuiTexts.name(messages, text("confirm", "Confirm"), "Confirm"),
                done -> {
                    ConfigService.SetOutcome outcome =
                            config.setValue(descriptor.path(), descriptor.defaultValue());
                    if (!outcome.ok()) {
                        feedback.failed(done, outcome);
                        angry(done);
                    } else {
                        feedback.scalarUpdated(done, descriptor.path(), outcome);
                    }
                    gui.navigate(done, caller.get());
                },
                caller);
        gui.navigate(player, confirm);
        if (sounds != null) {
            sounds.playSound(player, "compass.left-click");
        }
    }

    private String template(String key, String fallback, String value) {
        String line = messages.string(key, fallback);
        return value == null ? line : line.replace("{value}", value)
                .replace("{path}", value).replace("{type}", value).replace("{bounds}", value);
    }

    private String hint(SettingType type) {
        return switch (type) {
            case BOOL -> template("manhunt-gui.setting-hint-toggle", "Click to toggle", null);
            case OPTION -> template("manhunt-gui.setting-hint-cycle", "Click to cycle", null);
            case INT, FLOAT, STRING ->
                    template("manhunt-gui.setting-hint-edit", "Click to edit", null);
        };
    }

    private static String typeName(SettingType type) {
        return switch (type) {
            case BOOL -> "Boolean";
            case INT -> "Integer";
            case FLOAT -> "Number";
            case STRING -> "Text";
            case OPTION -> "Choice";
        };
    }

    private String dialogTitle(SettingDescriptor descriptor) {
        return messages.string("manhunt-gui.dialog-title-edit", "Edit {name}")
                .replace("{name}", prettify(leaf(descriptor.path())));
    }

    private String resetTitle(SettingDescriptor descriptor) {
        return messages.string("manhunt-gui.setting-reset-title", "Reset {name}?")
                .replace("{name}", prettify(leaf(descriptor.path())));
    }

    private String text(String key, String fallback) {
        return messages.string("manhunt-gui." + key, fallback);
    }

    private static String leaf(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
