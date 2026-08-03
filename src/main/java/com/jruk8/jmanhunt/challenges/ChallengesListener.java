package com.jruk8.jmanhunt.challenges;

import com.jruk8.jmanhunt.CommandPlaceholders;
import com.jruk8.jmanhunt.GameManager;
import com.jruk8.jmanhunt.MessageService;
import com.jruk8.jmanhunt.PlayerStateStore;
import com.jruk8.jmanhunt.Role;
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

import java.io.File;
import java.util.Map;

/** Handles the built-in challenges: no-jump, one-heart, and lucky-blocks. */
public final class ChallengesListener implements Listener, SettingsListener {
    private static final String LUCKY_BLOCK_DEFAULT_SOUND_KEY = "challenges.lucky-block-default";
    private final JavaPlugin plugin;
    private final GameManager game;
    private final PlayerStateStore playerStates;
    private final MessageService messages;
    private final SoundService sounds;
    private final LuckyBlockEngine luckyEngine = new LuckyBlockEngine();
    private final File luckyFile;

    public ChallengesListener(JavaPlugin plugin, GameManager game, PlayerStateStore playerStates,
                              MessageService messages, SoundService sounds) {
        this.plugin = plugin;
        this.game = game;
        this.playerStates = playerStates;
        this.messages = messages;
        this.sounds = sounds;
        this.luckyFile = new File(plugin.getDataFolder(), "settings/lucky-blocks.yml");
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
        if (playerStates.role(player) == Role.NONE) return;
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(2.0);
        player.setHealth(2.0);
    }

    @EventHandler public void onMove(PlayerMoveEvent event) {
        if (!plugin.getConfig().getBoolean("challenges.no-jump.enabled", false)) return;
        if (!game.isActive() || !game.isGameBegun()) return;
        Player player = event.getPlayer();
        if (playerStates.role(player) == Role.NONE) return;
        if (event.getFrom().getY() < event.getTo().getY() && player.getVelocity().getY() > 0) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("challenges.lucky-blocks.enabled", false)) return;
        if (!game.isActive() || !game.isGameBegun()) return;
        Player player = event.getPlayer();
        if (playerStates.role(player) == Role.NONE) return;
        Block block = event.getBlock();
        if (!isLuckyBlock(block.getType())) return;
        event.setDropItems(false);
        LuckyBlockEngine.Outcome outcome = luckyEngine.roll();
        if (outcome == null) return;
        switch (outcome.type()) {
            case ITEM -> {
                String name = outcome.itemName();
                if (!name.contains(":")) name = "minecraft:" + name;
                Material material = Registry.MATERIAL.get(NamespacedKey.fromString(name));
                if (material != null) {
                    player.getInventory().addItem(new ItemStack(material, outcome.quantity()));
                }
            }
            case COMMAND -> {
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
            case NONE -> { /* does nothing */ }
        }
        playFeedback(outcome, player);
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
        String definition = plugin.getConfig().getString("challenges.lucky-blocks.block-definition", "gold_block");
        if (!definition.contains(":")) definition = "minecraft:" + definition;
        NamespacedKey key = NamespacedKey.fromString(definition);
        if (key == null) return false;
        Material material = Registry.MATERIAL.get(key);
        return material != null && material == type;
    }

    @Override
    public void onStart() {
        onReload();
    }

    @Override
    public void onReload() {
        if (!plugin.getConfig().getBoolean("challenges.lucky-blocks.enabled", false)) return;
        try {
            if (!luckyEngine.load(luckyFile)) {
                plugin.getLogger().severe("Lucky blocks challenge is enabled but settings/lucky-blocks.yml could not be loaded.");
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().severe("Lucky blocks challenge is enabled but settings/lucky-blocks.yml is invalid: " + e.getMessage());
        }
    }

    @Override
    public String getDataPath() {
        return "lucky-blocks.yml";
    }
}