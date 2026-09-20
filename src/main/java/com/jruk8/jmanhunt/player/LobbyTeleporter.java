package com.jruk8.jmanhunt.player;

import org.bukkit.entity.Player;
import java.util.List;

public interface LobbyTeleporter {
    public boolean teleportToLobby(List<Player> targets, int lobbyId);

    /**
     * Sets the respawn location of the given players to the given lobby
     * without teleporting them. Returns false if the world-engine is
     * disabled or the lobby location is invalid.
     */
    public boolean setSpawnToLobby(List<Player> targets, int lobbyId);
}
