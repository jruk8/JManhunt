package com.jruk8.jmanhunt.command;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.World;
import java.util.List;
import java.util.function.Consumer;

/**
 * Console dispatch that hides command feedback: gamerule
 * SEND_COMMAND_FEEDBACK drops on the first world for the call and
 * restores after, so analysis, preset, and modifier commands never
 * spam ops with vanilla feedback lines.
 */
public final class QuietConsoleDispatch {

    private QuietConsoleDispatch() {
    }

    /**
     * Dispatches one line as console with feedback muted. No loaded
     * worlds means a plain dispatch (nothing to mute against).
     */
    public static void dispatch(String line) {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
            return;
        }
        dispatch(worlds.get(0), GameRules.SEND_COMMAND_FEEDBACK, line,
                muted -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), muted));
    }

    /**
     * Mutes one gamerule on one world around the dispatch sink, then
     * restores the previous value even when dispatch throws. The
     * rule rides as a parameter so unit tests never touch the
     * server-backed GameRules constants.
     */
    static void dispatch(World world, GameRule<Boolean> rule, String line,
            Consumer<String> dispatch) {
        Boolean previous = world.getGameRuleValue(rule);
        world.setGameRule(rule, false);
        try {
            dispatch.accept(line);
        } finally {
            world.setGameRule(rule, previous);
        }
    }
}
