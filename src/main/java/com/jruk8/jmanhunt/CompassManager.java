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
                .filter(p -> isCompass(p.getInventory().getItem(8)))
                .forEach(this::refreshCompass);
    }

    public void showHeldActionbars(boolean active) {
        if (!active) return;
        Bukkit.getOnlinePlayers().stream().filter(p -> role(p) != Role.NONE)
                .filter(p -> isCompass(p.getInventory().getItemInMainHand()) || isCompass(p.getInventory().getItemInOffHand()))
                .forEach(p -> p.sendActionBar(compassActionbars.getOrDefault(p.getUniqueId(),
                        component("compass.no-target-actionbar"))));
    }

    public void refreshCompass(Player holder) {
        ItemStack item = holder.getInventory().getItem(8);
        enforceCompassSlot(holder);
        item = holder.getInventory().getItem(8);
        if (!isCompass(item)) return;
        Player target = Bukkit.getOnlinePlayers().stream().filter(p -> role(p) == Role.SPEEDRUNNER
                        && playerStates.isActiveSpeedrunner(p.getUniqueId()) && p.getGameMode() != GameMode.SPECTATOR
                        && !p.getUniqueId().equals(holder.getUniqueId())
                        && p.getWorld().equals(holder.getWorld()))
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(holder.getLocation()))).orElse(null);
        Location location = target == null ? playerStates.sightings().entrySet().stream()
                .filter(entry -> playerStates.isActiveSpeedrunner(entry.getKey())
                        && !entry.getKey().equals(holder.getUniqueId())).map(Map.Entry::getValue)
                .map(worlds -> worlds.get(holder.getWorld().getUID())).filter(l -> l != null && l.getWorld() != null)
                .min(Comparator.comparingDouble(l -> l.distanceSquared(holder.getLocation()))).orElse(null) : target.getLocation();
        if (location != null && item != null && item.getItemMeta() instanceof CompassMeta meta) {
            meta.setLodestone(location); meta.setLodestoneTracked(false); item.setItemMeta(meta);
        }
        compassActionbars.put(holder.getUniqueId(), target == null ? component("compass.no-target-actionbar")
                : component("compass.compass-actionbar", Map.of("player", target.getName(),
                "distance", String.valueOf(Math.round(holder.getLocation().distance(target.getLocation()))))));
    }

    public void giveCompass(Player player) {
        if (role(player) != Role.HUNTER) return;
        ItemStack item = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) item.getItemMeta();
        meta.displayName(messages.nonItalic(component("compass.compass-name")));
        meta.lore(messages.strings("compass.compass-lore").stream().map(messages::parse).map(messages::nonItalic).toList());
        if (plugin.getConfig().getBoolean("extras.drop-compass-on-death.enabled", false)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        } else {
            meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta); player.getInventory().setItem(8, item);
    }

    public void enforceCompassSlot(Player player) {
        ItemStack found = null;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isCompass(item)) { if (found == null) found = item; player.getInventory().setItem(slot, null); }
        }
        if (found != null) player.getInventory().setItem(8, found);
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

    public void handleRightClick(Player player) {
        if (plugin.getConfig().getBoolean("compass-refresh.right-click.refresh-on-right-click", false)) {
            long now = System.currentTimeMillis();
            if (now - compassClicks.getOrDefault(player.getUniqueId(), 0L) >=
                    plugin.getConfig().getDouble("compass-refresh.right-click.right-click-cooldown", 3.0) * 1000) {
                compassClicks.put(player.getUniqueId(), now); refreshCompass(player);
            }
        }
    }

    private Role role(Player player) { return playerStates.role(player); }
    private Component component(String key) { return messages.component(key); }
    private Component component(String key, Map<String, String> values) { return messages.component(key, values); }
}
