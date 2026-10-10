package com.jruk8.jmanhunt.match.listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.config.MiscConfig;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class WorldEditWandListenerTest {

    private record Fixture(WorldEditWandListener listener, Player player, GameManager game,
            MiscConfig.Interop interop) {
    }

    private static Fixture fixture(Role role, boolean inMatch) {
        MiscConfig.Interop interop = new MiscConfig.Interop();
        interop.setBlockWorldeditWandInMatch(true);
        GameManager game = mock(GameManager.class);
        PlayerStateStore states = new PlayerStateStore();
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        states.setRole(id, role);
        if (inMatch) {
            GameInstance instance = mock(GameInstance.class);
            when(instance.active()).thenReturn(true);
            when(game.instanceOf(id)).thenReturn(Optional.of(instance));
        } else {
            when(game.instanceOf(id)).thenReturn(Optional.empty());
        }
        return new Fixture(new WorldEditWandListener(interop, game, states), player, game,
                interop);
    }

    private static PlayerInteractEvent interact(Player player, Action action, Material material,
            EquipmentSlot hand) {
        ItemStack item = material == null ? null : mock(ItemStack.class);
        if (item != null) {
            when(item.getType()).thenReturn(material);
        }
        return new PlayerInteractEvent(player, action, item, mock(Block.class), null, hand);
    }

    @Test
    void deniesAxeSelectionForParticipantsInMatch() {
        Fixture fixture = fixture(Role.HUNTER, true);
        PlayerInteractEvent event = interact(fixture.player(), Action.LEFT_CLICK_BLOCK,
                Material.WOODEN_AXE, EquipmentSlot.HAND);

        fixture.listener().onInteract(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void deniesRightClickToo() {
        Fixture fixture = fixture(Role.SPEEDRUNNER, true);
        PlayerInteractEvent event = interact(fixture.player(), Action.RIGHT_CLICK_BLOCK,
                Material.WOODEN_AXE, EquipmentSlot.HAND);

        fixture.listener().onInteract(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void ignoresWhenDisabledOffhandWrongItemAirOrOutsideMatch() {
        Fixture hunter = fixture(Role.HUNTER, true);
        hunter.interop().setBlockWorldeditWandInMatch(false);
        PlayerInteractEvent disabled = interact(hunter.player(), Action.LEFT_CLICK_BLOCK,
                Material.WOODEN_AXE, EquipmentSlot.HAND);
        hunter.listener().onInteract(disabled);
        assertFalse(disabled.isCancelled());

        Fixture on = fixture(Role.HUNTER, true);
        PlayerInteractEvent offhand = interact(on.player(), Action.LEFT_CLICK_BLOCK,
                Material.WOODEN_AXE, EquipmentSlot.OFF_HAND);
        on.listener().onInteract(offhand);
        assertFalse(offhand.isCancelled());

        PlayerInteractEvent pickaxe = interact(on.player(), Action.LEFT_CLICK_BLOCK,
                Material.DIAMOND_PICKAXE, EquipmentSlot.HAND);
        on.listener().onInteract(pickaxe);
        assertFalse(pickaxe.isCancelled());

        PlayerInteractEvent air = interact(on.player(), Action.LEFT_CLICK_AIR,
                Material.WOODEN_AXE, EquipmentSlot.HAND);
        on.listener().onInteract(air);
        assertFalse(air.isCancelled());

        Fixture spectator = fixture(Role.SPECTATOR, true);
        PlayerInteractEvent watching = interact(spectator.player(), Action.LEFT_CLICK_BLOCK,
                Material.WOODEN_AXE, EquipmentSlot.HAND);
        spectator.listener().onInteract(watching);
        assertFalse(watching.isCancelled());

        Fixture lobby = fixture(Role.HUNTER, false);
        PlayerInteractEvent outside = interact(lobby.player(), Action.LEFT_CLICK_BLOCK,
                Material.WOODEN_AXE, EquipmentSlot.HAND);
        lobby.listener().onInteract(outside);
        assertFalse(outside.isCancelled());
    }
}
