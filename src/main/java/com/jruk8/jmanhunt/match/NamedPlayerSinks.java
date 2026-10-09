package com.jruk8.jmanhunt.match;

import com.jruk8.jmanhunt.command.PlayerSinks;
import com.jruk8.jmanhunt.command.RosterValues;
import com.jruk8.jmanhunt.command.TagItems;
import com.jruk8.jmanhunt.command.TagLocations;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Named-player plumbing shared by modifier and debuff runs: the
 * {@code <pmessage>} and {@code <psound>} sinks, the engine message
 * format, and the online-player lookup. One home so both managers
 * deliver identically.
 */
public final class NamedPlayerSinks {

    private NamedPlayerSinks() {
    }

    /**
     * Sinks delivering engine-formatted output to one online player:
     * false when offline so the tags warn, true once attempted.
     * Unknown sound ids warn through the given logger and skip.
     */
    public static PlayerSinks of(MessageService messages, ModifiersMessages texts, SoundService sounds,
            Consumer<String> logWarning, String containerId, MaxHealthService maxHealth) {
        return new PlayerSinks() {
            @Override
            public boolean message(String playerName, String text) {
                Player target = onlinePlayer(playerName);
                if (target == null) {
                    return false;
                }
                messages.sendText(target, formatEngineMessage(texts, messages, text));
                return true;
            }

            @Override
            public boolean sound(String playerName, String soundId, float pitch, float volume) {
                Player target = onlinePlayer(playerName);
                if (target == null) {
                    return false;
                }
                return playSinkSound(target, sounds, logWarning, containerId, soundId, pitch,
                        volume);
            }

            @Override
            public boolean title(String playerName, String title, String subtitle,
                    double staySeconds, double inSeconds, double outSeconds) {
                Player target = onlinePlayer(playerName);
                if (target == null) {
                    return false;
                }
                showSinkTitle(target, messages, title, subtitle, staySeconds, inSeconds,
                        outSeconds);
                return true;
            }

            @Override
            public boolean setSlot(String playerName, RosterValues.InventorySlot slot,
                    String materialKey, int qty) {
                Player target = onlinePlayer(playerName);
                if (target == null || target.getInventory() == null) {
                    return false;
                }
                return setSlotInto(target, slot, materialKey, qty);
            }

            @Override
            public boolean setMaxHealth(String playerName, String id, double amount) {
                Player target = onlinePlayer(playerName);
                if (target == null) {
                    return false;
                }
                return maxHealth.setContribution(target, id, amount);
            }

            @Override
            public boolean modifyMaxHealth(String playerName, String id, double amount) {
                Player target = onlinePlayer(playerName);
                if (target == null) {
                    return false;
                }
                return maxHealth.modifyContribution(target, id, amount);
            }

            @Override
            public Optional<Double> getMaxHealth(String playerName, String id) {
                Player target = onlinePlayer(playerName);
                if (target == null) {
                    return Optional.empty();
                }
                return Optional.of(maxHealth.getContribution(target.getUniqueId(), id));
            }

            @Override
            public boolean clearMaxHealth(String playerName, String idOrNull) {
                Player target = onlinePlayer(playerName);
                if (target == null) {
                    return false;
                }
                return maxHealth.clearContribution(target, idOrNull);
            }

            @Override
            public boolean teleport(String playerName,
                    TagLocations.TeleportRequest target) {
                Player online = onlinePlayer(playerName);
                if (online == null) {
                    return false;
                }
                TeleportService.teleport(online, target, Bukkit.getWorlds(),
                        detail -> logWarning.accept("modifier \"" + containerId
                                + "\" teleport: " + detail));
                return true;
            }
        };
    }

    private static boolean playSinkSound(Player target, SoundService sounds,
            Consumer<String> logWarning, String containerId, String soundId, float pitch,
            float volume) {
        if (!sounds.isValidSound(soundId)) {
            logWarning.accept("modifier \"" + containerId
                    + "\" tried playing invalid sound \"" + soundId + "\"");
            return true;
        }
        sounds.playCustomSound(target, soundId, pitch, volume);
        return true;
    }

    private static void showSinkTitle(Player target, MessageService messages, String title,
            String subtitle, double staySeconds, double inSeconds, double outSeconds) {
        target.showTitle(Title.title(messages.parse(title), messages.parse(subtitle),
                Title.Times.times(
                        ticksToDuration(inSeconds),
                        ticksToDuration(staySeconds),
                        ticksToDuration(outSeconds))));
    }

    private static boolean setSlotInto(Player target, RosterValues.InventorySlot slot,
            String materialKey, int qty) {
        Material material =
                Material.matchMaterial(TagItems.normalizeMaterialKey(materialKey));
        if (material == null) {
            return false;
        }
        int clamped = Math.min(Math.max(qty, 1), material.getMaxStackSize());
        ItemStack stack = new ItemStack(material, clamped);
        PlayerInventory inventory = target.getInventory();
        if (slot instanceof RosterValues.InventorySlot.Named named) {
            switch (named.name()) {
                case "MAINHAND" -> inventory.setItemInMainHand(stack);
                case "OFFHAND" -> inventory.setItemInOffHand(stack);
                case "HELMET" -> inventory.setHelmet(stack);
                case "CHESTPLATE" -> inventory.setChestplate(stack);
                case "LEGGINGS" -> inventory.setLeggings(stack);
                default -> inventory.setBoots(stack);
            }
            return true;
        }
        if (slot instanceof RosterValues.InventorySlot.Index indexed) {
            int index = indexed.index();
            if (index < 0 || index >= inventory.getSize()) {
                return false;
            }
            inventory.setItem(index, stack);
            return true;
        }
        return false;
    }

    /** Seconds through the shared tick converter, as a title duration. */
    private static Duration ticksToDuration(double seconds) {
        return Duration.ofMillis(ModifierTriggers.secondsToTicks(seconds) * 50);
    }

    /** Engine message format shared by modifier and debuff runs. */
    public static String formatEngineMessage(ModifiersMessages texts, MessageService messages,
            String text) {
        return texts.getMessageFormat().replace("{prefix}", messages.prefix())
                .replace("{message}", text);
    }

    /** Online player by case-insensitive name, or null. */
    public static Player onlinePlayer(String playerName) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(playerName)) {
                return player;
            }
        }
        return null;
    }

    /**
     * One slot's raw stack, or null when the index falls outside the
     * inventory. Shared by the live and test slot reads.
     */
    public static ItemStack slotStack(PlayerInventory inventory,
            RosterValues.InventorySlot slot) {
        if (slot instanceof RosterValues.InventorySlot.Named named) {
            return switch (named.name()) {
                case "MAINHAND" -> inventory.getItemInMainHand();
                case "OFFHAND" -> inventory.getItemInOffHand();
                case "HELMET" -> inventory.getHelmet();
                case "CHESTPLATE" -> inventory.getChestplate();
                case "LEGGINGS" -> inventory.getLeggings();
                default -> inventory.getBoots();
            };
        }
        if (slot instanceof RosterValues.InventorySlot.Index indexed) {
            int index = indexed.index();
            if (index < 0 || index >= inventory.getSize()) {
                return null;
            }
            return inventory.getItem(index);
        }
        return null;
    }
}
