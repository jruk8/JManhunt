package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.gui.menus.SpectatorMenus;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
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
    void sneakExitsFollow() {
        Fixture fixture = fixture();

        fixture.listener().onSneak(new PlayerToggleSneakEvent(fixture.player(), true));

        verify(fixture.toolbar()).exitFollow(fixture.player());
    }

    @Test
    void unsneakLeavesFollowAlone() {
        Fixture fixture = fixture();

        fixture.listener().onSneak(new PlayerToggleSneakEvent(fixture.player(), false));

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
}
