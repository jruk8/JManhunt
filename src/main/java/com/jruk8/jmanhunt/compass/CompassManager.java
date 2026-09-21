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
import org.bukkit.World;
import org.bukkit.block.Block;
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
import java.util.concurrent.ThreadLocalRandom;

public final class CompassManager {
    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final SoundService sounds;
    private final PlayerStateStore playerStates;
    private final NamespacedKey compassKey;
    /** Last automatic refresh per holder; right-clicks also stamp this. */
    private final Map<UUID, Long> lastAutoRefresh = new HashMap<>();
    /** Last right-click refresh per holder; the click's own cooldown. */
    private final Map<UUID, Long> lastClickRefresh = new HashMap<>();
    /** Last accepted left-click scroll per holder; throttles held clicks. */
    private final Map<UUID, Long> lastScroll = new HashMap<>();
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
            // The automatic clock only: holders refreshed less than an
            // interval ago keep their fresh target. Right-clicks stamp
            // this clock too, so each click restarts the interval.
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
                        if (!shouldRefresh(now, lastAutoRefresh.getOrDefault(id, 0L), intervalMs)) {
                            return;
                        }
                        lastAutoRefresh.put(id, now);
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
        Role targetRole = holderRole == Role.HUNTER ? Role.SPEEDRUNNER : Role.HUNTER;
        String targetRoleString = messages.roleName(targetRole);
        if (holder.getGameMode() == GameMode.SPECTATOR) {
            showNoTarget(holder, item, slot, targetRoleString);
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

        List<CompassCandidate> opponents = collectOpponents(holder, targetRole, instance);
        List<CompassSighting> sightings = collectSightings(holder, targetRole, instance);
        UUID lockedId = locks.get(holder.getUniqueId());
        boolean locked = lockedId != null;
        if (locked) {
            List<CompassCandidate> lockedOpponents = opponents.stream()
                    .filter(candidate -> candidate.id().equals(lockedId)).toList();
            List<CompassSighting> lockedSightings = sightings.stream()
                    .filter(sighting -> sighting.ownerId().equals(lockedId)).toList();
            if (lockedOpponents.isEmpty() && lockedSightings.isEmpty()) {
                locks.remove(holder.getUniqueId());
                locked = false;
            } else {
                opponents = lockedOpponents;
                sightings = lockedSightings;
            }
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
        if (pick.kind() != CompassPick.Kind.NONE && badSignalForPick(holder, pick)) {
            showBadSignal(holder, item, slot);
            return;
        }
        switch (pick.kind()) {
            case TRACK_PLAYER -> trackPlayer(holder, item, slot, pick, targetRoleString, locked);
            case NEARBY -> {
                spinNeedle(item, holder);
                holder.getInventory().setItem(slot, item);
                compassActionbars.put(holder.getUniqueId(), component("compass.nearby-actionbar",
                        Map.of("player", pick.name())));
            }
            case TRACK_SIGHTING -> trackSighting(holder, item, slot, pick, targetRoleString, locked);
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

    private void showBadSignal(Player holder, ItemStack item, int slot) {
        spinNeedle(item, holder);
        holder.getInventory().setItem(slot, item);
        compassActionbars.put(holder.getUniqueId(), component("compass.bad-signal-actionbar"));
    }

    /**
     * True when signal interference fails this tracking attempt: the
     * holder's spot (and, with two-way, the target's spot) resolves to a
     * bad signal that the bypass roll does not save.
     */
    private boolean badSignal(Player holder, Location target, SignalInterference.Config interference,
            Boolean hasLineOfSight) {
        if (!plugin.getConfig().getBoolean("settings.compass.signal-interference.enabled", false)) {
            return false;
        }
        boolean ignoreTransparent = plugin.getConfig().getBoolean(
                "settings.compass.signal-interference.underground.ignore-transparent", true);
        SignalInterference.Snapshot targetSnapshot = interference.twoWay()
                ? targetSnapshot(target, ignoreTransparent) : null;
        return SignalInterference.badSignal(signalSnapshot(holder.getLocation(), ignoreTransparent),
                targetSnapshot, interference, ThreadLocalRandom.current().nextDouble(), hasLineOfSight);
    }

    /**
     * Interference verdict for a resolved pick. Sightings use their
     * recorded location; every live kind uses the player's location, or
     * none when they logged out between selection and this check. Only
     * live targets get a line-of-sight reading.
     */
    private boolean badSignalForPick(Player holder, CompassPick pick) {
        SignalInterference.Config interference = interferenceConfig();
        Location target = null;
        Player seen = null;
        if (pick.kind() == CompassPick.Kind.TRACK_SIGHTING) {
            target = playerStates.sightings().getOrDefault(pick.id(), Map.of())
                    .get(holder.getWorld().getUID());
        } else if (pick.id() != null) {
            seen = Bukkit.getPlayer(pick.id());
            target = seen == null ? null : seen.getLocation();
        }
        Boolean sight = interference.losEnabled() ? lineOfSight(holder, seen, interference) : null;
        return badSignal(holder, target, interference, sight);
    }

    /**
     * Interference verdict for a scroll attempt, evaluated against the
     * nearest candidate: live opponents first, then sightings.
     */
    private boolean badSignalForScroll(Player holder, List<CompassCandidate> opponents,
            List<CompassSighting> sightings) {
        SignalInterference.Config interference = interferenceConfig();
        Location target = null;
        Player seen = null;
        if (!opponents.isEmpty()) {
            seen = Bukkit.getPlayer(opponents.get(0).id());
            target = seen == null ? null : seen.getLocation();
        }
        if (target == null && !sightings.isEmpty()) {
            target = playerStates.sightings().getOrDefault(sightings.get(0).ownerId(), Map.of())
                    .get(holder.getWorld().getUID());
        }
        Boolean sight = interference.losEnabled() ? lineOfSight(holder, seen, interference) : null;
        return badSignal(holder, target, interference, sight);
    }

    /**
     * Eye-to-eye sight from the holder to a live target, or null when no
     * ray applies: no target, another world, or a target the holder
     * cannot share a world with.
     */
    private Boolean lineOfSight(Player holder, Player target, SignalInterference.Config interference) {
        if (target == null || !target.getWorld().equals(holder.getWorld())) {
            return null;
        }
        Location from = holder.getEyeLocation();
        Location to = target.getEyeLocation();
        World world = holder.getWorld();
        boolean clear = rayClear(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(),
                interference.losMaxDistance(),
                (x, y, z) -> y >= world.getMinHeight() && y < world.getMaxHeight()
                        && world.isChunkLoaded(x >> 4, z >> 4)
                        && world.getBlockAt(x, y, z).getType().isOccluding());
        return clear;
    }

    private SignalInterference.Config interferenceConfig() {
        String base = "settings.compass.signal-interference.";
        Set<SignalInterference.Weather> during = new HashSet<>();
        for (String raw : plugin.getConfig().getStringList(base + "weather.interfere-during")) {
            try {
                during.add(SignalInterference.Weather.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                // Unknown buckets are ignored, so one typo cannot break refreshes.
            }
        }
        SignalInterference.InterfereWhen when;
        try {
            String raw = plugin.getConfig().getString(base + "light-level.interfere-when", "ONE_UNMET");
            when = SignalInterference.InterfereWhen.valueOf(
                    (raw == null ? "ONE_UNMET" : raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            when = SignalInterference.InterfereWhen.ONE_UNMET;
        }
        SignalInterference.InterfereWhenVisible losWhen;
        try {
            String raw = plugin.getConfig().getString(base + "line-of-sight.interfere-when", "VISIBLE");
            losWhen = SignalInterference.InterfereWhenVisible.valueOf(
                    (raw == null ? "VISIBLE" : raw).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            losWhen = SignalInterference.InterfereWhenVisible.VISIBLE;
        }
        return new SignalInterference.Config(
                plugin.getConfig().getBoolean(base + "light-level.enabled", false),
                plugin.getConfig().getInt(base + "light-level.min-sky-light", 10),
                plugin.getConfig().getInt(base + "light-level.min-block-light", 5),
                when,
                plugin.getConfig().getBoolean(base + "underground.enabled", false),
                plugin.getConfig().getInt(base + "underground.max-blocks-above", 3),
                plugin.getConfig().getBoolean(base + "underwater.enabled", true),
                plugin.getConfig().getInt(base + "underwater.max-blocks-above", 2),
                plugin.getConfig().getBoolean(base + "altitude.enabled", false),
                plugin.getConfig().getInt(base + "altitude.min-y", -20),
                plugin.getConfig().getInt(base + "altitude.max-y", 120),
                plugin.getConfig().getBoolean(base + "weather.enabled", false),
                during,
                plugin.getConfig().getBoolean(base + "biome.enabled", false),
                new HashSet<>(plugin.getConfig().getStringList(base + "biome.interfere-in")),
                plugin.getConfig().getBoolean(base + "line-of-sight.enabled", false),
                losWhen,
                plugin.getConfig().getInt(base + "line-of-sight.max-ray-distance", 300),
                plugin.getConfig().getInt(base + "required-to-fail", 1),
                plugin.getConfig().getBoolean(base + "two-way", false),
                plugin.getConfig().getDouble(base + "chance-to-bypass", 0.0));
    }

    /** Signal snapshot for a spot, read from its feet block. */
    private SignalInterference.Snapshot signalSnapshot(Location location, boolean ignoreTransparent) {
        Block block = location.getBlock();
        World world = block.getWorld();
        SignalInterference.Weather weather;
        if (world.isThundering()) {
            weather = SignalInterference.Weather.STORM;
        } else if (world.hasStorm()) {
            weather = SignalInterference.Weather.RAIN;
        } else {
            weather = SignalInterference.Weather.CLEAR;
        }
        return new SignalInterference.Snapshot(
                block.getLightFromSky(),
                block.getLightFromBlocks(),
                world.getEnvironment() == World.Environment.NORMAL,
                solidBlocksAbove(block, ignoreTransparent),
                fluidBlocksAbove(block),
                block.getY(),
                weather,
                block.getBiome().getKey().toString());
    }

    /**
     * Target-side snapshot for two-way checks, or null when the spot's
     * chunk is not loaded. An unloaded sighting never fails the target
     * side, so refreshes never force chunk loads.
     */
    private SignalInterference.Snapshot targetSnapshot(Location target, boolean ignoreTransparent) {
        if (target == null || target.getWorld() == null
                || !target.getWorld().isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
            return null;
        }
        return signalSnapshot(target, ignoreTransparent);
    }

    /** Blocks strictly above the feet block, capped for cheap reads. */
    private static final int MAX_ABOVE_COUNT = 380;

    private int solidBlocksAbove(Block feet, boolean ignoreTransparent) {
        World world = feet.getWorld();
        int count = 0;
        for (int y = feet.getY() + 1; y < world.getMaxHeight(); y++) {
            Block block = world.getBlockAt(feet.getX(), y, feet.getZ());
            if (countsAsCover(block.isSolid(), block.getType().isOccluding(), ignoreTransparent)) {
                count++;
                if (count > MAX_ABOVE_COUNT) {
                    break;
                }
            }
        }
        return count;
    }

    /**
     * True when a block above the feet counts as cover: any solid block,
     * or only whole occluding ones when transparent blocks are ignored.
     * Pure for tests.
     */
    static boolean countsAsCover(boolean solid, boolean occluding, boolean ignoreTransparent) {
        if (!solid) {
            return false;
        }
        return !ignoreTransparent || occluding;
    }

    /** Water or lava blocks strictly above the feet block. */
    private int fluidBlocksAbove(Block feet) {
        World world = feet.getWorld();
        int count = 0;
        for (int y = feet.getY() + 1; y < world.getMaxHeight(); y++) {
            Material type = world.getBlockAt(feet.getX(), y, feet.getZ()).getType();
            if (type == Material.WATER || type == Material.LAVA) {
                count++;
                if (count > MAX_ABOVE_COUNT) {
                    break;
                }
            }
        }
        return count;
    }

    /** Answers whether one block coordinate blocks sight. */
    interface SightProbe {
        boolean blocks(int x, int y, int z);
    }

    /** Ray sampling step in blocks; whole cubes cannot hide between samples. */
    static final double LOS_STEP = 0.5;

    /**
     * Eye-to-eye sight along one ray: false past maxDistance or when any
     * sampled block between the endpoints blocks sight. The start block
     * is skipped, the end block counts. Pure for tests.
     */
    static boolean rayClear(double x0, double y0, double z0, double x1, double y1, double z1,
            double maxDistance, SightProbe probe) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double dz = z1 - z0;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance > maxDistance) {
            return false;
        }
        int steps = Math.max(1, (int) Math.ceil(distance / LOS_STEP));
        for (int step = 1; step <= steps; step++) {
            double fraction = (double) step / steps;
            int x = (int) Math.floor(x0 + dx * fraction);
            int y = (int) Math.floor(y0 + dy * fraction);
            int z = (int) Math.floor(z0 + dz * fraction);
            if (probe.blocks(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Spins the needle by pointing at spawn of a dimension the holder is
     * not in, which the client cannot resolve to a direction. With no
     * other dimension loaded, the lodestone is cleared instead, so the
     * compass falls back to vanilla behavior.
     */
    private void spinNeedle(ItemStack item, Player holder) {
        World.Environment here = holder.getWorld().getEnvironment();
        World other = Bukkit.getWorlds().stream()
                .filter(world -> world.getEnvironment() != here)
                .findFirst()
                .orElse(null);
        if (other == null) {
            clearLodestone(item);
            return;
        }
        setLodestone(item, new Location(other, 0.0, 64.0, 0.0));
    }

    private void setLodestone(ItemStack item, Location location) {
        if (item != null && item.getItemMeta() instanceof CompassMeta meta) {
            meta.setLodestone(location);
            meta.setLodestoneTracked(false);
            item.setItemMeta(meta);
        }
    }

    private void clearLodestone(ItemStack item) {
        if (item != null && item.getItemMeta() instanceof CompassMeta meta) {
            meta.clearLodestone();
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
        applyCompassIdentity(item, role(player));
        ItemMeta meta = item.getItemMeta();
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

    /**
     * True when the player may keep a compass: a hunter or speedrunner
     * inside a live match. Everyone else loses picked-up compasses.
     */
    public boolean mayHoldCompass(Player player) {
        Role holderRole = role(player);
        if (holderRole != Role.HUNTER && holderRole != Role.SPEEDRUNNER) {
            return false;
        }
        return game != null && game.instanceOf(player.getUniqueId()).isPresent();
    }

    /** Restamps the holder's compass with their role's name and lore. */
    public void refreshCompassIdentity(Player player) {
        int slot = findCompassSlot(player);
        if (slot < 0) {
            return;
        }
        ItemStack item = player.getInventory().getItem(slot);
        if (!isCompass(item)) {
            return;
        }
        applyCompassIdentity(item, role(player));
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

    public boolean mustBeInventory() {
        return plugin.getConfig().getBoolean("settings.compass.must-be-inventory.enabled", true);
    }

    public void handleRightClick(Player player) {
        if (!plugin.getConfig()
                .getBoolean("settings.compass.right-click.refresh-on-right-click", false)) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (plugin.getConfig()
                .getDouble("settings.compass.right-click.right-click-cooldown", 3.0) * 1000);
        if (!shouldRefresh(now, lastClickRefresh.getOrDefault(player.getUniqueId(), 0L), cooldownMs)) {
            return;
        }
        // Clicks run on their own cooldown, so a fresh automatic refresh
        // never blocks them; each click also stamps the automatic clock
        // at initiation, restarting the interval from here.
        lastClickRefresh.put(player.getUniqueId(), now);
        lastAutoRefresh.put(player.getUniqueId(), now);
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
     * in a live match. Clicks inside the scroll cooldown are ignored,
     * which also stops held clicks from scrolling; scrolling is refused
     * with one or fewer candidates, during bad signal, and during
     * analysis. Locks survive automatic refreshes; the refresh after
     * each click applies the new lock immediately.
     */
    public void handleLeftClick(Player player) {
        if (!plugin.getConfig()
                .getBoolean("settings.compass.left-click.enabled", false)) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (analyzing.contains(player.getUniqueId())) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (Math.max(0.0, plugin.getConfig()
                .getDouble("settings.compass.left-click.scroll-cooldown", 0.5)) * 1000);
        if (!shouldRefresh(now, lastScroll.getOrDefault(player.getUniqueId(), 0L), cooldownMs)) {
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
        if (CompassPick.orderedCandidates(opponents, sightings, maxTargets).size() <= 1) {
            locks.remove(player.getUniqueId());
            refreshCompass(player);
            return;
        }
        if (badSignalForScroll(player, opponents, sightings)) {
            refreshCompass(player);
            return;
        }
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
        lastScroll.put(player.getUniqueId(), now);
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