package com.jruk8.jmanhunt.lobby;

import org.bukkit.World;

/** A lobby world plus how it came to be. */
public record LobbyWorld(World world, boolean created, boolean lobbyZeroSet) {
}