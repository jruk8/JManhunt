package com.jruk8.jmanhunt.tutorial.ports;

import org.bukkit.entity.Player;

/** Plays the tutorial's neutral and angry feedback sounds. */
public interface TutorialSoundPlayer {

    void playNeutral(Player player);

    void playAngry(Player player);
}
