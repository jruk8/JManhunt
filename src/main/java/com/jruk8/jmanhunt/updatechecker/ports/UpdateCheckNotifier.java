package com.jruk8.jmanhunt.updatechecker.ports;

import org.bukkit.entity.Player;

/** Delivers update notices to admins. Adapted per platform. */
public interface UpdateCheckNotifier {
    /** Notices every online admin. */
    void notifyAdmins(String message);

    /** Notices one joining admin when an update is pending. */
    void notifyPlayer(Player player, String message);
}
