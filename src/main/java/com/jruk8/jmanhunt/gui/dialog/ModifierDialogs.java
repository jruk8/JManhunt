package com.jruk8.jmanhunt.gui.dialog;

import com.jruk8.jmanhunt.compass.SignalInterference;
import com.jruk8.jmanhunt.config.MatchConfig;
import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.GuiTexts;
import com.jruk8.jmanhunt.match.ModifierTriggers;
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
import java.util.Set;
import java.util.function.Consumer;
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
    private final SoundService sounds;
    private final GuiService gui;
    private final Plugin plugin;

    public ModifierDialogs(MessageService messages, SoundService sounds,
            GuiService gui, Plugin plugin) {
        this.messages = messages;
        this.sounds = sounds;
        this.gui = gui;
        this.plugin = plugin;
    }

    @Override
    public void openRunsOn(Player player, List<String> current,
            Consumer<Set<String>> onSubmit, Runnable reopen) {
        openChecklist(player, knownTriggers(),
                "modifiers-gui.runs-on-title", "Runs On",
                "modifiers-gui.runs-on-hint", "Tick the events this modifier runs on.",
                current, onSubmit, reopen);
    }

    @Override
    public void openGameRules(Player player, List<String> current,
            Consumer<Set<String>> onSubmit, Runnable reopen) {
        openChecklist(player, MatchConfig.GameRules.KNOWN,
                "manhunt-gui.game-rules-title", "Game Rules",
                "manhunt-gui.game-rules-hint", "Tick the game-state rules this server applies.",
                current, onSubmit, reopen);
    }

    @Override
    public void openInterfereDuring(Player player, List<String> current,
            Consumer<Set<String>> onSubmit, Runnable reopen) {
        List<String> known = new ArrayList<>();
        for (SignalInterference.Weather bucket : SignalInterference.Weather.values()) {
            known.add(bucket.name());
        }
        openChecklist(player, known,
                "manhunt-gui.interfere-during-title", "Interfere During",
                "manhunt-gui.interfere-during-hint",
                "Tick the weather that interferes with tracking.",
                current, onSubmit, reopen);
    }

    private void openChecklist(Player player, List<String> known,
            String titleKey, String titleFallback, String hintKey, String hintFallback,
            List<String> current, Consumer<Set<String>> onSubmit, Runnable reopen) {
        List<DialogBody> body = List.of(DialogBody.plainMessage(messages.parse(messages
                .string(hintKey, hintFallback))));
        List<DialogInput> inputs = new ArrayList<>();
        for (int index = 0; index < known.size(); index++) {
            String value = known.get(index);
            boolean checked = current.stream().anyMatch(value::equalsIgnoreCase);
            inputs.add(DialogInput.bool(DialogInputs.triggerKey(index), label(value))
                    .initial(checked).build());
        }
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(GuiTexts.title(messages, messages
                                .string(titleKey, titleFallback)))
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
                ? messages.string("manhunt-gui.dialog-submit", "Submit")
                : messages.string("manhunt-gui.dialog-cancel", "Cancel");
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
