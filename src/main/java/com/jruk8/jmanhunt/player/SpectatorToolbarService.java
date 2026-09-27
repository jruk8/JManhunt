package com.jruk8.jmanhunt.player;

import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.OverrideService;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Spectator hotbar toolbar: inventory snapshots, layout buttons, match
 * swapping, player teleporting, and lock-on follow.
 *
 * <p>The toolbar deploys only for the SPECTATOR role on fake-spectator
 * enable: headstart holds, death watches, and NONE watchers keep their
 * inventories. Snapshots live in memory; quit always restores, and join
 * strips toolbar items left behind by a crash.
 */
public final class SpectatorToolbarService implements FakeSpectatorService.ModeListener {

    /** Permission to swap to another lobby's match from the toolbar. */
    public static final String SWAP_LOBBY_PERMISSION = "jmanhunt.spectator.swaplobby";

    /** Hotbar layout path: 9 characters, one per slot 0 to 8. */
    public static final String LAYOUT_PATH = "settings.players.spectator.toolbar.layout";

    /** Lock-on follow toggle path. */
    public static final String LOCK_ON_PATH = "settings.players.spectator.toolbar.lock-on";

    /** Lock-on follow distance path, in blocks. */
    public static final String TP_DISTANCE_PATH = "settings.players.spectator.toolbar.tp-distance";

    /** Fallback layout when the configured one is not 9 characters. */
    public static final String DEFAULT_LAYOUT = "cp######b";

    /** Hotbar button kind. */
    public enum ToolbarButton {
        LOBBIES,
        PLAYERS,
        BACK,
        EMPTY
    }

    /** Snapshot of a spectator's real inventory while the toolbar is out. */
    public record InventorySnapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offhand) {
    }

    /**
     * One teleport or lock candidate. The active and runnerAlive flags
     * resolve true for targets outside any match, so lobby players only
     * need their role, and match players additionally need to be alive
     * and assigned.
     */
    public record SpectateCandidate(UUID id, Role role, boolean active,
            boolean runnerAlive, boolean fake, boolean online) {
    }

    /** One match-browser row. */
    public record MatchEntry(long matchId, String label, int runners, int hunters,
            boolean subLobby, boolean current) {
    }

    /** One player-browser row. */
    public record PlayerEntry(UUID id, String name, Role role, boolean locked) {
    }

    private final OverrideService overrides;
    private final MessageService messages;
    private final SoundService sounds;
    private final PlayerStateStore playerStates;
    private final FakeSpectatorService fakes;
    private final GameManager game;
    private final LobbyService lobbies;
    private final NamespacedKey toolbarKey;
    private final Map<UUID, InventorySnapshot> snapshots = new HashMap<>();
    private final Map<UUID, UUID> locks = new HashMap<>();

    public SpectatorToolbarService(OverrideService overrides, MessageService messages,
            SoundService sounds, PlayerStateStore playerStates, FakeSpectatorService fakes,
            GameManager game, LobbyService lobbies, NamespacedKey toolbarKey) {
        this.overrides = overrides;
        this.messages = messages;
        this.sounds = sounds;
        this.playerStates = playerStates;
        this.fakes = fakes;
        this.game = game;
        this.lobbies = lobbies;
        this.toolbarKey = toolbarKey;
        fakes.addModeListener(this);
    }

    /**
     * Parses a layout into 9 hotbar buttons: c browses matches, p
     * teleports to players, b returns to the lobby, # is empty, and
     * anything else is empty too. Anything but a 9-character string
     * falls back to the default layout. Pure for tests.
     */
    public static ToolbarButton[] parseLayout(String layout) {
        String effective = layout != null && layout.length() == 9 ? layout : DEFAULT_LAYOUT;
        ToolbarButton[] buttons = new ToolbarButton[9];
        for (int slot = 0; slot < 9; slot++) {
            buttons[slot] = switch (effective.charAt(slot)) {
                case 'c' -> ToolbarButton.LOBBIES;
                case 'p' -> ToolbarButton.PLAYERS;
                case 'b' -> ToolbarButton.BACK;
                default -> ToolbarButton.EMPTY;
            };
        }
        return buttons;
    }

    /**
     * True when the candidate teleports or locks: online, assigned,
     * out of fake spectator mode (respawning, held, or watching
     * players never qualify), holding a participant role, and alive
     * when a speedrunner. Pure for tests.
     */
    public static boolean targetValid(SpectateCandidate candidate) {
        if (candidate == null || !candidate.online() || !candidate.active()
                || candidate.fake() || !candidate.role().isParticipant()) {
            return false;
        }
        return candidate.role() != Role.SPEEDRUNNER || candidate.runnerAlive();
    }

    /**
     * Teleport-target pick: the first valid speedrunner, else the first
     * valid hunter, else empty for the cell-center fallback. Pure for
     * tests.
     */
    public static Optional<UUID> pickTeleportTarget(List<SpectateCandidate> candidates) {
        List<SpectateCandidate> valid = candidates.stream().filter(
                SpectatorToolbarService::targetValid).toList();
        return valid.stream().filter(candidate -> candidate.role() == Role.SPEEDRUNNER)
                .map(SpectateCandidate::id).findFirst()
                .or(() -> valid.stream().filter(candidate -> candidate.role() == Role.HUNTER)
                        .map(SpectateCandidate::id).findFirst());
    }

    @Override
    public void onModeChange(Player player, boolean enabled) {
        if (enabled) {
            if (playerStates.role(player) == Role.SPECTATOR) {
                deploy(player);
            }
            return;
        }
        restore(player);
        clearLock(player.getUniqueId());
    }

    /**
     * Join recovery: with no live deployment, toolbar items in the
     * inventory are crash leftovers and go, and any surviving snapshot
     * restores.
     */
    public void handleJoin(Player player) {
        if (isDeployed(player)) {
            return;
        }
        stripToolbarItems(player);
        restore(player);
    }

    /** True when the toolbar is currently deployed for the player. */
    public boolean isDeployed(Player player) {
        return player != null && snapshots.containsKey(player.getUniqueId());
    }

    /** True when the stack is a toolbar button. Null-safe. */
    public boolean isToolbarItem(ItemStack item) {
        return toolbarButton(item).isPresent();
    }

    /** Toolbar button letter of the stack, or empty when it has none. Null-safe. */
    public Optional<Character> toolbarButton(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String button = item.getItemMeta().getPersistentDataContainer()
                .get(toolbarKey, PersistentDataType.STRING);
        if (button == null || button.length() != 1) {
            return Optional.empty();
        }
        return Optional.of(button.charAt(0));
    }

    /** Drops the spectator's lock silently. */
    public void clearLock(UUID spectatorId) {
        locks.remove(spectatorId);
    }

    /**
     * Exits lock-on follow with chat feedback and a neutral sound, and
     * clears the follow actionbar. False when not following anyone.
     */
    public boolean exitFollow(Player spectator) {
        UUID targetId = locks.remove(spectator.getUniqueId());
        if (targetId == null) {
            return false;
        }
        Player target = Bukkit.getPlayer(targetId);
        String name = target != null ? target.getName() : playerStates.playerName(targetId);
        messages.message(spectator, "spectator.follow-exited", Map.of("player", name));
        sounds.playNeutralSound(spectator);
        spectator.sendActionBar(Component.empty());
        return true;
    }

    /** Locked target of the spectator, or null when none. */
    public UUID lockedTarget(UUID spectatorId) {
        return locks.get(spectatorId);
    }

    /** Origin lobby of the spectator's match, or null outside matches. */
    Integer lobbyOf(Player spectator) {
        return game.lobbyOfPlayer(spectator.getUniqueId());
    }

    /** Effective hotbar layout for the spectator. */
    ToolbarButton[] layout(Player spectator) {
        return parseLayout(overrides.getString(lobbyOf(spectator), LAYOUT_PATH, DEFAULT_LAYOUT));
    }

    /** True when lock-on follow applies to the spectator. */
    boolean lockOn(Player spectator) {
        return overrides.getBoolean(lobbyOf(spectator), LOCK_ON_PATH, true);
    }

    /** Follow teleport distance for the spectator, at least 1 block. */
    int tpDistance(Player spectator) {
        return Math.max(1, overrides.getInt(lobbyOf(spectator), TP_DISTANCE_PATH, 25));
    }

    /**
     * Shared lock tick, every 5 ticks: pulls locked spectators back
     * within range of their target and shows the follow actionbar.
     * Dead, logged out, or otherwise invalid targets exit the follow
     * with feedback; unloads and disabled lock-on drop silently.
     */
    public void tickLocks() {
        for (Map.Entry<UUID, UUID> entry : List.copyOf(locks.entrySet())) {
            UUID spectatorId = entry.getKey();
            Player spectator = Bukkit.getPlayer(spectatorId);
            if (spectator == null || !lockOn(spectator)) {
                locks.remove(spectatorId);
                continue;
            }
            UUID targetId = entry.getValue();
            Player target = Bukkit.getPlayer(targetId);
            if (target == null || !targetValid(candidateOf(target))) {
                exitFollow(spectator);
                continue;
            }
            Location from = spectator.getLocation();
            Location to = target.getLocation();
            if (!from.getWorld().equals(to.getWorld()) || from.distance(to) > tpDistance(spectator)) {
                spectator.teleport(to);
            }
            spectator.sendActionBar(messages.component("spectator.following-actionbar",
                    Map.of("role", messages.roleName(playerStates.role(target)),
                            "player", target.getName())));
        }
    }

    /**
     * Running matches for the browser: sublobby matches glow and sort
     * first, then by match id.
     */
    public List<MatchEntry> matchEntries(Player spectator) {
        long currentId = game.instanceOf(spectator.getUniqueId())
                .map(GameInstance::matchId).orElse(-1L);
        return game.liveInstances().stream()
                .filter(instance -> instance.active() && !instance.ending())
                .map(instance -> new MatchEntry(instance.matchId(), instance.lobbyTag(),
                        game.activeRunnerCount(instance), game.activeHunterCount(instance),
                        instance.subLobby() != null, instance.matchId() == currentId))
                .sorted(Comparator.comparing(MatchEntry::subLobby).reversed()
                        .thenComparingLong(MatchEntry::matchId))
                .toList();
    }

    /**
     * Moves a spectator into another match: same-lobby targets (including
     * sublobbies) teleport straight in, cross-lobby swaps need the swap
     * permission. Lands on the teleport-priority target. Returns false
     * with feedback when the move is refused.
     */
    public boolean swapSpectator(Player spectator, long matchId) {
        Optional<GameInstance> target = game.instance(matchId)
                .filter(instance -> instance.active() && !instance.ending());
        if (target.isEmpty()) {
            messages.message(spectator, "spectator.match-gone");
            spectator.closeInventory();
            return false;
        }
        Optional<GameInstance> current = game.instanceOf(spectator.getUniqueId());
        if (current.isPresent() && current.get().matchId() == matchId) {
            spectator.closeInventory();
            return true;
        }
        Integer fromLobby = current.map(GameInstance::originLobbyId)
                .or(() -> lobbies.lobbyOf(spectator.getUniqueId())
                        .map(lobby -> lobby.id()))
                .orElse(null);
        if (fromLobby != null && fromLobby != target.get().originLobbyId()
                && !spectator.hasPermission(SWAP_LOBBY_PERMISSION)) {
            messages.message(spectator, "command.no-permission");
            return false;
        }
        current.ifPresent(old -> game.leaveMatch(old, List.of(spectator), false));
        if (game.joinPlayers(target.get(), List.of(spectator), Role.SPECTATOR) == 0) {
            messages.message(spectator, "spectator.match-gone");
            spectator.closeInventory();
            return false;
        }
        teleportToPriority(spectator, target.get());
        clearLock(spectator.getUniqueId());
        spectator.closeInventory();
        return true;
    }

    /** Teleports to the priority target of a match, else its cell center. */
    void teleportToPriority(Player spectator, GameInstance target) {
        List<SpectateCandidate> candidates = new ArrayList<>();
        for (Player player : game.onlineActivePlayers(target)) {
            candidates.add(candidateOf(player, target));
        }
        Optional<Player> pick = pickTeleportTarget(candidates).map(Bukkit::getPlayer);
        if (pick.isPresent()) {
            spectator.teleport(pick.get().getLocation());
            return;
        }
        target.cellIndex().ifPresent(cell -> game.cellCenter(cell)
                .ifPresent(spectator::teleport));
    }

    /**
     * Spectateable players: online speedrunners and hunters of the
     * spectator's match, or of their lobby when outside matches.
     * Runners sort before hunters, then by name.
     */
    public List<PlayerEntry> playerEntries(Player spectator) {
        List<Player> pool = game.instanceOf(spectator.getUniqueId())
                .map(game::onlineActivePlayers)
                .orElseGet(() -> lobbyPool(spectator));
        UUID locked = lockedTarget(spectator.getUniqueId());
        return pool.stream()
                .filter(player -> playerStates.role(player).isParticipant())
                .map(player -> new PlayerEntry(player.getUniqueId(), player.getName(),
                        playerStates.role(player),
                        player.getUniqueId().equals(locked)))
                .sorted(Comparator.comparing((PlayerEntry entry) -> entry.role()
                        != Role.SPEEDRUNNER).thenComparing(entry -> entry.name()
                                .toLowerCase(Locale.ROOT)))
                .toList();
    }

    /**
     * Teleports to a player and locks on when lock-on applies. Always
     * chats the spectate confirmation; without a lock the follow
     * actionbar stays empty since there is nothing to follow.
     */
    public boolean teleportAndLock(Player spectator, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            spectator.closeInventory();
            return false;
        }
        spectator.teleport(target.getLocation());
        if (lockOn(spectator) && targetValid(candidateOf(target))) {
            locks.put(spectator.getUniqueId(), targetId);
        } else {
            locks.remove(spectator.getUniqueId());
        }
        messages.message(spectator, "spectator.now-spectating",
                Map.of("role", messages.roleName(playerStates.role(target)),
                        "player", target.getName()));
        spectator.closeInventory();
        return true;
    }

    /**
     * Back-to-lobby exit: leaves the match under NONE, or just unnones
     * outside matches. The role change unwinds fake mode and the
     * toolbar through the mode listener.
     */
    public void returnToLobby(Player spectator) {
        Optional<GameInstance> current = game.instanceOf(spectator.getUniqueId());
        if (current.isPresent()) {
            game.leaveMatchToLobby(current.get(), spectator, Role.NONE);
            return;
        }
        lobbies.lobbyOf(spectator.getUniqueId()).ifPresent(lobby -> {
            game.teleportToLobby(List.of(spectator), lobby.id());
            game.setSpawnToLobbyQuiet(List.of(spectator), lobby.id());
        });
        playerStates.setRole(spectator, Role.NONE);
    }

    private List<Player> lobbyPool(Player spectator) {
        Optional<Integer> lobbyId = lobbies.lobbyOf(spectator.getUniqueId()).map(lobby ->
                lobby.id());
        if (lobbyId.isEmpty()) {
            return List.of();
        }
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> lobbies.lobbyOf(player.getUniqueId())
                        .map(lobby -> lobby.id() == lobbyId.get()).orElse(false))
                .map(player -> (Player) player)
                .toList();
    }

    private SpectateCandidate candidateOf(Player player) {
        return candidateOf(player, game.instanceOf(player.getUniqueId()).orElse(null));
    }

    private SpectateCandidate candidateOf(Player player, GameInstance instance) {
        UUID id = player.getUniqueId();
        Role role = playerStates.role(player);
        boolean active = instance == null || instance.isActive(id);
        boolean runnerAlive = role != Role.SPEEDRUNNER
                || instance == null || playerStates.isActiveSpeedrunner(id);
        return new SpectateCandidate(id, role, active, runnerAlive,
                fakes.isFakeSpectator(player), player.isOnline());
    }

    private void deploy(Player player) {
        if (snapshots.containsKey(player.getUniqueId())) {
            return;
        }
        stripToolbarItems(player);
        PlayerInventory inventory = player.getInventory();
        snapshots.put(player.getUniqueId(), new InventorySnapshot(
                cloneAll(inventory.getContents()), cloneAll(inventory.getArmorContents()),
                cloneOne(inventory.getItemInOffHand())));
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
        ToolbarButton[] buttons = layout(player);
        for (int slot = 0; slot < buttons.length; slot++) {
            ItemStack button = buttonItem(buttons[slot]);
            if (button != null) {
                inventory.setItem(slot, button);
            }
        }
    }

    private void restore(Player player) {
        InventorySnapshot snapshot = snapshots.remove(player.getUniqueId());
        if (snapshot == null) {
            return;
        }
        stripToolbarItems(player);
        PlayerInventory inventory = player.getInventory();
        inventory.setContents(snapshot.contents());
        inventory.setArmorContents(snapshot.armor());
        inventory.setItemInOffHand(snapshot.offhand());
    }

    private void stripToolbarItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isToolbarItem(inventory.getItem(slot))) {
                inventory.setItem(slot, null);
            }
        }
        ItemStack[] armor = inventory.getArmorContents();
        for (int slot = 0; slot < armor.length; slot++) {
            if (isToolbarItem(armor[slot])) {
                armor[slot] = null;
            }
        }
        inventory.setArmorContents(armor);
        if (isToolbarItem(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        if (isToolbarItem(player.getOpenInventory().getCursor())) {
            player.getOpenInventory().setCursor(null);
        }
    }

    private ItemStack buttonItem(ToolbarButton button) {
        return switch (button) {
            case LOBBIES -> toolbarItem(Material.COMPASS, 'c',
                    "spectator.toolbar-lobbies-name", "Browse Matches",
                    "spectator.toolbar-lobbies-lore",
                    "Right-click to spectate\\nanother match");
            case PLAYERS -> toolbarItem(Material.BLAZE_ROD, 'p',
                    "spectator.toolbar-players-name", "Spectate Player",
                    "spectator.toolbar-players-lore",
                    "Teleport to a player\\nand follow them");
            case BACK -> toolbarItem(Material.PAPER, 'b',
                    "spectator.toolbar-back-name", "Back to Lobby",
                    "spectator.toolbar-back-lore",
                    "Return to your lobby\\nLeave spectator mode");
            case EMPTY -> null;
        };
    }

    private ItemStack toolbarItem(Material material, char button,
            String nameKey, String nameFallback, String loreKey, String loreFallback) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(messages.nonItalic(messages.parse(
                "<white>" + messages.string(nameKey, nameFallback))));
        List<String> lines = new ArrayList<>(List.of(
                messages.string(loreKey, loreFallback).split("\\\\n|\n", -1)));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(messages.nonItalic(messages.parse("<gray>" + line)));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(toolbarKey,
                PersistentDataType.STRING, String.valueOf(button));
        item.setItemMeta(meta);
        return item;
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
}
