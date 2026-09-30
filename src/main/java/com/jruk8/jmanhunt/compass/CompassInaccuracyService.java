package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.JManhuntPlugin;
import org.bukkit.Location;

/** Signal inaccuracy verdicts and donut samples for tracking attempts. */
final class CompassInaccuracyService {
    private final JManhuntPlugin plugin;

    CompassInaccuracyService(JManhuntPlugin plugin) {
        this.plugin = plugin;
    }

    /** One resolved drift: needle spot, shown distance, and error facts. */
    public record Result(Location needleSpot, double feedbackDistance, double errorDistance,
            double theoreticalMax, boolean applied) {
    }

    /**
     * Resolves the raw inaccuracy config for one lobby. Callers never
     * read these paths directly; they use the boolean getters and
     * {@link #resolve}.
     */
    SignalInaccuracy.Config config(Integer lobby) {
        String base = "settings.compass.signal.inaccuracy.";
        var overrides = plugin.overrides();
        return new SignalInaccuracy.Config(
                overrides.getBoolean(lobby, base + "enabled", false),
                overrides.getDouble(lobby, base + "inner-deadzone", 0.4),
                overrides.getDouble(lobby, base + "drift-radius", 0.6),
                overrides.getDouble(lobby, base + "thresholds.min-distance", 100.0),
                overrides.getDouble(lobby, base + "thresholds.max-distance", 1000.0),
                SignalInaccuracy.parseTarget(
                        overrides.getString(lobby, base + "inaccurate-on", "BOTH")));
    }

    /**
     * True when the needle drifts: the feature is on, the target covers
     * the needle, and the obtaining item is strictly "compass" (the only
     * item with a settable needle).
     */
    boolean isNeedleInaccurate(Integer lobby) {
        return isNeedleInaccurate(lobby, config(lobby));
    }

    /** Same verdict against a pre-resolved config. */
    boolean isNeedleInaccurate(Integer lobby, SignalInaccuracy.Config config) {
        if (!config.enabled() || (config.target() != SignalInaccuracy.InaccurateOn.NEEDLE
                && config.target() != SignalInaccuracy.InaccurateOn.BOTH)) {
            return false;
        }
        String item = plugin.overrides().getString(lobby,
                "settings.compass.obtaining.item", "compass");
        return item != null && item.trim().equals("compass");
    }

    /**
     * True when the shown distance drifts: the feature is on, the target
     * covers distance feedback, and at least one distance readout is
     * visible so holders actually see the drift.
     */
    boolean isDistanceInaccurate(Integer lobby) {
        return isDistanceInaccurate(lobby, config(lobby));
    }

    /** Same verdict against a pre-resolved config. */
    boolean isDistanceInaccurate(Integer lobby, SignalInaccuracy.Config config) {
        if (!config.enabled() || (config.target() != SignalInaccuracy.InaccurateOn.DISTANCE_FEEDBACK
                && config.target() != SignalInaccuracy.InaccurateOn.BOTH)) {
            return false;
        }
        var overrides = plugin.overrides();
        return overrides.getBoolean(lobby,
                "settings.compass.feedback.actionbar.show-distance", true)
                || overrides.getBoolean(lobby,
                        "settings.compass.feedback.actionbar.show-distance-delta.enabled", true);
    }

    /**
     * Resolves one drifted readout: draws the donut sample when the
     * threshold gate passes and at least one readout drifts, else
     * returns the exact spot. The needle spot honors the needle
     * verdict, the feedback distance honors the distance verdict, and
     * the error distance always measures the drawn sample against the
     * truth (0 when nothing was drawn). Spots that cannot be compared
     * (missing or mismatched worlds) stay exact with a 0 distance.
     */
    Result resolve(Integer lobby, Location trackerSpot, Location trueSpot, double u1,
            double u2) {
        SignalInaccuracy.Config config = config(lobby);
        double distance = safeDistance(trackerSpot, trueSpot);
        double theory = theoreticalMax(config, distance);
        boolean needle = isNeedleInaccurate(lobby, config);
        boolean feedback = isDistanceInaccurate(lobby, config);
        if (distance < 0.0 || !SignalInaccuracy.applies(config, distance)
                || (!needle && !feedback)) {
            return new Result(trueSpot, Math.max(0.0, distance), 0.0, theory, false);
        }
        double outer = SignalInaccuracy.outerRadius(distance, config.drift(),
                config.maxDistance());
        double[] sampled = SignalInaccuracy.sample(trueSpot.getX(), trueSpot.getZ(), outer,
                config.deadzone(), u1, u2);
        Location drifted = new Location(trueSpot.getWorld(), sampled[0], trueSpot.getY(),
                sampled[1], trueSpot.getYaw(), trueSpot.getPitch());
        double error = flatDistance(trueSpot, drifted);
        Location needleSpot = needle ? drifted : trueSpot;
        double shown = feedback ? safeDistance(trackerSpot, drifted) : distance;
        return new Result(needleSpot, shown, error, theory, true);
    }

    /**
     * Theoretical worst error for one distance: max-distance times drift
     * when max is set, else the sample's own outer radius. Pure for
     * tests; negative distances read 0.
     */
    static double theoreticalMax(SignalInaccuracy.Config config, double distance) {
        if (!(distance > 0.0)) {
            return 0.0;
        }
        if (config.maxDistance() > 0.0 && !Double.isNaN(config.maxDistance())) {
            return SignalInaccuracy.clampDrift(config.drift()) * config.maxDistance();
        }
        return SignalInaccuracy.outerRadius(distance, config.drift(), config.maxDistance());
    }

    /**
     * 3D distance between two spots, or -1 when they cannot be compared:
     * either spot missing, worldless, or in another world. Pure.
     */
    static double safeDistance(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null || to.getWorld() == null
                || !from.getWorld().equals(to.getWorld())) {
            return -1.0;
        }
        return from.distance(to);
    }

    /** Flat X/Z distance between two spots, ignoring Y. Pure. */
    static double flatDistance(Location from, Location to) {
        double dx = from.getX() - to.getX();
        double dz = from.getZ() - to.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
