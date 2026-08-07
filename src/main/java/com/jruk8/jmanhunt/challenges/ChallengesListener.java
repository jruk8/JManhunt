package com.jruk8.jmanhunt.challenges;

import com.jruk8.jmanhunt.CommandPlaceholders;
import com.jruk8.jmanhunt.ConfigService;
import com.jruk8.jmanhunt.GameManager;
import com.jruk8.jmanhunt.MessageService;
import com.jruk8.jmanhunt.PlayerStateStore;
import com.jruk8.jmanhunt.SoundService;
import com.jruk8.jmanhunt.settings.SettingsListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Handles the built-in challenges: no-jump, one-heart, and lucky-blocks. */
public final class ChallengesListener implements Listener, SettingsListener {
    private static final int MAX_REROLLS = 20;
    private static final String LUCKY_BLOCK_DEFAULT_SOUND_KEY = "challenges.lucky-block-default";
    private final JavaPlugin plugin;
    private final GameManager game;
    private final PlayerStateStore playerStates;
    private final MessageService messages;
    private final SoundService sounds;
    private final ConfigService configService;
    private final LuckyBlockEngine luckyEngine = new LuckyBlockEngine();
    private final File luckyFile;
    private final File structuresDir;
    private Material currentLuckyBlock;

    public ChallengesListener(JavaPlugin plugin, GameManager game, PlayerStateStore playerStates,
                               MessageService messages, SoundService sounds, ConfigService configService) {
        this.plugin = plugin;
        this.game = game;
        this.playerStates = playerStates;
        this.messages = messages;
        this.sounds = sounds;
        this.configService = configService;
        this.luckyFile = new File(plugin.getDataFolder(), "challenges/lucky-block/lucky-blocks.yml");
        this.structuresDir = new File(plugin.getDataFolder(), "challenges/structures");
        configService.onChange("challenges.lucky-blocks.enabled", (oldValue, newValue) -> onReload());
    }

    /**
     * Called when a match starts. Resolves the lucky block definition (a
     * random block if configured) and announces the chosen block.
     */
    public void onGameStart() {
        if (!plugin.getConfig().getBoolean("challenges.lucky-blocks.enabled", false)) return;
        String definition = plugin.getConfig().getString("challenges.lucky-blocks.block-definition", "gold_block");
        Material block = LuckyBlockResolver.resolve(definition);
        if (block == null) {
            plugin.getLogger().severe("Lucky blocks challenge is enabled but the block definition '"
                    + definition + "' is not a valid block.");
            return;
        }
        currentLuckyBlock = block;
        messages.broadcast("manhunt.lucky-block-announce",
                Map.of("block", LuckyBlockResolver.displayName(block)));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        applyOneHeart(event.getPlayer());
    }

    @EventHandler public void onRespawn(PlayerRespawnEvent event) {
        applyOneHeart(event.getPlayer());
    }

    private void applyOneHeart(Player player) {
        if (!plugin.getConfig().getBoolean("challenges.one-heart.enabled", false)) return;
        if (!game.isActive() || !game.isGameBegun()) return;
        if (!playerStates.role(player).isParticipant()) return;
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(2.0);
        player.setHealth(2.0);
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("challenges.no-jump.enabled", false)) return;
        if (!game.isActive() || !game.isGameBegun()) return;
        Player player = event.getPlayer();
        if (!playerStates.role(player).isParticipant()) return;
        if (event.getFrom().getY() < event.getTo().getY() && player.getVelocity().getY() > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("challenges.lucky-blocks.enabled", false)) return;
        if (!game.isActive() || !game.isGameBegun()) return;
        if (!event.isDropItems()) {
            // Block break did not yield drops
            return;
        }
        Block block = event.getBlock();
        if (!isLuckyBlock(block.getType())) return;

        Player player = event.getPlayer();
        if (!playerStates.role(player).isParticipant()) return;

        event.setDropItems(false);
        LuckyBlockEngine.Outcome outcome = rollWithRerolls(block, player);
        if (outcome == null) return;

        executeOutcome(outcome, block, player);
        playFeedback(outcome, player);
    }

    /**
     * Rolls a lucky block outcome, rerolling if a structure outcome fails to
     * load or place. Returns null if all rerolls are exhausted.
     */
    private LuckyBlockEngine.Outcome rollWithRerolls(Block block, Player player) {
        for (int i = 0; i <= MAX_REROLLS; i++) {
            LuckyBlockEngine.Outcome outcome = luckyEngine.roll();
            if (outcome == null) return null;
            if (outcome.structure() == null) {
                return outcome;
            }
            try {
                placeStructure(outcome, block);
                return outcome;
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to place structure '" + outcome.structure().name()
                        + "' for lucky block outcome '" + outcome.name() + "': " + e.getMessage());
                if (i == MAX_REROLLS) {
                    plugin.getLogger().warning("Lucky block roll aborted after exhausting all "
                            + MAX_REROLLS + " rerolls.");
                    return null;
                }
            }
        }
        return null;
    }

    private void executeOutcome(LuckyBlockEngine.Outcome outcome, Block block, Player player) {
        // Items: drop at the block location, replacing the Lucky Block's normal drops.
        for (LuckyBlockEngine.ItemEntry item : outcome.items()) {
            String name = item.name();
            if (!name.contains(":")) name = "minecraft:" + name;
            Material material = Registry.MATERIAL.get(NamespacedKey.fromString(name));
            if (material != null && material.isItem()) {
                block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(material, item.quantity()));
            }
        }

        // Commands: dispatch as console, resolving tildes relative to block or player.
        if (!outcome.commands().isEmpty()) {
            Location origin = outcome.relativeTo().equals("PLAYER")
                    ? player.getLocation()
                    : block.getLocation();
            for (String command : outcome.commands()) {
                String parsed = CommandPlaceholders.replace(command, player.getName(),
                        origin.getX(), origin.getY(), origin.getZ());
                if (parsed.startsWith("/")) parsed = parsed.substring(1);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }

        // Structure: already placed in rollWithRerolls.
    }

    /**
     * Loads and places a structure at the lucky block location.
     * Throws if loading or placement fails.
     */
    private void placeStructure(LuckyBlockEngine.Outcome outcome, Block block) throws Exception {
        LuckyBlockEngine.StructureSettings settings = outcome.structure();
        if (settings == null) {
            throw new IllegalStateException("Outcome '" + outcome.name() + "' has no structure settings");
        }
        File structureFile = new File(structuresDir, settings.name() + ".nbt");
        if (!structureFile.exists()) {
            throw new FileNotFoundException("Structure file not found: " + structureFile.getAbsolutePath());
        }
        StructureManager structureManager = Bukkit.getStructureManager();
        Structure structure = structureManager.loadStructure(structureFile);
        if (structure == null) {
            throw new IllegalStateException("Failed to load structure from: " + structureFile.getAbsolutePath());
        }
        StructureRotation rotation = StructureRotation.NONE;
        if (settings.randomRotation()) {
            StructureRotation[] rotations = StructureRotation.values();
            rotation = rotations[ThreadLocalRandom.current().nextInt(rotations.length)];
        }
        structure.place(block.getLocation(), true, rotation, Mirror.NONE, 0, 1.0f, new Random());
    }

    private void playFeedback(LuckyBlockEngine.Outcome outcome, Player player) {
        LuckyBlockEngine.Feedback feedback = outcome.feedback();
        if (feedback != null && feedback.sound() != null && feedback.sound().enabled()) {
            LuckyBlockEngine.Sound sound = feedback.sound();
            sounds.playCustomSound(player, sound.sound(), sound.pitch(), sound.volume());
        } else {
            sounds.playSound(player, LUCKY_BLOCK_DEFAULT_SOUND_KEY);
        }

        if (feedback == null) return;
        String format = messages.string("lucky-block-feedback-format", "\n{prefix}{value}");
        String prefix = messages.string("prefix", "");
        Map<String, String> values = Map.of("player", player.getName());

        if (feedback.message() != null && !feedback.message().isBlank()) {
            String raw = format.replace("{prefix}", prefix).replace("{value}", feedback.message());
            for (Map.Entry<String, String> entry : values.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            player.sendMessage(messages.parse(raw));
        }

        if (feedback.broadcast() != null && !feedback.broadcast().isBlank()) {
            String raw = format.replace("{prefix}", prefix).replace("{value}", feedback.broadcast());
            for (Map.Entry<String, String> entry : values.entrySet()) {
                raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
            }
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.equals(player)) continue;
                online.sendMessage(messages.parse(raw));
            }
        }
    }

    private boolean isLuckyBlock(Material type) {
        if (currentLuckyBlock == null) {
            // Fall back to the resolved definition when no match has started yet.
            currentLuckyBlock = LuckyBlockResolver.resolve(
                    plugin.getConfig().getString("challenges.lucky-blocks.block-definition", "gold_block"));
        }
        return currentLuckyBlock != null && currentLuckyBlock == type;
    }

    @Override
    public void onStart() {
        onReload();
    }

    @Override
    public void onReload() {
        luckyEngine.clear();
        if (!plugin.getConfig().getBoolean("challenges.lucky-blocks.enabled", false)) return;
        try {
            if (!luckyEngine.load(luckyFile)) {
                plugin.getLogger().severe("Lucky blocks challenge is enabled but "
                        + "challenges/lucky-block/lucky-blocks.yml could not be loaded.");
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Lucky blocks challenge is enabled but "
                    + "challenges/lucky-block/lucky-blocks.yml is invalid: " + e.getMessage());
        }
    }

    @Override
    public String getDataPath() {
        return "challenges/lucky-block/lucky-blocks.yml";
    }
}
