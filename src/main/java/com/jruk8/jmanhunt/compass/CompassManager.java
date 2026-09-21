package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.command.CommandPlaceholders;
import com.jruk8.jmanhunt.core.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CompassManager {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final SoundService sounds;
    private final PlayerStateStore playerStates;
    private final NamespacedKey compassKey;
    private final Map<UUID, Long> lastRefresh = new HashMap<>();
    /** Manual left-click target locks: holder id -> locked target id. */
    private final Map<UUID, UUID> locks = new HashMap<>();
    private final Map<UUID, Component> compassActionbars = new HashMap<>();
    private final Set<UUID> analyzing = new HashSet<>();
    private GameManager game;

    public CompassManager(JManhuntPlugin plugin, MessageService messages, SoundService sounds,
                          PlayerStateStore playerStates, NamespacedKey compassKey) {
        this.plugin = plugin;
        this.messages = messages;
        this.sounds = sounds;
        this.playerStates = playerStates;
        this.compassKey = compassKey;
    }

    /** Wires the game after construction so targets resolve within one match. */
    public void setGameManager(GameManager game) {
        this.game = game;
    }

    public void refreshAllCompasses(boolean active) {
        if (active) {
            // One universal refresh clock shared with right-click refreshes,
            // stamped at initiation: holders refreshed less than an interval
            // ago keep their fresh target.
            long intervalMs = (long) (plugin.getConfig()
                    .getDouble("settings.compass.refresh-interval", 10.0) * 1000);
            long now = System.currentTimeMillis();
            boolean analyze = analyzeEnabled(true);
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> role(p).isParticipant())
                    .filter(p -> inLiveInstance(p))
                    .filter(this::hasCompass)
                    .forEach(holder -> {
                        UUID id = holder.getUniqueId();
                        if (!shouldRefresh(now, lastRefresh.getOrDefault(id, 0L), intervalMs)) {
                            return;
                        }
                        lastRefresh.put(id, now);
                        if (analyze) {
                            startAnalysis(holder);
                        } else {
                            refreshCompass(holder);
                        }
                    });
        }
    }

    /** True when the cooldown has elapsed since the last refresh. Pure for tests. */
    static boolean shouldRefresh(long nowMillis, long lastMillis, long cooldownMs) {
        return nowMillis - lastMillis >= cooldownMs;
    }

    public void showHeldActionbars(boolean active) {
        if (!active) {
            return;
        }
        Bukkit.getOnlinePlayers().stream().filter(p -> role(p).isParticipant())
                .filter(p -> inLiveInstance(p))
                .filter(p -> isCompass(p.getInventory().getItemInMainHand())
                        || isCompass(p.getInventory().getItemInOffHand()))
                .forEach(p -> p.sendActionBar(compassActionbars.getOrDefault(p.getUniqueId(),
                        component("compass.no-target-actionbar",
                                Map.of("role", messages.roleName(role(p) == Role.HUNTER
                                        ? Role.SPEEDRUNNER : Role.HUNTER))))));
    }

    /** True when the holder actively participates in a live match. */
    private boolean inLiveInstance(Player player) {
        return game != null && game.instanceOf(player.getUniqueId()).isPresent();
    }

    public void refreshCompass(Player holder) {
        deduplicateCompasses(holder);
        int slot = findCompassSlot(holder);
        if (slot < 0) {
            return;
        }
        ItemStack item = holder.getInventory().getItem(slot);
        if (!isCompass(item)) {
            return;
        }

        Role holderRole = role(holder);
        if (!holderRole.isParticipant()) {
            locks.remove(holder.getUniqueId());
            return;
        }
        if (game == null) {
            return;
        }
        Optional<GameInstance> match = game.instanceOf(holder.getUniqueId());
        if (match.isEmpty()) {
            locks.remove(holder.getUniqueId());
            return;
        }
        GameInstance instance = match.get();
        Role targetRole = holderRole == Role.HUNTER ? Role.SPEEDRUNNER : Role.HUNTER;
        String targetRoleString = messages.roleName(targetRole);

        List<CompassCandidate> opponents = collectOpponents(holder, targetRole, instance);
        List<CompassSighting> sightings = collectSightings(holder, targetRole, instance);
        UUID locked = locks.get(holder.getUniqueId());
        if (locked != null) {
            if (trackLockedTarget(holder, item, slot, locked, opponents, sightings, targetRoleString)) {
                return;
            }
            locks.remove(holder.getUniqueId());
        }
        String roleBase = "settings.compass." + holderRole.name().toLowerCase(Locale.ROOT) + ".";
        boolean nearbyEnabled = plugin.getConfig()
                .getBoolean(roleBase + "min-distance.enabled", true);
        double nearbyThreshold = plugin.getConfig()
                .getDouble(roleBase + "min-distance.distance", 25.0);
        double trackingDistance = plugin.getConfig()
                        .getBoolean(roleBase + "max-distance.enabled", true)
                ? plugin.getConfig().getDouble(roleBase + "max-distance.distance", -1.0)
                : -1.0;
        CompassPick pick = CompassPick.resolve(opponents, sightings, nearbyEnabled, nearbyThreshold,
                trackingDistance);
        switch (pick.kind()) {
            case TRACK_PLAYER -> trackPlayer(holder, item, slot, pick, targetRoleString, false);
            case NEARBY -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                compassActionbars.put(holder.getUniqueId(), component("compass.nearby-actionbar",
                        Map.of("player", pick.name())));
            }
            case TRACK_SIGHTING -> trackSighting(holder, item, slot, pick, targetRoleString, false);
            case TOO_FAR -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                compassActionbars.put(holder.getUniqueId(), component("compass.too-far-actionbar",
                        Map.of("player", pick.name())));
            }
            case NONE -> showNoTarget(holder, item, slot, targetRoleString);
        }
    }

    /**
     * Live opponents of the given role in the same world and match,
     * nearest first.
     */
    private List<CompassCandidate> collectOpponents(Player holder, Role targetRole, GameInstance instance) {
        Location origin = holder.getLocation();
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> role(p) == targetRole
                        && isTrackableTarget(p.getUniqueId(), targetRole, instance)
                        && p.getGameMode() != GameMode.SPECTATOR
                        && !p.getUniqueId().equals(holder.getUniqueId())
                        && p.getWorld().equals(holder.getWorld()))
                .map(player -> new CompassCandidate(player.getUniqueId(), player.getName(),
                        origin.distance(player.getLocation()),
                        flatDistance(origin, player.getLocation())))
                .sorted(Comparator.comparingDouble(CompassCandidate::distance))
                .toList();
    }

    /** Flat X/Z distance between two spots, ignoring Y. Pure for tests. */
    static double flatDistance(Location from, Location to) {
        double dx = from.getX() - to.getX();
        double dz = from.getZ() - to.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Points a locked compass at its target: live location for a live
     * opponent, else their last-seen location. A manual lock bypasses the
     * min/max distance overrides. Returns false when the
     * target is no longer trackable, so the caller falls back to
     * automatic.
     */
    private boolean trackLockedTarget(Player holder, ItemStack item, int slot, UUID locked,
            List<CompassCandidate> opponents, List<CompassSighting> sightings, String targetRoleString) {
        for (CompassCandidate candidate : opponents) {
            if (candidate.id().equals(locked)) {
                trackPlayer(holder, item, slot,
                        new CompassPick(CompassPick.Kind.TRACK_PLAYER, locked, candidate.name()),
                        targetRoleString, true);
                return true;
            }
        }
        for (CompassSighting sighting : sightings) {
            if (sighting.ownerId().equals(locked)) {
                trackSighting(holder, item, slot,
                        new CompassPick(CompassPick.Kind.TRACK_SIGHTING, locked, sighting.name()),
                        targetRoleString, true);
                return true;
            }
        }
        return false;
    }

    private void trackPlayer(Player holder, ItemStack item, int slot, CompassPick pick, String targetRoleString,
            boolean locked) {
        Player target = Bukkit.getPlayer(pick.id());
        if (target == null) {
            showNoTarget(holder, item, slot, targetRoleString);
            return;
        }
        setLodestone(item, target.getLocation());
        holder.getInventory().setItem(slot, item);
        compassActionbars.put(holder.getUniqueId(), component(
                locked ? "compass.compass-locked-actionbar" : "compass.compass-actionbar",
                Map.of("player", target.getName(),
                        "distance",
                        String.valueOf(Math.round(holder.getLocation().distance(target.getLocation()))))));
    }

    private void trackSighting(Player holder, ItemStack item, int slot, CompassPick pick, String targetRoleString,
            boolean locked) {
        Location location = playerStates.sightings().getOrDefault(pick.id(), Map.of())
                .get(holder.getWorld().getUID());
        Player seen = Bukkit.getPlayer(pick.id());
        if (location == null || location.getWorld() == null
                || skipLastSeen(seen != null, seen == null ? null : seen.getGameMode())) {
            showNoTarget(holder, item, slot, targetRoleString);
            return;
        }
        setLodestone(item, location);
        holder.getInventory().setItem(slot, item);
        String reason = seen != null ? "Another Dimension" : "Log-Out";
        compassActionbars.put(holder.getUniqueId(), component(
                locked ? "compass.compass-last-seen-locked-actionbar" : "compass.compass-last-seen-actionbar",
                Map.of("player", pick.name(),
                        "distance",
                        String.valueOf(Math.round(holder.getLocation().distance(location))),
                        "reason", reason)));
    }

    private void showNoTarget(Player holder, ItemStack item, int slot, String targetRoleString) {
        spinNeedle(item, holder);
        holder.getInventory().setItem(slot, item);
        compassActionbars.put(holder.getUniqueId(), component("compass.no-target-actionbar",
                Map.of("role", targetRoleString)));
    }

    /** Lodestone offset radius for a spinning needle, in blocks. */
    private static final double SPIN_RADIUS = 8.0;

    /** Points the needle at a rotating offset so it visibly spins. */
    private void spinNeedle(ItemStack item, Player holder) {
        double seconds = plugin.getConfig()
                .getDouble("settings.compass.spin.seconds-per-revolution", 2.0);
        double angle = Math.toRadians(spinAngle(System.currentTimeMillis(), seconds));
        Location origin = holder.getLocation();
        setLodestone(item, new Location(origin.getWorld(),
                origin.getX() + Math.cos(angle) * SPIN_RADIUS, origin.getY(),
                origin.getZ() + Math.sin(angle) * SPIN_RADIUS));
    }

    /**
     * Needle angle in degrees for one revolution per the given seconds; a
     * non-positive period falls back to the 2-second default. Pure for
     * tests.
     */
    static double spinAngle(long nowMillis, double revSeconds) {
        double period = revSeconds > 0 ? revSeconds : 2.0;
        return ((nowMillis / 1000.0) / period * 360.0) % 360.0;
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
            if (isCompass(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTrackableTarget(UUID playerId, Role targetRole, GameInstance instance) {
        if (playerStates.role(playerId) != targetRole) {
            return false;
        }
        if (!instance.isActive(playerId)) {
            return false;
        }
        if (targetRole == Role.SPEEDRUNNER) {
            return playerStates.isActiveSpeedrunner(playerId);
        }
        return true;
    }

    /**
     * Online spectators are mid-respawn (or otherwise out of play): never a
     * last-seen target. Offline players still report their log-out spot.
     * Pure for tests.
     */
    static boolean skipLastSeen(boolean online, GameMode mode) {
        return online && mode == GameMode.SPECTATOR;
    }

    /**
     * Last-seen locations of trackable opponents in the holder's world,
     * nearest first.
     */
    private List<CompassSighting> collectSightings(Player holder, Role targetRole, GameInstance instance) {
        Location origin = holder.getLocation();
        return playerStates.sightings().entrySet().stream()
                .filter(entry -> isTrackableTarget(entry.getKey(), targetRole, instance))
                .filter(entry -> !entry.getKey().equals(holder.getUniqueId()))
                .map(entry -> {
                    Location loc = entry.getValue().get(holder.getWorld().getUID());
                    if (loc == null || loc.getWorld() == null) {
                        return null;
                    }
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (skipLastSeen(player != null,
                            player == null ? null : player.getGameMode())) {
                        return null;
                    }
                    String name = player != null
                            ? player.getName() : playerStates.playerName(entry.getKey());
                    return new CompassSighting(entry.getKey(), name, origin.distance(loc));
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(CompassSighting::distance))
                .toList();
    }

    public boolean shouldReceiveCompass(Role role) {
        return plugin.getConfig()
                .getBoolean("settings.compass.given-to." + role.name().toLowerCase(Locale.ROOT),
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

    public void giveCompass(Player player) {
        if (!shouldReceiveCompass(role(player))) {
            return;
        }
        removeCompasses(player);
        String configured = plugin.getConfig().getString("settings.compass.item", "compass");
        Material material = resolveCompassMaterial(configured);
        if (material == null) {
            plugin.logger().warning("Unknown or placeable settings.compass.item '"
                    + configured + "'. Using minecraft:compass.");
            material = Material.COMPASS;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.nonItalic(component("compass.compass-name")));
        meta.lore(messages.strings("compass.compass-lore").stream()
                .map(messages::parse).map(messages::nonItalic).toList());
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
    private int findCompassSlot(Player player) {
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
            if (isCompass(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    public boolean isCompass(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                        .has(compassKey, PersistentDataType.BYTE);
    }

    public boolean mustBeInventory() {
        return plugin.getConfig().getBoolean("settings.compass.must-be-inventory.enabled", true);
    }

    public void handleRightClick(Player player) {
        if (!plugin.getConfig()
                .getBoolean("settings.compass.right-click.refresh-on-right-click", false)) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (plugin.getConfig()
                .getDouble("settings.compass.right-click.right-click-cooldown", 3.0) * 1000);
        if (!shouldRefresh(now, lastRefresh.getOrDefault(player.getUniqueId(), 0L), cooldownMs)) {
            return;
        }
        // The universal clock stamps at initiation (this click), which also
        // restarts the automatic interval; analysis cooldowns run from here.
        lastRefresh.put(player.getUniqueId(), now);
        if (analyzeEnabled(false)) {
            startAnalysis(player);
            return;
        }
        refreshCompass(player);
    }

    /**
     * Cycles the holder's manual target lock one step: automatic locks
     * the nearest candidate, further clicks advance through the rest,
     * and cycling past the last candidate returns to automatic. No-op
     * unless left-click cycling is enabled and the holder participates
     * in a live match. Locks survive automatic refreshes; the refresh
     * after each click applies the new lock immediately.
     */
    public void handleLeftClick(Player player) {
        if (!plugin.getConfig()
                .getBoolean("settings.compass.left-click.enabled", false)) {
            return;
        }
        if (game == null || !role(player).isParticipant()) {
            return;
        }
        Optional<GameInstance> match = game.instanceOf(player.getUniqueId());
        if (match.isEmpty()) {
            locks.remove(player.getUniqueId());
            return;
        }
        GameInstance instance = match.get();
        Role targetRole = role(player) == Role.HUNTER ? Role.SPEEDRUNNER : Role.HUNTER;
        List<CompassCandidate> opponents = collectOpponents(player, targetRole, instance);
        List<CompassSighting> sightings = collectSightings(player, targetRole, instance);
        int maxTargets = plugin.getConfig()
                .getInt("settings.compass.left-click.max-targets", 5);
        UUID current = locks.get(player.getUniqueId());
        UUID next = CompassPick.cycleLock(opponents, sightings, current, maxTargets);
        if (!Objects.equals(next, current)) {
            sounds.playSound(player, "compass.left-click");
        }
        if (next == null) {
            locks.remove(player.getUniqueId());
        } else {
            locks.put(player.getUniqueId(), next);
        }
        refreshCompass(player);
    }

    /**
     * Purposeful analysis lag before a refresh resolves: shows
     * "Analyzing...", waits out the configured delay, then refreshes.
     * No second analysis starts while one runs. The caller stamps the
     * universal refresh clock at analysis start, so cooldowns run from
     * the click (or auto fire), not from resolution.
     */
    private void startAnalysis(Player holder) {
        UUID id = holder.getUniqueId();
        if (!analyzing.add(id)) {
            return;
        }
        runAnalysisDebuffs(holder);
        compassActionbars.put(id, component("compass.analyzing-actionbar"));
        long delayTicks = analyzeDelayTicks(
                plugin.getConfig().getDouble("settings.compass.analyze.delay-seconds", 1.0));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            analyzing.remove(id);
            refreshCompass(holder);
            if (!inLiveInstance(holder) || !role(holder).isParticipant()) {
                compassActionbars.remove(id);
            }
        }, delayTicks);
    }

    /** Analysis delay in ticks, at least one. Pure for tests. */
    static long analyzeDelayTicks(double delaySeconds) {
        return Math.max(1L, Math.round(delaySeconds * 20.0));
    }

    /**
     * Runs the configured analysis debuff commands for a participant
     * holder: the shared player list plus their own role list, resolved
     * modifier-style and dispatched as console.
     */
    private void runAnalysisDebuffs(Player holder) {
        if (!plugin.getConfig().getBoolean("settings.compass.analyze.debuffs.enabled", false)) {
            return;
        }
        Role holderRole = role(holder);
        if (!holderRole.isParticipant()) {
            return;
        }
        double delaySeconds = plugin.getConfig()
                .getDouble("settings.compass.analyze.delay-seconds", 1.0);
        List<String> commands = new ArrayList<>(plugin.getConfig()
                .getStringList("settings.compass.analyze.debuffs.commands.player"));
        commands.addAll(plugin.getConfig().getStringList(
                "settings.compass.analyze.debuffs.commands." + holderRole.name().toLowerCase(Locale.ROOT)));
        Location location = holder.getLocation();
        for (String command : commands) {
            if (command.isBlank()) {
                continue;
            }
            try {
                String parsed = CommandPlaceholders.replace(
                        CommandPlaceholders.withDuration(command, delaySeconds),
                        holder.getName(), location.getX(), location.getY(), location.getZ());
                if (parsed.startsWith("/")) {
                    parsed = parsed.substring(1);
                }
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            } catch (Exception exception) {
                plugin.logger().severe(
                        "Failed to run analysis debuff command '" + command + "'. Skipping..");
                exception.printStackTrace();
            }
        }
    }

    private boolean analyzeEnabled(boolean auto) {
        return plugin.getConfig().getBoolean(auto
                ? "settings.compass.analyze.auto" : "settings.compass.analyze.right-click", false);
    }

    private Role role(Player player) {
        return playerStates.role(player);
    }

    private Component component(String key) {
        return messages.component(key);
    }

    private Component component(String key, Map<String, String> values) {
        return messages.component(key, values);
    }
}