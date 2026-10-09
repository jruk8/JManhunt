package com.jruk8.jmanhunt.player;

import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.config.PlayersSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.message.SpectatorMessages;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Spectator hotbar toolbar: layout buttons, match swapping, player
 * teleporting, and lock-on follow.
 *
 * <p>The toolbar deploys only for the SPECTATOR role on fake-spectator
 * enable, onto the inventory the fake-spectator snapshot already
 * cleared. Gear snapshots live in FakeSpectatorService alone; this
 * service only places and strips its tagged buttons. Quit restores
 * through fake mode, and join strips toolbar items left by a crash.
 */
public final class SpectatorToolbarService implements FakeSpectatorService.ModeListener {

    /** Permission to swap to another lobby's match from the toolbar. */
    public static final String SWAP_LOBBY_PERMISSION = "jmanhunt.spectator.swaplobby";

    /** Fallback layout when the configured one is not 9 characters. */
    public static final String DEFAULT_LAYOUT = "cp##s###b";

    /** Double-shift pair window for follow exit, in milliseconds. */
    public static final long SNEAK_PAIR_WINDOW_MILLIS = 500L;

    /** Hotbar button kind. */
    public enum ToolbarButton {
        LOBBIES,
        PLAYERS,
        SNOWBALL,
        BACK,
        EMPTY
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

    /** Message bus, spectator/command texts, and sounds. */
    public record ToolbarTexts(MessageService messages, SpectatorMessages spectator,
            CommandMessages command, SoundService sounds) {
    }

    /** States, fakes, game, and lobbies. */
    public record ToolbarMatch(PlayerStateStore playerStates, FakeSpectatorService fakes,
            GameManager game, LobbyService lobbies) {
    }

    private final PlayersSettingsFacade settings;
    private final ToolbarTexts texts;
    private final ToolbarMatch match;
    private final NamespacedKey toolbarKey;
    private final Set<UUID> deployed = new HashSet<>();
    private final Map<UUID, UUID> locks = new HashMap<>();
    private final Map<UUID, Long> lastSneaks = new HashMap<>();
    private final Map<UUID, ItemStack> previousHelmets = new HashMap<>();

    public SpectatorToolbarService(PlayersSettingsFacade settings, ToolbarTexts texts,
            ToolbarMatch match, NamespacedKey toolbarKey) {
        this.settings = settings;
        this.texts = texts;
        this.match = match;
        this.toolbarKey = toolbarKey;
        match.fakes().addModeListener(this);
    }

    /**
     * Parses a layout into 9 hotbar buttons: c browses matches, p
     * teleports to players, s throws the snowball, b returns to the
     * lobby, # is empty, and anything else is empty too. Anything but
     * a 9-character string falls back to the default layout. Pure for
     * tests.
     */
    public static ToolbarButton[] parseLayout(String layout) {
        String effective = layout != null && layout.length() == 9 ? layout : DEFAULT_LAYOUT;
        ToolbarButton[] buttons = new ToolbarButton[9];
        for (int slot = 0; slot < 9; slot++) {
            buttons[slot] = switch (effective.charAt(slot)) {
                case 'c' -> ToolbarButton.LOBBIES;
                case 'p' -> ToolbarButton.PLAYERS;
                case 's' -> ToolbarButton.SNOWBALL;
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

    @Override
    public void onModeChange(Player player, boolean enabled) {
        if (enabled) {
            if (match.playerStates().role(player) == Role.SPECTATOR) {
                deploy(player);
            }
            placeHead(player);
            return;
        }
        undeploy(player);
        clearLock(player.getUniqueId());
        removeHead(player);
    }

    /** Places the spectator marker head, preserving any real helmet. */
    private void placeHead(Player player) {
        UUID id = player.getUniqueId();
        ItemStack previous = player.getInventory().getHelmet();
        if (!isSpectatorHead(previous, id)) {
            previousHelmets.put(id, previous);
        }
        player.getInventory().setHelmet(buildSpectatorHead(player));
    }

    /** Removes the marker head, restoring the preserved helmet. */
    private void removeHead(Player player) {
        UUID id = player.getUniqueId();
        if (isSpectatorHead(player.getInventory().getHelmet(), id)) {
            player.getInventory().setHelmet(previousHelmets.remove(id));
        } else {
            previousHelmets.remove(id);
        }
    }

    /**
     * True when the item is the spectator marker head owned by the id:
     * our toolbar tag plus a skull profile for the owner. Pure for
     * tests (Bukkit-free; callers supply the item and owner).
     */
    boolean isSpectatorHead(ItemStack item, UUID ownerId) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || ownerId == null) {
            return false;
        }
        if (!toolbarButton(item).map(button -> button == 'h').orElse(false)) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof SkullMeta skull) || skull.getOwningPlayer() == null) {
            return false;
        }
        return ownerId.equals(skull.getOwningPlayer().getUniqueId());
    }

    /** Marker head with the player's own skin for mutual spectator visibility. */
    ItemStack buildSpectatorHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD, 1);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(Bukkit.getOfflinePlayer(player.getUniqueId()));
        }
        meta.getPersistentDataContainer().set(toolbarKey,
                PersistentDataType.STRING, "h");
        head.setItemMeta(meta);
        return head;
    }

    /**
     * Join recovery: with no live deployment, toolbar items in the
     * inventory are crash leftovers and go. Gear itself restores
     * through FakeSpectatorService.
     */
    public void handleJoin(Player player) {
        if (isDeployed(player)) {
            return;
        }
        stripToolbarItems(player);
    }

    /** True when the toolbar is currently deployed for the player. */
    public boolean isDeployed(Player player) {
        return player != null && deployed.contains(player.getUniqueId());
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
        lastSneaks.remove(spectatorId);
    }

    /**
     * Registers a sneak tap for double-shift follow exit: true when this
     * tap lands within the pair window of the previous tap, completing
     * the pair. First taps, expired taps, and backwards-clock taps
     * return false and stamp the new first tap. Stamps are single-use,
     * and expired stamps prune on every tap so the map stays small.
     */
    public boolean registerSneak(UUID spectatorId, long nowMillis) {
        lastSneaks.entrySet().removeIf(
                entry -> nowMillis - entry.getValue() > SNEAK_PAIR_WINDOW_MILLIS);
        Long previous = lastSneaks.get(spectatorId);
        if (previous != null) {
            long gap = nowMillis - previous;
            if (gap >= 0 && gap <= SNEAK_PAIR_WINDOW_MILLIS) {
                lastSneaks.remove(spectatorId);
                return true;
            }
        }
        lastSneaks.put(spectatorId, nowMillis);
        return false;
    }

    /**
     * Exits lock-on follow with chat feedback and a neutral sound, and
     * clears the follow actionbar. False when not following anyone.
     */
    public boolean exitFollow(Player spectator) {
        lastSneaks.remove(spectator.getUniqueId());
        UUID targetId = locks.remove(spectator.getUniqueId());
        if (targetId == null) {
            return false;
        }
        Player target = Bukkit.getPlayer(targetId);
        String name = target != null ? target.getName() : match.playerStates().playerName(targetId);
        texts.messages().messageRaw(spectator, this.texts.spectator().getFollowExited(), Map.of("player", name));
        texts.sounds().playNeutralSound(spectator);
        spectator.sendActionBar(Component.empty());
        return true;
    }

    /** Locked target of the spectator, or null when none. */
    public UUID lockedTarget(UUID spectatorId) {
        return locks.get(spectatorId);
    }

    /** Origin lobby of the spectator's match, or null outside matches. */
    Integer lobbyOf(Player spectator) {
        return match.game().lobbyOfPlayer(spectator.getUniqueId());
    }

    /** Effective hotbar layout for the spectator. */
    ToolbarButton[] layout(Player spectator) {
        return parseLayout(settings.toolbarLayout(lobbyOf(spectator)));
    }

    /** True when lock-on follow applies to the spectator. */
    boolean lockOn(Player spectator) {
        return settings.toolbarLockOn(lobbyOf(spectator));
    }

    /** Follow teleport distance for the spectator, at least 1 block. */
    int tpDistance(Player spectator) {
        return Math.max(1, settings.toolbarTpDistance(lobbyOf(spectator)));
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
            spectator.sendActionBar(texts.messages().componentRaw(this.texts.spectator().getFollowingActionbar(),
                    Map.of("role", texts.messages().roleName(match.playerStates().role(target)),
                            "player", target.getName())));
        }
    }

    /**
     * Running matches for the browser: sublobby matches glow and sort
     * first, then by match id.
     */
    public List<MatchEntry> matchEntries(Player spectator) {
        long currentId = match.game().instanceOf(spectator.getUniqueId())
                .map(GameInstance::matchId).orElse(-1L);
        return match.game().liveInstances().stream()
                .filter(instance -> instance.active() && !instance.ending())
                .map(instance -> new MatchEntry(instance.matchId(), instance.lobbyTag(),
                        match.game().activeRunnerCount(instance), match.game().activeHunterCount(instance),
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
        Optional<GameInstance> target = match.game().instance(matchId)
                .filter(instance -> instance.active() && !instance.ending());
        if (target.isEmpty()) {
            texts.messages().messageRaw(spectator, this.texts.spectator().getMatchGone());
            texts.sounds().playAngrySound(spectator);
            spectator.closeInventory();
            return false;
        }
        Optional<GameInstance> current = match.game().instanceOf(spectator.getUniqueId());
        if (current.isPresent() && current.get().matchId() == matchId) {
            texts.messages().messageRaw(spectator, this.texts.spectator().getAlreadyInMatch());
            texts.sounds().playNeutralSound(spectator);
            spectator.closeInventory();
            return true;
        }
        Integer fromLobby = current.map(GameInstance::originLobbyId)
                .or(() -> match.lobbies().lobbyOf(spectator.getUniqueId())
                        .map(lobby -> lobby.id()))
                .orElse(null);
        if (fromLobby != null && fromLobby != target.get().originLobbyId()
                && !spectator.hasPermission(SWAP_LOBBY_PERMISSION)) {
            texts.messages().messageRaw(spectator, texts.command().getNoPermission());
            texts.sounds().playAngrySound(spectator);
            return false;
        }
        current.ifPresent(old -> match.game().leaveMatch(old, List.of(spectator), false));
        if (match.game().joinPlayers(target.get(), List.of(spectator), Role.SPECTATOR) == 0) {
            texts.messages().messageRaw(spectator, this.texts.spectator().getMatchGone());
            texts.sounds().playAngrySound(spectator);
            spectator.closeInventory();
            return false;
        }
        teleportToPriority(spectator, target.get());
        clearLock(spectator.getUniqueId());
        texts.sounds().playNeutralSound(spectator);
        spectator.closeInventory();
        return true;
    }

    /** Teleports to the shared spectator spawn pick of a match. */
    void teleportToPriority(Player spectator, GameInstance target) {
        SpectatorSpawnResolver resolver = new SpectatorSpawnResolver(match.playerStates(), match.fakes());
        Location center = null;
        if (target.cellIndex().isPresent()) {
            center = match.game().cellCenter(target.cellIndex().getAsLong()).orElse(null);
        }
        SpectatorSpawnResolver.resolve(resolver.candidatesOf(target), center)
                .ifPresent(spectator::teleport);
    }

    /**
     * Spectateable players: online speedrunners and hunters of the
     * spectator's match, or of their lobby when outside matches.
     * Runners sort before hunters, then by name.
     */
    public List<PlayerEntry> playerEntries(Player spectator) {
        List<Player> pool = match.game().instanceOf(spectator.getUniqueId())
                .map(match.game()::onlineActivePlayers)
                .orElseGet(() -> lobbyPool(spectator));
        UUID locked = lockedTarget(spectator.getUniqueId());
        return pool.stream()
                .filter(player -> match.playerStates().role(player).isParticipant())
                .map(player -> new PlayerEntry(player.getUniqueId(), player.getName(),
                        match.playerStates().role(player),
                        player.getUniqueId().equals(locked)))
                .sorted(Comparator.comparing((PlayerEntry entry) -> entry.role()
                        != Role.SPEEDRUNNER).thenComparing(entry -> entry.name()
                                .toLowerCase(Locale.ROOT)))
                .toList();
    }

    /**
     * Teleports to a player and locks on when lock-on applies, chatting
     * the spectate confirmation; without a lock the follow actionbar
     * stays empty since there is nothing to follow. Re-clicking the
     * locked target chats the already-spectating line instead.
     */
    public boolean teleportAndLock(Player spectator, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            texts.sounds().playAngrySound(spectator);
            spectator.closeInventory();
            return false;
        }
        if (lockOn(spectator) && targetId.equals(lockedTarget(spectator.getUniqueId()))) {
            texts.messages().messageRaw(spectator, this.texts.spectator().getAlreadySpectating(),
                    Map.of("player", target.getName()));
            texts.sounds().playNeutralSound(spectator);
            spectator.closeInventory();
            return true;
        }
        spectator.teleport(target.getLocation());
        if (lockOn(spectator) && targetValid(candidateOf(target))) {
            locks.put(spectator.getUniqueId(), targetId);
        } else {
            locks.remove(spectator.getUniqueId());
        }
        texts.messages().messageRaw(spectator, this.texts.spectator().getNowSpectating(),
                Map.of("role", texts.messages().roleName(match.playerStates().role(target)),
                        "player", target.getName()));
        texts.sounds().playNeutralSound(spectator);
        spectator.closeInventory();
        return true;
    }

    /**
     * Back-to-lobby exit: leaves the match under NONE, or just unnones
     * outside matches. The role change unwinds fake mode and the
     * toolbar through the mode listener.
     */
    public void returnToLobby(Player spectator) {
        Optional<GameInstance> current = match.game().instanceOf(spectator.getUniqueId());
        if (current.isPresent()) {
            match.game().leaveMatchToLobby(current.get(), spectator, Role.NONE);
            return;
        }
        match.lobbies().lobbyOf(spectator.getUniqueId()).ifPresent(lobby -> {
            match.game().teleportToLobby(List.of(spectator), lobby.id());
            match.game().setSpawnToLobbyQuiet(List.of(spectator), lobby.id());
        });
        match.playerStates().setRole(spectator, Role.NONE);
    }

    private List<Player> lobbyPool(Player spectator) {
        Optional<Integer> lobbyId = match.lobbies().lobbyOf(spectator.getUniqueId()).map(lobby ->
                lobby.id());
        if (lobbyId.isEmpty()) {
            return List.of();
        }
        return Bukkit.getOnlinePlayers().stream()
                .filter(player -> match.lobbies().lobbyOf(player.getUniqueId())
                        .map(lobby -> lobby.id() == lobbyId.get()).orElse(false))
                .map(player -> (Player) player)
                .toList();
    }

    private SpectateCandidate candidateOf(Player player) {
        return candidateOf(player, match.game().instanceOf(player.getUniqueId()).orElse(null));
    }

    private SpectateCandidate candidateOf(Player player, GameInstance instance) {
        UUID id = player.getUniqueId();
        Role role = match.playerStates().role(player);
        boolean active = instance == null || instance.isActive(id);
        boolean runnerAlive = role != Role.SPEEDRUNNER
                || instance == null || match.playerStates().isActiveSpeedrunner(id);
        return new SpectateCandidate(id, role, active, runnerAlive,
                match.fakes().isFakeSpectator(player), player.isOnline());
    }

    private void deploy(Player player) {
        if (!deployed.add(player.getUniqueId())) {
            return;
        }
        stripToolbarItems(player);
        PlayerInventory inventory = player.getInventory();
        ToolbarButton[] buttons = layout(player);
        boolean snowball = snowballEnabled(player);
        for (int slot = 0; slot < buttons.length; slot++) {
            if (buttons[slot] == ToolbarButton.SNOWBALL && !snowball) {
                continue;
            }
            ItemStack button = buttonItem(player, buttons[slot]);
            if (button != null) {
                inventory.setItem(slot, button);
            }
        }
    }

    private void undeploy(Player player) {
        if (!deployed.remove(player.getUniqueId())) {
            return;
        }
        stripToolbarItems(player);
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

    private ItemStack buttonItem(Player player, ToolbarButton button) {
        return switch (button) {
            case LOBBIES -> toolbarItem(Material.COMPASS, 'c', texts.spectator().getToolbarLobbiesName(),
                    texts.spectator().getToolbarLobbiesLore());
            case PLAYERS -> toolbarItem(Material.BLAZE_ROD, 'p', texts.spectator().getToolbarPlayersName(),
                    texts.spectator().getToolbarPlayersLore());
            case SNOWBALL -> snowballItem(snowballCooldownSeconds(player));
            case BACK -> toolbarItem(Material.PAPER, 'b', texts.spectator().getToolbarBackName(),
                    texts.spectator().getToolbarBackLore());
            case EMPTY -> null;
        };
    }

    /** True when the snowball button deploys for the spectator. */
    public boolean snowballEnabled(Player spectator) {
        return settings.snowballEnabled(lobbyOf(spectator));
    }

    /** Snowball recharge time in seconds, never negative. */
    public int snowballCooldownSeconds(Player spectator) {
        return Math.max(0, settings.snowballCooldownSeconds(lobbyOf(spectator)));
    }

    /** Hotbar slot holding the snowball in a parsed layout, or -1 when absent. Pure for tests. */
    static int snowballSlot(ToolbarButton[] buttons) {
        for (int slot = 0; slot < buttons.length; slot++) {
            if (buttons[slot] == ToolbarButton.SNOWBALL) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * Fresh tagged snowball stack, count 1, for deploy and post-throw
     * restore. Carries the item-native use cooldown, so no plugin-side
     * cooldown state is needed for honest clients.
     */
    public ItemStack snowballItem(int cooldownSeconds) {
        ItemStack item = toolbarItem(Material.SNOWBALL, 's', texts.spectator().getToolbarSnowballName(),
                texts.spectator().getToolbarSnowballLore());
        if (cooldownSeconds > 0) {
            item.setData(DataComponentTypes.USE_COOLDOWN,
                    UseCooldown.useCooldown((float) cooldownSeconds).build());
        }
        return item;
    }

    private ItemStack toolbarItem(Material material, char button, String name, String loreText) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(texts.messages().nonItalic(texts.messages().parse("<white>" + name)));
        List<String> lines = new ArrayList<>(List.of(loreText.split("\\\\n|\n", -1)));
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(texts.messages().nonItalic(texts.messages().parse("<gray>" + line)));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(toolbarKey,
                PersistentDataType.STRING, String.valueOf(button));
        item.setItemMeta(meta);
        return item;
    }

}
