package com.jruk8.jmanhunt.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jruk8.jmanhunt.core.TaskScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

class FakeSpectatorServiceTest {

    private record Fixture(FakeSpectatorService fakes, Plugin plugin, PlayerStateStore players,
            Player watched, Player viewer) {
    }

    private static Fixture fixture() {
        Plugin plugin = mock(Plugin.class);
        TaskScheduler tasks = mock(TaskScheduler.class);
        when(tasks.plugin()).thenReturn(plugin);
        PlayerStateStore players = new PlayerStateStore();
        Player watched = mock(Player.class);
        when(watched.getUniqueId()).thenReturn(UUID.randomUUID());
        stubOnlineInventory(watched);
        Player viewer = mock(Player.class);
        when(viewer.getUniqueId()).thenReturn(UUID.randomUUID());
        stubOnlineInventory(viewer);
        FakeSpectatorService fakes =
                new FakeSpectatorService(tasks, players, () -> List.of(watched, viewer));
        return new Fixture(fakes, plugin, players, watched, viewer);
    }

    /** Online player with an empty live inventory, location, and open view. */
    private static void stubOnlineInventory(Player player) {
        when(player.isOnline()).thenReturn(true);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getContents()).thenReturn(new ItemStack[36]);
        when(inventory.getArmorContents()).thenReturn(new ItemStack[4]);
        when(player.getInventory()).thenReturn(inventory);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(mock(Inventory.class));
        when(player.getOpenInventory()).thenReturn(view);
        World world = mock(World.class);
        when(player.getLocation()).thenReturn(new Location(world, 1.0, 2.0, 3.0));
    }

    @Test
    void enableAppliesFlightAndHidesFromOthers() {
        Fixture fixture = fixture();

        fixture.fakes().enable(fixture.watched());

        assertTrue(fixture.fakes().isFakeSpectator(fixture.watched()));
        assertFalse(fixture.fakes().isFakeSpectator(fixture.viewer()));
        verify(fixture.watched()).setGameMode(GameMode.ADVENTURE);
        verify(fixture.watched()).setAllowFlight(true);
        verify(fixture.watched()).setFlying(true);
        verify(fixture.watched()).setCollidable(false);
        verify(fixture.viewer()).hidePlayer(fixture.plugin(), fixture.watched());
        verify(fixture.watched(), never()).hidePlayer(
                fixture.plugin(), fixture.watched());
    }

    @Test
    void enableIsIdempotent() {
        Fixture fixture = fixture();

        fixture.fakes().enable(fixture.watched());
        fixture.fakes().enable(fixture.watched());

        assertTrue(fixture.fakes().isFakeSpectator(fixture.watched()));
    }

    @Test
    void disableRestoresAndShows() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.watched());

        fixture.fakes().disable(fixture.watched());

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.watched()).setFlying(false);
        verify(fixture.watched()).setAllowFlight(false);
        verify(fixture.watched()).setCollidable(true);
        verify(fixture.watched()).setFallDistance(0F);
        verify(fixture.watched()).setGameMode(GameMode.SURVIVAL);
        verify(fixture.viewer()).showPlayer(fixture.plugin(), fixture.watched());
    }

    @Test
    void disableOfNonFakeStillRestores() {
        Fixture fixture = fixture();

        fixture.fakes().disable(fixture.watched());

        verify(fixture.watched()).setGameMode(GameMode.SURVIVAL);
        verify(fixture.watched()).setAllowFlight(false);
    }

    @Test
    void quitDisables() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.watched());

        fixture.fakes().handleQuit(fixture.watched());

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.viewer()).showPlayer(fixture.plugin(), fixture.watched());
    }

    @Test
    void modeListenersHearEnableAndDisable() {
        Fixture fixture = fixture();
        List<String> events = new ArrayList<>();
        fixture.fakes().addModeListener((player, enabled) ->
                events.add(player.getUniqueId() + "=" + enabled));

        fixture.fakes().enable(fixture.watched());
        fixture.fakes().disable(fixture.watched());

        assertEquals(List.of(fixture.watched().getUniqueId() + "=true",
                fixture.watched().getUniqueId() + "=false"), events);
    }

    @Test
    void quitOfNonFakeTouchesNothing() {
        Fixture fixture = fixture();

        fixture.fakes().handleQuit(fixture.watched());

        verify(fixture.watched(), never()).setGameMode(GameMode.SURVIVAL);
        verify(fixture.watched(), never()).setAllowFlight(false);
    }

    @Test
    void hideFromHidesOnlyActives() {
        Fixture fixture = fixture();
        Player joiner = mock(Player.class);
        when(joiner.getUniqueId()).thenReturn(UUID.randomUUID());
        fixture.fakes().enable(fixture.watched());

        fixture.fakes().hideFrom(joiner);

        verify(joiner).hidePlayer(fixture.plugin(), fixture.watched());
        verify(joiner, never()).hidePlayer(fixture.plugin(), fixture.viewer());
    }

    @Test
    void clearDanglingResetsFlightAndVisibility() {
        Fixture fixture = fixture();

        fixture.fakes().clearDanglingState(fixture.watched());

        verify(fixture.watched()).setFlying(false);
        verify(fixture.watched()).setAllowFlight(false);
        verify(fixture.viewer()).showPlayer(fixture.plugin(), fixture.watched());
    }

    @Test
    void clearDanglingKeepsCreativeFlight() {
        Fixture fixture = fixture();
        when(fixture.watched().getGameMode()).thenReturn(GameMode.CREATIVE);

        fixture.fakes().clearDanglingState(fixture.watched());

        verify(fixture.watched(), never()).setFlying(false);
        verify(fixture.watched(), never()).setAllowFlight(false);
        verify(fixture.viewer()).showPlayer(fixture.plugin(), fixture.watched());
    }

    @Test
    void disableKeepsCreativeFlight() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.watched());
        when(fixture.watched().getGameMode()).thenReturn(GameMode.CREATIVE);

        fixture.fakes().disable(fixture.watched());

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.watched(), never()).setFlying(false);
        verify(fixture.watched(), never()).setAllowFlight(false);
        verify(fixture.watched()).setCollidable(true);
        verify(fixture.viewer()).showPlayer(fixture.plugin(), fixture.watched());
    }

    @Test
    void spectatorRoleNeverEnablesFakeMode() {
        Fixture fixture = fixture();

        fixture.players().setRole(fixture.watched(), Role.SPECTATOR);

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.watched(), never()).setGameMode(GameMode.ADVENTURE);
        verify(fixture.watched(), never()).setAllowFlight(true);
    }

    @Test
    void leavingSpectatorRoleDisablesOnlyWhenFake() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.watched());
        fixture.players().setRole(fixture.watched(), Role.SPECTATOR);

        fixture.players().setRole(fixture.watched(), Role.HUNTER);

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.watched()).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void leavingSpectatorRoleWithoutFakeTouchesNothing() {
        Fixture fixture = fixture();
        fixture.players().setRole(fixture.watched(), Role.SPECTATOR);

        fixture.players().setRole(fixture.watched(), Role.HUNTER);

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.watched(), never()).setGameMode(GameMode.SURVIVAL);
        verify(fixture.watched(), never()).setAllowFlight(false);
    }

    @Test
    void otherRoleChangesLeaveModeAlone() {
        Fixture fixture = fixture();

        fixture.players().setRole(fixture.watched(), Role.HUNTER);
        fixture.players().setRole(fixture.watched(), Role.SPEEDRUNNER);

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        verify(fixture.watched(), never()).setGameMode(GameMode.ADVENTURE);
    }

    @Test
    void offlineRoleChangesOnlyTrack() {
        Fixture fixture = fixture();
        UUID offlineId = UUID.randomUUID();

        fixture.players().setRole(offlineId, Role.SPECTATOR);

        assertFalse(fixture.fakes().isFakeSpectator(offlineId));
        verify(fixture.viewer(), never()).hidePlayer(
                fixture.plugin(), fixture.watched());
    }

    @Test
    void offlineRoleResetDropsTrackingWithoutBukkitCalls() {
        TaskScheduler tasks = mock(TaskScheduler.class);
        when(tasks.plugin()).thenReturn(mock(Plugin.class));
        PlayerStateStore players = new PlayerStateStore();
        Player watched = mock(Player.class);
        UUID watchedId = UUID.randomUUID();
        when(watched.getUniqueId()).thenReturn(watchedId);
        stubOnlineInventory(watched);
        List<Player> online = new ArrayList<>(List.of(watched));
        FakeSpectatorService fakes = new FakeSpectatorService(tasks, players, () -> online);
        players.setRole(watched, Role.SPECTATOR);
        fakes.enable(watched);
        assertTrue(fakes.isFakeSpectator(watchedId));
        online.clear();

        players.setRole(watchedId, Role.NONE);

        assertFalse(fakes.isFakeSpectator(watchedId));
    }

    @Test
    void fakeCheckIsNullSafe() {
        Fixture fixture = fixture();

        assertFalse(fixture.fakes().isFakeSpectator((Player) null));
        assertFalse(fixture.fakes().isFakeSpectator((UUID) null));
    }

    @Test
    void seesPlayerTruthTable() {
        assertTrue(FakeSpectatorService.seesPlayer(false, false));
        assertFalse(FakeSpectatorService.seesPlayer(false, true));
        assertTrue(FakeSpectatorService.seesPlayer(true, false));
        assertTrue(FakeSpectatorService.seesPlayer(true, true));
    }

    @Test
    void enableShowsMutuallyToFakeViewers() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.viewer());
        clearInvocations(fixture.viewer(), fixture.watched());

        fixture.fakes().enable(fixture.watched());

        verify(fixture.viewer()).showPlayer(fixture.plugin(), fixture.watched());
        verify(fixture.watched()).showPlayer(fixture.plugin(), fixture.viewer());
        verify(fixture.viewer(), never()).hidePlayer(fixture.plugin(), fixture.watched());
        verify(fixture.watched(), never()).hidePlayer(fixture.plugin(), fixture.viewer());
    }

    @Test
    void enableShowsNewFakeToAliveViewersButHidesBack() {
        Fixture fixture = fixture();

        fixture.fakes().enable(fixture.watched());

        verify(fixture.watched()).showPlayer(fixture.plugin(), fixture.viewer());
        verify(fixture.viewer()).hidePlayer(fixture.plugin(), fixture.watched());
        verify(fixture.viewer(), never()).showPlayer(fixture.plugin(), fixture.watched());
    }

    @Test
    void disableHidesRemainingFakesFromLeaver() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.viewer());
        fixture.fakes().enable(fixture.watched());
        clearInvocations(fixture.viewer(), fixture.watched());

        fixture.fakes().disable(fixture.watched());

        verify(fixture.viewer()).showPlayer(fixture.plugin(), fixture.watched());
        verify(fixture.watched()).hidePlayer(fixture.plugin(), fixture.viewer());
    }

    @Test
    void disableShowsAliveViewersBothWays() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.watched());
        clearInvocations(fixture.viewer(), fixture.watched());

        fixture.fakes().disable(fixture.watched());

        verify(fixture.viewer()).showPlayer(fixture.plugin(), fixture.watched());
        verify(fixture.watched()).showPlayer(fixture.plugin(), fixture.viewer());
        verify(fixture.watched(), never()).hidePlayer(fixture.plugin(), fixture.viewer());
    }

    @Test
    void enableAppliesInfiniteInvisibilityWithoutParticlesOrIcon() {
        Fixture fixture = fixture();

        fixture.fakes().enable(fixture.watched());

        verify(fixture.watched()).addPotionEffect(argThat(effect ->
                effect != null
                        && effect.getDuration() == PotionEffect.INFINITE_DURATION
                        && effect.getAmplifier() == 0
                        && !effect.isAmbient()
                        && !effect.hasParticles()
                        && !effect.hasIcon()));
    }

    @Test
    void disableRemovesInvisibility() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.watched());

        fixture.fakes().disable(fixture.watched());

        verify(fixture.watched()).removePotionEffect(any());
    }

    @Test
    void clearDanglingRemovesStrandedInfiniteInvisibility() {
        Fixture fixture = fixture();
        when(fixture.watched().getPotionEffect(PotionEffectType.INVISIBILITY)).thenReturn(
                new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0));

        fixture.fakes().clearDanglingState(fixture.watched());

        verify(fixture.watched()).removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    @Test
    void clearDanglingKeepsFiniteInvisibility() {
        Fixture fixture = fixture();
        when(fixture.watched().getPotionEffect(PotionEffectType.INVISIBILITY)).thenReturn(
                new PotionEffect(PotionEffectType.INVISIBILITY, 200, 0));

        fixture.fakes().clearDanglingState(fixture.watched());

        verify(fixture.watched(), never()).removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    @Test
    void clearDanglingWithoutInvisibilityTouchesNothing() {
        Fixture fixture = fixture();

        fixture.fakes().clearDanglingState(fixture.watched());

        verify(fixture.watched(), never()).removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    @Test
    void enableSnapshotsAndClearsInventory() {
        Fixture fixture = fixture();
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.DIAMOND_SWORD);
        when(fixture.watched().getInventory().getContents()).thenReturn(contents);

        fixture.fakes().enable(fixture.watched());

        assertTrue(fixture.fakes().hasSnapshot(fixture.watched().getUniqueId()));
        verify(fixture.watched().getInventory()).clear();
        verify(fixture.watched().getInventory()).setArmorContents(
                argThat(cleared -> cleared != null && cleared.length == 4));
        verify(fixture.watched().getInventory()).setItemInOffHand(null);
        verify(fixture.watched().getOpenInventory()).setCursor(null);
    }

    @Test
    void enableSnapshotsCraftingGrid() {
        Fixture fixture = fixture();
        CraftingInventory crafting = mock(CraftingInventory.class);
        ItemStack[] matrix = new ItemStack[4];
        matrix[0] = new ItemStack(Material.OAK_PLANKS);
        when(crafting.getMatrix()).thenReturn(matrix);
        when(crafting.getResult()).thenReturn(new ItemStack(Material.CRAFTING_TABLE));
        when(fixture.watched().getOpenInventory().getTopInventory()).thenReturn(crafting);
        fixture.fakes().enable(fixture.watched());

        verify(crafting).clear();

        fixture.fakes().disable(fixture.watched());

        verify(crafting).setMatrix(
                argThat(restored -> restored[0] != null
                        && restored[0].getType() == Material.OAK_PLANKS));
        verify(crafting).setResult(argThat(item -> item != null && item.getType() == Material.CRAFTING_TABLE));
    }

    @Test
    void secondEnableKeepsFirstSnapshot() {
        Fixture fixture = fixture();
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.DIAMOND_SWORD);
        when(fixture.watched().getInventory().getContents()).thenReturn(contents);
        clearInvocations(fixture.watched().getInventory());

        fixture.fakes().enable(fixture.watched());
        fixture.fakes().enable(fixture.watched());
        fixture.fakes().disable(fixture.watched());

        verify(fixture.watched().getInventory(), times(1)).getContents();
        verify(fixture.watched().getInventory()).setContents(
                argThat(restored -> restored[0] != null
                        && restored[0].getType() == Material.DIAMOND_SWORD));
    }

    @Test
    void disableRestoresSnapshot() {
        Fixture fixture = fixture();
        PlayerInventory inventory = fixture.watched().getInventory();
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack[] armor = new ItemStack[4];
        armor[3] = new ItemStack(Material.DIAMOND_HELMET);
        when(inventory.getContents()).thenReturn(contents);
        when(inventory.getArmorContents()).thenReturn(armor);
        when(inventory.getItemInOffHand()).thenReturn(new ItemStack(Material.SHIELD));
        when(fixture.watched().getOpenInventory().getCursor())
                .thenReturn(new ItemStack(Material.DIRT));
        fixture.fakes().enable(fixture.watched());

        fixture.fakes().disable(fixture.watched());

        verify(inventory).setContents(
                argThat(restored -> restored[0] != null
                        && restored[0].getType() == Material.DIAMOND_SWORD));
        verify(inventory).setArmorContents(
                argThat(restored -> restored[3] != null
                        && restored[3].getType() == Material.DIAMOND_HELMET));
        verify(inventory).setItemInOffHand(argThat(item -> item != null && item.getType() == Material.SHIELD));
        verify(fixture.watched().getOpenInventory()).setCursor(
                argThat(item -> item != null && item.getType() == Material.DIRT));
        assertFalse(fixture.fakes().hasSnapshot(fixture.watched().getUniqueId()));
    }

    @Test
    void quitRestoresButRetainsSnapshot() {
        Fixture fixture = fixture();
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.DIAMOND_SWORD);
        when(fixture.watched().getInventory().getContents()).thenReturn(contents);
        fixture.fakes().enable(fixture.watched());

        fixture.fakes().handleQuit(fixture.watched());

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        assertTrue(fixture.fakes().hasSnapshot(fixture.watched().getUniqueId()));
        verify(fixture.watched().getInventory()).setContents(
                argThat(restored -> restored[0] != null
                        && restored[0].getType() == Material.DIAMOND_SWORD));
    }

    @Test
    void quitOfNonFakeDiscardsStaleSnapshot() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.watched());
        fixture.fakes().handleQuit(fixture.watched());
        assertTrue(fixture.fakes().hasSnapshot(fixture.watched().getUniqueId()));

        fixture.fakes().handleQuit(fixture.watched());

        assertFalse(fixture.fakes().hasSnapshot(fixture.watched().getUniqueId()));
    }

    @Test
    void offlineEnableIsRefused() {
        Fixture fixture = fixture();
        when(fixture.watched().isOnline()).thenReturn(false);

        fixture.fakes().enable(fixture.watched());

        assertFalse(fixture.fakes().isFakeSpectator(fixture.watched()));
        assertFalse(fixture.fakes().hasSnapshot(fixture.watched().getUniqueId()));
        verify(fixture.watched(), never()).setGameMode(GameMode.ADVENTURE);
    }

    @Test
    void offlineDisableDiscardsWithoutRestore() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.watched());
        when(fixture.watched().isOnline()).thenReturn(false);

        fixture.fakes().disable(fixture.watched());

        assertFalse(fixture.fakes().hasSnapshot(fixture.watched().getUniqueId()));
        verify(fixture.watched().getInventory(), never()).setContents(any());
    }

    @Test
    void restoreSnapshotHandsGearBackWithoutModeChange() {
        Fixture fixture = fixture();
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.DIAMOND_SWORD);
        when(fixture.watched().getInventory().getContents()).thenReturn(contents);
        fixture.fakes().enable(fixture.watched());

        fixture.fakes().restoreSnapshot(fixture.watched());

        verify(fixture.watched().getInventory()).setContents(
                argThat(restored -> restored[0] != null
                        && restored[0].getType() == Material.DIAMOND_SWORD));
        assertFalse(fixture.fakes().hasSnapshot(fixture.watched().getUniqueId()));
        assertTrue(fixture.fakes().isFakeSpectator(fixture.watched()));
    }

    @Test
    void dropSnapshotDropsAtHoldLocation() {
        Fixture fixture = fixture();
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.DIAMOND_SWORD);
        when(fixture.watched().getInventory().getContents()).thenReturn(contents);
        fixture.fakes().enable(fixture.watched());
        World world = fixture.watched().getLocation().getWorld();

        assertTrue(fixture.fakes().dropSnapshot(fixture.watched().getUniqueId()));

        verify(world).dropItemNaturally(
                argThat(at -> new Location(world, 1.0, 2.0, 3.0).equals(at)),
                argThat(item -> item != null && item.getType() == Material.DIAMOND_SWORD));
        assertFalse(fixture.fakes().hasSnapshot(fixture.watched().getUniqueId()));
    }

    @Test
    void dropSnapshotWithoutSnapshotReturnsFalse() {
        Fixture fixture = fixture();

        assertFalse(fixture.fakes().dropSnapshot(fixture.watched().getUniqueId()));
    }

    @Test
    void sweepDiscardsOnlyListedSnapshots() {
        Fixture fixture = fixture();
        fixture.fakes().enable(fixture.watched());
        fixture.fakes().enable(fixture.viewer());

        fixture.fakes().sweepSnapshots(List.of(fixture.watched().getUniqueId()));

        assertFalse(fixture.fakes().hasSnapshot(fixture.watched().getUniqueId()));
        assertTrue(fixture.fakes().hasSnapshot(fixture.viewer().getUniqueId()));
    }

    @Test
    void snapshotCheckIsNullSafe() {
        Fixture fixture = fixture();

        assertFalse(fixture.fakes().hasSnapshot(null));
    }
}
