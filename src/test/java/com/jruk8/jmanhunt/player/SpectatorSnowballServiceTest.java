package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

/** Spectator snowball throw gating, restore, and zero-damage rule. */
class SpectatorSnowballServiceTest {

    private record Fixture(SpectatorSnowballService snowballs, SpectatorToolbarService toolbar,
            MessageService messages, SoundService sounds, Player player,
            PlayerInventory inventory, ItemStack stack) {
    }

    private Fixture fixture() {
        SpectatorToolbarService toolbar = mock(SpectatorToolbarService.class);
        MessageService messages = mock(MessageService.class);
        SoundService sounds = mock(SoundService.class);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack stack = mock(ItemStack.class);
        when(toolbar.isDeployed(player)).thenReturn(true);
        when(toolbar.snowballEnabled(player)).thenReturn(true);
        when(toolbar.snowballCooldownSeconds(player)).thenReturn(8);
        when(toolbar.layout(player)).thenReturn(SpectatorToolbarService.parseLayout("cp##s###b"));
        when(toolbar.snowballItem(anyInt())).thenReturn(stack);
        SpectatorSnowballService snowballs = new SpectatorSnowballService(
                mock(Plugin.class), toolbar, messages, sounds);
        return new Fixture(snowballs, toolbar, messages, sounds, player, inventory, stack);
    }

    private Snowball thrownBy(Fixture fixture) {
        Snowball snowball = mock(Snowball.class);
        when(snowball.getShooter()).thenReturn(fixture.player());
        return snowball;
    }

    @Test
    void spectatorThrowRestoresTagsAndCoolsDown() {
        Fixture fixture = fixture();
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(thrownBy(fixture));

        fixture.snowballs().onLaunch(event);

        assertFalse(event.isCancelled());
        verify(fixture.inventory()).setItem(4, fixture.stack());
        verify(fixture.player()).setCooldown(Material.SNOWBALL, 160);
    }

    @Test
    void cooldownBlockedThrowCancelsButKeepsItem() {
        Fixture fixture = fixture();
        when(fixture.player().hasCooldown(Material.SNOWBALL)).thenReturn(true);
        when(fixture.player().getCooldown(Material.SNOWBALL)).thenReturn(45);
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(thrownBy(fixture));

        fixture.snowballs().onLaunch(event);

        assertTrue(event.isCancelled());
        verify(fixture.inventory()).setItem(4, fixture.stack());
        verify(fixture.player(), never()).setCooldown(any(), any(Integer.class));
        verify(fixture.messages()).message(eq(fixture.player()),
                eq("spectator.snowball-cooldown"), eq(Map.of("seconds", "3")));
        verify(fixture.sounds()).playNeutralSound(fixture.player());
    }

    @Test
    void zeroCooldownThrowsFreely() {
        Fixture fixture = fixture();
        when(fixture.toolbar().snowballCooldownSeconds(fixture.player())).thenReturn(0);
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(thrownBy(fixture));

        fixture.snowballs().onLaunch(event);

        assertFalse(event.isCancelled());
        verify(fixture.inventory()).setItem(4, fixture.stack());
        verify(fixture.player(), never()).setCooldown(any(), any(Integer.class));
    }

    @Test
    void disabledSnowballLeavesThrowsAlone() {
        Fixture fixture = fixture();
        when(fixture.toolbar().snowballEnabled(fixture.player())).thenReturn(false);
        ProjectileLaunchEvent event = new ProjectileLaunchEvent(thrownBy(fixture));

        fixture.snowballs().onLaunch(event);

        assertFalse(event.isCancelled());
        verifyNoInteractions(fixture.inventory(), fixture.messages(), fixture.sounds());
    }

    @Test
    void undeployedShooterAndOtherProjectilesPassThrough() {
        Fixture fixture = fixture();
        when(fixture.toolbar().isDeployed(fixture.player())).thenReturn(false);
        ProjectileLaunchEvent snowballEvent = new ProjectileLaunchEvent(thrownBy(fixture));
        Arrow arrow = mock(Arrow.class);
        ProjectileLaunchEvent arrowEvent = new ProjectileLaunchEvent(arrow);

        fixture.snowballs().onLaunch(snowballEvent);
        fixture.snowballs().onLaunch(arrowEvent);

        assertFalse(snowballEvent.isCancelled());
        assertFalse(arrowEvent.isCancelled());
        verifyNoInteractions(fixture.inventory(), fixture.messages(), fixture.sounds());
    }

    @Test
    void taggedSnowballDamageCancelled() {
        // Real damage events need a running server, so the event is mocked.
        Fixture fixture = fixture();
        Snowball snowball = mock(Snowball.class);
        when(snowball.hasMetadata(SpectatorSnowballService.TAG)).thenReturn(true);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(snowball);

        fixture.snowballs().onDamage(event);

        verify(event).setCancelled(true);
    }

    @Test
    void untaggedDamagePassesThrough() {
        Fixture fixture = fixture();
        Snowball snowball = mock(Snowball.class);
        when(snowball.hasMetadata(SpectatorSnowballService.TAG)).thenReturn(false);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(snowball);

        fixture.snowballs().onDamage(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    void cooldownTicksScaleSeconds() {
        assertEquals(160, SpectatorSnowballService.cooldownTicks(8));
        assertEquals(0, SpectatorSnowballService.cooldownTicks(0));
        assertEquals(0, SpectatorSnowballService.cooldownTicks(-3));
    }

    @Test
    void snowballSlotFollowsLayout() {
        assertEquals(4, SpectatorToolbarService.snowballSlot(
                SpectatorToolbarService.parseLayout("cp##s###b")));
        assertEquals(-1, SpectatorToolbarService.snowballSlot(
                SpectatorToolbarService.parseLayout("cp######b")));
    }
}
