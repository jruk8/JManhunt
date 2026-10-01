package com.jruk8.jmanhunt.gui.dialog;

import com.jruk8.jmanhunt.command.PlaceholderCheatsheet;
import com.jruk8.jmanhunt.compass.SignalInterference;
import com.jruk8.jmanhunt.config.MatchConfig;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.match.ModifierTriggers;
import com.jruk8.jmanhunt.message.ManhuntGuiMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersGuiMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.Role;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Native client dialogs for modifier editing.
 *
 * <p>Runs On shows one checkbox per known trigger, initialled from the
 * live list; Game Rules shows one checkbox per known game-state rule,
 * and Interfere During one per weather bucket.
 * Submit reports the checked set, plays neutral, and reopens the
 * caller; Cancel reopens silently. Reopens run through the scheduler
 * so the dialog close never swallows the returning menu. Opening plays
 * the neutral sound, like every other dialog.
 */
public final class ModifierDialogs implements ModifierDialog {

    private final MessageService messages;
    private final ModifiersGuiMessages modifiersGui;
    private final ManhuntGuiMessages manhuntGui;
    private final SoundService sounds;
    private final GuiService gui;
    private final Plugin plugin;

    public ModifierDialogs(MessageService messages, ModifiersGuiMessages modifiersGui,
            ManhuntGuiMessages manhuntGui, SoundService sounds, GuiService gui, Plugin plugin) {
        this.messages = messages;
        this.modifiersGui = modifiersGui;
        this.manhuntGui = manhuntGui;
        this.sounds = sounds;
        this.gui = gui;
        this.plugin = plugin;
    }

    @Override
    public void openRunsOn(Player player, List<String> current,
            Consumer<Set<String>> onSubmit, Runnable reopen) {
        openChecklist(player, knownTriggers(), modifiersGui.getRunsOnTitle(),
                modifiersGui.getRunsOnHint(), current, onSubmit, reopen);
    }

    @Override
    public void openGameRules(Player player, List<String> current,
            Consumer<Set<String>> onSubmit, Runnable reopen) {
        openChecklist(player, MatchConfig.GameRules.KNOWN, manhuntGui.getGameRulesTitle(),
                manhuntGui.getGameRulesHint(), current, onSubmit, reopen);
    }

    @Override
    public void openInterfereDuring(Player player, List<String> current,
            Consumer<Set<String>> onSubmit, Runnable reopen) {
        List<String> known = new ArrayList<>();
        for (SignalInterference.Weather bucket : SignalInterference.Weather.values()) {
            known.add(bucket.name());
        }
        openChecklist(player, known, manhuntGui.getInterfereDuringTitle(),
                manhuntGui.getInterfereDuringHint(), current, onSubmit, reopen);
    }

    @Override
    public void openTestCommands(Player player, ModifierDialog.TestSubmission initial,
            Function<ModifierDialog.TestSubmission, Runnable> onSubmit, Runnable onCancel) {
        List<DialogBody> body = new ArrayList<>();
        for (String line : PlaceholderCheatsheet.commandDialogLines()) {
            body.add(DialogBody.plainMessage(messages.parse(line)));
        }
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(GuiTexts.title(messages, modifiersGui.getTestTitle()))
                        .body(body)
                        .inputs(testInputs(initial))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.confirmation(
                        confirmButton(clickAction((response, audience) -> {
                            Runnable next = onSubmit.apply(readTestSubmission(response::getBoolean,
                                    response::getText));
                            sounds.playNeutralSound(player);
                            runLater(player, next);
                        }), true),
                        confirmButton(clickAction((response, audience) ->
                                runLater(player, onCancel)), false))));
        sounds.playNeutralSound(player);
        player.showDialog(dialog);
    }

    /** Remember checkbox, role scroller, and five command boxes. */
    private List<DialogInput> testInputs(ModifierDialog.TestSubmission initial) {
        List<DialogInput> inputs = new ArrayList<>();
        inputs.add(DialogInput.bool(DialogInputs.TEST_REMEMBER_KEY,
                GuiTexts.name(messages,
                        modifiersGui.getTestRemember(),
                        "Remember Commands"))
                .initial(initial.remember()).build());
        inputs.add(DialogInput.singleOption(DialogInputs.TEST_ROLE_KEY,
                GuiTexts.name(messages,
                        modifiersGui.getTestRole(), "Role"),
                List.of(
                        SingleOptionDialogInput.OptionEntry.create("SPEEDRUNNER",
                                messages.parse(messages.roleName(Role.SPEEDRUNNER)),
                                !"HUNTER".equals(initial.role())),
                        SingleOptionDialogInput.OptionEntry.create("HUNTER",
                                messages.parse(messages.roleName(Role.HUNTER)),
                                "HUNTER".equals(initial.role()))))
                .build());
        for (int index = 0; index < DialogInputs.TEST_COMMAND_BOXES; index++) {
            String label = modifiersGui.getTestCommandBox()
                    .replace("{n}", String.valueOf(index + 1));
            String value = index < initial.commands().size() ? initial.commands().get(index) : "";
            inputs.add(DialogInput.text(DialogInputs.testCommandKey(index),
                    GuiTexts.name(messages, label, label))
                    .initial(value == null ? "" : value)
                    .maxLength(DialogInputs.TEST_COMMAND_MAX_LENGTH).build());
        }
        return inputs;
    }

    /** Reads one test submit off the response getters. */
    private static ModifierDialog.TestSubmission readTestSubmission(
            Function<String, Boolean> bool, Function<String, String> text) {
        return DialogInputs.readTestSubmission(bool, text);
    }

    private void openChecklist(Player player, List<String> known, String title, String hint,
            List<String> current, Consumer<Set<String>> onSubmit, Runnable reopen) {
        List<DialogBody> body = List.of(DialogBody.plainMessage(messages.parse(hint)));
        List<DialogInput> inputs = new ArrayList<>();
        for (int index = 0; index < known.size(); index++) {
            String value = known.get(index);
            boolean checked = current.stream().anyMatch(value::equalsIgnoreCase);
            inputs.add(DialogInput.bool(DialogInputs.triggerKey(index), label(value))
                    .initial(checked).build());
        }
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(GuiTexts.title(messages, title))
                        .body(body)
                        .inputs(inputs)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .build())
                .type(DialogType.confirmation(
                        confirmButton(clickAction((response, audience) -> {
                            onSubmit.accept(DialogInputs.checkedTriggers(
                                    response::getBoolean, known));
                            sounds.playNeutralSound(player);
                            runLater(player, reopen);
                        }), true),
                        confirmButton(clickAction((response, audience) ->
                                runLater(player, reopen)), false))));
        sounds.playNeutralSound(player);
        player.showDialog(dialog);
    }

    private List<String> knownTriggers() {
        return ModifierTriggers.KNOWN;
    }

    private Component label(String value) {
        return GuiTexts.name(messages, value, value);
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
                ? manhuntGui.getDialogSubmit()
                : manhuntGui.getDialogCancel();
        return ActionButton.builder(GuiTexts.name(messages, text, text))
                .action(action)
                .build();
    }

    private void runLater(Player player, Runnable callback) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                callback.run();
            }
        });
    }
}
