package com.jruk8.jmanhunt.gui.dialog;

import com.jruk8.jmanhunt.command.SettingFeedback;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.SettingDescriptor;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.gui.GuiConfig;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.config.SettingRegistry;
import com.jruk8.jmanhunt.config.SettingType;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.message.ManhuntGuiMessages;
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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Native client dialogs for INT, FLOAT, and STRING settings.
 *
 * <p>Bounded numbers get a number-range slider; strings and numbers without
 * both static bounds get a text field. Submit runs the shared typed setter
 * as the clicking player: valid input reports success and reopens the
 * caller, invalid input keeps the original value, reports the validation
 * error with its boundaries, plays the fail sound, and reopens the caller.
 * Cancel reopens silently. Reopens run one tick later so the dialog close
 * never swallows the returning menu. Every opened dialog plays the neutral
 * sound on enter; overlong input refuses the open with the fail sound.
 */
public final class SettingDialogs implements SettingDialog {

    private static final String VALUE_KEY = "value";
    /** Text input ceiling, shared with the headless input helpers. */
    static final int TEXT_MAX_LENGTH = DialogInputs.TEXT_MAX_LENGTH;

    /** Global plus override setting backends. */
    public record SettingStores(ConfigService config, OverrideService overrides) {
    }

    /** Dialog chat, text, and texts.sounds(). */
    public record SettingTexts(MessageService messages, ManhuntGuiMessages guiTexts,
            SoundService sounds) {
    }

    /** Navigation, feedback, and descriptions. */
    public record SettingUi(GuiService gui, SettingFeedback feedback, GuiConfig guiData) {
    }

    private final SettingStores stores;
    private final SettingTexts texts;
    private final SettingUi ui;
    private final TaskScheduler tasks;

    public SettingDialogs(SettingStores stores, SettingTexts texts, SettingUi ui,
            TaskScheduler tasks) {
        this.stores = stores;
        this.texts = texts;
        this.ui = ui;
        this.tasks = tasks;
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
        Integer lobby = ui.gui().overrideLobby(player);
        String current = currentText(lobby, descriptor);
        if (!ranged && tooLong(player, current, () -> reopenLater(player, reopen))) {
            return;
        }
        DialogInput input = ranged
                ? rangeInput(lobby, descriptor, title)
                : DialogInput.text(VALUE_KEY, title)
                        .initial(current)
                        .maxLength(TEXT_MAX_LENGTH)
                        .build();
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .body(bodyLines(lobby, descriptor))
                        .inputs(List.of(input))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.confirmation(
                        confirmButton(submitAction(player, descriptor, reopen, ranged), true),
                        confirmButton(clickAction((response, audience) ->
                                reopenLater(player, reopen)), false))));
        texts.sounds().playNeutralSound(player);
        player.showDialog(dialog);
    }

    @Override
    public void openListEntry(Player player, String listPath, int index,
            Component title, Supplier<Menu> reopen) {
        List<String> entries = stores.overrides().getStringList(ui.gui().overrideLobby(player), listPath);
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

    /**
     * Single free-text prompt with parsed body lines. Submit and cancel
     * run one tick later so callers may navigate safely.
     */
    @Override
    public void prompt(Player player, String titleText, List<String> body,
            Consumer<String> onSubmit, Runnable onCancel) {
        prompt(player, titleText, "", body, onSubmit, onCancel);
    }

    /**
     * Same, with a prefilled value for editing existing text. Overlong
     * initial values take the cancel path like any other text dialog.
     */
    @Override
    public void prompt(Player player, String titleText, String initial, List<String> body,
            Consumer<String> onSubmit, Runnable onCancel) {
        promptBodies(player, titleText, initial, body, false, onSubmit, onCancel);
    }

    @Override
    public void promptWithIcon(Player player, String titleText, String initial,
            List<String> body, Consumer<String> onSubmit, Runnable onCancel) {
        promptBodies(player, titleText, initial, body, true, onSubmit, onCancel);
    }

    /** Shared prompt flow; the icon form prepends the value sprite. */
    private void promptBodies(Player player, String titleText, String initial, List<String> body,
            boolean withIcon, Consumer<String> onSubmit, Runnable onCancel) {
        List<DialogBody> lines = new ArrayList<>();
        if (withIcon) {
            lines.addAll(iconBody(initial));
        }
        for (String line : body) {
            lines.add(DialogBody.plainMessage(texts.messages().parse(line)));
        }
        openText(player, GuiTexts.title(texts.messages(), titleText), initial,
                lines,
                value -> runLater(player, () -> onSubmit.accept(value)),
                () -> runLater(player, onCancel));
    }

    /**
     * Item sprite body for one string value: the item when the value
     * names one, else nothing. Shared by setting dialogs and the
     * icon prompt so the sprite renders from one place.
     */
    static List<DialogBody> iconBody(String value) {
        List<DialogBody> lines = new ArrayList<>();
        DialogInputs.iconSprite(value).ifPresent(material ->
                lines.add(DialogBody.item(new ItemStack(material)).build()));
        return lines;
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
        texts.sounds().playNeutralSound(player);
        player.showDialog(dialog);
    }

    private void submitListSet(Player player, String listPath, int index,
            Supplier<Menu> reopen, String raw) {
        Integer lobby = ui.gui().overrideLobby(player);
        ConfigService.SetOutcome outcome = lobby == null
                ? stores.config().listSet(listPath, index, raw)
                : stores.overrides().listSetOverride(lobby, listPath, index, raw);
        if (!outcome.ok()) {
            ui.feedback().failed(player, outcome);
            texts.sounds().playAngrySound(player);
        } else if (lobby == null) {
            ui.feedback().scalarUpdated(player, listPath + "." + index, outcome);
        } else {
            ui.feedback().overrideScalarUpdated(player, lobby, listPath + "." + index, outcome);
        }
        reopenLater(player, reopen);
    }

    private void submitListAdd(Player player, String listPath,
            Supplier<Menu> reopen, String raw) {
        Integer lobby = ui.gui().overrideLobby(player);
        ConfigService.SetOutcome outcome = lobby == null
                ? stores.config().listAdd(listPath, raw)
                : stores.overrides().listAddOverride(lobby, listPath, raw);
        if (!outcome.ok()) {
            ui.feedback().failed(player, outcome);
            texts.sounds().playAngrySound(player);
        } else if (lobby == null) {
            ui.feedback().listAdded(player, listPath, outcome);
        } else {
            ui.feedback().overrideListAdded(player, lobby, listPath, outcome);
        }
        reopenLater(player, reopen);
    }

    private DialogInput rangeInput(Integer lobby, SettingDescriptor descriptor, Component title) {
        float min = descriptor.min().floatValue();
        float max = descriptor.max().floatValue();
        var builder = DialogInput.numberRange(VALUE_KEY, title, min, max)
                .initial(DialogInputs.clamp(currentNumber(lobby, descriptor, min), min, max));
        if (descriptor.type() == SettingType.INT) {
            builder.step(1.0f);
        } else {
            // No decimal-places control exists: labelFormat takes a
            // translation key, not a number pattern (verified against the
            // Paper 26.2 API). A 0.001 step snaps values to three decimals
            // and the body line below renders them exactly.
            builder.step(0.001f);
        }
        return builder.build();
    }

    private String currentText(Integer lobby, SettingDescriptor descriptor) {
        return ConfigService.displayValue(stores.overrides().effectiveValue(lobby, descriptor.path()));
    }

    /** Current value, with floats rendered to three decimals. */
    private String displayCurrent(Integer lobby, SettingDescriptor descriptor) {
        if (descriptor.type() == SettingType.FLOAT) {
            Object value = stores.overrides().effectiveValue(lobby, descriptor.path());
            if (value instanceof Number number) {
                return DialogInputs.formatFloat(number.floatValue());
            }
        }
        return currentText(lobby, descriptor);
    }

    /**
     * Dialog initial text from a live value: null becomes blank and
     * overlong values fall back to blank so a huge current value never
     * refuses the open; callers echo the full value in the body instead.
     */
    public static String safeInitial(String current) {
        return DialogInputs.safeInitial(current);
    }

    private float currentNumber(Integer lobby, SettingDescriptor descriptor, float fallback) {
        Object value = stores.overrides().effectiveValue(lobby, descriptor.path());
        if (value instanceof Number number) {
            return number.floatValue();
        }
        return fallback;
    }

    private List<DialogBody> bodyLines(Integer lobby, SettingDescriptor descriptor) {
        List<DialogBody> lines = new ArrayList<>();
        if (descriptor.type() == SettingType.STRING) {
            lines.addAll(iconBody(currentText(lobby, descriptor)));
        }
        String current = texts.guiTexts().getDialogCurrent()
                .replace("{value}", escape(displayCurrent(lobby, descriptor)));
        String bounds = null;
        if (SettingRegistry.hasBounds(descriptor)) {
            bounds = texts.guiTexts().getDialogBounds()
                    .replace("{bounds}", escape(SettingRegistry.boundsText(
                            descriptor, path -> stores.overrides().effectiveRaw(lobby, path))));
        }
        for (String line : DialogInputs.orderedBody(
                ui.guiData().description(descriptor.path()), current, bounds)) {
            lines.add(DialogBody.plainMessage(texts.messages().parse(line)));
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
        texts.messages().messageRaw(player, texts.guiTexts().getDialogTooLong(), Map.of(
                "length", String.valueOf(initial == null ? 0 : initial.length()),
                "max", String.valueOf(TEXT_MAX_LENGTH)));
        texts.sounds().playAngrySound(player);
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
                ? texts.guiTexts().getDialogSubmit()
                : texts.guiTexts().getDialogCancel();
        return ActionButton.builder(GuiTexts.name(texts.messages(), text, text))
                .action(action)
                .build();
    }

    private void submit(Player player, SettingDescriptor descriptor,
            Supplier<Menu> reopen, String raw) {
        Integer lobby = ui.gui().overrideLobby(player);
        ConfigService.SetOutcome outcome = lobby == null
                ? stores.config().setValue(descriptor.path(), raw)
                : stores.overrides().setSettingOverride(lobby, descriptor.path(), raw);
        if (!outcome.ok()) {
            ui.feedback().failed(player, outcome);
            texts.sounds().playAngrySound(player);
        } else if (lobby == null) {
            ui.feedback().scalarUpdated(player, descriptor.path(), outcome);
        } else {
            ui.feedback().overrideScalarUpdated(player, lobby, descriptor.path(), outcome);
        }
        reopenLater(player, reopen);
    }

    private void reopenLater(Player player, Supplier<Menu> reopen) {
        tasks.run(() -> {
            if (player.isOnline()) {
                ui.gui().navigate(player, reopen.get());
            }
        });
    }

    /** Runs a dialog callback next tick, skipping offline players. */
    private void runLater(Player player, Runnable callback) {
        tasks.run(() -> {
            if (player.isOnline()) {
                callback.run();
            }
        });
    }
}
