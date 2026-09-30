package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.gui.menus.SpectatorMenus;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

/** Spectator toolbar dispatch without a Bukkit server. */
class SpectatorToolbarListenerTest {

    private record Fixture(SpectatorToolbarService toolbar, SpectatorMenus menus,
            SpectatorToolbarListener listener, Player player) {
    }

    private Fixture fixture() {
        SpectatorToolbarService toolbar = mock(SpectatorToolbarService.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(toolbar.isDeployed(player)).thenReturn(true);
        SpectatorMenus menus = mock(SpectatorMenus.class);
        return new Fixture(toolbar, menus,
                new SpectatorToolbarListener(toolbar, menus), player);
    }

    private PlayerInteractEvent interact(Player player, Action action) {
        return new PlayerInteractEvent(player, action, mock(ItemStack.class),
                mock(org.bukkit.block.Block.class), org.bukkit.block.BlockFace.UP);
    }

    @Test
    void compassRightClickOpensLobbies() {
        Fixture fixture = fixture();
        when(fixture.toolbar().toolbarButton(any())).thenReturn(Optional.of('c'));
        PlayerInteractEvent event = interact(fixture.player(), Action.RIGHT_CLICK_AIR);

        fixture.listener().onInteract(event);

        assertTrue(event.isCancelled());
        verify(fixture.menus()).openLobbiesMenu(fixture.player());
    }

    @Test
    void compassLeftClickOpensNothing() {
        Fixture fixture = fixture();
        when(fixture.toolbar().toolbarButton(any())).thenReturn(Optional.of('c'));
        PlayerInteractEvent event = interact(fixture.player(), Action.LEFT_CLICK_AIR);

        fixture.listener().onInteract(event);

        assertTrue(event.isCancelled());
        verify(fixture.menus(), never()).openLobbiesMenu(any());
    }

    @Test
    void backAndPlayerButtonsActOnAnyClick() {
        Fixture back = fixture();
        when(back.toolbar().toolbarButton(any())).thenReturn(Optional.of('b'));
        back.listener().onInteract(interact(back.player(), Action.LEFT_CLICK_BLOCK));
        verify(back.toolbar()).returnToLobby(back.player());

        Fixture players = fixture();
        when(players.toolbar().toolbarButton(any())).thenReturn(Optional.of('p'));
        players.listener().onInteract(interact(players.player(), Action.RIGHT_CLICK_BLOCK));
        verify(players.menus()).openPlayersMenu(players.player());
    }

    @Test
    void unmarkedItemPassesThrough() {
        Fixture fixture = fixture();
        when(fixture.toolbar().toolbarButton(any())).thenReturn(Optional.empty());
        PlayerInteractEvent event = interact(fixture.player(), Action.RIGHT_CLICK_AIR);

        fixture.listener().onInteract(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void staleToolbarItemRecoversInsteadOfActing() {
        Fixture fixture = fixture();
        when(fixture.toolbar().toolbarButton(any())).thenReturn(Optional.of('b'));
        when(fixture.toolbar().isDeployed(fixture.player())).thenReturn(false);
        PlayerInteractEvent event = interact(fixture.player(), Action.RIGHT_CLICK_AIR);

        fixture.listener().onInteract(event);

        assertTrue(event.isCancelled());
        verify(fixture.toolbar()).handleJoin(fixture.player());
        verify(fixture.toolbar(), never()).returnToLobby(any());
    }

    @Test
    void firstSneakTapStaysSilent() {
        Fixture fixture = fixture();
        when(fixture.toolbar().registerSneak(any(), anyLong())).thenReturn(false);

        fixture.listener().onSneak(new PlayerToggleSneakEvent(fixture.player(), true));

        verify(fixture.toolbar(), never()).exitFollow(any());
    }

    @Test
    void pairCompletingSneakExitsFollow() {
        Fixture fixture = fixture();
        when(fixture.toolbar().registerSneak(any(), anyLong())).thenReturn(true);

        fixture.listener().onSneak(new PlayerToggleSneakEvent(fixture.player(), true));

        verify(fixture.toolbar()).exitFollow(fixture.player());
    }

    @Test
    void unsneakLeavesFollowAlone() {
        Fixture fixture = fixture();

        fixture.listener().onSneak(new PlayerToggleSneakEvent(fixture.player(), false));

        verify(fixture.toolbar(), never()).registerSneak(any(), anyLong());
        verify(fixture.toolbar(), never()).exitFollow(any());
    }

    @Test
    void dropAndClickGuardsCancelToolbarItems() {
        Fixture fixture = fixture();
        when(fixture.toolbar().isToolbarItem(any())).thenReturn(true);
        Item drop = mock(Item.class);
        PlayerDropItemEvent dropEvent = new PlayerDropItemEvent(
                fixture.player(), drop);
        InventoryView view = mock(InventoryView.class);
        when(view.getPlayer()).thenReturn(fixture.player());
        InventoryClickEvent clickEvent = new InventoryClickEvent(view,
                InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.NOTHING);

        fixture.listener().onDrop(dropEvent);
        fixture.listener().onInventoryClick(clickEvent);

        assertTrue(dropEvent.isCancelled());
        assertTrue(clickEvent.isCancelled());
    }

    @Test
    void guardsIgnorePlainItems() {
        Fixture fixture = fixture();
        when(fixture.toolbar().isToolbarItem(any())).thenReturn(false);
        Item drop = mock(Item.class);
        PlayerDropItemEvent dropEvent = new PlayerDropItemEvent(
                fixture.player(), drop);

        fixture.listener().onDrop(dropEvent);

        assertFalse(dropEvent.isCancelled());
    }

    private InventoryClickEvent click(Fixture fixture, ClickType type) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        when(event.getWhoClicked()).thenReturn(fixture.player());
        when(event.getClick()).thenReturn(type);
        return event;
    }

    @Test
    void numberKeySwapWithToolbarHotbarItemCancels() {
        Fixture fixture = fixture();
        ItemStack toolbarStack = mock(ItemStack.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(fixture.player().getInventory()).thenReturn(inventory);
        when(inventory.getItem(8)).thenReturn(toolbarStack);
        when(fixture.toolbar().isToolbarItem(toolbarStack)).thenReturn(true);
        InventoryClickEvent event = click(fixture, ClickType.NUMBER_KEY);
        when(event.getHotbarButton()).thenReturn(8);

        fixture.listener().onInventoryClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void swapOffhandWithToolbarOffhandCancels() {
        Fixture fixture = fixture();
        ItemStack toolbarStack = mock(ItemStack.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(fixture.player().getInventory()).thenReturn(inventory);
        when(inventory.getItemInOffHand()).thenReturn(toolbarStack);
        when(fixture.toolbar().isToolbarItem(toolbarStack)).thenReturn(true);
        InventoryClickEvent event = click(fixture, ClickType.SWAP_OFFHAND);

        fixture.listener().onInventoryClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void spectatorHeadClickCancelsWithoutDeploy() {
        Fixture fixture = fixture();
        UUID playerId = fixture.player().getUniqueId();
        when(fixture.toolbar().isDeployed(fixture.player())).thenReturn(false);
        ItemStack head = mock(ItemStack.class);
        when(fixture.toolbar().isSpectatorHead(eq(head), eq(playerId))).thenReturn(true);
        InventoryClickEvent event = click(fixture, ClickType.LEFT);
        when(event.getCurrentItem()).thenReturn(head);

        fixture.listener().onInventoryClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void spectatorHeadDragCancelsWithoutDeploy() {
        Fixture fixture = fixture();
        UUID playerId = fixture.player().getUniqueId();
        when(fixture.toolbar().isDeployed(fixture.player())).thenReturn(false);
        ItemStack head = mock(ItemStack.class);
        when(fixture.toolbar().isSpectatorHead(eq(head), eq(playerId))).thenReturn(true);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(fixture.player());
        when(event.getCursor()).thenReturn(head);

        fixture.listener().onInventoryDrag(event);

        verify(event).setCancelled(true);
    }

    @Test
    void clicksIntoOwnInventoryCancel() {
        Fixture fixture = fixture();
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(fixture.player().getInventory()).thenReturn(inventory);
        InventoryClickEvent event = click(fixture, ClickType.LEFT);
        when(event.getClickedInventory()).thenReturn(inventory);

        fixture.listener().onInventoryClick(event);

        verify(event).setCancelled(true);
    }

    @Test
    void menuTopClickWithPlainItemsPasses() {
        Fixture fixture = fixture();
        when(fixture.player().getInventory()).thenReturn(mock(PlayerInventory.class));
        InventoryClickEvent event = click(fixture, ClickType.LEFT);
        when(event.getClickedInventory()).thenReturn(mock(Inventory.class));

        fixture.listener().onInventoryClick(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void swapHandsCancelsOnlyWhenDeployed() {
        Fixture fixture = fixture();
        ItemStack main = mock(ItemStack.class);
        ItemStack off = mock(ItemStack.class);
        PlayerSwapHandItemsEvent deployed =
                new PlayerSwapHandItemsEvent(fixture.player(), main, off);

        fixture.listener().onSwapHands(deployed);

        assertTrue(deployed.isCancelled());

        when(fixture.toolbar().isDeployed(fixture.player())).thenReturn(false);
        PlayerSwapHandItemsEvent loose =
                new PlayerSwapHandItemsEvent(fixture.player(), main, off);

        fixture.listener().onSwapHands(loose);

        assertFalse(loose.isCancelled());
    }

    @Test
    void dragTouchingOwnInventoryCancels() {
        Fixture fixture = fixture();
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(fixture.player());
        Inventory top = mock(Inventory.class);
        when(top.getSize()).thenReturn(27);
        when(event.getInventory()).thenReturn(top);
        when(event.getRawSlots()).thenReturn(Set.of(5, 30));

        fixture.listener().onInventoryDrag(event);

        verify(event).setCancelled(true);
    }

    @Test
    void dragWithToolbarCursorCancels() {
        Fixture fixture = fixture();
        ItemStack toolbarStack = mock(ItemStack.class);
        when(fixture.toolbar().isToolbarItem(toolbarStack)).thenReturn(true);
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(fixture.player());
        when(event.getCursor()).thenReturn(toolbarStack);

        fixture.listener().onInventoryDrag(event);

        verify(event).setCancelled(true);
    }

    @Test
    void dragConfinedToMenuTopPasses() {
        Fixture fixture = fixture();
        InventoryDragEvent event = mock(InventoryDragEvent.class);
        when(event.getWhoClicked()).thenReturn(fixture.player());
        Inventory top = mock(Inventory.class);
        when(top.getSize()).thenReturn(27);
        when(event.getInventory()).thenReturn(top);
        when(event.getRawSlots()).thenReturn(Set.of(0, 5));

        fixture.listener().onInventoryDrag(event);

        verify(event, never()).setCancelled(true);
    }
}
