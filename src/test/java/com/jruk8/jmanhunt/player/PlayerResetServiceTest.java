package com.jruk8.jmanhunt.player;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

class PlayerResetServiceTest {

    private record Fixture(Player player, InventoryView view, World world) {
    }

    private static Fixture fixture() {
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[0]);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[0]);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getEnderChest()).thenReturn(mock(Inventory.class));
        InventoryView view = mock(InventoryView.class);
        when(player.getOpenInventory()).thenReturn(view);
        World world = mock(World.class);
        when(player.getLocation()).thenReturn(new Location(world, 1.0, 2.0, 3.0));
        return new Fixture(player, view, world);
    }

    @Test
    void clearContainersClearsInventoryAndEnderChest() {
        Fixture fixture = fixture();

        PlayerResetService.clearContainers(fixture.player());

        verify(fixture.player().getInventory()).clear();
        verify(fixture.player().getEnderChest()).clear();
    }

    @Test
    void clearContainersClearsCraftingGridAndCursor() {
        Fixture fixture = fixture();
        CraftingInventory crafting = mock(CraftingInventory.class);
        when(fixture.view().getTopInventory()).thenReturn(crafting);

        PlayerResetService.clearContainers(fixture.player());

        verify(fixture.player().getInventory()).clear();
        verify(fixture.player().getEnderChest()).clear();
        verify(crafting).clear();
        verify(fixture.view()).setCursor(null);
    }

    @Test
    void clearContainersLeavesOpenContainerAlone() {
        Fixture fixture = fixture();
        Inventory chest = mock(Inventory.class);
        when(fixture.view().getTopInventory()).thenReturn(chest);

        PlayerResetService.clearContainers(fixture.player());

        verify(chest, never()).clear();
        verify(fixture.view()).setCursor(null);
    }

    @Test
    void dropAllGearDropsGridAndCursor() {
        Fixture fixture = fixture();
        CraftingInventory crafting = mock(CraftingInventory.class);
        when(crafting.getMatrix()).thenReturn(
                new ItemStack[] {new ItemStack(Material.STONE), null});
        when(fixture.view().getTopInventory()).thenReturn(crafting);
        when(fixture.view().getCursor()).thenReturn(new ItemStack(Material.DIAMOND));

        PlayerResetService.dropAllGear(fixture.player());

        verify(fixture.world(), times(2)).dropItemNaturally(any(Location.class), any(ItemStack.class));
        verify(crafting).clear();
        verify(fixture.view()).setCursor(null);
    }

    @Test
    void dropAllGearLeavesOpenContainerAlone() {
        Fixture fixture = fixture();
        Inventory chest = mock(Inventory.class);
        when(fixture.view().getTopInventory()).thenReturn(chest);
        when(fixture.view().getCursor()).thenReturn(new ItemStack(Material.DIAMOND));

        PlayerResetService.dropAllGear(fixture.player());

        verify(chest, never()).clear();
        verify(fixture.world(), times(1)).dropItemNaturally(any(Location.class), any(ItemStack.class));
        verify(fixture.view()).setCursor(null);
    }
}
