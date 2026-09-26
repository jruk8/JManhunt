package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Compass items: giving, finding, identifying, and restamping. */
final class CompassItemService {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final PlayerStateStore playerStates;
    private final NamespacedKey compassKey;
    private GameManager game;

    CompassItemService(JManhuntPlugin plugin, MessageService messages, PlayerStateStore playerStates,
            NamespacedKey compassKey) {
        this.plugin = plugin;
        this.messages = messages;
        this.playerStates = playerStates;
        this.compassKey = compassKey;
    }

    /** Wires the game after construction; holding rules need matches. */
    void setGameManager(GameManager game) {
        this.game = game;
    }

    /** Origin lobby of the holder's match, or null outside matches. */
    private Integer lobbyOf(Player holder) {
        return game == null ? null : game.lobbyOfPlayer(holder.getUniqueId());
    }

    boolean shouldReceiveCompass(Integer lobby, Role role) {
        return plugin.overrides()
                .getBoolean(lobby, "settings.compass.given-to." + role.name().toLowerCase(Locale.ROOT),
                        role == Role.HUNTER);
    }

    private static final Set<String> PLACEABLE_SUFFIXES =
            Set.of("_BUCKET", "_SPAWN_EGG", "_BOAT", "_MINECART", "_RAFT");
    private static final Set<String> PLACEABLE_ITEMS = Set.of(
            "BUCKET", "MILK_BUCKET", "REDSTONE", "STRING",
            "WHEAT_SEEDS", "BEETROOT_SEEDS", "MELON_SEEDS", "PUMPKIN_SEEDS",
            "TORCHFLOWER_SEEDS", "PITCHER_POD", "NETHER_WART", "COCOA_BEANS",
            "GLOW_BERRIES", "SWEET_BERRIES", "MINECART",
            "ARMOR_STAND", "ITEM_FRAME", "GLOW_ITEM_FRAME", "PAINTING", "END_CRYSTAL",
            "FLINT_AND_STEEL", "FIRE_CHARGE");

    /**
     * Resolves a configured compass item such as "clock" or
     * "minecraft:recovery_compass" to its material. The minecraft namespace
     * may be omitted. Returns null for unknown names and for items with
     * placement functionality (blocks and anything that places blocks or
     * entities), which cannot serve as compasses.
     */
    static Material resolveCompassMaterial(String raw) {
        if (raw == null) {
            return null;
        }
        String input = raw.trim();
        if (input.isEmpty()) {
            return null;
        }
        int colon = input.indexOf(':');
        String namespace = colon < 0
                ? NamespacedKey.MINECRAFT
                : input.substring(0, colon).toLowerCase(Locale.ROOT);
        String path = colon < 0 ? input : input.substring(colon + 1);
        if (!NamespacedKey.MINECRAFT.equals(namespace)) {
            return null;
        }
        Material material;
        try {
            material = Material.valueOf(
                    path.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException exception) {
            return null;
        }
        return isAllowedCompassItem(material) ? material : null;
    }

    static boolean isAllowedCompassItem(Material material) {
        if (material == null || !material.isItem() || material.isBlock()) {
            return false;
        }
        String name = material.name();
        if (PLACEABLE_ITEMS.contains(name)) {
            return false;
        }
        for (String suffix : PLACEABLE_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }

    void giveCompass(Player player) {
        Integer lobby = lobbyOf(player);
        if (!shouldReceiveCompass(lobby, playerStates.role(player))) {
            return;
        }
        removeCompasses(player);
        String configured =
                plugin.overrides().getString(lobby, "settings.compass.item", "compass");
        Material material = resolveCompassMaterial(configured);
        if (material == null) {
            plugin.logger().warning("Unknown or placeable settings.compass.item '"
                    + configured + "'. Using minecraft:compass.");
            material = Material.COMPASS;
        }
        ItemStack item = new ItemStack(material);
        applyCompassIdentity(item, playerStates.role(player));
        ItemMeta meta = item.getItemMeta();
        if (plugin.overrides().getBoolean(lobby, "settings.compass.drop-on-death.enabled", false)) {
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
        if (preferred == null || preferred.getType() == Material.AIR) {
            return preferredSlot;
        }
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() == Material.AIR) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Finds the slot containing the first compass in the player's inventory.
     * Returns -1 if no compass is found.
     */
    int findCompassSlot(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isCompass(player.getInventory().getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Removes all but the first compass from the player's inventory.
     * Handles any number of duplicate compasses, including multiple picked
     * up in a single tick.
     */
    void deduplicateCompasses(Player player) {
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

    void removeCompasses(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isCompass(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    boolean isCompass(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                        .has(compassKey, PersistentDataType.BYTE);
    }

    boolean hasCompass(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isCompass(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when the player may keep a compass: a hunter or speedrunner
     * inside a live match. Everyone else loses picked-up compasses.
     */
    boolean mayHoldCompass(Player player) {
        Role holderRole = playerStates.role(player);
        if (holderRole != Role.HUNTER && holderRole != Role.SPEEDRUNNER) {
            return false;
        }
        return game != null && game.instanceOf(player.getUniqueId()).isPresent();
    }

    /** Restamps the holder's compass with their role's name and lore. */
    void refreshCompassIdentity(Player player) {
        int slot = findCompassSlot(player);
        if (slot < 0) {
            return;
        }
        ItemStack item = player.getInventory().getItem(slot);
        if (!isCompass(item)) {
            return;
        }
        applyCompassIdentity(item, playerStates.role(player));
        player.getInventory().setItem(slot, item);
    }

    /**
     * Stamps a compass with its holder role's name and lore. Missing
     * per-role keys fall back to the legacy shared text.
     */
    private void applyCompassIdentity(ItemStack item, Role holderRole) {
        ItemMeta meta = item.getItemMeta();
        String nameKey = compassNameKey(holderRole);
        if (messages.string(nameKey, null) == null) {
            nameKey = "compass.compass-name";
        }
        meta.displayName(messages.nonItalic(messages.component(nameKey)));
        List<String> lore = messages.strings(compassLoreKey(holderRole));
        if (lore.isEmpty()) {
            lore = messages.strings("compass.compass-lore");
        }
        meta.lore(lore.stream().map(messages::parse).map(messages::nonItalic).toList());
        item.setItemMeta(meta);
    }

    /** Message key for a role's compass name. Pure for tests. */
    static String compassNameKey(Role holderRole) {
        if (holderRole == Role.SPEEDRUNNER) {
            return "compass.speedrunner-name";
        }
        if (holderRole == Role.HUNTER) {
            return "compass.hunter-name";
        }
        return "compass.compass-name";
    }

    /** Message key for a role's compass lore. Pure for tests. */
    static String compassLoreKey(Role holderRole) {
        if (holderRole == Role.SPEEDRUNNER) {
            return "compass.speedrunner-lore";
        }
        if (holderRole == Role.HUNTER) {
            return "compass.hunter-lore";
        }
        return "compass.compass-lore";
    }

    boolean mustBeInventory(Integer lobby) {
        return plugin.overrides().getBoolean(lobby,
                "settings.compass.must-be-inventory.enabled", true);
    }
}
