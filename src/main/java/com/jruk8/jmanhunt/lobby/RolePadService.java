package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.CapLimits;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Stand-on role pads in the lobby world: a player inside a configured pad
 * block's XZ cell and at most PAD_REACH blocks above it is assigned the
 * mapped role, mirroring setplayer (caps, mid-match policy, sounds).
 * Guards exit cheapest-first so idle players cost one map lookup.
 */
public final class RolePadService implements Listener {
    /** How far above a pad block a player may stand and still trigger it. */
    static final int PAD_REACH = 4;

    private final JManhuntPlugin plugin;
    private final LobbyService lobbies;
    private final PlayerStateStore playerStates;
    private final GameManager game;
    private final MessageService messages;
    private final SoundService sounds;
    private final Supplier<String> lobbyWorldName;
    /** Last checked block position per player, packed for one-lookup exits. */
    private final Map<UUID, Long> lastChecked = new HashMap<>();
    /** Pad keys already warned about, so bad materials warn once per run. */
    private final Set<String> warnedMaterials = new HashSet<>();

    public RolePadService(JManhuntPlugin plugin, LobbyService lobbies, PlayerStateStore playerStates,
            GameManager game, MessageService messages, SoundService sounds, Supplier<String> lobbyWorldName) {
        this.plugin = plugin;
        this.lobbies = lobbies;
        this.playerStates = playerStates;
        this.game = game;
        this.messages = messages;
        this.sounds = sounds;
        this.lobbyWorldName = lobbyWorldName;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        check(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        check(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastChecked.remove(event.getPlayer().getUniqueId());
    }

    private void check(Player player) {
        if (!plugin.getConfig().getBoolean("world-engine.role-pads.enabled", true)) {
            return;
        }
        if (!player.getWorld().getName().equals(lobbyWorldName.get())) {
            return;
        }
        Location location = player.getLocation();
        long packed = packBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        Long previous = lastChecked.put(player.getUniqueId(), packed);
        if (previous != null && previous == packed) {
            return;
        }
        Role role = padRoleAt(player.getWorld(), location.getBlockX(),
                location.getBlockY(), location.getBlockZ());
        if (role == null || playerStates.role(player) == role) {
            return;
        }
        assignPadRole(player, role);
    }

    /**
     * Nearest configured pad material at or below the feet block, within
     * reach. Scanning the player's own column enforces the XZ cell.
     */
    private Role padRoleAt(World world, int x, int y, int z) {
        Map<Material, Role> pads = padMaterials();
        if (pads.isEmpty()) {
            return null;
        }
        int bottom = Math.max(y - PAD_REACH, world.getMinHeight());
        for (int scan = y; scan >= bottom; scan--) {
            Role role = pads.get(world.getBlockAt(x, scan, z).getType());
            if (role != null) {
                return role;
            }
        }
        return null;
    }

    private Map<Material, Role> padMaterials() {
        Map<Material, Role> pads = new EnumMap<>(Material.class);
        matchPad(pads, "speedrunner", Role.SPEEDRUNNER);
        matchPad(pads, "hunter", Role.HUNTER);
        matchPad(pads, "afk", Role.AFK);
        matchPad(pads, "spectator", Role.SPECTATOR);
        matchPad(pads, "none", Role.NONE);
        return pads;
    }

    private void matchPad(Map<Material, Role> pads, String key, Role role) {
        String raw = plugin.getConfig().getString("world-engine.role-pads.blocks." + key, "");
        Material material = parsePadMaterial(raw);
        if (material == null) {
            if (raw != null && !raw.isBlank() && warnedMaterials.add(key)) {
                plugin.logger().warning("Unknown role-pad material '" + raw + "' for '" + key
                        + "'; that pad stays inactive until fixed.");
            }
            return;
        }
        warnedMaterials.remove(key);
        pads.put(material, role);
    }

    /**
     * Case-insensitive Bukkit material lookup; null on blank or unknown
     * names. Pure for tests.
     */
    public static Material parsePadMaterial(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Material.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Packs block coordinates into one long for the position cache. Pure for tests. */
    public static long packBlock(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    /**
     * Pad assignment with setplayer semantics: mid-match policy when a
     * match runs, queue caps otherwise, sounds and messages on change.
     * Silent on caps and blocks so moving players never get spammed.
     */
    private void assignPadRole(Player player, Role role) {
        Optional<Lobby> targetLobby = lobbies.lobbyOf(player.getUniqueId());
        Optional<GameInstance> live = targetLobby.flatMap(lobby -> game.instanceForLobby(lobby.id()));
        if (live.isPresent()) {
            if (!lobbies.multiLobbyAllowed()) {
                return;
            }
            MidMatchPolicy policy = MidMatchPolicy.parse(
                    plugin.getConfig().getString("lobbies.mid-match-setplayer", "SUBLOBBY"));
            if (policy.joinsMidMatch(role)
                    && game.joinPlayers(live.get(), List.of(player), role) == 1) {
                return;
            }
            boolean member = live.get().isActive(player.getUniqueId());
            if (!member && !capAllows(targetLobby, role)) {
                return;
            }
            setPadRole(player, role);
            if (!member && !padSilent()) {
                messages.message(player, "manhunt.setplayer-held",
                        Map.of("role", messages.roleName(role)));
            }
            return;
        }
        if (!capAllows(targetLobby, role)) {
            return;
        }
        setPadRole(player, role);
    }

    private void setPadRole(Player player, Role role) {
        Role from = playerStates.role(player);
        playerStates.setRole(player, role);
        plugin.roleTeams().sync(player);
        if (!padSilent()) {
            messages.message(player, "manhunt.role-assigned",
                    Map.of("role", messages.roleName(role)));
            sounds.playNeutralSound(player);
        }
        if (from != role) {
            game.updateAutostartState();
            if (!padSilent()) {
                game.announceRoleChange(player, from, role);
            }
        }
    }

    /** True when pads assign roles quietly. */
    private boolean padSilent() {
        return plugin.getConfig().getBoolean("world-engine.role-pads.silent-role-assignment", false);
    }

    private boolean capAllows(Optional<Lobby> lobby, Role role) {
        if (!role.isParticipant() || lobby.isEmpty()) {
            return true;
        }
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (playerStates.role(online) == role && lobby.get().contains(online.getUniqueId())) {
                count++;
            }
        }
        return CapLimits.allows(count, plugin.getConfig()
                .getInt("lobbies.queue-caps." + role.name().toLowerCase(Locale.ROOT), -1));
    }

}
