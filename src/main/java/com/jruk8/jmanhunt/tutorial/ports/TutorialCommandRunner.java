package com.jruk8.jmanhunt.tutorial.ports;

import org.bukkit.entity.Player;

/** Runs a configured tutorial command as the player, with their permissions. */
public interface TutorialCommandRunner {

    void runAsPlayer(Player player, String command);
}
