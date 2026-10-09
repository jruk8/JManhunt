package com.jruk8.jmanhunt.player;

import com.jruk8.jmanhunt.core.TaskScheduler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Fake spectator mode: adventure plus flight plus no collision plus
 * infinite invisibility, hidden from alive players but visible to
 * other fake spectators. This keeps terrain collision (noclip is a
 * vanilla spectator property, not a flight property) while hiding held
 * items and armor client-side. Role-spectator and fake-spectator-mode
 * are not the same thing: queuing as a spectator never enters this
 * mode by itself. Only explicit join and watch paths enable it;
 * death waits, headstart holds, and NONE watchers also use it with
 * other roles.
 *
 * <p>Enabling snapshots the live inventory (contents, armor, offhand,
 * cursor, and the crafting grid) plus the hold location, then clears
 * everything, so every hold watches with a completely empty inventory.
 * Disabling restores the snapshot. Quitting restores too, so the quit
 * save persists real gear, but retains the memory copy for a
 * disconnect-grace expiry drop.
 *
 * <p>State is memory only: a crash or restart always starts clean, and
 * quit plus join handlers reset any dangling flight and invisibility.
 */
public final class FakeSpectatorService {
    /** Notified after fake spectator mode turns on or off for a player. */
    public interface ModeListener {
        void onModeChange(Player player, boolean enabled);
    }

    /** Held inventory plus hold location, cloned off the live player. */
    private record GearSnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offhand,
            ItemStack cursor, ItemStack[] matrix, ItemStack result, Location location) {
    }

    private final TaskScheduler tasks;
    private final Supplier<Collection<? extends Player>> onlinePlayers;
    private final Set<UUID> actives = new HashSet<>();
    private final Map<UUID, GearSnapshot> snapshots = new HashMap<>();
    private final List<ModeListener> modeListeners = new ArrayList<>();

    public FakeSpectatorService(TaskScheduler tasks, PlayerStateStore playerStates) {
        this(tasks, playerStates, Bukkit::getOnlinePlayers);
    }

    FakeSpectatorService(TaskScheduler tasks, PlayerStateStore playerStates,
            Supplier<Collection<? extends Player>> onlinePlayers) {
        this.tasks = tasks;
        this.onlinePlayers = onlinePlayers;
        playerStates.addRoleListener(this::onRoleChange);
    }

    /** Subscribes to fake spectator mode changes. */
    public void addModeListener(ModeListener listener) {
        modeListeners.add(listener);
    }

    /**
     * Enables fake spectator mode. Idempotent: a second enable is a
     * full no-op, so overlapping holds never re-snapshot (which would
     * capture the cleared inventory) and never strip a deployed
     * spectator toolbar. Offline players are refused: their entity is
     * gone, so only the quit save owns their gear.
     */
    public void enable(Player player) {
        if (!player.isOnline()) {
            return;
        }
        UUID id = player.getUniqueId();
        if (!actives.add(id)) {
            return;
        }
        snapshots.putIfAbsent(id, takeSnapshot(player));
        clearInventory(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                PotionEffect.INFINITE_DURATION, 0, false, false, false));
        for (Player viewer : onlinePlayers.get()) {
            if (viewer.getUniqueId().equals(id)) {
                continue;
            }
            // The new fake sees everyone; only fakes see them back.
            player.showPlayer(tasks.plugin(), viewer);
            if (seesPlayer(isFakeSpectator(viewer), true)) {
                viewer.showPlayer(tasks.plugin(), player);
            } else {
                viewer.hidePlayer(tasks.plugin(), player);
            }
        }
        notifyMode(player, true);
    }

    /**
     * Disables fake spectator mode and restores normal state. Always
     * restores (survival, collidable, visible) so call sites can swap
     * their survival restores for this unconditionally; flight is
     * grounded unless the player is in creative mode. The retained
     * snapshot restores into the live inventory first, so call sites
     * never see the cleared hold inventory.
     */
    public void disable(Player player) {
        disableInner(player, false);
    }

    /** Shared disable tail: restore plus the mode and visibility reset. */
    private void disableInner(Player player, boolean keepSnapshot) {
        UUID id = player.getUniqueId();
        actives.remove(id);
        if (keepSnapshot) {
            writeSnapshot(player, snapshots.get(id));
        } else {
            restoreSnapshot(player);
        }
        stopFlightUnlessCreative(player);
        player.setCollidable(true);
        // Falling distance gathered while flying must not survive the
        // exit: it would land as fall damage on the next touchdown.
        player.setFallDistance(0F);
        player.setGameMode(GameMode.SURVIVAL);
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        for (Player viewer : onlinePlayers.get()) {
            if (viewer.getUniqueId().equals(id)) {
                continue;
            }
            viewer.showPlayer(tasks.plugin(), player);
            if (seesPlayer(false, isFakeSpectator(viewer))) {
                player.showPlayer(tasks.plugin(), viewer);
            } else {
                player.hidePlayer(tasks.plugin(), viewer);
            }
        }
        notifyMode(player, false);
    }

    /**
     * Clones the live inventory plus the hold location. The crafting
     * grid is snapshotted only while a crafting view is open: an open
     * chest or furnace belongs to the world block and is left alone.
     */
    private static GearSnapshot takeSnapshot(Player player) {
        PlayerInventory inventory = player.getInventory();
        InventoryView view = player.getOpenInventory();
        ItemStack[] matrix = null;
        ItemStack result = null;
        if (view.getTopInventory() instanceof CraftingInventory crafting) {
            matrix = cloneAll(crafting.getMatrix());
            result = cloneOne(crafting.getResult());
        }
        return new GearSnapshot(cloneAll(inventory.getContents()),
                cloneAll(inventory.getArmorContents()),
                cloneOne(inventory.getItemInOffHand()),
                cloneOne(view.getCursor()),
                matrix, result, player.getLocation().clone());
    }

    /** Empties the live inventory, armor, offhand, cursor, and crafting grid. */
    private static void clearInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
        InventoryView view = player.getOpenInventory();
        if (view.getTopInventory() instanceof CraftingInventory crafting) {
            crafting.clear();
        }
        view.setCursor(null);
    }

    /**
     * Writes a snapshot back into the live inventory. Offline players
     * are skipped: their entity is gone and the quit save already owns
     * their gear. A crafting grid that is no longer open drops at the
     * player's feet instead of voiding.
     */
    private static void writeSnapshot(Player player, GearSnapshot snapshot) {
        if (snapshot == null || !player.isOnline()) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        inventory.setContents(snapshot.contents());
        inventory.setArmorContents(snapshot.armor());
        inventory.setItemInOffHand(snapshot.offhand());
        InventoryView view = player.getOpenInventory();
        view.setCursor(snapshot.cursor());
        if (snapshot.matrix() == null) {
            return;
        }
        if (view.getTopInventory() instanceof CraftingInventory crafting) {
            crafting.setMatrix(snapshot.matrix());
            crafting.setResult(snapshot.result());
            return;
        }
        Location at = player.getLocation();
        if (at.getWorld() != null) {
            dropStacks(at.getWorld(), at, snapshot.matrix());
            PlayerResetService.dropStack(at.getWorld(), at, snapshot.result());
        }
    }

    /** Drops every snapshotted stack at the location, death style. */
    private static void dropAll(GearSnapshot snapshot, Location at) {
        World world = at.getWorld();
        dropStacks(world, at, snapshot.contents());
        dropStacks(world, at, snapshot.armor());
        PlayerResetService.dropStack(world, at, snapshot.offhand());
        PlayerResetService.dropStack(world, at, snapshot.cursor());
        if (snapshot.matrix() != null) {
            dropStacks(world, at, snapshot.matrix());
            PlayerResetService.dropStack(world, at, snapshot.result());
        }
    }

    private static void dropStacks(World world, Location at, ItemStack[] items) {
        for (ItemStack item : items) {
            PlayerResetService.dropStack(world, at, item);
        }
    }

    private static ItemStack[] cloneAll(ItemStack[] items) {
        ItemStack[] clones = new ItemStack[items.length];
        for (int index = 0; index < items.length; index++) {
            clones[index] = cloneOne(items[index]);
        }
        return clones;
    }

    private static ItemStack cloneOne(ItemStack item) {
        return item == null ? null : item.clone();
    }

    /**
     * True when a viewer sees a target player: anyone sees the alive,
     * but only fake spectators see fakes. Pure for tests.
     */
    static boolean seesPlayer(boolean viewerFake, boolean targetFake) {
        return !targetFake || viewerFake;
    }

    private void notifyMode(Player player, boolean enabled) {
        for (ModeListener listener : List.copyOf(modeListeners)) {
            listener.onModeChange(player, enabled);
        }
    }

    /**
     * Restores a retained snapshot into the live inventory and drops
     * it, for paths that handle gear themselves: leave drops and
     * wipes run on the restored contents. A missing snapshot or an
     * offline player means no-op.
     */
    public void restoreSnapshot(Player player) {
        GearSnapshot snapshot = snapshots.remove(player.getUniqueId());
        writeSnapshot(player, snapshot);
    }

    /** Discards a retained snapshot without restoring it. */
    public void discardSnapshot(UUID playerId) {
        snapshots.remove(playerId);
    }

    /** True when a snapshot is retained for the id. Null-safe. */
    public boolean hasSnapshot(UUID playerId) {
        return playerId != null && snapshots.containsKey(playerId);
    }

    /**
     * Drops a retained snapshot at its hold location and discards it,
     * for disconnect-grace expiry. Returns false when nothing was
     * retained. A missing world still discards.
     */
    public boolean dropSnapshot(UUID playerId) {
        GearSnapshot snapshot = snapshots.remove(playerId);
        if (snapshot == null) {
            return false;
        }
        Location at = snapshot.location();
        if (at == null || at.getWorld() == null) {
            return true;
        }
        dropAll(snapshot, at);
        return true;
    }

    /** Discards retained snapshots, for match teardown. */
    public void sweepSnapshots(Collection<UUID> playerIds) {
        playerIds.forEach(snapshots::remove);
    }

    /**
     * Quit entry: flight and visibility must never survive a disconnect.
     * Non-fakes are untouched (a creative admin keeps their gamemode)
     * apart from a stale-snapshot discard. Fakes restore into the live
     * inventory, so the quit save persists real gear, while the memory
     * copy is retained for a disconnect-grace expiry drop.
     */
    public void handleQuit(Player player) {
        if (player == null) {
            return;
        }
        if (!isFakeSpectator(player)) {
            discardSnapshot(player.getUniqueId());
            return;
        }
        disableInner(player, true);
    }

    /** Hides every active fake spectator from a late joiner. */
    public void hideFrom(Player joiner) {
        UUID joinerId = joiner.getUniqueId();
        for (Player online : onlinePlayers.get()) {
            if (actives.contains(online.getUniqueId())
                    && !online.getUniqueId().equals(joinerId)) {
                joiner.hidePlayer(tasks.plugin(), online);
            }
        }
    }

    /**
     * Clears joiner-side dangling state after a disconnect or crash:
     * persisted flight flags, a stranded infinite invisibility, plus
     * visibility to everyone online. Creative players keep their flight.
     */
    public void clearDanglingState(Player joiner) {
        stopFlightUnlessCreative(joiner);
        clearStrandedInvisibility(joiner);
        for (Player viewer : onlinePlayers.get()) {
            if (!viewer.getUniqueId().equals(joiner.getUniqueId())) {
                viewer.showPlayer(tasks.plugin(), joiner);
            }
        }
    }

    /**
     * Removes our infinite invisibility when a crash stranded it on a
     * joiner: clean quits already drop fake mode through handleQuit, so
     * an infinite effect is ours left behind when quit handlers never
     * ran. Finite effects (vanilla potions) are never touched.
     */
    private static void clearStrandedInvisibility(Player joiner) {
        PotionEffect active = joiner.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (active != null && active.getDuration() < 0) {
            joiner.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    /**
     * Grounds the player unless they are in creative mode, where
     * flight is inherent and must never be stripped by cleanup.
     */
    private static void stopFlightUnlessCreative(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    /** True when the player is in fake spectator mode. Null-safe. */
    public boolean isFakeSpectator(Player player) {
        return player != null && actives.contains(player.getUniqueId());
    }

    /** True when the id is in fake spectator mode. Null-safe. */
    public boolean isFakeSpectator(UUID playerId) {
        return playerId != null && actives.contains(playerId);
    }

    private void onRoleChange(UUID playerId, Role from, Role to) {
        // Queuing as a spectator never enters fake mode by itself: only
        // explicit join and watch paths enable it. Leaving the role
        // unwinds fake mode, but only for players who actually have it.
        if (to == Role.SPECTATOR || from != Role.SPECTATOR) {
            return;
        }
        if (!isFakeSpectator(playerId)) {
            actives.remove(playerId);
            return;
        }
        for (Player online : onlinePlayers.get()) {
            if (online.getUniqueId().equals(playerId)) {
                disable(online);
                return;
            }
        }
        actives.remove(playerId);
    }
}
