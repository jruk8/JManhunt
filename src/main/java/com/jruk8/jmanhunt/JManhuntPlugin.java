package com.jruk8.jmanhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class JManhuntPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final PlayerStateStore playerStates = new PlayerStateStore();
    private final Map<UUID, Stats> stats = new HashMap<>();
    private final Map<UUID, Long> compassClicks = new HashMap<>();
    private final Map<UUID, Component> compassActionbars = new HashMap<>();
    private NamespacedKey compassKey;
    private boolean active;
    private boolean ending;
    private boolean gameBegun;
    private BukkitTask waitingReminderTask;
    private final MessageService messageService = new MessageService();
    private static final int CONFIG_VERSION = 2;
    private static final int MESSAGES_VERSION = 2;

    @Override public void onEnable() {
        YamlFileUpdater.update(this, "config.yml", "config-version", CONFIG_VERSION);
        YamlFileUpdater.update(this, "messages.yml", "messages-version", MESSAGES_VERSION);
        saveResource("placeholders.yml", false);
        reloadMessages();
        compassKey = new NamespacedKey(this, "hunters_compass");
        reloadMessages();
        getCommand("manhunt").setExecutor(this);
        getCommand("manhunt").setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);
        long ticks = Math.max(1L, Math.round(getConfig().getDouble("compass-refresh-interval", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskTimer(this, this::refreshAllCompasses, ticks, ticks);
        Bukkit.getScheduler().runTaskTimer(this, this::showHeldActionbars, 1L, 20L);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if (!has(sender, "jmanhunt.command." + sub)) return message(sender, "command.no-permission");
        switch (sub) {
            case "help" -> { return help(sender); }
            case "status" -> { return status(sender); }
            case "setplayer" -> { return setPlayer(sender, args); }
            case "start" -> { return start(sender); }
            case "end" -> { return end(sender, true); }
            case "reload" -> { YamlFileUpdater.update(this, "config.yml", "config-version", CONFIG_VERSION); YamlFileUpdater.update(this, "messages.yml", "messages-version", MESSAGES_VERSION); reloadConfig(); reloadMessages(); return message(sender, "manhunt.reload-success"); }
            default -> { return message(sender, "command.invalid"); }
        }
    }

    private boolean help(CommandSender sender) {
        message(sender, "manhunt.help-header");
        String[][] lines = {{"/manhunt help", "show commands"}, {"/manhunt", "show match status"},
                {"/manhunt setplayer <selector> <hunter|speedrunner|none>", "assign roles"},
                {"/manhunt start", "start a match"}, {"/manhunt end", "end a match"}, {"/manhunt reload", "reload files"}};
        for (String[] line : lines) message(sender, "manhunt.help-line", Map.of("command", line[0], "description", line[1]));
        return true;
    }

    private boolean status(CommandSender sender) {
        message(sender, "manhunt.status-header", Map.of("status", active ? "ACTIVE" : "INACTIVE"));
        sendRoleSection(sender, Role.SPEEDRUNNER, "manhunt.speedrunners-header");
        sendRoleSection(sender, Role.HUNTER, "manhunt.hunters-header");
        sendRoleSection(sender, Role.NONE, "manhunt.none-header");
        return true;
    }

    private void sendRoleSection(CommandSender sender, Role role, String header) {
        List<Player> players = Bukkit.getOnlinePlayers().stream().filter(p -> role(p) == role)
                .map(p -> (Player) p).sorted(Comparator.comparing(Player::getName)).toList();
        if (players.isEmpty()) return;
        message(sender, header);
        players.forEach(player -> message(sender, "manhunt.status-player", Map.of("player", player.getName())));
    }

    private boolean setPlayer(CommandSender sender, String[] args) {
        if (args.length < 3) return message(sender, "command.invalid");
        Role role;
        try { role = Role.valueOf(args[2].toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return message(sender, "command.invalid"); }
        List<Entity> selected;
        try { selected = Bukkit.selectEntities(sender, args[1]); } catch (IllegalArgumentException e) { return message(sender, "command.invalid"); }
        int changed = 0, skipped = 0;
        for (Entity entity : selected) if (entity instanceof Player player) {
            if (role == Role.HUNTER && !player.hasPermission("jmanhunt.hunter") || role == Role.SPEEDRUNNER && !player.hasPermission("jmanhunt.speedrunner")) { skipped++; continue; }
            playerStates.setRole(player, role); changed++;
            if (active && role == Role.NONE) {
                playerStates.setSpeedrunnerAlive(player.getUniqueId(), false);
                player.setGameMode(GameMode.SPECTATOR);
                removeCompasses(player);
            }
            if (active && role == Role.HUNTER) giveCompass(player);
        }
        message(sender, "manhunt.set-success", Map.of("count", String.valueOf(changed), "role", role.name()));
        if (skipped > 0) message(sender, "manhunt.set-skipped", Map.of("count", String.valueOf(skipped)));
        return true;
    }

    private boolean start(CommandSender sender) {
        if (active) return message(sender, "manhunt.already-active");
        List<Player> players = Bukkit.getOnlinePlayers().stream().filter(p -> role(p) != Role.NONE).map(p -> (Player) p).toList();
        if (players.stream().noneMatch(p -> role(p) == Role.HUNTER) || players.stream().noneMatch(p -> role(p) == Role.SPEEDRUNNER)) return message(sender, "manhunt.start-invalid");
        active = true; ending = false; gameBegun = false; stats.clear(); playerStates.clearMatch();
        Bukkit.getOnlinePlayers().forEach(player -> player.setGameMode(GameMode.SURVIVAL));
        for (Player player : players) { Stats stat = new Stats(); stat.player = player.getName(); stats.put(player.getUniqueId(), stat); if (role(player) == Role.SPEEDRUNNER) { playerStates.setSpeedrunnerAlive(player.getUniqueId(), true); recordLastSeen(player, player.getLocation()); } if (role(player) == Role.HUNTER) { giveCompass(player); refreshCompass(player); } }
        runConfigured("start-commands", "");
        broadcast("manhunt.start-success"); sound("neutral-sound");
        if (getConfig().getBoolean("start-on-speedrunner-damage", true)) scheduleWaitingReminder(); else beginGame();
        return true;
    }

    private boolean end(CommandSender sender, boolean manual) {
        if (!active) return message(sender, "manhunt.not-active");
        finish(manual ? Role.HUNTER : Role.SPEEDRUNNER);
        return true;
    }

    private void finish(Role winner) {
        if (ending) return;
        ending = true;
        if (waitingReminderTask != null) waitingReminderTask.cancel();
        String winnerName = winner == Role.HUNTER ? "Hunters" : "Speedrunners";
        String winMessage = winner == Role.HUNTER ? "game.hunters-win" : "game.speedrunners-win";
        String title = winner == Role.HUNTER ? "game.hunters-title" : "game.speedrunners-title";
        broadcast(winMessage);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(component(title), Component.empty(), Title.Times.times(java.time.Duration.ofMillis(500), java.time.Duration.ofSeconds(3), java.time.Duration.ofMillis(500))));
        }
        sound(winner == Role.HUNTER ? "fail-sound" : "win-sound");
        long delay = Math.max(0L, Math.round(getConfig().getDouble("game-end-delay", 10.0) * 20.0));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            showStats(winner);
        }, delay / 2);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            runConfigured("end-commands", winnerName);
            for (Player player : Bukkit.getOnlinePlayers()) { player.getInventory().clear(); if (getConfig().getBoolean("run-default-commands", true)) player.setGameMode(GameMode.SURVIVAL); }
            active = false; ending = false; gameBegun = false; compassActionbars.clear(); playerStates.clearMatch();
        }, delay);
    }

    private void finishLater(Role winner) {
        Bukkit.getScheduler().runTask(this, () -> finish(winner));
    }

    private void scheduleWaitingReminder() {
        long delay = Math.max(1L, Math.round(getConfig().getDouble("start-reminder-interval", 10.0) * 20.0));
        broadcast("manhunt.waiting-for-damage");
        waitingReminderTask = Bukkit.getScheduler().runTaskTimer(this,
                () -> { if (active && !gameBegun) broadcast("manhunt.waiting-for-damage"); }, delay, delay);
    }

    private void beginGame() {
        if (gameBegun) return;
        gameBegun = true;
        if (waitingReminderTask != null) waitingReminderTask.cancel();
        broadcast("manhunt.started-by-damage");
        sound("neutral-sound");
        applyStartDebuffs();
    }

    private void applyStartDebuffs() {
        if (!getConfig().getBoolean("start-debuffs.enabled", false)) return;
        ConfigurationSection effects = getConfig().getConfigurationSection("start-debuffs.effects");
        if (effects == null) return;
        for (Player hunter : Bukkit.getOnlinePlayers()) {
            if (role(hunter) != Role.HUNTER) continue;
            for (String effectName : effects.getKeys(false)) {
                ConfigurationSection effect = effects.getConfigurationSection(effectName);
                if (effect == null) continue;
                PotionEffectType type = PotionEffectType.getByName(effectName.toUpperCase(Locale.ROOT));
                if (type == null) continue;
                double seconds = effect.getDouble("seconds", 10.0);
                int amplifier = effect.getInt("amplifier", 0);
                hunter.addPotionEffect(new PotionEffect(type, Math.max(1, (int) Math.round(seconds * 20.0)), amplifier, false, false, true));
                message(hunter, "manhunt.debuff-applied", Map.of("effect", effectName, "seconds", String.valueOf(seconds)));
            }
        }
    }

    private void showStats(Role winner) {
        for (String stat : getConfig().getStringList("end-statistics")) {
            if (stat.equalsIgnoreCase("PROGRESSION") && winner == Role.SPEEDRUNNER) continue;
            List<String> values = stats.values().stream().sorted(Comparator.comparingInt((Stats s) -> s.value(stat)).reversed()).limit(3)
                    .map(s -> s.player + ": " + s.value(stat)).toList();
            List<Stats> ranked = stats.values().stream().sorted(Comparator.comparingInt((Stats s) -> s.value(stat)).reversed())
                    .filter(s -> s.value(stat) > 0).limit(3).toList();
            if (ranked.isEmpty()) continue;
            String displayName = messageService.string("game.stat-names." + stat, stat);
            broadcast("game.stat-header", Map.of("stat", displayName));
            for (int index = 0; index < ranked.size(); index++) {
                Stats statValue = ranked.get(index);
                broadcast("game.stat-entry", Map.of("rank", String.valueOf(index + 1), "player", statValue.player,
                        "value", String.valueOf(statValue.value(stat))));
            }
        }
    }

    private void refreshAllCompasses() {
        if (active) Bukkit.getOnlinePlayers().stream().filter(p -> role(p) == Role.HUNTER).forEach(this::refreshCompass);
    }

    private void showHeldActionbars() {
        if (!active) return;
        Bukkit.getOnlinePlayers().stream().filter(p -> role(p) == Role.HUNTER)
                .filter(p -> isCompass(p.getInventory().getItemInMainHand()) || isCompass(p.getInventory().getItemInOffHand()))
                .forEach(p -> p.sendActionBar(compassActionbars.getOrDefault(p.getUniqueId(), component("compass.no-target-actionbar"))));
    }

    private void refreshCompass(Player hunter) {
        ItemStack item = hunter.getInventory().getItem(8);
        enforceCompassSlot(hunter);
        item = hunter.getInventory().getItem(8);
        if (!isCompass(item)) { giveCompass(hunter); item = hunter.getInventory().getItem(8); }
        Player target = Bukkit.getOnlinePlayers().stream().filter(p -> role(p) == Role.SPEEDRUNNER
                        && playerStates.isActiveSpeedrunner(p.getUniqueId())
                        && p.getGameMode() != GameMode.SPECTATOR && p.getWorld().equals(hunter.getWorld()))
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(hunter.getLocation()))).orElse(null);
        Location location = target == null ? playerStates.sightings().entrySet().stream().filter(entry -> playerStates.isActiveSpeedrunner(entry.getKey()))
                .map(Map.Entry::getValue).map(worlds -> worlds.get(hunter.getWorld().getUID()))
                .filter(l -> l != null && l.getWorld() != null).min(Comparator.comparingDouble(l -> l.distanceSquared(hunter.getLocation()))).orElse(null) : target.getLocation();
        if (location != null && item != null && item.getItemMeta() instanceof CompassMeta meta) { meta.setLodestone(location); meta.setLodestoneTracked(false); item.setItemMeta(meta); }
        if (target != null) {
            compassActionbars.put(hunter.getUniqueId(), component("compass.compass-actionbar", Map.of("player", target.getName(), "distance", String.valueOf(Math.round(hunter.getLocation().distance(target.getLocation()))))));
        } else {
            compassActionbars.put(hunter.getUniqueId(), component("compass.no-target-actionbar"));
        }
    }

    private void giveCompass(Player player) { if (role(player) != Role.HUNTER) return; ItemStack item = new ItemStack(Material.COMPASS); CompassMeta meta = (CompassMeta) item.getItemMeta(); meta.displayName(messageService.nonItalic(component("compass.compass-name"))); meta.lore(messageService.strings("compass.compass-lore").stream().map(messageService::parse).map(messageService::nonItalic).toList()); meta.addEnchant(Enchantment.VANISHING_CURSE, 1, true); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS); meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1); item.setItemMeta(meta); player.getInventory().setItem(8, item); }
    private void enforceCompassSlot(Player player) {
        ItemStack found = null;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (isCompass(item)) {
                if (found == null) found = item;
                player.getInventory().setItem(slot, null);
            }
        }
        if (found != null) player.getInventory().setItem(8, found);
    }
    private void removeCompasses(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isCompass(player.getInventory().getItem(slot))) player.getInventory().setItem(slot, null);
        }
    }
    private boolean isCompass(ItemStack item) { return item != null && item.getType() == Material.COMPASS && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.BYTE); }

    @EventHandler public void onJoin(PlayerJoinEvent event) { playerStates.resetRolesIfAbsent(event.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) { if (active && role(event.getPlayer()) == Role.HUNTER) Bukkit.getScheduler().runTask(this, () -> giveCompass(event.getPlayer())); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { Player player = event.getEntity(); recordLastSeen(player, player.getLocation()); if (!active || !gameBegun) return; if (role(player) == Role.SPEEDRUNNER) { playerStates.setSpeedrunnerAlive(player.getUniqueId(), false); if (player.getKiller() != null) stats.computeIfAbsent(player.getKiller().getUniqueId(), k -> new Stats()).finalKills++; Bukkit.getScheduler().runTask(this, () -> player.setGameMode(GameMode.SPECTATOR)); if (Bukkit.getOnlinePlayers().stream().filter(p -> role(p) == Role.SPEEDRUNNER && playerStates.isActiveSpeedrunner(p.getUniqueId())).count() == 0) finishLater(Role.HUNTER); } }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) { if (active && role(event.getPlayer()) == Role.SPEEDRUNNER) recordLastSeen(event.getPlayer(), event.getTo()); if (active && gameBegun && role(event.getPlayer()) == Role.SPEEDRUNNER && playerStates.isActiveSpeedrunner(event.getPlayer().getUniqueId()) && event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) finishLater(Role.SPEEDRUNNER); }
    @EventHandler public void onMove(org.bukkit.event.player.PlayerMoveEvent event) { if (active && role(event.getPlayer()) == Role.SPEEDRUNNER && event.getTo() != null) recordLastSeen(event.getPlayer(), event.getTo()); }
    @EventHandler public void onDrop(PlayerDropItemEvent event) { if (isCompass(event.getItemDrop().getItemStack())) event.setCancelled(true); }
    @EventHandler public void onClick(InventoryClickEvent event) { if (isCompass(event.getCurrentItem()) || isCompass(event.getCursor()) || isCompass(event.getHotbarButton() >= 0 ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null)) event.setCancelled(true); }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (isCompass(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(this::isCompass)) event.setCancelled(true); }
    @EventHandler public void onMove(InventoryMoveItemEvent event) { if (isCompass(event.getItem())) event.setCancelled(true); }
    @EventHandler public void onPickup(InventoryPickupItemEvent event) { if (isCompass(event.getItem().getItemStack())) event.setCancelled(true); }
    @EventHandler public void onSwap(PlayerSwapHandItemsEvent event) { if (isCompass(event.getMainHandItem()) || isCompass(event.getOffHandItem())) event.setCancelled(true); }
    @EventHandler public void onInteract(PlayerInteractEvent event) { if (event.getHand() != EquipmentSlot.HAND || !isCompass(event.getItem()) || !active) return; if (getConfig().getBoolean("compass-refresh.refresh-on-right-click", false)) { long now = System.currentTimeMillis(); if (now - compassClicks.getOrDefault(event.getPlayer().getUniqueId(), 0L) >= getConfig().getDouble("compass-refresh.compass-right-click-cooldown", 3.0) * 1000) { compassClicks.put(event.getPlayer().getUniqueId(), now); refreshCompass(event.getPlayer()); } } }
    @EventHandler public void onDamage(EntityDamageByEntityEvent event) { if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player attacker) || event.getFinalDamage() <= 0) return; if (active && !gameBegun && role(attacker) == Role.SPEEDRUNNER && role(victim) == Role.HUNTER) beginGame(); if (!active || !gameBegun || role(attacker) == Role.NONE || role(victim) == Role.NONE) return; Stats s = stats.computeIfAbsent(attacker.getUniqueId(), k -> new Stats()); s.damage += event.getFinalDamage(); }
    @EventHandler public void onEntityDeath(EntityDeathEvent event) { if (!active || !gameBegun || !(event.getEntity() instanceof Player victim) || event.getEntity().getKiller() == null || role(victim) == Role.NONE || role(event.getEntity().getKiller()) == Role.NONE) return; stats.computeIfAbsent(event.getEntity().getKiller().getUniqueId(), k -> new Stats()).kills++; }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) { if (args.length == 1) return partial(args[0], List.of("help", "status", "setplayer", "start", "end", "reload")); if (args.length == 2 && args[0].equalsIgnoreCase("setplayer")) { List<String> selectors = new ArrayList<>(List.of("@a", "@r", "@s", "@p")); Bukkit.getOnlinePlayers().forEach(player -> selectors.add(player.getName())); if (args[1].startsWith("@a[")) { return partial(args[1], List.of("@a[distance=", "@a[limit=", "@a[name=", "@a[gamemode=")); } return partial(args[1], selectors); } if (args.length == 3 && args[0].equalsIgnoreCase("setplayer")) return partial(args[2], List.of("hunter", "speedrunner", "none")); return List.of(); }
    private List<String> partial(String value, List<String> options) { return options.stream().filter(s -> s.startsWith(value.toLowerCase(Locale.ROOT))).toList(); }
    private Role role(Player p) { return playerStates.role(p); }
    private Role roleById(UUID id) { return playerStates.role(id); }
    private void recordLastSeen(Player player, Location location) { playerStates.recordLastSeen(player, location); }
    private boolean has(CommandSender sender, String permission) { return sender.hasPermission(permission); }
    private void runConfigured(String key, String winner) { for (String command : getConfig().getStringList(key)) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{winner}", winner)); }
    private void broadcast(String key) { broadcast(key, Map.of()); }
    private void broadcast(String key, Map<String, String> values) { Bukkit.broadcast(component(key, values)); }
    private boolean message(CommandSender sender, String key) { sender.sendMessage(component(key)); return true; }
    private void message(CommandSender sender, String key, Map<String, String> values) { sender.sendMessage(component(key, values)); }
    private void sound(String key) { try { Sound sound = Sound.valueOf(getConfig().getString("sounds." + key, "BLOCK_NOTE_BLOCK_PLING")); Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), sound, 1, 1)); } catch (IllegalArgumentException ignored) { } }
    private void reloadMessages() { messageService.reload(YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(), "messages.yml")), getConfig().getString("text-format", "minimessage")); }
    private Component component(String key) { return messageService.component(key); }
    private Component component(String key, Map<String, String> values) { return messageService.component(key, values); }
    private static final class Stats { String player = "unknown"; double damage; int kills; int finalKills; int progression; int value(String s) { return switch (s.toUpperCase(Locale.ROOT)) { case "DAMAGE_DEALT" -> (int) damage; case "KILLS" -> kills; case "FINAL_KILLS" -> finalKills; case "PROGRESSION" -> progression; default -> 0; }; } }
}
