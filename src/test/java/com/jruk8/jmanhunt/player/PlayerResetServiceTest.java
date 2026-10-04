package com.jruk8.jmanhunt.player;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class PlayerResetServiceTest {

    @Test
    void clearContainersClearsInventoryAndEnderChest() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        Inventory enderChest = mock(Inventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getEnderChest()).thenReturn(enderChest);

        PlayerResetService.clearContainers(player);

        verify(inventory).clear();
        verify(enderChest).clear();
    }
}
