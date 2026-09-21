package com.jruk8.jmanhunt.tutorial.ports;

import org.bukkit.entity.Player;
import java.util.List;

/** Sends fully rendered dialogue lines to one player. */
public interface TutorialMessenger {

    void send(Player player, List<String> lines);
}
