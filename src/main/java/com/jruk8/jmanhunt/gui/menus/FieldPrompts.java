package com.jruk8.jmanhunt.gui.menus;

import com.jruk8.jmanhunt.gui.GuiService;
import com.jruk8.jmanhunt.gui.Menu;
import com.jruk8.jmanhunt.gui.dialog.SettingDialogs;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.List;
import java.util.Map;
import org.bukkit.entity.Player;

/**
 * Shared string-field prompt for the modifier and preset editors.
 *
 * <p>Prefills the live value, echoes it in the body, maps blank input to
 * null on clearable fields, and plays neutral on success or angry plus a
 * chat error on validation failure. Submit and Cancel both rebuild the
 * calling menu.
 */
public final class FieldPrompts {

    /** Field submitter: patches the target, returning an error or null. */
    public interface Submit {
        String submit(String raw);
    }

    private FieldPrompts() {
    }

    /**
     * @param title dialog title, pre-rendered by the caller
     * @param current live value, prefilled; null shows as unset
     * @param clearable blank input clears the field instead of submitting
     * @param submit receives the raw text (or null when cleared)
     */
    public static void prompt(SettingDialogs dialogs, GuiService gui,
            MessageService messages, SoundService sounds,
            Player player, Menu self, String title, String current,
            boolean clearable, Submit submit) {
        String shown = current == null
                ? messages.string("modifiers-gui.editor-unset", "Not set") : current;
        dialogs.prompt(player, title,
                SettingDialogs.safeInitial(current),
                List.of(messages
                        .string("modifiers-gui.editor-prompt-current", "Current value: {value}")
                        .replace("{value}", shown)),
                raw -> {
                    String error = clearable && raw.isBlank()
                            ? submit.submit(null) : submit.submit(raw);
                    if (error != null) {
                        messages.message(player, "modifiers.edit-invalid",
                                Map.of("error", error));
                        sounds.playAngrySound(player);
                    } else {
                        sounds.playNeutralSound(player);
                    }
                    gui.navigate(player, self);
                },
                () -> gui.navigate(player, self));
    }
}
