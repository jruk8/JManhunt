package com.jruk8.jmanhunt.core;

import net.kyori.adventure.text.Component;
import java.util.UUID;

/** Delivers rendered debug lines; a Bukkit implementation lives in {@link BukkitDebugSink}. */
public interface DebugSink {
    void sendToConsole(Component message);
    void sendToPlayer(UUID playerId, Component message);
}