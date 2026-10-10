package com.jruk8.jmanhunt.match.listeners;

import com.jruk8.jmanhunt.config.MiscConfig;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import java.util.Optional;

/**
 * Blocks the WorldEdit selection wand mid match: hunters and
 * speedrunners in an active match cannot select with a wooden axe.
 * Only the item-in-hand use is denied, so vanilla breaking and
 * stripping still work. Passive, no WorldEdit dependency.
 */
public final class WorldEditWandListener implements Listener {
    private final MiscConfig.Interop interop;
    private final GameManager game;
    private final PlayerStateStore playerStates;

    public WorldEditWandListener(MiscConfig.Interop interop, GameManager game,
            PlayerStateStore playerStates) {
        this.interop = interop;
        this.game = game;
        this.playerStates = playerStates;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (!interop.isBlockWorldeditWandInMatch()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getItem() == null || event.getItem().getType() != Material.WOODEN_AXE) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty() || !match.get().active()
                || !playerStates.role(player).isParticipant()) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
    }
}
