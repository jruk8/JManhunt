package com.jruk8.jmanhunt;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CompassManager {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final PlayerStateStore playerStates;
    private final NamespacedKey compassKey;
    private final Map<UUID, Long> compassClicks = new HashMap<>();
    private final Map<UUID, Component> compassActionbars = new HashMap<>();

    public CompassManager(JManhuntPlugin plugin, MessageService messages, PlayerStateStore playerStates,
                          NamespacedKey compassKey) {
        this.plugin = plugin;
        this.messages = messages;
        this.playerStates = playerStates;
        this.compassKey = compassKey;
    }

    public void refreshAllCompasses(boolean active) {
        if (active) Bukkit.getOnlinePlayers().stream()
                .filter(p -> role(p) != Role.NONE)
                .filter(this::hasCompass)
                .forEach(this::refreshCompass);
    }

    public void showHeldActionbars(boolean active) {
        if (!active) return;
        Bukkit.getOnlinePlayers().stream().filter(p -> role(p) != Role.NONE)
                .filter(p -> isCompass(p.getInventory().getItemInMainHand()) || isCompass(p.getInventory().getItemInOffHand()))
                .forEach(p -> p.sendActionBar(compassActionbars.getOrDefault(p.getUniqueId(),
                        component("compass.no-target-actionbar",
                                Map.of("role", role(p) == Role.HUNTER ? "speedrunner" : "hunter")))));
    }

    public void refreshCompass(Player holder) {
        deduplicateCompasses(holder);
        int slot = findCompassSlot(holder);
        if (slot < 0) return;
        ItemStack item = holder.getInventory().getItem(slot);
        if (!isCompass(item)) return;

        Role holderRole = role(holder);
        if (holderRole == Role.NONE) return;
        Role targetRole = holderRole == Role.HUNTER ? Role.SPEEDRUNNER : Role.HUNTER;
        String targetRoleString = targetRole == Role.SPEEDRUNNER ? "speedrunner" : "hunter";

        // Find nearest online target in the same world
        Player target = Bukkit.getOnlinePlayers().stream().filter(p -> role(p) == targetRole
                        && isTrackableTarget(p.getUniqueId(), targetRole) && p.getGameMode() != GameMode.SPECTATOR
                        && !p.getUniqueId().equals(holder.getUniqueId())
                        && p.getWorld().equals(holder.getWorld()))
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(holder.getLocation()))).orElse(null);

        if (target != null) {
            setLodestone(item, target.getLocation());
            holder.getInventory().setItem(slot, item);
            compassActionbars.put(holder.getUniqueId(), component("compass.compass-actionbar",
                    Map.of("player", target.getName(),
                            "distance", String.valueOf(Math.round(holder.getLocation().distance(target.getLocation()))))));
            return;
        }

        // Last seen fallback - find nearest last seen location
        LastSeenResult lastSeen = findNearestLastSeen(holder, targetRole);
        if (lastSeen != null) {
            setLodestone(item, lastSeen.location());
            holder.getInventory().setItem(slot, item);
            String reason = lastSeen.online() ? "Another Dimension" : "Log-Out";
            compassActionbars.put(holder.getUniqueId(), component("compass.compass-last-seen-actionbar",
                    Map.of("player", lastSeen.name(),
                            "distance", String.valueOf(Math.round(holder.getLocation().distance(lastSeen.location()))),
                            "reason", reason)));
            return;
        }

        // Truly no location available
        compassActionbars.put(holder.getUniqueId(), component("compass.no-target-actionbar",
                Map.of("role", targetRoleString)));
    }

    private void setLodestone(ItemStack item, Location location) {
        if (item != null && item.getItemMeta() instanceof CompassMeta meta) {
            meta.setLodestone(location);
            meta.setLodestoneTracked(false);
            item.setItemMeta(meta);
        }
    }

    private boolean hasCompass(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCompass(item)) return true;
        }
        return false;
    }

    private boolean isTrackableTarget(UUID playerId, Role targetRole) {
        if (playerStates.role(playerId) != targetRole) return false;
        if (targetRole == Role.SPEEDRUNNER) return playerStates.isActiveSpeedrunner(playerId);
        return playerStates.isMatchParticipant(playerId);
    }

    private record LastSeenResult(String name, Location location, boolean online) {}

    private LastSeenResult findNearestLastSeen(Player holder, Role targetRole) {
        return playerStates.sightings().entrySet().stream()
                .filter(entry -> isTrackableTarget(entry.getKey(), targetRole))
                .filter(entry -> !entry.getKey().equals(holder.getUniqueId()))
                .map(entry -> {
                    Location loc = entry.getValue().get(holder.getWorld().getUID());
                    if (loc == null || loc.getWorld() == null) return null;
                    Player player = Bukkit.getPlayer(entry.getKey());
                    String name = player != null ? player.getName() : playerStates.playerName(entry.getKey());
                    return new LastSeenResult(name, loc, player != null);
                })
                .filter(result -> result != null)
                .min(Comparator.comparingDouble(result -> result.location().distanceSquared(holder.getLocation())))
                .orElse(null);
    }

    public void giveCompass(Player player) {
        if (role(player) != Role.HUNTER) return;
        removeCompasses(player);
        ItemStack item = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) item.getItemMeta();
        meta.displayName(messages.nonItalic(component("compass.compass-name")));
        meta.lore(messages.strings("compass.compass-lore").stream().map(messages::parse).map(messages::nonItalic).toList());
        if (plugin.getConfig().getBoolean("settings.compass.drop-on-death.enabled", false)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        } else {
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        // Try last slot (8) first, then find next available slot without overriding
        int slot = findAvailableSlot(player, 8);
        if (slot >= 0) {
            player.getInventory().setItem(slot, item);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
    }

    /**
     * Finds an available inventory slot, preferring the given slot first.
     * Returns -1 if no slot is available.
     */
    private int findAvailableSlot(Player player, int preferredSlot) {
        ItemStack preferred = player.getInventory().getItem(preferredSlot);
        if (preferred == null || preferred.getType() == Material.AIR) return preferredSlot;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() == Material.AIR) return slot;
        }
        return -1;
    }

    /**
     * Finds the slot containing the first compass in the player's inventory.
     * Returns -1 if no compass is found.
     */
    private int findCompassSlot(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isCompass(player.getInventory().getItem(slot))) return slot;
        }
        return -1;
    }

    /**
     * Removes all but the first compass from the player's inventory.
     * Handles any number of duplicate compasses, including multiple picked
     * up in a single tick.
     */
    public void deduplicateCompasses(Player player) {
        boolean found = false;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isCompass(item)) {
                if (found) {
                    player.getInventory().setItem(slot, null);
                } else {
                    found = true;
                }
            }
        }
    }

    public void removeCompasses(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isCompass(player.getInventory().getItem(slot))) player.getInventory().setItem(slot, null);
        }
    }

    public boolean isCompass(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE);
    }

    public boolean mustBeInventory() {
        return plugin.getConfig().getBoolean("settings.compass.must-be-inventory.enabled", true);
    }

    public void handleRightClick(Player player) {
        if (plugin.getConfig().getBoolean("settings.compass.right-click.refresh-on-right-click", false)) {
            long now = System.currentTimeMillis();
            if (now - compassClicks.getOrDefault(player.getUniqueId(), 0L) >=
                    plugin.getConfig().getDouble("settings.compass.right-click.right-click-cooldown", 3.0) * 1000) {
                compassClicks.put(player.getUniqueId(), now); refreshCompass(player);
            }
        }
    }

    private Role role(Player player) { return playerStates.role(player); }
    private Component component(String key) { return messages.component(key); }
    private Component component(String key, Map<String, String> values) { return messages.component(key, values); }
}