package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.command.UnitSyntaxException;
import com.jruk8.jmanhunt.lobby.Lobby;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** The start verb: start a match in a lobby. */
public final class StartUnit implements SubcommandUnit {
    private final GameManager game;
    private final LobbyService lobbies;
    private final ManhuntMessages texts;
    private final CommandSupport support;

    public StartUnit(GameManager game, LobbyService lobbies, ManhuntMessages texts,
            CommandSupport support) {
        this.game = game;
        this.lobbies = lobbies;
        this.texts = texts;
        this.support = support;
    }

    /** Empty lobby id means the sender's lobby (or the default). */
    public record StartArgs(Optional<Integer> lobbyId) {
    }

    public static StartArgs parse(String[] args, ManhuntMessages texts)
            throws UnitSyntaxException {
        if (args.length == 1) {
            return new StartArgs(Optional.empty());
        }
        if (args.length == 2) {
            OptionalInt parsed = LobbyService.parseId(args[1]);
            if (parsed.isPresent()) {
                return new StartArgs(Optional.of(parsed.getAsInt()));
            }
            throw new UnitSyntaxException(texts.getLobbyInvalidId(), Map.of());
        }
        throw new UnitSyntaxException(texts.getStartUsage(), Map.of());
    }

    @Override public String primaryName() {
        return "start";
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

    public boolean executeParsed(CommandSender sender, StartArgs args) {
        int lobbyId = args.lobbyId().orElseGet(() -> startLobbyFor(sender));
        if (lobbyId < 0) {
            return support.message(sender, texts.getStartInvalid());
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId != 0) {
            return support.message(sender, texts.getLobbyWorldengineRequired());
        }
        if (liveMatchBlocks(game, lobbies, lobbyId)) {
            return support.message(sender, texts.getAlreadyActive());
        }
        Location surroundOrigin = sender instanceof Player executor ? executor.getLocation() : null;
        if (!game.start(lobbyId, surroundOrigin)) {
            return support.message(sender, texts.getStartInvalid());
        }
        return true;
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return CommandSupport.partial(args[1], CommandSupport.lobbyIdOptions(lobbies));
        }
        return null;
    }

    /**
     * True when a live lobby match blocks a new start: any live match
     * except under sublobby policies with the world engine on, where the
     * new match becomes the next child sublobby.
     */
    static boolean liveMatchBlocks(GameManager game, LobbyService lobbies, int lobbyId) {
        return game.instanceForLobby(lobbyId).isPresent()
                && !lobbies.midMatchPolicy().allowsConcurrentStart(lobbies.multiLobbyAllowed());
    }

    /** Lobby a bare start targets: the sender's lobby, else the default. */
    private int startLobbyFor(CommandSender sender) {
        if (sender instanceof Player player) {
            return lobbies.lobbyOf(player.getUniqueId()).map(Lobby::id)
                    .orElseGet(() -> lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0);
        }
        return lobbies.multiLobbyAllowed() ? lobbies.defaultLobbyId() : 0;
    }
}
