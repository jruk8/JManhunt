package com.jruk8.jmanhunt.gui.dialog;

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
 * live list. Submit reports the checked set, plays neutral, and reopens
 * the caller; Cancel reopens silently. Reopens run through the scheduler
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
        List<String> known = knownTriggers();
        List<DialogBody> body = List.of(DialogBody.plainMessage(messages.parse(messages
                .string("modifiers-gui.runs-on-hint", "Tick the events this modifier runs on."))));
        List<DialogInput> inputs = new ArrayList<>();
        for (int index = 0; index < known.size(); index++) {
            String trigger = known.get(index);
            boolean checked = current.stream().anyMatch(trigger::equalsIgnoreCase);
            inputs.add(DialogInput.bool(DialogInputs.triggerKey(index), label(trigger))
                    .initial(checked).build());
        }
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(GuiTexts.title(messages, messages
                                .string("modifiers-gui.runs-on-title", "Runs On")))
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

    private Component label(String trigger) {
        return GuiTexts.name(messages, trigger, trigger);
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
