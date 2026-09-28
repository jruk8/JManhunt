package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.message.MessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Distance-delta tracking actionbars: renders the plain or triangle
 * distance, stores the bar, and reverts blinked deltas to plain.
 * Owns the per-holder distance history and blink generations; the
 * actionbar map itself stays shared with the manager.
 */
final class CompassDeltaRenderer {

    private final JManhuntPlugin plugin;
    private final MessageService messages;
    private final Map<UUID, Component> actionbars;
    /** Last rounded distance per holder and tracking key plus target. */
    private final Map<UUID, Map<String, Long>> lastRoundedDistances = new HashMap<>();
    /** Blink revert generation per holder; stale reverts never land. */
    private final Map<UUID, Long> deltaGenerations = new HashMap<>();

    CompassDeltaRenderer(JManhuntPlugin plugin, MessageService messages,
            Map<UUID, Component> actionbars) {
        this.plugin = plugin;
        this.messages = messages;
        this.actionbars = actionbars;
    }

    /** Rendered distance plus its plain form and whether it blinked. */
    record DistanceRender(String text, String plain, boolean blinked) {
    }

    /**
     * Renders and stores one tracking actionbar, scheduling the plain
     * revert for blinked deltas. The revert only lands when no newer
     * render replaced it and the holder is still online.
     */
    void putTrackingBar(Player holder, Integer lobby, String key, String playerName,
            UUID targetId, double distance, Map<String, String> extra) {
        DistanceRender render = distanceRender(holder, lobby, key, targetId, distance);
        Map<String, String> slots = new HashMap<>(extra);
        slots.put("player", playerName);
        slots.put("distance", render.text());
        actionbars.put(holder.getUniqueId(), messages.component(key, slots));
        if (!render.blinked()) {
            return;
        }
        slots.put("distance", render.plain());
        Component plainBar = messages.component(key, slots);
        UUID id = holder.getUniqueId();
        long generation = deltaGenerations.merge(id, 1L, Long::sum);
        long delayTicks = blinkDelayTicks(plugin.overrides().getDouble(lobby,
                "settings.compass.actionbar.show-distance-delta.blink-duration-seconds", 0.6));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (deltaGenerations.getOrDefault(id, 0L) != generation) {
                return;
            }
            if (Bukkit.getPlayer(id) == null) {
                return;
            }
            actionbars.put(id, plainBar);
        }, Math.max(1L, delayTicks));
    }

    /** Drops one holder's distance history and blink generation. */
    void forget(UUID holderId) {
        lastRoundedDistances.remove(holderId);
        deltaGenerations.remove(holderId);
    }

    /**
     * Distance substitution for one tracking actionbar: the plain rounded
     * meters, or the delta triangle format when the target moved enough
     * since this holder last saw it under the same tracking key. BLINK
     * mode flags the render so the bar reverts to plain shortly after.
     */
    private DistanceRender distanceRender(Player holder, Integer lobby, String trackingKey,
            UUID targetId, double distance) {
        long rounded = Math.round(distance);
        String plain = String.valueOf(rounded);
        DistanceRender plainRender = new DistanceRender(plain, plain, false);
        if (!Double.isFinite(distance) || distance < 0.0) {
            return plainRender;
        }
        String base = "settings.compass.actionbar.show-distance-delta.";
        if (!plugin.overrides().getBoolean(lobby, base + "enabled", true)) {
            return plainRender;
        }
        String historyKey = trackingKey + "|" + targetId;
        Map<String, Long> history =
                lastRoundedDistances.computeIfAbsent(holder.getUniqueId(), ignored -> new HashMap<>());
        Long previous = history.get(historyKey);
        double maxDistance = plugin.overrides().getDouble(lobby, base + "max-distance", 200.0);
        double minDelta = plugin.overrides().getDouble(lobby, base + "min-delta-to-show", 5.0);
        DistanceDelta.Kind kind = DistanceDelta.of(previous, rounded, maxDistance, minDelta);
        history.put(historyKey, rounded);
        if (kind == DistanceDelta.Kind.SAME) {
            return plainRender;
        }
        boolean hold = DistanceDelta.Mode.parse(
                plugin.overrides().getString(lobby, base + "mode", "BLINK")) == DistanceDelta.Mode.HOLD;
        long blinkTicks = blinkDelayTicks(plugin.overrides()
                .getDouble(lobby, base + "blink-duration-seconds", 0.6));
        boolean blink = !hold && blinkTicks > 0;
        if (!hold && !blink) {
            return plainRender;
        }
        String formatted = kind == DistanceDelta.Kind.FURTHER
                ? plugin.overrides().getString(lobby, base + "further-format", "<green>▲{distance}")
                : plugin.overrides().getString(lobby, base + "closer-format", "<red>▼{distance}");
        return new DistanceRender(formatted.replace("{distance}", plain), plain, blink);
    }

    /** Blink revert delay in ticks, never negative. Pure for tests. */
    static long blinkDelayTicks(double seconds) {
        return Math.max(0L, Math.round(Math.max(0.0, seconds) * 20.0));
    }
}
