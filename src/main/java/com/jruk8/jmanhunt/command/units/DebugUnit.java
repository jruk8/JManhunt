package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.command.UnitSyntaxException;
import com.jruk8.jmanhunt.core.DebugLevel;
import com.jruk8.jmanhunt.core.DebugService;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The debug verb: bare toggles, one arg sets the level. */
public final class DebugUnit implements SubcommandUnit {
    private final DebugService debugService;
    private final ManhuntMessages texts;
    private final CommandSupport support;

    public DebugUnit(DebugService debugService, ManhuntMessages texts,
            CommandSupport support) {
        this.debugService = debugService;
        this.texts = texts;
        this.support = support;
    }

    /** Empty level means toggle; present level means set. */
    public record DebugArgs(Optional<DebugLevel> level) {
    }

    public static DebugArgs parse(String[] args, ManhuntMessages texts)
            throws UnitSyntaxException {
        if (args.length == 1) {
            return new DebugArgs(Optional.empty());
        }
        if (args.length == 2) {
            DebugLevel level = DebugLevel.parse(args[1]);
            if (level != null) {
                return new DebugArgs(Optional.of(level));
            }
        }
        throw new UnitSyntaxException(texts.getDebugUsage(), Map.of());
    }

    @Override public String primaryName() {
        return "debug";
    }

    @Override public Set<String> aliases() {
        return Set.of();
    }

    @Override public boolean execute(CommandSender sender, String[] args) {
        try {
            return executeParsed(sender, parse(args, texts));
        } catch (UnitSyntaxException failure) {
            support.message(sender, failure.template(), failure.placeholders());
            return true;
        }
    }

    public boolean executeParsed(CommandSender sender, DebugArgs args) {
        if (args.level().isEmpty()) {
            Optional<DebugLevel> now = toggleDebug(sender);
            if (now.isEmpty()) {
                support.message(sender, texts.getDebugDisabled());
            } else {
                support.message(sender, texts.getDebugEnabled(), Map.of("level", now.get().name()));
            }
            support.neutralSound(sender);
            return true;
        }
        setDebug(sender, args.level().get());
        support.message(sender, texts.getDebugEnabled(), Map.of("level", args.level().get().name()));
        support.neutralSound(sender);
        return true;
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return CommandSupport.partial(args[1], List.of("INFO", "WARN", "SEVERE"));
        }
        return null;
    }

    private Optional<DebugLevel> toggleDebug(CommandSender sender) {
        if (sender instanceof Player player) {
            return debugService.togglePlayer(player.getUniqueId());
        }
        return debugService.toggleConsole();
    }

    private DebugLevel setDebug(CommandSender sender, DebugLevel level) {
        if (sender instanceof Player player) {
            return debugService.setPlayerLevel(player.getUniqueId(), level);
        }
        return debugService.setConsoleLevel(level);
    }
}
