package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RolePadServiceTest {

    @Test
    void padMaterialsParseCaseInsensitively() {
        assertEquals(Material.LIME_CONCRETE, RolePadService.parsePadMaterial("lime_concrete"));
        assertEquals(Material.RED_CONCRETE, RolePadService.parsePadMaterial("RED_CONCRETE"));
        assertEquals(Material.LIGHT_GRAY_CONCRETE, RolePadService.parsePadMaterial(" light_gray_concrete "));
    }

    @Test
    void padMaterialsRejectBlanksAndUnknown() {
        assertNull(RolePadService.parsePadMaterial(null));
        assertNull(RolePadService.parsePadMaterial(""));
        assertNull(RolePadService.parsePadMaterial("not-a-material"));
    }

    @Test
    void spectatorGamemodeNeverTriggersPads() {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        ConfigService config = mock(ConfigService.class);
        when(plugin.configService()).thenReturn(config);
        when(config.getBoolean("world-engine.role-pads.enabled", true)).thenReturn(true);
        PlayerStateStore playerStates = mock(PlayerStateStore.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("jmh-lobby");
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        when(playerStates.role(player)).thenReturn(Role.HUNTER);
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getPlayer()).thenReturn(player);
        RolePadService pads = new RolePadService(plugin, mock(LobbyService.class),
                playerStates, mock(GameManager.class), mock(MessageService.class),
                mock(SoundService.class), () -> "jmh-lobby");

        pads.onMove(event);

        verify(playerStates, never()).setRole(any(Player.class), any(Role.class));
        verify(playerStates, never()).setRole(any(UUID.class), any(Role.class));
    }

    @Test
    void spectatorRoleInSurvivalPassesThePadGate() {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        ConfigService config = mock(ConfigService.class);
        when(plugin.configService()).thenReturn(config);
        when(config.getBoolean("world-engine.role-pads.enabled", true)).thenReturn(true);
        PlayerStateStore playerStates = mock(PlayerStateStore.class);
        World world = mock(World.class);
        when(world.getName()).thenReturn("jmh-lobby");
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getLocation()).thenReturn(mock(Location.class));
        when(playerStates.role(player)).thenReturn(Role.SPECTATOR);
        PlayerMoveEvent event = mock(PlayerMoveEvent.class);
        when(event.getPlayer()).thenReturn(player);
        RolePadService pads = new RolePadService(plugin, mock(LobbyService.class),
                playerStates, mock(GameManager.class), mock(MessageService.class),
                mock(SoundService.class), () -> "jmh-lobby");

        pads.onMove(event);

        // Reaching the location read proves the gamemode gate passed;
        // no pads are configured, so nothing assigns.
        verify(player).getLocation();
        verify(playerStates, never()).setRole(any(Player.class), any(Role.class));
        verify(playerStates, never()).setRole(any(UUID.class), any(Role.class));
    }

    @Test
    void packedBlocksDistinguishPositions() {
        assertEquals(RolePadService.packBlock(10, 64, -3), RolePadService.packBlock(10, 64, -3));
        assertNotEquals(RolePadService.packBlock(10, 64, -3), RolePadService.packBlock(11, 64, -3));
        assertNotEquals(RolePadService.packBlock(10, 64, -3), RolePadService.packBlock(10, 65, -3));
        assertNotEquals(RolePadService.packBlock(10, 64, -3), RolePadService.packBlock(10, 64, -4));
    }
}
