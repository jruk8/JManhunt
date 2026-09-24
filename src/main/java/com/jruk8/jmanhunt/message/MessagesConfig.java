package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import eu.okaeri.configs.annotation.Header;

/**
 * Typed root of messages.yml. Every chat, actionbar, title, and GUI
 * string lives here and is read through {@link MessageService} by
 * dotted path, so renaming a key means updating the reads too.
 * An explicitly empty string disables that message wherever it
 * would be sent.
 */
@SuppressWarnings("FieldMayBeFinal")
@Header({
        "JManhunt messages.",
        "",
        "Placeholders: {prefix}, {player}, {role}, {distance}, {world},",
        "{winner}, {stat}, {value}, {effect}, {seconds}, {rank-color}.",
        "Both MiniMessage and legacy &-codes are accepted."
})
public class MessagesConfig extends OkaeriConfig {

    @CustomKey("prefix")
    private String prefix = "<#a6a6a6>[<gradient:#e66550:#de7766><bold>J</bold>Manhunt</gradient>]</#a6a6a6> ";

    @CustomKey("command")
    private CommandMessages command = new CommandMessages();

    @CustomKey("manhunt")
    private ManhuntMessages manhunt = new ManhuntMessages();

    @CustomKey("game")
    private GameMessages game = new GameMessages();

    @CustomKey("compass")
    private CompassMessages compass = new CompassMessages();

    @CustomKey("role-colors")
    @Comment({
            "One color tag per role. Used directly in templates as",
            "{role-color-<role>} and by code that builds colored role",
            "names. Legacy-format servers can set &-codes here instead",
            "of the default MiniMessage hex tags."
    })
    private RoleColorsMessages roleColors = new RoleColorsMessages();

    @CustomKey("wincon")
    @Comment({
            "Win-condition sentence fragments, joined with \", \" and",
            "\"and\" into the {conditions} of the status-win lines above."
    })
    private WinconMessages wincon = new WinconMessages();

    @CustomKey("debug")
    private DebugMessages debug = new DebugMessages();

    @CustomKey("dev")
    @Comment("Developer schematic tools (/manhunt dev schem). Not for production use.")
    private DevMessages dev = new DevMessages();

    @CustomKey("modifiers")
    @Comment("Modifier toggles (/manhunt modifiers ...). {state} is on or off.")
    private ModifiersMessages modifiers = new ModifiersMessages();

    @CustomKey("modifiers-gui")
    @Comment({
            "Modifiers GUI text. Names render white, lore gray; user tags",
            "override. {enabled} and {total} count enabled entries.",
            "Never add {prefix} here."
    })
    private ModifiersGuiMessages modifiersGui = new ModifiersGuiMessages();

    @CustomKey("manhunt-gui")
    @Comment({
            "Manhunt GUI chrome shared by the settings browser, confirm",
            "panels, and value dialogs. Names render white, lore gray;",
            "user tags override. Never add {prefix} here."
    })
    private ManhuntGuiMessages manhuntGui = new ManhuntGuiMessages();

}
