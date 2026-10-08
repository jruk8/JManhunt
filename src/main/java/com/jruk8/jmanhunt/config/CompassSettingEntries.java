package com.jruk8.jmanhunt.config;

import static com.jruk8.jmanhunt.config.SettingRegistry.bool;
import static com.jruk8.jmanhunt.config.SettingRegistry.floatVal;
import static com.jruk8.jmanhunt.config.SettingRegistry.intDynamic;
import static com.jruk8.jmanhunt.config.SettingRegistry.intVal;
import static com.jruk8.jmanhunt.config.SettingRegistry.option;
import static com.jruk8.jmanhunt.config.SettingRegistry.string;

import java.util.List;

/** Compass and signal interference registry entries. */
final class CompassSettingEntries {

    private CompassSettingEntries() {
    }

    static void addAll(List<SettingDescriptor> entries) {
        addCompassEntries(entries);
        addSignalInaccuracyEntries(entries);
        addSignalAccuracyEntries(entries);
        addSignalInterferenceEntries(entries);
    }

private static void addSignalInaccuracyEntries(List<SettingDescriptor> entries) {
    String root = "settings.compass.signal.inaccuracy.";
    entries.add(bool(root + "enabled", true));
    entries.add(floatVal(root + "inner-deadzone", 0.65, 0.0, 1.0));
    entries.add(floatVal(root + "drift-radius", 0.8, 0.0, 1.0));
    entries.add(floatVal(root + "thresholds.min-distance", 100.0, -1.0, null));
    entries.add(floatVal(root + "thresholds.max-distance", 1000.0, -1.0, null));
    entries.add(option(root + "inaccurate-on", "BOTH", "NEEDLE", "DISTANCE_FEEDBACK", "BOTH"));
}

private static void addSignalAccuracyEntries(List<SettingDescriptor> entries) {
    String root = "settings.compass.signal.inaccuracy.accuracy-hotspot.";
    entries.add(bool(root + "enabled", true));
    entries.add(floatVal(root + "hotspot-radius", 70.0, 0.0, null));
    entries.add(intVal(root + "sample-interval", 10, 1, 100));
    entries.add(intVal(root + "max-points", 40, 1, null));
    entries.add(floatVal(root + "full-accuracy-fraction", 0.5, 0.0, 1.0));
    entries.add(floatVal(root + "max-reduction", 0.9, 0.0, 1.0));
}

private static void addCompassEntries(List<SettingDescriptor> entries) {
    entries.add(bool("settings.compass.obtaining.given-to.hunters", true));
    entries.add(bool("settings.compass.obtaining.given-to.speedrunners", false));
    entries.add(string("settings.compass.obtaining.item", "compass"));
    entries.add(bool("settings.compass.lock-to-inventory", true));
    entries.add(bool("settings.compass.obtaining.drop-on-death.enabled", true));
    entries.add(bool("settings.compass.actions.auto.enabled", true));
    entries.add(floatVal("settings.compass.actions.auto.interval", 35.0, 0.0, null));
    entries.add(floatVal("settings.compass.actions.auto.deviation", 0.0, 0.0, null));
    entries.add(bool("settings.compass.actions.manual.enabled", true));
    entries.add(floatVal("settings.compass.actions.manual.cooldown", 3.0, -1.0, null));
    entries.add(bool("settings.compass.actions.target-cycling.enabled", true));
    entries.add(intVal("settings.compass.actions.target-cycling.max-targets", 5, 1, 20));
    entries.add(floatVal("settings.compass.actions.target-cycling.scroll-cooldown", 0.5, 0.0, null));
    entries.add(bool("settings.compass.actions.teammates.enabled", true));
    entries.add(floatVal("settings.compass.actions.teammates.switch-cooldown", 0.5, 0.0, null));
    entries.add(bool("settings.compass.feedback.chat-messages.enabled", true));
    entries.add(bool("settings.compass.distance-limits.hunter.min-distance.enabled", true));
    entries.add(floatVal("settings.compass.distance-limits.hunter.min-distance.distance", 25.0, 0.0, null));
    entries.add(bool("settings.compass.distance-limits.hunter.max-distance.enabled", true));
    entries.add(floatVal("settings.compass.distance-limits.hunter.max-distance.distance", -1.0, -1.0, null));
    entries.add(bool("settings.compass.distance-limits.speedrunner.min-distance.enabled", true));
    entries.add(floatVal("settings.compass.distance-limits.speedrunner.min-distance.distance", 25.0, 0.0, null));
    entries.add(bool("settings.compass.distance-limits.speedrunner.max-distance.enabled", true));
    entries.add(floatVal("settings.compass.distance-limits.speedrunner.max-distance.distance", -1.0, -1.0, null));
    addCompassAnalysisEntries(entries);
}

private static void addCompassAnalysisEntries(List<SettingDescriptor> entries) {
    String root = "settings.compass.actions.manual.analysis.";
    entries.add(bool(root + "enabled", true));
    entries.add(floatVal(root + "delay-seconds", 2.0, 0.0, null));
    entries.add(floatVal(root + "delay-deviation-seconds", 1.0, 0.0, null));
    entries.add(floatVal(root + "sound-interval-seconds", 0.5, 0.05, 3.0));
    entries.add(bool(root + "debuffs.enabled", true));
    addAnalysisCostEntries(entries, root + "cost.");
    addCancelEarlyEntries(entries, root + "cancel-early.");
    addCompassActionbarEntries(entries);
}

private static void addAnalysisCostEntries(List<SettingDescriptor> entries, String root) {
    entries.add(bool(root + "enabled", false));
    entries.add(option(root + "cost-on", "INITIATE", "INITIATE", "SUCCESS", "BOTH"));
    entries.add(bool(root + "payment.saturation.enabled", false));
    entries.add(intVal(root + "payment.saturation.value", 3, 1, 40));
    entries.add(bool(root + "payment.health.enabled", false));
    entries.add(intVal(root + "payment.health.value", 4, 1, 100));
    entries.add(bool(root + "payment.health.can-kill", true));
    entries.add(bool(root + "payment.exp-level.enabled", true));
    entries.add(intVal(root + "payment.exp-level.value", 1, 1, 100));
    entries.add(floatVal(root + "payment.failure-cooldown", 1.0, 0.0, null));
    entries.add(bool(root + "poverty-behavior.cancel-when-poor", true));
    entries.add(bool(root + "poverty-behavior.show-reason", true));
}

private static void addCancelEarlyEntries(List<SettingDescriptor> entries, String root) {
    entries.add(bool(root + "enabled", true));
    entries.add(floatVal(root + "time-multiplier", 0.3, 0.0, 1.0));
}

private static void addCompassActionbarEntries(List<SettingDescriptor> entries) {
    String root = "settings.compass.feedback.actionbar.";
    entries.add(intVal(root + "refresh-ticks", 1, 1, null));
    entries.add(bool(root + "show-distance", true));
    entries.add(bool(root + "show-accuracy.enabled", false));
    entries.add(string(root + "show-accuracy.accurate-color", "#63d42a"));
    entries.add(string(root + "show-accuracy.inaccurate-color", "#cc472d"));
    entries.add(bool(root + "show-distance-delta.enabled", true));
    entries.add(string(root + "show-distance-delta.further-format", "<green>▲{distance}"));
    entries.add(string(root + "show-distance-delta.closer-format", "<red>▼{distance}"));
    entries.add(floatVal(root + "show-distance-delta.max-distance", 500.0, 0.0, null));
    entries.add(floatVal(root + "show-distance-delta.min-delta-to-show", 5.0, 0.0, null));
    entries.add(option(root + "show-distance-delta.mode", "BLINK", "HOLD", "BLINK"));
    entries.add(floatVal(root + "show-distance-delta.blink-duration-seconds", 0.6, 0.0, null));
    entries.add(bool(root + "show-distance-delta.reverse-on-hunter", true));
}

private static void addSignalInterferenceEntries(List<SettingDescriptor> entries) {
    entries.add(bool("settings.compass.signal.interference.enabled", true));
    entries.add(intDynamic("settings.compass.signal.interference.required-to-fail", 1, 1,
            SettingDescriptor.DynamicBound.ENABLED_OPTION_COUNT));
    entries.add(floatVal("settings.compass.signal.interference.chance-to-bypass", 0.0, 0.0, 1.0));
    entries.add(bool("settings.compass.signal.interference.show-reason-in-actionbar", true));
    entries.add(bool("settings.compass.signal.interference.light-level.enabled", false));
    entries.add(intVal("settings.compass.signal.interference.light-level.min-sky-light", 10, 0, 15));
    entries.add(intVal("settings.compass.signal.interference.light-level.min-block-light", 5, 0, 15));
    entries.add(option("settings.compass.signal.interference.light-level.interfere-when", "BOTH_UNMET",
            "ONE_UNMET", "BOTH_UNMET"));
    entries.add(option("settings.compass.signal.interference.light-level.check-on", "SELF",
            "SELF", "TARGET", "BOTH"));
    entries.add(bool("settings.compass.signal.interference.underground.enabled", false));
    entries.add(intVal("settings.compass.signal.interference.underground.max-blocks-above", 3, 1, 380));
    entries.add(bool("settings.compass.signal.interference.underground.ignore-transparent", true));
    entries.add(option("settings.compass.signal.interference.underground.check-on", "BOTH",
            "SELF", "TARGET", "BOTH"));
    entries.add(bool("settings.compass.signal.interference.underwater.enabled", false));
    entries.add(intVal("settings.compass.signal.interference.underwater.max-blocks-above", 2, 1, 380));
    entries.add(option("settings.compass.signal.interference.underwater.check-on", "BOTH",
            "SELF", "TARGET", "BOTH"));
    entries.add(bool("settings.compass.signal.interference.altitude.enabled", false));
    entries.add(intVal("settings.compass.signal.interference.altitude.min-y", -20, -64, 319));
    entries.add(intVal("settings.compass.signal.interference.altitude.max-y", 120, -64, 319));
    entries.add(option("settings.compass.signal.interference.altitude.check-on", "BOTH",
            "SELF", "TARGET", "BOTH"));
    entries.add(bool("settings.compass.signal.interference.weather.enabled", false));
    entries.add(bool("settings.compass.signal.interference.biome.enabled", false));
    entries.add(option("settings.compass.signal.interference.biome.check-on", "BOTH",
            "SELF", "TARGET", "BOTH"));
    entries.add(bool("settings.compass.signal.interference.movement.enabled", false));
    entries.add(floatVal("settings.compass.signal.interference.movement.threshold-blocks",
            0.2, 0.0, null));
    entries.add(option("settings.compass.signal.interference.movement.check-on", "SELF",
            "SELF", "TARGET", "BOTH"));
    entries.add(bool("settings.compass.signal.interference.line-of-sight.enabled", false));
    entries.add(option("settings.compass.signal.interference.line-of-sight.interfere-when", "VISIBLE",
            "VISIBLE", "NOT_VISIBLE"));
    entries.add(intVal("settings.compass.signal.interference.line-of-sight.max-ray-distance",
            300, 1, 1000));
    addInvisibleEntries(entries);
    addPlayerStatsEntries(entries);
}

private static void addInvisibleEntries(List<SettingDescriptor> entries) {
    entries.add(bool("settings.compass.signal.interference.invisible.enabled", true));
    entries.add(option("settings.compass.signal.interference.invisible.check-on", "BOTH",
            "SELF", "TARGET", "BOTH"));
}

private static void addPlayerStatsEntries(List<SettingDescriptor> entries) {
    String root = "settings.compass.signal.interference.player-stats.";
    entries.add(bool(root + "health.enabled", false));
    entries.add(intVal(root + "health.min-health", 8, 1, 100));
    entries.add(option(root + "health.check-on", "SELF", "SELF", "TARGET", "BOTH"));
    entries.add(bool(root + "hunger.enabled", false));
    entries.add(intVal(root + "hunger.min-hunger", 10, 1, 20));
    entries.add(option(root + "hunger.check-on", "SELF", "SELF", "TARGET", "BOTH"));
    entries.add(bool(root + "experience.enabled", false));
    entries.add(intVal(root + "experience.min-exp-level", 5, 1, 100));
    entries.add(option(root + "experience.check-on", "SELF", "SELF", "TARGET", "BOTH"));
}
}
