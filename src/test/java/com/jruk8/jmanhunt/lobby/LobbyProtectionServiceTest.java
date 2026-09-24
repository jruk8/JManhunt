package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Lobby world protection decisions without a Bukkit server. */
class LobbyProtectionServiceTest {

    private LobbyProtectionService service(LobbyConfig config) {
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.lobbyConfig()).thenReturn(config);
        return new LobbyProtectionService(plugin, () -> "jmh-lobby");
    }

    private World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        return world;
    }

    private Player player(World world, boolean bypass) {
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.hasPermission(LobbyProtectionService.EDIT_PERMISSION)).thenReturn(bypass);
        return player;
    }

    @Test
    void deniesUnpermittedPlayersInProtectedLobbyWorld() {
        LobbyProtectionService service = service(new LobbyConfig());

        assertTrue(service.denies(player(world("jmh-lobby"), false)));
    }

    @Test
    void allowsBypassHolders() {
        LobbyProtectionService service = service(new LobbyConfig());

        assertFalse(service.denies(player(world("jmh-lobby"), true)));
    }

    @Test
    void ignoresOtherWorlds() {
        LobbyProtectionService service = service(new LobbyConfig());

        assertFalse(service.denies(player(world("world"), false)));
    }

    @Test
    void ignoresDisabledProtection() {
        LobbyConfig config = new LobbyConfig();
        config.setProtectedWorld(false);

        assertFalse(service(config).denies(player(world("jmh-lobby"), false)));
    }

    @Test
    void cancelsBreaksWithoutBypass() {
        LobbyProtectionService service = service(new LobbyConfig());
        World world = world("jmh-lobby");
        Block block = mock(Block.class);
        when(block.getWorld()).thenReturn(world);
        BlockBreakEvent event = new BlockBreakEvent(block, player(world, false));

        service.onBlockBreak(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void locksHungerInProtectedLobbyWorld() {
        LobbyProtectionService service = service(new LobbyConfig());
        Player player = player(world("jmh-lobby"), false);
        FoodLevelChangeEvent event = new FoodLevelChangeEvent(player, 10);

        service.onHunger(event);

        assertTrue(event.isCancelled());
    }
}
