package com.jruk8.jmanhunt;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the /jmanhunt schem subcommand for saving, listing, and deleting
 * structure (.nbt) files. Also tracks wand selections for region capture.
 */
public final class SchemCommand implements Listener {
    private static final long CONFIRMATION_TIMEOUT_MS = 5000;
    /** Display name for the schematic wand, shared as a static constant. */
    public static final String WAND_DISPLAY_NAME = "Manhunt Schematic Wand";
    private static final String WAND_PDC_KEY = "schematic_wand";
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final SoundService sounds;
    private final File structuresDir;
    private final NamespacedKey wandKey;
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final Map<String, Long> pendingSaveConfirmations = new HashMap<>();
    private final Map<String, Long> pendingDeleteConfirmations = new HashMap<>();

    public SchemCommand(JManhuntPlugin plugin, MessageService messages, SoundService sounds) {
        this.plugin = plugin;
        this.messages = messages;
        this.sounds = sounds;
        this.structuresDir = new File(plugin.getDataFolder(), "challenges/structures");
        this.wandKey = new NamespacedKey(plugin, WAND_PDC_KEY);
    }

    public boolean onCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            message(sender, "manhunt.schem-usage");
            return true;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "save" -> save(sender, args);
            case "list" -> list(sender);
            case "delete" -> delete(sender, args);
            case "wand" -> wand(sender);
            default -> message(sender, "command.invalid");
        };
    }

    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return partial(args[1], List.of("save", "list", "delete", "wand"));
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("save") || args[1].equalsIgnoreCase("delete"))) {
            return partial(args[2], listSchematicNames());
        }
        return List.of();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("jmanhunt.command.schem")) return;
        if (!isWandItem(player)) return;
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            Location newPos = event.getClickedBlock().getLocation();
            Location current = pos1.get(player.getUniqueId());
            if (current == null || !sameBlock(current, newPos)) {
                pos1.put(player.getUniqueId(), newPos);
                message(player, "manhunt.schem-pos1", Map.of("pos", formatLocation(newPos)));
            }
            event.setCancelled(true);
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            Location newPos = event.getClickedBlock().getLocation();
            Location current = pos2.get(player.getUniqueId());
            if (current == null || !sameBlock(current, newPos)) {
                pos2.put(player.getUniqueId(), newPos);
                message(player, "manhunt.schem-pos2", Map.of("pos", formatLocation(newPos)));
            }
            event.setCancelled(true);
        }
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private boolean wand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            message(sender, "command.player-only");
            return true;
        }
        if (!player.hasPermission("jmanhunt.command.schem")) {
            message(sender, "command.no-permission");
            return true;
        }
        ItemStack wand = createWand();
        // Try to place in the player's hand first, then inventory.
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(wand);
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), wand);
        }
        message(sender, "manhunt.schem-wand-given");
        sounds.playNeutralSound(player);
        return true;
    }

    /** Creates the schematic wand item with plugin-owned persistent data. */
    public ItemStack createWand() {
        ItemStack item = new ItemStack(Material.BREEZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.parse(WAND_DISPLAY_NAME));
        meta.lore(messages.strings("schem-wand-lore").stream().map(messages::parse).toList());
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Checks whether the player is holding the schematic wand in their main hand. */
    public boolean isWandItem(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    private boolean save(CommandSender sender, String[] args) {
        if (args.length < 3) {
            message(sender, "manhunt.schem-save-usage");
            return true;
        }
        String name = args[2];
        if (!name.matches("[a-zA-Z0-9_\\-]+")) {
            message(sender, "manhunt.schem-invalid-name");
            return true;
        }
        File targetFile = new File(structuresDir, name + ".nbt");
        String confirmKey = getConfirmKey(sender, name);
        if (targetFile.exists() && !isConfirmationPending(pendingSaveConfirmations, confirmKey)) {
            pendingSaveConfirmations.put(confirmKey, System.currentTimeMillis());
            message(sender, "manhunt.schem-save-confirm", Map.of("name", name));
            return true;
        }
        if (targetFile.exists() && isConfirmationPending(pendingSaveConfirmations, confirmKey)) {
            pendingSaveConfirmations.remove(confirmKey);
        }
        if (!(sender instanceof Player player)) {
            message(sender, "command.player-only");
            return true;
        }
        Location loc1 = pos1.get(player.getUniqueId());
        Location loc2 = pos2.get(player.getUniqueId());
        if (loc1 == null || loc2 == null) {
            message(sender, "manhunt.schem-no-selection");
            return true;
        }
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            message(sender, "manhunt.schem-different-worlds");
            return true;
        }
        try {
            structuresDir.mkdirs();
            StructureManager sm = Bukkit.getStructureManager();
            Structure structure = sm.createStructure();
            // Structure.fill treats the second corner as exclusive, so expand
            // the max corner by one block to capture the full selection.
            Location min = new Location(loc1.getWorld(),
                    Math.min(loc1.getBlockX(), loc2.getBlockX()),
                    Math.min(loc1.getBlockY(), loc2.getBlockY()),
                    Math.min(loc1.getBlockZ(), loc2.getBlockZ()));
            Location max = new Location(loc1.getWorld(),
                    Math.max(loc1.getBlockX(), loc2.getBlockX()) + 1,
                    Math.max(loc1.getBlockY(), loc2.getBlockY()) + 1,
                    Math.max(loc1.getBlockZ(), loc2.getBlockZ()) + 1);
            structure.fill(min, max, true);
            sm.saveStructure(targetFile, structure);
            message(sender, "manhunt.schem-save-success", Map.of("name", name));
            if (sender instanceof Player p) sounds.playNeutralSound(p);
        } catch (IOException e) {
            message(sender, "manhunt.schem-save-failed", Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown"));
            plugin.getLogger().severe("Failed to save schematic '" + name + "': " + e.getMessage());
        }
        return true;
    }

    private boolean list(CommandSender sender) {
        List<String> names = listSchematicNames();
        if (names.isEmpty()) {
            message(sender, "manhunt.schem-list-empty");
            return true;
        }
        message(sender, "manhunt.schem-list-header");
        for (String name : names) {
            message(sender, "manhunt.schem-list-entry", Map.of("name", name));
        }
        if (sender instanceof Player p) sounds.playNeutralSound(p);
        return true;
    }

    private boolean delete(CommandSender sender, String[] args) {
        if (args.length < 3) {
            message(sender, "manhunt.schem-delete-usage");
            return true;
        }
        String name = args[2];
        File targetFile = new File(structuresDir, name + ".nbt");
        if (!targetFile.exists()) {
            message(sender, "manhunt.schem-not-found", Map.of("name", name));
            return true;
        }
        String confirmKey = getConfirmKey(sender, name);
        if (!isConfirmationPending(pendingDeleteConfirmations, confirmKey)) {
            pendingDeleteConfirmations.put(confirmKey, System.currentTimeMillis());
            message(sender, "manhunt.schem-delete-confirm", Map.of("name", name));
            return true;
        }
        pendingDeleteConfirmations.remove(confirmKey);
        if (targetFile.delete()) {
            message(sender, "manhunt.schem-delete-success", Map.of("name", name));
            if (sender instanceof Player p) sounds.playNeutralSound(p);
        } else {
            message(sender, "manhunt.schem-delete-failed", Map.of("name", name));
        }
        return true;
    }

    private List<String> listSchematicNames() {
        List<String> names = new ArrayList<>();
        File[] files = structuresDir.listFiles((dir, filename) -> filename.endsWith(".nbt"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                names.add(name.substring(0, name.length() - 4));
            }
        }
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private boolean isConfirmationPending(Map<String, Long> pending, String key) {
        Long timestamp = pending.get(key);
        if (timestamp == null) return false;
        if (System.currentTimeMillis() - timestamp > CONFIRMATION_TIMEOUT_MS) {
            pending.remove(key);
            return false;
        }
        return true;
    }

    private static String getConfirmKey(CommandSender sender, String name) {
        return (sender instanceof Player p ? p.getUniqueId().toString() : "console") + ":" + name;
    }

    private String formatLocation(Location loc) {
        return loc.getWorld().getName() + " @ " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    private List<String> partial(String value, List<String> options) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }

    private boolean message(CommandSender sender, String key) {
        sender.sendMessage(messages.component(key));
        return true;
    }

    private void message(CommandSender sender, String key, Map<String, String> values) {
        sender.sendMessage(messages.component(key, values));
    }
}
