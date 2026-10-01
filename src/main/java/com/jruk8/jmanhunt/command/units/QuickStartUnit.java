package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.QuickStartArgs;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.lifecycle.QuickStartOutcome;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Set;

/** The quickstart and qs verbs: assign teams and start immediately. */
public final class QuickStartUnit implements SubcommandUnit {
    private final GameManager game;
    private final LobbyService lobbies;
    private final ManhuntMessages texts;
    private final CommandSupport support;

    public QuickStartUnit(GameManager game, LobbyService lobbies, ManhuntMessages texts,
            CommandSupport support) {
        this.game = game;
        this.lobbies = lobbies;
        this.texts = texts;
        this.support = support;
    }

    public static QuickStartArgs parseQuickStartArgs(String[] args) {
        if (args.length > 2) {
            return new QuickStartArgs(null, false);
        }
        if (args.length == 1) {
            return new QuickStartArgs(null, true);
        }
        try {
            return new QuickStartArgs(Integer.parseInt(args[1]), true);
        } catch (NumberFormatException exception) {
            return new QuickStartArgs(null, false);
        }
    }

    @Override public String primaryName() {
        return "quickstart";
    }

    @Override public Set<String> aliases() {
        return Set.of("qs");
    }

    @Override public boolean execute(CommandSender sender, String[] args) {
        QuickStartArgs parsed = parseQuickStartArgs(args);
        if (!parsed.valid()) {
            return support.message(sender, texts.getQuickstartUsage());
        }
        return executeParsed(sender, parsed);
    }

    public boolean executeParsed(CommandSender sender, QuickStartArgs parsed) {
        int percent = parsed.percent() == null ? -1 : parsed.percent();
        if (parsed.percent() != null && (percent < 0 || percent > 100)) {
            return support.message(sender, texts.getQuickstartInvalidPercent());
        }
        int lobbyId;
        if (sender instanceof Player player) {
            lobbyId = lobbies.lobbyOf(player.getUniqueId()).map(Lobby::id)
                    .orElseGet(() -> lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0);
        } else {
            lobbyId = lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0;
        }
        if (lobbyId < 0) {
            return support.message(sender, texts.getQuickstartFailed());
        }
        if (StartUnit.liveMatchBlocks(game, lobbies, lobbyId)) {
            return support.message(sender, texts.getAlreadyActive());
        }
        Location surroundOrigin = sender instanceof Player executor ? executor.getLocation() : null;
        QuickStartOutcome outcome = game.quickStart(percent, lobbyId, surroundOrigin);
        if (!outcome.started()) {
            return support.message(sender, texts.getQuickstartFailed());
        }
        return true;
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2 && (args[0].equalsIgnoreCase("quickstart") || args[0].equalsIgnoreCase("qs"))) {
            return CommandSupport.partial(args[1], List.of("50"));
        }
        return null;
    }
}
