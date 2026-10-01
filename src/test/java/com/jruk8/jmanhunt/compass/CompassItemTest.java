package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.config.ConfigPathMapper;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.JManhuntConfig;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.modifiers.ModifierStore;
import com.jruk8.jmanhunt.modifiers.files.ModifierFiles;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompassItemTest {

    @Test
    void resolvesModernNames() {
        assertEquals(Material.COMPASS, CompassItemService.resolveCompassMaterial("compass"));
        assertEquals(Material.COMPASS, CompassItemService.resolveCompassMaterial("minecraft:compass"));
        assertEquals(Material.RECOVERY_COMPASS,
                CompassItemService.resolveCompassMaterial("recovery_compass"));
        assertEquals(Material.CLOCK, CompassItemService.resolveCompassMaterial("minecraft:clock"));
    }

    @Test
    void rejectsUnknownNames() {
        assertNull(CompassItemService.resolveCompassMaterial("compas"));
        assertNull(CompassItemService.resolveCompassMaterial(null));
        assertNull(CompassItemService.resolveCompassMaterial("  "));
        assertNull(CompassItemService.resolveCompassMaterial("other:compass"));
    }

    @Test
    void rejectsPlaceableItems() {
        assertNull(CompassItemService.resolveCompassMaterial("dirt"));
        assertNull(CompassItemService.resolveCompassMaterial("minecraft:oak_sign"));
        assertNull(CompassItemService.resolveCompassMaterial("redstone"));
        assertNull(CompassItemService.resolveCompassMaterial("minecraft:water_bucket"));
        assertNull(CompassItemService.resolveCompassMaterial("zombie_spawn_egg"));
    }

    @Test
    void allowsRawItems() {
        assertTrue(CompassItemService.isAllowedCompassItem(Material.COMPASS));
        assertTrue(CompassItemService.isAllowedCompassItem(Material.CLOCK));
        assertTrue(CompassItemService.isAllowedCompassItem(Material.RECOVERY_COMPASS));
        assertFalse(CompassItemService.isAllowedCompassItem(Material.DIRT));
        assertFalse(CompassItemService.isAllowedCompassItem(null));
    }

    @Test
    void skipsOnlyRespawningSpectators() {
        assertTrue(CompassManager.skipLastSeen(true, true));
        assertFalse(CompassManager.skipLastSeen(true, false));
        assertFalse(CompassManager.skipLastSeen(false, true));
        assertFalse(CompassManager.skipLastSeen(false, false));
    }

    @Test
    void analyzeDelayTicksConvertsSeconds() {
        assertEquals(20L, AnalysisTiming.analyzeDelayTicks(1.0));
        assertEquals(10L, AnalysisTiming.analyzeDelayTicks(0.5));
        assertEquals(1L, AnalysisTiming.analyzeDelayTicks(0.0));
        assertEquals(1L, AnalysisTiming.analyzeDelayTicks(-2.0));
    }

    @Test
    void analysisTickIntervalRoundsToWholeTicks() {
        assertEquals(10L, AnalysisTiming.analysisTickInterval(0.5));
        assertEquals(20L, AnalysisTiming.analysisTickInterval(1.0));
        assertEquals(1L, AnalysisTiming.analysisTickInterval(0.07));
        assertEquals(2L, AnalysisTiming.analysisTickInterval(0.08));
        assertEquals(1L, AnalysisTiming.analysisTickInterval(0.0));
        assertEquals(1L, AnalysisTiming.analysisTickInterval(-1.0));
    }

    @Test
    void eliminatedSpeedrunnerMayNotHoldCompass() {
        UUID uuid = UUID.randomUUID();
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 0L);
        instance.activate(uuid);
        instance.deactivate(uuid);
        CompassItemService items = service(Role.SPEEDRUNNER, uuid, Optional.of(instance));

        assertFalse(items.mayHoldCompass(player(uuid)));
    }

    @Test
    void activeHunterMayHoldCompass() {
        UUID uuid = UUID.randomUUID();
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 0L);
        instance.activate(uuid);
        CompassItemService items = service(Role.HUNTER, uuid, Optional.of(instance));

        assertTrue(items.mayHoldCompass(player(uuid)));
    }

    @Test
    void spectatorAndLobbyPlayersMayNotHoldCompass() {
        UUID uuid = UUID.randomUUID();
        GameInstance instance = new GameInstance(1L, 0, OptionalLong.empty(), 0L);
        instance.activate(uuid);

        assertFalse(service(Role.SPECTATOR, uuid, Optional.of(instance))
                .mayHoldCompass(player(uuid)));
        assertFalse(service(Role.HUNTER, uuid, Optional.empty()).mayHoldCompass(player(uuid)));
    }

    @Test
    void deduplicateCollapsesStackedCompassAndClearsExtraSlots() {
        CompassItemService items = new CompassItemService(mock(JManhuntPlugin.class),
                mock(MessageService.class), null, mock(PlayerStateStore.class),
                new NamespacedKey("jmanhunt", "hunters_compass"));
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(3);
        ItemStack stacked = compassStack(2);
        ItemStack single = compassStack(1);
        ItemStack plain = mock(ItemStack.class);
        when(inventory.getItem(0)).thenReturn(stacked);
        when(inventory.getItem(1)).thenReturn(plain);
        when(inventory.getItem(2)).thenReturn(single);

        items.deduplicateCompasses(player);

        verify(stacked).setAmount(1);
        verify(inventory).setItem(2, null);
        verify(inventory, never()).setItem(0, null);
        verify(inventory, never()).setItem(1, null);
        verify(single, never()).setAmount(anyInt());
    }

    @Test
    void compassPrefersLastHotbarSlot() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getSize()).thenReturn(36);

        assertEquals(8, CompassItemService.DEFAULT_SLOT);
        assertEquals(8,
                CompassItemService.findAvailableSlot(inventory, CompassItemService.DEFAULT_SLOT));
    }

    @Test
    void compassSlotFallsBackGracefully() {
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getSize()).thenReturn(36);
        ItemStack occupied = mock(ItemStack.class);
        when(occupied.getType()).thenReturn(Material.DIRT);
        when(inventory.getItem(8)).thenReturn(occupied);

        assertEquals(0,
                CompassItemService.findAvailableSlot(inventory, CompassItemService.DEFAULT_SLOT));

        when(inventory.getItem(anyInt())).thenReturn(occupied);
        assertEquals(-1,
                CompassItemService.findAvailableSlot(inventory, CompassItemService.DEFAULT_SLOT));
    }

    @Test
    void givenToTogglesResolveFromConfig() {
        JManhuntConfig root = new JManhuntConfig();
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        ConfigService configService = new ConfigService(root,
                new ModifierStore(ModifierFiles.inMemory(), log));
        JManhuntPlugin plugin = mock(JManhuntPlugin.class);
        when(plugin.overrides()).thenReturn(
                new OverrideService(configService, new LobbyConfig(), () -> { }));
        CompassItemService items = new CompassItemService(plugin, mock(MessageService.class), null,
                mock(PlayerStateStore.class), new NamespacedKey("jmanhunt", "hunters_compass"));

        assertTrue(items.shouldReceiveCompass(null, Role.HUNTER));
        assertFalse(items.shouldReceiveCompass(null, Role.SPEEDRUNNER));
        assertFalse(items.shouldReceiveCompass(null, Role.SPECTATOR));

        ConfigPathMapper.set(root, "settings.compass.obtaining.given-to.speedrunners", true);
        ConfigPathMapper.set(root, "settings.compass.obtaining.given-to.hunters", false);

        assertTrue(items.shouldReceiveCompass(null, Role.SPEEDRUNNER));
        assertFalse(items.shouldReceiveCompass(null, Role.HUNTER));
    }

    private static CompassItemService service(Role role, UUID uuid, Optional<GameInstance> match) {
        PlayerStateStore playerStates = mock(PlayerStateStore.class);
        when(playerStates.role(any(Player.class))).thenAnswer(invocation -> {
            Player candidate = invocation.getArgument(0);
            return uuid.equals(candidate.getUniqueId()) ? role : Role.NONE;
        });
        GameManager game = mock(GameManager.class);
        when(game.instanceOf(uuid)).thenReturn(match);
        CompassItemService items = new CompassItemService(mock(JManhuntPlugin.class),
                mock(MessageService.class), null, playerStates,
                new NamespacedKey("jmanhunt", "hunters_compass"));
        items.setGameManager(game);
        return items;
    }

    private static Player player(UUID uuid) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(uuid);
        return player;
    }

    private static ItemStack compassStack(int amount) {
        ItemStack stack = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer container = mock(PersistentDataContainer.class);
        when(stack.hasItemMeta()).thenReturn(true);
        when(stack.getItemMeta()).thenReturn(meta);
        when(stack.getAmount()).thenReturn(amount);
        when(meta.getPersistentDataContainer()).thenReturn(container);
        when(container.has(any(NamespacedKey.class), any())).thenReturn(true);
        return stack;
    }
}
