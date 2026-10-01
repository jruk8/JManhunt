package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.JManhuntPlugin;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

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

    /** Tracking-bar distance segment: separator colors, bullet, meters. */
    private static final Pattern DISTANCE_SEGMENT =
            Pattern.compile("(<[^>]+>)?\\s*•\\s*(<[^>]+>)?\\{distance\\}m");

    /**
     * Renders and stores one tracking actionbar, scheduling the plain
     * revert for blinked deltas. The revert only lands when no newer
     * render replaced it and the holder is still online. With
     * show-distance off, the distance segment (and its delta
     * triangle) is dropped and no distance history is recorded. The
     * drift record feeds the optional accuracy segment; the blink
     * revert reuses the same template so the segment survives it.
     */
    void putTrackingBar(Player holder, Role holderRole, Integer lobby, String template, String playerName,
            UUID targetId, double distance, Map<String, String> extra,
            CompassInaccuracyService.Result drift) {
        boolean showDistance = plugin.overrides().getBoolean(lobby,
                "settings.compass.feedback.actionbar.show-distance", true);
        DistanceRender render = showDistance
                ? distanceRender(holder, holderRole, lobby, template, targetId, distance)
                : new DistanceRender("", "", false);
        Map<String, String> slots = new HashMap<>(extra);
        slots.put("player", playerName);
        slots.put("distance", render.text());
        if (!showDistance) {
            template = stripDistanceSegment(template);
        }
        template = insertAccuracy(template, lobby, drift);
        actionbars.put(holder.getUniqueId(), messages.renderLiteral(template, slots));
        if (!render.blinked()) {
            return;
        }
        slots.put("distance", render.plain());
        Component plainBar = messages.renderLiteral(template, slots);
        UUID id = holder.getUniqueId();
        long generation = deltaGenerations.merge(id, 1L, Long::sum);
        long delayTicks = blinkDelayTicks(plugin.overrides().getDouble(lobby,
                "settings.compass.feedback.actionbar.show-distance-delta.blink-duration-seconds", 0.6));
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
     * Appends the accuracy segment after the player slot when
     * show-accuracy is on: unapplied samples (inaccuracy off or below
     * the gate) read 100 percent, else the drift record's error over
     * its theoretical max, stepped. Pure apart from config reads.
     */
    private String insertAccuracy(String template, Integer lobby,
            CompassInaccuracyService.Result drift) {
        String base = "settings.compass.feedback.actionbar.show-accuracy.";
        if (!plugin.overrides().getBoolean(lobby, base + "enabled", false)) {
            return template;
        }
        double accuracy = drift != null && drift.applied()
                ? CompassAccuracyRenderer.accuracy(drift.errorDistance(),
                        drift.theoreticalMax())
                : 1.0;
        double stepped = CompassAccuracyRenderer.stepped(accuracy);
        String hex = CompassAccuracyRenderer.lerpColor(
                plugin.overrides().getString(lobby, base + "inaccurate-color", "#cc472d"),
                plugin.overrides().getString(lobby, base + "accurate-color", "#63d42a"),
                stepped);
        String segment = CompassAccuracyRenderer.segment(
                CompassAccuracyRenderer.percent(stepped), hex);
        return template.replace("{player}", "{player} " + segment);
    }

    /**
     * Drops the distance segment from a tracking-bar template, leaving
     * the surrounding text cleanly joined. Pure for tests.
     */
    static String stripDistanceSegment(String template) {
        return DISTANCE_SEGMENT.matcher(template).replaceAll("");
    }

    /**
     * Distance substitution for one tracking actionbar: the plain rounded
     * meters, or the delta triangle format when the target moved enough
     * since this holder last saw it under the same tracking key. BLINK
     * mode flags the render so the bar reverts to plain shortly after.
     */
    private DistanceRender distanceRender(Player holder, Role holderRole, Integer lobby,
            String trackingKey, UUID targetId, double distance) {
        long rounded = Math.round(distance);
        String plain = String.valueOf(rounded);
        DistanceRender plainRender = new DistanceRender(plain, plain, false);
        if (!Double.isFinite(distance) || distance < 0.0) {
            return plainRender;
        }
        String base = "settings.compass.feedback.actionbar.show-distance-delta.";
        if (!plugin.overrides().getBoolean(lobby, base + "enabled", true)) {
            return plainRender;
        }
        String historyKey = trackingKey + "|" + targetId;
        Map<String, Long> history =
                lastRoundedDistances.computeIfAbsent(holder.getUniqueId(), ignored -> new HashMap<>());
        Long previous = history.get(historyKey);
        double maxDistance = plugin.overrides().getDouble(lobby, base + "max-distance", 500.0);
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
        boolean reverse = deltaReverse(holderRole,
                plugin.overrides().getBoolean(lobby, base + "reverse-on-hunter", true));
        String formatted = deltaFormat(kind,
                plugin.overrides().getString(lobby, base + "further-format", "<green>▲{distance}"),
                plugin.overrides().getString(lobby, base + "closer-format", "<red>▼{distance}"),
                reverse);
        return new DistanceRender(formatted.replace("{distance}", plain), plain, blink);
    }

    /**
     * True when the holder sees swapped delta formats: hunters with
     * the reverse toggle on. Holder role alone decides. Pure for tests.
     */
    static boolean deltaReverse(Role holderRole, boolean reverseOnHunter) {
        return holderRole == Role.HUNTER && reverseOnHunter;
    }

    /**
     * Picks the further or closer format for a delta kind, swapped
     * when reversed. Pure for tests.
     */
    static String deltaFormat(DistanceDelta.Kind kind, String furtherFormat,
            String closerFormat, boolean reverse) {
        if (kind == DistanceDelta.Kind.FURTHER) {
            return reverse ? closerFormat : furtherFormat;
        }
        return reverse ? furtherFormat : closerFormat;
    }

    /** Blink revert delay in ticks, never negative. Pure for tests. */
    static long blinkDelayTicks(double seconds) {
        return Math.max(0L, Math.round(Math.max(0.0, seconds) * 20.0));
    }
}
