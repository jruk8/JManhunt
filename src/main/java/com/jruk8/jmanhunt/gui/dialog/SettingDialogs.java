package com.jruk8.jmanhunt.gui.dialog;

import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.config.SettingType;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Native client dialogs for INT, FLOAT, and STRING settings.
 *
 * <p>Bounded numbers get a number-range slider; strings and numbers without
 * both static bounds get a text field. Submit runs the shared typed setter
 * as the clicking player: valid input reports success and reopens the
 * caller, invalid input keeps the original value, reports the validation
 * error with its boundaries, plays the fail sound, and reopens the caller.
 * Cancel reopens silently. Reopens run one tick later so the dialog close
 * never swallows the returning menu.
 */
public final class SettingDialogs implements SettingDialog {

    private static final String VALUE_KEY = "value";
    /** Text input ceiling: the classic full-string cap, far above any sane value. */
    static final int TEXT_MAX_LENGTH = 32767;

    private final ConfigService config;
    private final MessageService messages;
    private final SoundService sounds;
    private final GuiService gui;
    private final SettingFeedback feedback;
    private final Plugin plugin;

    public SettingDialogs(ConfigService config, MessageService messages, SoundService sounds,
            GuiService gui, SettingFeedback feedback, Plugin plugin) {
        this.config = config;
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.feedback = feedback;
        this.plugin = plugin;
    }

    @Override
    public void openSetting(Player player, SettingDescriptor descriptor,
            Component title, Supplier<Menu> reopen) {
        if (descriptor.type() != SettingType.INT
                && descriptor.type() != SettingType.FLOAT
                && descriptor.type() != SettingType.STRING) {
            throw new IllegalArgumentException(
                    "Dialogs edit INT, FLOAT, and STRING only: " + descriptor.path());
        }
        boolean ranged = DialogInputs.useNumberRange(descriptor);
        if (!ranged && tooLong(player, currentText(descriptor), () -> reopenLater(player, reopen))) {
            return;
        }
        DialogInput input = ranged
                ? rangeInput(descriptor, title)
                : DialogInput.text(VALUE_KEY, title)
                        .initial(currentText(descriptor))
                        .maxLength(TEXT_MAX_LENGTH)
                        .build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .body(bodyLines(descriptor))
                        .inputs(List.of(input))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.confirmation(
                        confirmButton(submitAction(player, descriptor, reopen, ranged), true),
                        confirmButton(clickAction((response, audience) ->
                                reopenLater(player, reopen)), false))));
        player.showDialog(dialog);
    }

    @Override
    public void openListEntry(Player player, String listPath, int index,
            Component title, Supplier<Menu> reopen) {
        List<String> entries = config.getStringList(listPath);
        if (index < 0 || index >= entries.size()) {
            reopenLater(player, reopen);
            return;
        }
        openText(player, title, entries.get(index), List.of(),
                raw -> submitListSet(player, listPath, index, reopen, raw),
                () -> reopenLater(player, reopen));
    }

    @Override
    public void openListAppend(Player player, String listPath,
            Component title, Supplier<Menu> reopen) {
        openText(player, title, "", List.of(),
                raw -> submitListAdd(player, listPath, reopen, raw),
                () -> reopenLater(player, reopen));
    }

    /** Shared text dialog with caller-supplied submit and cancel behavior. */
    private void openText(Player player, Component title, String initial,
            List<DialogBody> body, Consumer<String> onSubmit,
            Runnable onCancel) {
        if (tooLong(player, initial, onCancel)) {
            return;
        }
        DialogInput input = DialogInput.text(VALUE_KEY, title).initial(initial)
                .maxLength(TEXT_MAX_LENGTH).build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .body(body)
                        .inputs(List.of(input))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.confirmation(
                        confirmButton(clickAction((response, audience) ->
                                onSubmit.accept(response.getText(VALUE_KEY))), true),
                        confirmButton(clickAction((response, audience) ->
                                onCancel.run()), false))));
        player.showDialog(dialog);
    }

    private void submitListSet(Player player, String listPath, int index,
            Supplier<Menu> reopen, String raw) {
        ConfigService.SetOutcome outcome = config.listSet(listPath, index, raw);
        if (!outcome.ok()) {
            feedback.failed(player, outcome);
            sounds.playAngrySound(player);
        } else {
            feedback.scalarUpdated(player, listPath + "." + index, outcome);
        }
        reopenLater(player, reopen);
    }

    private void submitListAdd(Player player, String listPath,
            Supplier<Menu> reopen, String raw) {
        ConfigService.SetOutcome outcome = config.listAdd(listPath, raw);
        if (!outcome.ok()) {
            feedback.failed(player, outcome);
            sounds.playAngrySound(player);
        } else {
            feedback.listAdded(player, listPath, outcome);
        }
        reopenLater(player, reopen);
    }

    private DialogInput rangeInput(SettingDescriptor descriptor, Component title) {
        float min = descriptor.min().floatValue();
        float max = descriptor.max().floatValue();
        var builder = DialogInput.numberRange(VALUE_KEY, title, min, max)
                .initial(currentNumber(descriptor, min));
        if (descriptor.type() == SettingType.INT) {
            builder.step(1.0f);
        }
        return builder.build();
    }

    private String currentText(SettingDescriptor descriptor) {
        return ConfigService.displayValue(config.getValue(descriptor.path()));
    }

    private float currentNumber(SettingDescriptor descriptor, float fallback) {
        Object value = config.getValue(descriptor.path());
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return fallback;
    }

    private List<DialogBody> bodyLines(SettingDescriptor descriptor) {
        List<DialogBody> lines = new ArrayList<>();
        lines.add(DialogBody.plainMessage(messages.parse(messages
                .string("manhunt-gui.dialog-current", "Current value: {value}")
                .replace("{value}", escape(currentText(descriptor))))));
        if (descriptor.type() == SettingType.INT || descriptor.type() == SettingType.FLOAT) {
            lines.add(DialogBody.plainMessage(messages.parse(messages
                    .string("manhunt-gui.dialog-bounds", "Allowed: {bounds}")
                    .replace("{bounds}", escape(SettingRegistry.boundsText(
                            descriptor, config::getValue))))));
        }
        return lines;
    }

    /**
     * Refuses values even the ceiling cannot hold: chat error plus fail
     * sound, then the cancel path. True when the dialog must not open.
     */
    private boolean tooLong(Player player, String initial, Runnable onCancel) {
        if (initial != null && initial.length() <= TEXT_MAX_LENGTH) {
            return false;
        }
        messages.message(player, "manhunt-gui.dialog-too-long", Map.of(
                "length", String.valueOf(initial == null ? 0 : initial.length()),
                "max", String.valueOf(TEXT_MAX_LENGTH)));
        sounds.playAngrySound(player);
        onCancel.run();
        return true;
    }

    /** Escapes user data so only template markup parses as MiniMessage. */
    private static String escape(String raw) {
        return MiniMessage.miniMessage().escapeTags(raw);
    }

    private DialogAction submitAction(Player player, SettingDescriptor descriptor,
            Supplier<Menu> reopen, boolean ranged) {
        return clickAction((response, audience) -> {
            String raw = ranged
                    ? DialogInputs.submitText(descriptor, response.getFloat(VALUE_KEY))
                    : response.getText(VALUE_KEY);
            submit(player, descriptor, reopen, raw);
        });
    }

    private static DialogAction clickAction(DialogActionCallback callback) {
        ClickCallback.Options options = ClickCallback.Options.builder()
                .uses(1)
                .lifetime(Duration.ofMinutes(15))
                .build();
        return DialogAction.customClick(callback, options);
    }

    private ActionButton confirmButton(DialogAction action, boolean submit) {
        String text = submit
                ? messages.string("manhunt-gui.dialog-submit", "Submit")
                : messages.string("manhunt-gui.dialog-cancel", "Cancel");
        return ActionButton.builder(GuiTexts.name(messages, text, text))
                .action(action)
                .build();
    }

    private void submit(Player player, SettingDescriptor descriptor,
            Supplier<Menu> reopen, String raw) {
        ConfigService.SetOutcome outcome = config.setValue(descriptor.path(), raw);
        if (!outcome.ok()) {
            feedback.failed(player, outcome);
            sounds.playAngrySound(player);
        } else {
            feedback.scalarUpdated(player, descriptor.path(), outcome);
        }
        reopenLater(player, reopen);
    }

    private void reopenLater(Player player, Supplier<Menu> reopen) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                gui.navigate(player, reopen.get());
            }
        });
    }
}
