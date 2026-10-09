package com.jruk8.jmanhunt.player;

import com.jruk8.jmanhunt.lobby.config.OverrideService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/**
 * Leave and match-end player cleanup: full wipes, vitals resets, and
 * death-style gear drops. Owns the end-wipe rule check behind the
 * game-rules bundle.
 */
public final class PlayerResetService {

    private final OverrideService overrides;

    public PlayerResetService(OverrideService overrides) {
        this.overrides = overrides;
    }

    /**
     * True when the end phase wipes participant inventories for the
     * lobby: the game-rules bundle is on. The wipe itself is integral
     * and no longer gated on a rule key.
     */
    public boolean endWipeEnabled(int lobbyId) {
        return overrides.getBoolean(lobbyId,
                "advanced.advanced-match-controls.game-rules.enabled", true);
    }

    /**
     * Full match-end style wipe for one player: inventory, ender chest,
     * vitals, and advancements. Used by auto-leave so a removed player
     * restarts clean.
     */
    public void resetPlayer(Player player) {
        resetPlayerStats(player, true, true);
    }

    /**
     * Vitals-only reset for one player: no inventory clear, no advancement
     * wipe. Used by voluntary leave after the leaver's gear has dropped.
     */
    public void resetVitals(Player player) {
        resetPlayerStats(player, false, false);
    }

    /** Drops a player's full gear at their feet, death style. */
    public static void dropAllGear(Player player) {
        Location at = player.getLocation();
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            dropStack(world, at, item);
        }
        for (ItemStack item : player.getInventory().getArmorContents()) {
            dropStack(world, at, item);
        }
        dropStack(world, at, player.getInventory().getItemInOffHand());
        InventoryView view = player.getOpenInventory();
        if (view.getTopInventory() instanceof CraftingInventory crafting) {
            for (ItemStack item : crafting.getMatrix()) {
                dropStack(world, at, item);
            }
            view.getTopInventory().clear();
        }
        dropStack(world, at, view.getCursor());
        view.setCursor(null);
        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);
        player.getInventory().setItemInOffHand(null);
    }

    /** Clears the player inventory plus the ender chest. Package-visible for tests. */
    static void clearContainers(Player player) {
        player.getInventory().clear();
        player.getEnderChest().clear();
        clearCraftingAndCursor(player);
    }

    /**
     * Clears the crafting grid plus the cursor stack: neither belongs to
     * the player inventory, so without this stashed items survive wipes
     * into the lobby. Only crafting tops are touched; an open chest or
     * furnace belongs to the world block and is left alone.
     */
    private static void clearCraftingAndCursor(Player player) {
        InventoryView view = player.getOpenInventory();
        if (view.getTopInventory() instanceof CraftingInventory) {
            view.getTopInventory().clear();
        }
        view.setCursor(null);
    }

    private void resetPlayerStats(Player player, boolean clearInventory, boolean wipeAdvancements) {
        if (clearInventory) {
            clearContainers(player);
        }
        player.setLevel(0);
        player.setExp(0.0f);
        player.clearActivePotionEffects();
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        if (wipeAdvancements) {
            clearAdvancements(player);
        }
    }

    /** Drops one non-air stack. Package-visible for snapshot expiry drops. */
    static void dropStack(World world, Location at, ItemStack item) {
        if (item != null && !item.getType().isAir()) {
            world.dropItemNaturally(at, item);
        }
    }

    private void clearAdvancements(Player player) {
        Bukkit.advancementIterator().forEachRemaining(advancement ->
                player.getAdvancementProgress(advancement).getAwardedCriteria().forEach(criteria ->
                        player.getAdvancementProgress(advancement).revokeCriteria(criteria)));
    }
}
