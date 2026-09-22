package com.jruk8.jmanhunt.lobby.world;

import org.bukkit.World;

/** A lobby world plus how it came to be. */
public record LobbyWorld(World world, boolean created, boolean lobbyZeroSet) {
}