package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.EndArgs;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The end verb: cancel one match or all live matches. */
public final class EndUnit implements SubcommandUnit {
    private final GameManager game;
    private final ManhuntMessages texts;
    private final CommandSupport support;

    public EndUnit(GameManager game, ManhuntMessages texts, CommandSupport support) {
        this.game = game;
        this.texts = texts;
        this.support = support;
    }

    public static EndArgs parseEndArgs(String[] args) {
        Optional<String> instanceId = Optional.empty();
        boolean immediate = false;
        boolean all = false;
        for (int i = 1; i < args.length; i++) {
            if (isImmediateFlag(args[i])) {
                immediate = true;
            } else if (args[i].equalsIgnoreCase("all") && instanceId.isEmpty() && !all) {
                all = true;
            } else if (instanceId.isEmpty() && !all) {
                instanceId = Optional.of(args[i]);
            } else {
                return new EndArgs(Optional.empty(), false, false, false);
            }
        }
        return new EndArgs(instanceId, immediate, all, true);
    }

    /** True for the -i and -immediate flags accepted by end. */
    public static boolean isImmediateFlag(String arg) {
        return arg.equalsIgnoreCase("-i") || arg.equalsIgnoreCase("-immediate");
    }

    @Override public String primaryName() {
        return "end";
    }

    @Override public Set<String> aliases() {
        return Set.of();
    }

    @Override public boolean execute(CommandSender sender, String[] args) {
        EndArgs parsed = parseEndArgs(args);
        if (!parsed.valid()) {
            return support.message(sender, texts.getEndUsage());
        }
        return executeParsed(sender, parsed);
    }

    public boolean executeParsed(CommandSender sender, EndArgs parsed) {
        if (parsed.all()) {
            List<GameInstance> live = game.liveInstances();
            if (live.isEmpty()) {
                return support.message(sender, texts.getNotActive());
            }
            for (GameInstance each : live) {
                game.cancel(each, parsed.immediate());
            }
            support.message(sender, texts.getEndAllSuccess(),
                    Map.of("count", String.valueOf(live.size())));
            return true;
        }
        GameInstance instance;
        if (parsed.instanceId().isPresent()) {
            Optional<GameInstance> resolved = game.resolveInstance(parsed.instanceId().get());
            if (resolved.isEmpty()) {
                return support.message(sender, texts.getInvalidInstanceId());
            }
            instance = resolved.get();
        } else if (sender instanceof Player player) {
            Optional<GameInstance> own = game.instanceOf(player.getUniqueId());
            if (own.isEmpty()) {
                if (!game.isActive()) {
                    return support.message(sender, texts.getNotActive());
                }
                return support.message(sender, texts.getNotInMatch());
            }
            instance = own.get();
        } else {
            return support.message(sender, texts.getConsoleRequiresId());
        }
        game.cancel(instance, parsed.immediate());
        return true;
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("end")) {
            List<String> options = new ArrayList<>(List.of("all", "-i", "-immediate"));
            options.addAll(CommandSupport.instanceIdOptions(game));
            return CommandSupport.partial(args[1], options);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("end")) {
            if (isImmediateFlag(args[1])) {
                List<String> options = new ArrayList<>(List.of("all"));
                options.addAll(CommandSupport.instanceIdOptions(game));
                return CommandSupport.partial(args[2], options);
            }
            return CommandSupport.partial(args[2], List.of("-i", "-immediate"));
        }
        return null;
    }
}
