package com.jruk8.jmanhunt;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Live-server delivery for debug lines: console sender plus online players. */
public final class BukkitDebugSink implements JManhuntLogger.DebugSink {
    public static final BukkitDebugSink INSTANCE = new BukkitDebugSink();

    private BukkitDebugSink() {
    }

    @Override
    public void sendToConsole(Component message) {
        Bukkit.getConsoleSender().sendMessage(message);
    }

    @Override
    public void sendToPlayer(UUID playerId, Component message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(message);
        }
    }
}
