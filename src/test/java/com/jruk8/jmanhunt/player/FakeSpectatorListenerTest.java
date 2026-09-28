package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;

/** Fake spectator gate decisions without a Bukkit server. */
class FakeSpectatorListenerTest {

    private record Fixture(FakeSpectatorService fakes, PlayerStateStore players,
            GameManager game, FakeSpectatorListener listener, Player player) {
    }

    private Fixture fixture() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return new Fixture(mock(FakeSpectatorService.class), mock(PlayerStateStore.class),
                mock(GameManager.class), null, player);
    }

    private FakeSpectatorListener listener(Fixture fixture) {
        return new FakeSpectatorListener(fixture.fakes(), fixture.players(), fixture.game());
    }

    private void fake(Fixture fixture) {
        when(fixture.fakes().isFakeSpectator(fixture.player())).thenReturn(true);
    }

    @Test
    void joinClearsDanglingStateAndHidesActives() {
        Fixture fixture = fixture();
        when(fixture.players().role(fixture.player())).thenReturn(Role.HUNTER);

        listener(fixture).onJoin(new PlayerJoinEvent(fixture.player(), ""));

        verify(fixture.fakes()).clearDanglingState(fixture.player());
        verify(fixture.fakes()).hideFrom(fixture.player());
    }

    @Test
    void joinReenablesSpectatorRoleInMatch() {
        Fixture fixture = fixture();
        when(fixture.players().role(fixture.player())).thenReturn(Role.SPECTATOR);
        when(fixture.game().instanceOf(fixture.player().getUniqueId()))
                .thenReturn(Optional.of(mock(GameInstance.class)));

        listener(fixture).onJoin(new PlayerJoinEvent(fixture.player(), ""));

        verify(fixture.fakes()).enable(fixture.player());
    }

    @Test
    void joinNeverEnablesSpectatorRoleOutsideMatch() {
        Fixture fixture = fixture();
        when(fixture.players().role(fixture.player())).thenReturn(Role.SPECTATOR);
        when(fixture.game().instanceOf(fixture.player().getUniqueId()))
                .thenReturn(Optional.empty());

        listener(fixture).onJoin(new PlayerJoinEvent(fixture.player(), ""));

        verify(fixture.fakes(), never()).enable(any(Player.class));
    }

    @Test
    void quitDisables() {
        Fixture fixture = fixture();

        listener(fixture).onQuit(new PlayerQuitEvent(fixture.player(), ""));

        verify(fixture.fakes()).handleQuit(fixture.player());
    }

    @Test
    void cancelsDamageHungerTargetPlaceBreakForFakes() {
        Fixture fixture = fixture();
        fake(fixture);
        Block block = mock(Block.class);
        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(fixture.player());
        FoodLevelChangeEvent hunger = new FoodLevelChangeEvent(fixture.player(), 10);
        EntityTargetEvent target = new EntityTargetEvent(mock(org.bukkit.entity.Entity.class),
                fixture.player(), EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        BlockPlaceEvent place = new BlockPlaceEvent(block, mock(org.bukkit.block.BlockState.class),
                block, mock(org.bukkit.inventory.ItemStack.class), fixture.player(), true,
                EquipmentSlot.HAND);
        BlockBreakEvent breakEvent = new BlockBreakEvent(block, fixture.player());

        FakeSpectatorListener listener = listener(fixture);
        listener.onDamage(damage);
        listener.onHunger(hunger);
        listener.onTarget(target);
        listener.onPlace(place);
        listener.onBreak(breakEvent);

        verify(damage).setCancelled(true);
        assertTrue(hunger.isCancelled());
        assertTrue(target.isCancelled());
        assertTrue(place.isCancelled());
        assertTrue(breakEvent.isCancelled());
    }

    @Test
    void ignoresNonFakes() {
        Fixture fixture = fixture();
        EntityDamageEvent damage = mock(EntityDamageEvent.class);
        when(damage.getEntity()).thenReturn(fixture.player());

        listener(fixture).onDamage(damage);

        verify(damage, never()).setCancelled(true);
    }

    @Test
    void cancelsPickupForFakes() {
        Fixture fixture = fixture();
        fake(fixture);
        EntityPickupItemEvent pickup = new EntityPickupItemEvent(fixture.player(),
                mock(org.bukkit.entity.Item.class), 1);

        listener(fixture).onPickup(pickup);

        assertTrue(pickup.isCancelled());
    }

    @Test
    void allowsPickupForNonFakes() {
        Fixture fixture = fixture();
        EntityPickupItemEvent pickup = new EntityPickupItemEvent(fixture.player(),
                mock(org.bukkit.entity.Item.class), 1);

        listener(fixture).onPickup(pickup);

        assertFalse(pickup.isCancelled());
    }

    @Test
    void cancelsBlockClickButAllowsHotbarUse() {
        Fixture fixture = fixture();
        fake(fixture);
        PlayerInteractEvent blockClick = new PlayerInteractEvent(fixture.player(),
                Action.RIGHT_CLICK_BLOCK, mock(org.bukkit.inventory.ItemStack.class),
                mock(Block.class), org.bukkit.block.BlockFace.UP);
        PlayerInteractEvent hotbarUse = new PlayerInteractEvent(fixture.player(),
                Action.RIGHT_CLICK_AIR, mock(org.bukkit.inventory.ItemStack.class),
                mock(Block.class), org.bukkit.block.BlockFace.UP);

        FakeSpectatorListener listener = listener(fixture);
        listener.onInteract(blockClick);
        listener.onInteract(hotbarUse);

        assertTrue(blockClick.isCancelled());
        assertFalse(hotbarUse.isCancelled());
    }

    @Test
    void cancelsDamageDealtByFakesAndTheirProjectiles() {
        Fixture fixture = fixture();
        fake(fixture);
        Projectile projectile = mock(Projectile.class);
        when(projectile.getShooter()).thenReturn(fixture.player());
        EntityDamageByEntityEvent melee = mock(EntityDamageByEntityEvent.class);
        when(melee.getDamager()).thenReturn(fixture.player());
        EntityDamageByEntityEvent shot = mock(EntityDamageByEntityEvent.class);
        when(shot.getDamager()).thenReturn(projectile);

        FakeSpectatorListener listener = listener(fixture);
        listener.onDealDamage(melee);
        listener.onDealDamage(shot);

        verify(melee).setCancelled(true);
        verify(shot).setCancelled(true);
    }

    @Test
    void cancelsNetherAndEndPortalsForFakes() {
        Fixture fixture = fixture();
        fake(fixture);
        Location from = mock(Location.class);
        Location to = mock(Location.class);
        PlayerPortalEvent nether = new PlayerPortalEvent(fixture.player(), from, to,
                PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);
        PlayerPortalEvent end = new PlayerPortalEvent(fixture.player(), from, to,
                PlayerTeleportEvent.TeleportCause.END_PORTAL);

        FakeSpectatorListener listener = listener(fixture);
        listener.onPortal(nether);
        listener.onPortal(end);

        assertTrue(nether.isCancelled());
        assertTrue(end.isCancelled());
    }

    @Test
    void allowsOtherPortalCausesAndNonFakes() {
        Fixture fixture = fixture();
        fake(fixture);
        Location from = mock(Location.class);
        Location to = mock(Location.class);
        PlayerPortalEvent gateway = new PlayerPortalEvent(fixture.player(), from, to,
                PlayerTeleportEvent.TeleportCause.END_GATEWAY);

        listener(fixture).onPortal(gateway);

        assertFalse(gateway.isCancelled());

        Fixture plain = fixture();
        PlayerPortalEvent nether = new PlayerPortalEvent(plain.player(), from, to,
                PlayerTeleportEvent.TeleportCause.NETHER_PORTAL);

        listener(plain).onPortal(nether);

        assertFalse(nether.isCancelled());
    }
}
