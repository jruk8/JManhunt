package com.jruk8.jmanhunt.command.units;

import com.jruk8.jmanhunt.command.CommandSupport;
import com.jruk8.jmanhunt.command.ManhuntCommand;
import com.jruk8.jmanhunt.command.PendingConfirmations;
import com.jruk8.jmanhunt.command.SubcommandUnit;
import com.jruk8.jmanhunt.command.UnitSyntaxException;
import com.jruk8.jmanhunt.config.ConfigService;
import com.jruk8.jmanhunt.config.DevConfig;
import com.jruk8.jmanhunt.lobby.LobbyService;
import com.jruk8.jmanhunt.lobby.bounds.LobbyBounds;
import com.jruk8.jmanhunt.lobby.config.LobbyConfig;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.CommandMessages;
import com.jruk8.jmanhunt.message.ManhuntMessages;
import com.jruk8.jmanhunt.player.LobbyTeleporter;
import com.jruk8.jmanhunt.world.WorldEngineService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

/** The worldengine verb: lobbyconfig, tpto, and cellindex actions. */
public final class WorldEngineUnit implements SubcommandUnit {
    public record WorldEngineDeps(GameManager game, ConfigService config, LobbyService lobbies,
            LobbyTeleporter teleporter, LobbyConfig lobbyConfig,
            DevConfig devConfig, PendingConfirmations confirms) {
    }

    public record WorldEngineTexts(ManhuntMessages manhunt, CommandMessages command,
            CommandSupport support) {
    }

    private final GameManager game;
    private final ConfigService config;
    private final LobbyService lobbies;
    private final LobbyTeleporter teleporter;
    private final LobbyConfig lobbyConfig;
    private final ManhuntMessages texts;
    private final CommandMessages commandTexts;
    private final CommandSupport support;
    private final PendingConfirmations confirms;
    private final WorldEngineTpto tpto;
    private final Map<UUID, Location> boundPos1 = new HashMap<>();
    private final Map<UUID, Location> boundPos2 = new HashMap<>();

    public WorldEngineUnit(WorldEngineDeps deps, WorldEngineTexts texts) {
        this.game = deps.game();
        this.config = deps.config();
        this.lobbies = deps.lobbies();
        this.teleporter = deps.teleporter();
        this.lobbyConfig = deps.lobbyConfig();
        this.texts = texts.manhunt();
        this.commandTexts = texts.command();
        this.support = texts.support();
        this.confirms = deps.confirms();
        this.tpto = new WorldEngineTpto(game, config, deps.devConfig(), this.texts,
                this.commandTexts, this.support);
    }

    /** Validated action plus the raw line: actions keep their own shapes. */
    public record WorldEngineArgs(String sub, String[] args) {
    }

    public static WorldEngineArgs parse(String[] args, ManhuntMessages texts)
            throws UnitSyntaxException {
        if (args.length == 1 || args[1].isBlank()) {
            throw new UnitSyntaxException(texts.getWorldengineUsage(), Map.of());
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("lobbyconfig") || sub.equals("tpto") || sub.equals("cellindex")) {
            return new WorldEngineArgs(sub, args);
        }
        throw new UnitSyntaxException(texts.getWorldengineUsage(), Map.of());
    }

    @Override public String primaryName() {
        return "worldengine";
    }

    @Override public Set<String> aliases() {
        return Set.of();
    }

    @Override public boolean execute(CommandSender sender, String[] args) {
        // Pre-check preserves the disabled-before-usage error order;
        // executeParsed re-checks for direct callers.
        if (!config.getBoolean("world-engine.enabled", false)) {
            return support.message(sender, texts.getWorldengineDisabled());
        }
        try {
            return executeParsed(sender, parse(args, texts));
        } catch (UnitSyntaxException failure) {
            support.message(sender, failure.template(), failure.placeholders());
            return true;
        }
    }

    public boolean executeParsed(CommandSender sender, WorldEngineArgs args) {
        if (!config.getBoolean("world-engine.enabled", false)) {
            return support.message(sender, texts.getWorldengineDisabled());
        }
        if (!ManhuntCommand.canUseWorldEngineAction(sender, args.sub())) {
            return support.message(sender, commandTexts.getNoPermission());
        }
        return switch (args.sub()) {
            case "lobbyconfig" -> worldEngineLobbyConfig(sender, args.args());
            case "tpto" -> tpto.execute(sender, args.args());
            case "cellindex" -> worldEngineCellIndex(sender, args.args());
            default -> throw new IllegalArgumentException("WorldEngineUnit cannot handle " + args.sub());
        };
    }

    @Override public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("worldengine")) {
            List<String> actions = new ArrayList<>(List.of("lobbyconfig", "cellindex", "tpto"));
            actions.removeIf(action -> !ManhuntCommand.canUseWorldEngineAction(sender, action));
            return CommandSupport.partial(args[1], actions);
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("worldengine")
                && args[1].equalsIgnoreCase("lobbyconfig")) {
            return completeWorldEngineLobbyConfigTab(sender, args);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine") && args[1].equalsIgnoreCase("cellindex")) {
            if (!ManhuntCommand.canUseWorldEngineAction(sender, "cellindex")) {
                return List.of();
            }
            return CommandSupport.partial(args[2], List.of("get", "set", "buffer"));
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("worldengine")
                && args[1].equalsIgnoreCase("tpto")) {
            return tpto.completeTptoTab(args);
        }
        return null;
    }

    /** Live pending lobby corners for the debug particle draft boxes. */
    public Map<UUID, Location> boundPos1View() {
        return Collections.unmodifiableMap(boundPos1);
    }

    /** Live pending lobby corners for the debug particle draft boxes. */
    public Map<UUID, Location> boundPos2View() {
        return Collections.unmodifiableMap(boundPos2);
    }

    private boolean worldEngineCellIndex(CommandSender sender, String[] args) {
        if (args.length < 3 || args[2].isBlank()) {
            return support.message(sender, texts.getWorldengineCellindexUsage());
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        if (action.equals("buffer")) {
            return cellIndexBuffer(sender, args);
        }
        if (action.equals("get")) {
            return cellIndexGet(sender, args);
        }
        if (action.equals("set")) {
            return cellIndexSet(sender, args);
        }
        return support.message(sender, texts.getWorldengineCellindexUsage());
    }

    /** Lists the buffered world-engine cell indexes. */
    private boolean cellIndexBuffer(CommandSender sender, String[] args) {
        if (args.length > 3) {
            return support.message(sender, texts.getWorldengineCellindexUsage());
        }
        support.message(sender, texts.getWorldengineCellindexBufferHeader());
        for (long index : game.bufferedCellIndexes()) {
            support.message(sender, texts.getWorldengineCellindexBufferEntry(),
                    Map.of("index", String.valueOf(index)));
        }
        support.neutralSound(sender);
        return true;
    }

    /** Shows the current world-engine cell index. */
    private boolean cellIndexGet(CommandSender sender, String[] args) {
        if (args.length > 3) {
            return support.message(sender, texts.getWorldengineCellindexUsage());
        }
        OptionalLong index = game.cellIndex();
        if (index.isEmpty()) {
            return support.message(sender, texts.getWorldengineCellindexUnavailable());
        }
        support.message(sender, texts.getWorldengineCellindexGet(),
                Map.of("index", String.valueOf(index.getAsLong())));
        support.neutralSound(sender);
        return true;
    }

    /** Sets the world-engine cell index, clamped to the cap. */
    private boolean cellIndexSet(CommandSender sender, String[] args) {
        if (args.length != 4) {
            return support.message(sender, texts.getWorldengineCellindexUsage());
        }
        long max = game.cellIndexCap();
        long value;
        try {
            value = Long.parseLong(args[3].trim());
        } catch (NumberFormatException exception) {
            support.message(sender, texts.getWorldengineCellindexInvalid(),
                    Map.of("max", String.valueOf(max)));
            return true;
        }
        OptionalLong before = game.cellIndex();
        long clamped = WorldEngineService.clampCellIndex(value, max);
        if (!game.cellIndex(clamped)) {
            return support.message(sender, texts.getWorldengineCellindexUnavailable());
        }
        support.message(sender, texts.getWorldengineCellindexSet(), Map.of("index", String.valueOf(clamped),
                "was", before.isPresent() ? String.valueOf(before.getAsLong()) : "none",
                "max", String.valueOf(max)));
        support.neutralSound(sender);
        return true;
    }

    /**
     * Stores a lobby teleport: setlobbytp &lt;lobby-id&gt;
     * [x y z yaw pitch]. Coords are all-or-none; omitted coords use the
     * sender's position, so the console must pass all five. The
     * position path requires standing in the lobby world; explicit
     * coords skip that check.
     */
    private boolean lobbyConfigSetTp(CommandSender sender, String[] args) {
        if (args.length != 4 && args.length != 9) {
            return support.message(sender, texts.getWorldengineLobbyconfigSetlobbytpUsage());
        }
        OptionalInt lobbyId = LobbyService.parseId(args[3]);
        if (lobbyId.isEmpty()) {
            return support.message(sender, texts.getLobbyInvalidId());
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId.getAsInt() != 0) {
            return support.message(sender, texts.getLobbyWorldengineRequired());
        }
        LobbyTpTarget target = resolveLobbyTpTarget(sender, args);
        if (target == null) {
            return true;
        }
        saveLobbyTp(lobbyId.getAsInt(), target.location());
        List<Player> targets = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (game.instanceOf(online.getUniqueId()).isEmpty()) {
                targets.add(online);
            }
        }
        teleporter.setSpawnToLobby(targets, lobbyId.getAsInt());
        support.message(sender, texts.getWorldengineLobbyconfigSetlobbytpSuccess(), Map.of(
                "lobby", String.valueOf(lobbyId.getAsInt()), "location", target.shown()));
        support.neutralSound(sender);
        return true;
    }

    /**
     * Resolves the setlobbytp target: explicit coords, or the sender's
     * spot in the lobby world. Null when invalid (message already sent).
     */
    private LobbyTpTarget resolveLobbyTpTarget(CommandSender sender, String[] args) {
        if (args.length == 9) {
            double[] coords = parseLobbyTpCoords(args[4], args[5], args[6], args[7], args[8]);
            if (coords == null) {
                support.message(sender, texts.getWorldengineInvalidLocation());
                return null;
            }
            Location location = new Location(null, coords[0], coords[1], coords[2],
                    (float) coords[3], (float) coords[4]);
            return new LobbyTpTarget(location,
                    formatCoords(coords[0], coords[1], coords[2], coords[3], coords[4]));
        }
        if (!(sender instanceof Player player)) {
            support.message(sender, commandTexts.getPlayerOnly());
            return null;
        }
        String lobbyWorld = game.lobbyWorldName();
        if (player.getWorld() == null || !player.getWorld().getName().equals(lobbyWorld)) {
            support.message(sender, texts.getWorldengineLobbyconfigSetlobbytpWrongWorld(),
                    Map.of("world", lobbyWorld));
            return null;
        }
        Location location = player.getLocation();
        return new LobbyTpTarget(location, formatLocation(location));
    }

    /** A resolved setlobbytp target: where, plus how to show it. */
    private record LobbyTpTarget(Location location, String shown) {
    }

    /**
     * Parses setlobbytp coords: x, y, z, yaw, pitch. Null when any part
     * is not a number. Pure for tests.
     */
    public static double[] parseLobbyTpCoords(String xText, String yText, String zText,
            String yawText, String pitchText) {
        try {
            return new double[]{
                    Double.parseDouble(xText.trim()),
                    Double.parseDouble(yText.trim()),
                    Double.parseDouble(zText.trim()),
                    Double.parseDouble(yawText.trim()),
                    Double.parseDouble(pitchText.trim())};
        } catch (NumberFormatException expected) {
            return null;
        }
    }

    private static String formatCoords(double x, double y, double z, double yaw, double pitch) {
        return String.format("%.2f, %.2f, %.2f, %.2f, %.2f", x, y, z, yaw, pitch);
    }

    /**
     * Manages lobby teleports and boundary boxes in the lobby store:
     * pos1|pos2 records the executing player's feet block as a bounds
     * corner, setbounds stores both corners, setlobbytp stores a
     * teleport, and deletelobby removes the whole entry.
     */
    private boolean worldEngineLobbyConfig(CommandSender sender, String[] args) {
        if (args.length < 3 || args[2].isBlank()) {
            return support.message(sender, texts.getWorldengineLobbyconfigUsage());
        }
        String action = args[2].toLowerCase(Locale.ROOT);
        switch (action) {
            case "pos1", "pos2" -> {
                return lobbyConfigPos(sender, action.equals("pos1"), args);
            }
            case "setbounds" -> {
                return lobbyConfigSetBounds(sender, args);
            }
            case "setlobbytp" -> {
                return lobbyConfigSetTp(sender, args);
            }
            case "deletelobby" -> {
                return lobbyConfigDelete(sender, args);
            }
            default -> {
                return support.message(sender, texts.getWorldengineLobbyconfigUsage());
            }
        }
    }

    private boolean lobbyConfigPos(CommandSender sender, boolean first, String[] args) {
        if (!(sender instanceof Player player)) {
            return support.message(sender, commandTexts.getPlayerOnly());
        }
        if (args.length != 3) {
            return support.message(sender, texts.getWorldengineLobbyconfigUsage());
        }
        (first ? boundPos1 : boundPos2).put(player.getUniqueId(), player.getLocation().clone());
        support.message(sender, first ? texts.getWorldengineLobbyconfigPos1()
                        : texts.getWorldengineLobbyconfigPos2(),
                Map.of("pos", blockCoords(player.getLocation())));
        support.neutralSound(sender);
        return true;
    }

    /**
     * Stores both recorded corners as a lobby's bounds. Player-only,
     * since the corners come from the sender. Overwriting existing
     * bounds needs a second run within 10 seconds.
     */
    private boolean lobbyConfigSetBounds(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return support.message(sender, commandTexts.getPlayerOnly());
        }
        if (args.length != 4) {
            return support.message(sender, texts.getWorldengineLobbyconfigSetboundsUsage());
        }
        OptionalInt lobbyId = LobbyService.parseId(args[3]);
        if (lobbyId.isEmpty()) {
            return support.message(sender, texts.getLobbyInvalidId());
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId.getAsInt() != 0) {
            return support.message(sender, texts.getLobbyWorldengineRequired());
        }
        Location first = boundPos1.get(player.getUniqueId());
        Location second = boundPos2.get(player.getUniqueId());
        if (first == null || second == null) {
            return support.message(sender, texts.getWorldengineLobbyconfigNeedSelection());
        }
        if (first.getWorld() == null || !first.getWorld().equals(second.getWorld())) {
            return support.message(sender, texts.getWorldengineLobbyconfigWorldMismatch());
        }
        OptionalInt duplicate = LobbyBounds.duplicateOf(lobbyConfig.getLobbies(), lobbyId.getAsInt(),
                first.getBlockX(), first.getBlockY(), first.getBlockZ(),
                second.getBlockX(), second.getBlockY(), second.getBlockZ());
        if (duplicate.isPresent()) {
            support.message(sender, texts.getWorldengineLobbyconfigDuplicateBounds(),
                    Map.of("other", String.valueOf(duplicate.getAsInt())));
            return true;
        }
        LobbyConfig.LobbyEntry entry =
                lobbyConfig.getLobbies().get(String.valueOf(lobbyId.getAsInt()));
        boolean hasBounds = entry != null && entry.getBounds() != null
                && entry.getBounds().getPos1() != null && entry.getBounds().getPos2() != null;
        if (hasBounds && !confirms.confirm(
                "lobbyconfig-setbounds:" + CommandSupport.senderKey(sender) + ":" + lobbyId.getAsInt())) {
            support.message(sender, texts.getWorldengineLobbyconfigSetboundsConfirm(),
                    Map.of("lobby", String.valueOf(lobbyId.getAsInt())));
            return true;
        }
        writeLobbyBounds(sender, lobbyId.getAsInt(), first, second);
        return true;
    }

    /** Writes one lobby's bounds box from two corners and reports it. */
    private void writeLobbyBounds(CommandSender sender, int lobbyId, Location first, Location second) {
        LobbyConfig.LobbyEntry entry =
                lobbyConfig.getLobbies().get(String.valueOf(lobbyId));
        if (entry == null) {
            entry = new LobbyConfig.LobbyEntry();
            lobbyConfig.getLobbies().put(String.valueOf(lobbyId), entry);
        }
        LobbyConfig.BoundsData bounds = new LobbyConfig.BoundsData();
        bounds.setPos1(LobbyConfig.Position.of(first.getBlockX(), first.getBlockY(), first.getBlockZ()));
        bounds.setPos2(LobbyConfig.Position.of(second.getBlockX(), second.getBlockY(), second.getBlockZ()));
        entry.setBounds(bounds);
        lobbyConfig.save();
        support.message(sender, texts.getWorldengineLobbyconfigSetboundsSuccess(), Map.of(
                "lobby", String.valueOf(lobbyId),
                "from", blockCoords(first), "to", blockCoords(second)));
        support.neutralSound(sender);
    }

    /**
     * Deletes a lobby entry (teleport plus bounds) from the lobby
     * store. Live lobbies, members, and matches are untouched.
     * Console-capable. Needs a second run within 10 seconds.
     */
    private boolean lobbyConfigDelete(CommandSender sender, String[] args) {
        if (args.length != 4) {
            return support.message(sender, texts.getWorldengineLobbyconfigDeletelobbyUsage());
        }
        OptionalInt lobbyId = LobbyService.parseId(args[3]);
        if (lobbyId.isEmpty()) {
            return support.message(sender, texts.getLobbyInvalidId());
        }
        if (!lobbies.multiLobbyAllowed() && lobbyId.getAsInt() != 0) {
            return support.message(sender, texts.getLobbyWorldengineRequired());
        }
        if (!lobbyConfigHasLobby(lobbyConfig, lobbyId.getAsInt())) {
            support.message(sender, texts.getWorldengineLobbyconfigDeletelobbyMissing(),
                    Map.of("lobby", String.valueOf(lobbyId.getAsInt())));
            return true;
        }
        if (!confirms.confirm(
                "lobbyconfig-delete:" + CommandSupport.senderKey(sender) + ":" + lobbyId.getAsInt())) {
            support.message(sender, texts.getWorldengineLobbyconfigDeletelobbyConfirm(),
                    Map.of("lobby", String.valueOf(lobbyId.getAsInt())));
            return true;
        }
        lobbyConfig.getLobbies().remove(String.valueOf(lobbyId.getAsInt()));
        lobbyConfig.save();
        support.message(sender, texts.getWorldengineLobbyconfigDeletelobbySuccess(),
                Map.of("lobby", String.valueOf(lobbyId.getAsInt())));
        support.neutralSound(sender);
        return true;
    }

    /**
     * True when the lobby store defines the index. A lobby counts as
     * existing with an index alone, even without a teleport or bounds.
     * Pure for tests.
     */
    public static boolean lobbyConfigHasLobby(LobbyConfig lobbyConfig, int lobbyId) {
        return lobbyConfig != null && lobbyConfig.getLobbies() != null
                && lobbyConfig.getLobbies().containsKey(String.valueOf(lobbyId));
    }

    /** Smallest lobby id without bounds yet. Pure for tests. */
    public static int nextFreeBoundsId(Set<Integer> boundedIds) {
        int id = 0;
        while (boundedIds.contains(id)) {
            id++;
        }
        return id;
    }

    private static String blockCoords(Location corner) {
        return corner.getBlockX() + ", " + corner.getBlockY() + ", " + corner.getBlockZ();
    }

    /**
     * Saves a lobby teleport in the lobby store. The world is never
     * stored: teleports always resolve in the lobby world.
     */
    private void saveLobbyTp(int lobbyId, Location location) {
        LobbyConfig.LobbyEntry entry = lobbyConfig.getLobbies()
                .computeIfAbsent(String.valueOf(lobbyId), key -> new LobbyConfig.LobbyEntry());
        entry.setLobbytp(LobbyConfig.LobbyTp.of(location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch()));
        lobbyConfig.save();
    }

    /** Renders a teleport target for chat. Pure for tests. */
    public static String formatLocation(Location location) {
        String worldName = location.getWorld() == null ? "null" : location.getWorld().getName();
        String xFormatted = String.format("%.2f", location.getX());
        String yFormatted = String.format("%.2f", location.getY());
        String zFormatted = String.format("%.2f", location.getZ());
        String yawFormatted = String.format("%.2f", location.getYaw());
        String pitchFormatted = String.format("%.2f", location.getPitch());
        return worldName + " @ " + xFormatted + ", " + yFormatted + ", " + zFormatted
                + ", " + yawFormatted + ", " + pitchFormatted;
    }

    /** Tab completion for worldengine lobbyconfig. Null when inapplicable. */
    private List<String> completeWorldEngineLobbyConfigTab(CommandSender sender, String[] args) {
        if (args.length == 3 && args[0].equalsIgnoreCase("worldengine")
                && args[1].equalsIgnoreCase("lobbyconfig")) {
            if (!ManhuntCommand.canUseWorldEngineAction(sender, "lobbyconfig")) {
                return List.of();
            }
            return CommandSupport.partial(args[2], List.of("pos1", "pos2", "setbounds", "setlobbytp", "deletelobby"));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("worldengine")
                && args[1].equalsIgnoreCase("lobbyconfig") && args[2].equalsIgnoreCase("setbounds")) {
            if (!ManhuntCommand.canUseWorldEngineAction(sender, "lobbyconfig")) {
                return List.of();
            }
            return CommandSupport.partial(args[3], lobbyBoundsSetOptions());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("worldengine")
                && args[1].equalsIgnoreCase("lobbyconfig") && args[2].equalsIgnoreCase("setlobbytp")) {
            if (!ManhuntCommand.canUseWorldEngineAction(sender, "lobbyconfig")) {
                return List.of();
            }
            return CommandSupport.partial(args[3], CommandSupport.lobbyIdOptions(lobbies));
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("worldengine")
                && args[1].equalsIgnoreCase("lobbyconfig") && args[2].equalsIgnoreCase("deletelobby")) {
            if (!ManhuntCommand.canUseWorldEngineAction(sender, "lobbyconfig")) {
                return List.of();
            }
            return CommandSupport.partial(args[3], lobbyConfigIdOptions());
        }
        return null;
    }

    /**
     * Lobby id completion for lobbyconfig setbounds: the next id
     * without bounds first, then live lobby ids. Any valid id stays
     * accepted.
     */
    private List<String> lobbyBoundsSetOptions() {
        Set<Integer> bounded = new HashSet<>();
        if (lobbyConfig != null && lobbyConfig.getLobbies() != null) {
            for (Map.Entry<String, LobbyConfig.LobbyEntry> entry : lobbyConfig.getLobbies().entrySet()) {
                int id;
                try {
                    id = Integer.parseInt(entry.getKey().trim());
                } catch (NumberFormatException expected) {
                    continue;
                }
                LobbyConfig.LobbyEntry value = entry.getValue();
                if (id >= 0 && value != null && value.getBounds() != null
                        && value.getBounds().getPos1() != null && value.getBounds().getPos2() != null) {
                    bounded.add(id);
                }
            }
        }
        List<String> options = new ArrayList<>();
        options.add(String.valueOf(nextFreeBoundsId(bounded)));
        for (String id : CommandSupport.lobbyIdOptions(lobbies)) {
            if (!options.contains(id)) {
                options.add(id);
            }
        }
        return options;
    }

    /**
     * Lobby id completion for lobbyconfig deletelobby: indexes defined
     * in the lobby store, sorted. Empty when none are defined.
     */
    private List<String> lobbyConfigIdOptions() {
        List<Integer> ids = new ArrayList<>();
        if (lobbyConfig != null && lobbyConfig.getLobbies() != null) {
            for (String key : lobbyConfig.getLobbies().keySet()) {
                try {
                    int id = Integer.parseInt(key.trim());
                    if (id >= 0) {
                        ids.add(id);
                    }
                } catch (NumberFormatException expected) {
                    // Skip non-numeric indexes.
                }
            }
        }
        return ids.stream().sorted().map(String::valueOf).toList();
    }
}
