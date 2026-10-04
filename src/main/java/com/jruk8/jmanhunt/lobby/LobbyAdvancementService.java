package com.jruk8.jmanhunt.lobby;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import java.util.function.Predicate;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Blocks advancement criterion grants in the lobby world, so waiting
 * never unlocks toasts, chat lines, or recipe book entries. Unlike
 * lobby protection this is unconditional: no toggle and no permission
 * bypass. Match worlds are untouched.
 */
public final class LobbyAdvancementService implements Listener {

    private final Predicate<World> lobbyWorlds;

    public LobbyAdvancementService(Predicate<World> lobbyWorlds) {
        this.lobbyWorlds = lobbyWorlds;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCriterionGrant(PlayerAdvancementCriterionGrantEvent event) {
        if (lobbyWorlds.test(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }
}
