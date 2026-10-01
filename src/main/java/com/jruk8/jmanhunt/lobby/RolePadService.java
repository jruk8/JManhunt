package com.jruk8.jmanhunt.lobby;

import com.jruk8.jmanhunt.config.LobbiesConfig;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.CapLimits;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import com.jruk8.jmanhunt.player.RoleTeamService;
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

    /** Lobby service, settings, and world name. */
    public record RolePadLobby(LobbyService lobbies, LobbiesConfig lobbySettings,
            Supplier<String> lobbyWorldName) {
    }

    /** Assignment chat plus texts.sounds(). */
    public record RolePadTexts(MessageService messages, ManhuntMessages manhunt,
            SoundService sounds) {
    }

    /** Role plus fake-spectator reads. */
    public record RolePadPlayers(PlayerStateStore states, FakeSpectatorService fakes) {
    }

    /** Logger plus role teams. */
    public record RolePadEdge(JManhuntLogger log, RoleTeamService roleTeams) {
    }

    private final RolePadLobby lobby;
    private final RolePadTexts texts;
    private final RolePadPlayers players;
    private final GameManager game;
    private final RolePadEdge edge;
    /** Last checked block position per player, packed for one-lookup exits. */
    private final Map<UUID, Long> lastChecked = new HashMap<>();
    /** Pad keys already warned about, so bad materials warn once per run. */
    private final Set<String> warnedMaterials = new HashSet<>();

    public RolePadService(RolePadLobby lobby, RolePadTexts texts, RolePadPlayers players,
            GameManager game, RolePadEdge edge) {
        this.lobby = lobby;
        this.texts = texts;
        this.players = players;
        this.game = game;
        this.edge = edge;
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
        if (!lobby.lobbySettings().getRolePads().isEnabled()) {
            return;
        }
        if (!player.getWorld().getName().equals(lobby.lobbyWorldName().get())) {
            return;
        }
        // Fake spectators are watching: standing on a pad must never
        // pull them back into a playing role. The JManhunt role is
        // irrelevant here; only fake spectator mode gates pads.
        if (players.fakes().isFakeSpectator(player)) {
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
        if (role == null || players.states().role(player) == role) {
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
        var blocks = lobby.lobbySettings().getRolePads().getBlocks();
        String raw = switch (role) {
            case SPEEDRUNNER -> blocks.getSpeedrunner();
            case HUNTER -> blocks.getHunter();
            case AFK -> blocks.getAfk();
            case SPECTATOR -> blocks.getSpectator();
            case NONE -> blocks.getNone();
        };
        Material material = parsePadMaterial(raw);
        if (material == null) {
            if (raw != null && !raw.isBlank() && warnedMaterials.add(key)) {
                edge.log().warning("Unknown role-pad material '" + raw + "' for '" + key
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
        Optional<Lobby> targetLobby = lobby.lobbies().lobbyOf(player.getUniqueId());
        Optional<GameInstance> live = targetLobby.flatMap(lobby -> game.instanceForLobby(lobby.id()));
        if (live.isPresent()) {
            if (!lobby.lobbies().multiLobbyAllowed()) {
                return;
            }
            MidMatchPolicy policy = lobby.lobbies().midMatchPolicy();
            GameInstance target = targetLobby.map(lobby ->
                    game.midMatchJoinTarget(policy, lobby.id(), live.get(), role)).orElse(live.get());
            if (policy.joinsMidMatch(role)
                    && game.joinPlayers(target, List.of(player), role) == 1) {
                return;
            }
            boolean member = live.get().isActive(player.getUniqueId());
            if (!member && !capAllows(targetLobby, role)) {
                return;
            }
            setPadRole(player, role);
            if (!member && !padSilent()) {
                texts.messages().messageRaw(player, policy.queueMessageTemplate(texts.manhunt()),
                        Map.of("role", texts.messages().roleName(role)));
            }
            return;
        }
        if (!capAllows(targetLobby, role)) {
            return;
        }
        setPadRole(player, role);
    }

    private void setPadRole(Player player, Role role) {
        Role from = players.states().role(player);
        players.states().setRole(player, role);
        edge.roleTeams().sync(player);
        if (!padSilent()) {
            texts.messages().messageRaw(player, texts.manhunt().getRoleAssigned(),
                    Map.of("role", texts.messages().roleName(role)));
            texts.sounds().playNeutralSound(player);
        }
        if (from != role) {
            game.updateAutostartState();
            if (!padSilent()) {
                game.messaging().announceRoleChange(player, from, role);
            }
        }
    }

    /** True when pads assign roles quietly. */
    private boolean padSilent() {
        return lobby.lobbySettings().getRolePads().isSilentRoleAssignment();
    }

    private boolean capAllows(Optional<Lobby> lobby, Role role) {
        if (!role.isParticipant() || lobby.isEmpty()) {
            return true;
        }
        int count = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (players.states().role(online) == role && lobby.get().contains(online.getUniqueId())
                    && game.instanceOf(online.getUniqueId()).isEmpty()) {
                count++;
            }
        }
        var caps = this.lobby.lobbySettings().getQueueCaps();
        int cap = role == Role.HUNTER ? caps.getHunter() : caps.getSpeedrunner();
        return CapLimits.allows(count, cap);
    }

}
