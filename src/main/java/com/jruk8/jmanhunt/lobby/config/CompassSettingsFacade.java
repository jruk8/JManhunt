package com.jruk8.jmanhunt.lobby.config;

import com.jruk8.jmanhunt.compass.SignalInterference;
import com.jruk8.jmanhunt.config.CompassSettings;
import java.util.List;
import java.util.Locale;

/**
 * Typed lobby-aware reads for the settings.compass subtree. Each method
 * resolves one override path: the lobby value wins, the typed global
 * section is the fallback. Callers take this facade, never raw paths.
 */
public final class CompassSettingsFacade {
    private static final String BASE = "settings.compass.";

    private final OverrideService overrides;
    private final CompassSettings global;

    public CompassSettingsFacade(OverrideService overrides, CompassSettings global) {
        this.overrides = overrides;
        this.global = global;
    }
    /** Compass item id from obtaining.item. */
    public String obtainingItem(Integer lobby) {
        return overrides.getString(lobby, BASE + "obtaining.item", global.getObtaining().getItem());
    }
    /** True when hunters receive a compass. */
    public boolean givenToHunters(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "obtaining.given-to.hunters",
                global.getObtaining().getGivenTo().isHunters());
    }
    /** True when speedrunners receive a compass. */
    public boolean givenToSpeedrunners(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "obtaining.given-to.speedrunners",
                global.getObtaining().getGivenTo().isSpeedrunners());
    }
    /** True when the compass drops on death instead of vanishing. */
    public boolean dropOnDeathEnabled(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "obtaining.drop-on-death.enabled",
                global.getObtaining().getDropOnDeath().isEnabled());
    }
    /** True when the compass must stay in the inventory to track. */
    public boolean lockToInventory(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "lock-to-inventory", global.isLockToInventory());
    }
    /** True when automatic refreshes run. */
    public boolean autoEnabled(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "actions.auto.enabled", global.getActions().getAuto().isEnabled());
    }
    /** Seconds between automatic refreshes. */
    public double autoIntervalSeconds(Integer lobby) {
        return overrides.getDouble(lobby, BASE + "actions.auto.interval", global.getActions().getAuto().getInterval());
    }
    /** Random jitter applied to the auto interval. */
    public double autoDeviationSeconds(Integer lobby) {
        return overrides.getDouble(lobby, BASE + "actions.auto.deviation",
                global.getActions().getAuto().getDeviation());
    }
    /** True when manual refresh analysis runs. */
    public boolean analysisEnabled(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "actions.manual.analysis.enabled",
                global.getActions().getManual().getAnalysis().isEnabled());
    }
    /** Seconds an analysis takes before the compass updates. */
    public double analysisDelaySeconds(Integer lobby) {
        return overrides.getDouble(lobby, BASE + "actions.manual.analysis.delay-seconds",
                global.getActions().getManual().getAnalysis().getDelaySeconds());
    }
    /** Jitter applied to the analysis delay. */
    public double analysisDelayDeviationSeconds(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "actions.manual.analysis.delay-deviation-seconds",
                global.getActions().getManual().getAnalysis().getDelayDeviationSeconds());
    }
    /** Seconds between analysis tick sounds. */
    public double analysisSoundIntervalSeconds(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "actions.manual.analysis.sound-interval-seconds",
                global.getActions().getManual().getAnalysis()
                        .getSoundIntervalSeconds());
    }
    /** True when doomed analyses finish early. */
    public boolean cancelEarlyEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "actions.manual.analysis.cancel-early.enabled",
                global.getActions().getManual().getAnalysis().getCancelEarly().isEnabled());
    }
    /** Fraction of remaining time a doomed analysis keeps. */
    public double cancelEarlyTimeMultiplier(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "actions.manual.analysis.cancel-early.time-multiplier",
                global.getActions().getManual().getAnalysis().getCancelEarly()
                        .getTimeMultiplier());
    }
    /** True when analysis debuff commands run. */
    public boolean analysisDebuffsEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "actions.manual.analysis.debuffs.enabled",
                global.getActions().getManual().getAnalysis().getDebuffs().isEnabled());
    }
    /** Shared debuff commands for every analyzing holder. */
    public List<String> debuffCommandsPlayer(Integer lobby) {
 return overrides.getStringList(lobby, BASE + "actions.manual.analysis.debuffs.commands.player");
    }
    /** Extra debuff commands for analyzing hunters. */
    public List<String> debuffCommandsHunter(Integer lobby) {
 return overrides.getStringList(lobby, BASE + "actions.manual.analysis.debuffs.commands.hunter");
    }
    /** Extra debuff commands for analyzing speedrunners. */
    public List<String> debuffCommandsSpeedrunner(Integer lobby) {
 return overrides.getStringList(lobby, BASE + "actions.manual.analysis.debuffs.commands.speedrunner");
    }
    /** True when analyses charge the holder. */
    public boolean analysisCostEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "actions.manual.analysis.cost.enabled",
                global.getActions().getManual().getAnalysis().getCost().isEnabled());
    }
    /** When to charge: INITIATE, SUCCESS, or BOTH. */
    public String analysisCostOn(Integer lobby) {
        return overrides.getString(lobby,
                BASE + "actions.manual.analysis.cost.cost-on",
                global.getActions().getManual().getAnalysis().getCost().getCostOn());
    }
    /** Seconds a holder who cannot pay must wait before refreshing again. */
    public double analysisFailureCooldownSeconds(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "actions.manual.analysis.cost.payment.failure-cooldown",
                global.getActions().getManual().getAnalysis().getCost().getPayment()
                        .getFailureCooldown());
    }
    /** True when the saturation charge applies. */
    public boolean saturationChargeEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "actions.manual.analysis.cost.payment.saturation.enabled",
                global.getActions().getManual().getAnalysis().getCost().getPayment()
                        .getSaturation().isEnabled());
    }
    /** Saturation points drained per charge. */
    public int saturationChargeValue(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "actions.manual.analysis.cost.payment.saturation.value",
                global.getActions().getManual().getAnalysis().getCost().getPayment()
                        .getSaturation().getValue());
    }
    /** True when the health charge applies. */
    public boolean healthChargeEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "actions.manual.analysis.cost.payment.health.enabled",
                global.getActions().getManual().getAnalysis().getCost().getPayment()
                        .getHealth().isEnabled());
    }
    /** Health points drained per charge. */
    public int healthChargeValue(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "actions.manual.analysis.cost.payment.health.value",
                global.getActions().getManual().getAnalysis().getCost().getPayment()
                        .getHealth().getValue());
    }
    /** True when the health charge can kill. */
    public boolean healthChargeCanKill(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "actions.manual.analysis.cost.payment.health.can-kill",
                global.getActions().getManual().getAnalysis().getCost().getPayment()
                        .getHealth().isCanKill());
    }
    /** True when the experience charge applies. */
    public boolean expChargeEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "actions.manual.analysis.cost.payment.exp-level.enabled",
                global.getActions().getManual().getAnalysis().getCost().getPayment()
                        .getExpLevel().isEnabled());
    }
    /** Experience levels drained per charge. */
    public int expChargeValue(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "actions.manual.analysis.cost.payment.exp-level.value",
                global.getActions().getManual().getAnalysis().getCost().getPayment()
                        .getExpLevel().getValue());
    }
    /** True when a holder who cannot pay never starts the analysis. */
    public boolean povertyCancelWhenPoor(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "actions.manual.analysis.cost.poverty-behavior.cancel-when-poor",
                global.getActions().getManual().getAnalysis().getCost()
                        .getPovertyBehavior().isCancelWhenPoor());
    }
    /** True when a cancelled analysis names each lacking charge. */
    public boolean povertyShowReason(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "actions.manual.analysis.cost.poverty-behavior.show-reason",
                global.getActions().getManual().getAnalysis().getCost()
                        .getPovertyBehavior().isShowReason());
    }
    /** Seconds between accepted teammate switches. */
    public double teammateSwitchCooldownSeconds(Integer lobby) {
        return overrides.getDouble(lobby, BASE + "actions.teammates.switch-cooldown",
                global.getActions().getTeammates().getSwitchCooldown());
    }
    /** True when the hunter min-distance cutoff applies. */
    public boolean hunterMinDistanceEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "distance-limits.hunter.min-distance.enabled",
                global.getDistanceLimits().getHunter().getMinDistance().isEnabled());
    }
    /** Flat hunter tracking cutoff in blocks. */
    public double hunterMinDistance(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "distance-limits.hunter.min-distance.distance",
                global.getDistanceLimits().getHunter().getMinDistance().getDistance());
    }
    /** True when the hunter max-distance cutoff applies. */
    public boolean hunterMaxDistanceEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "distance-limits.hunter.max-distance.enabled",
                global.getDistanceLimits().getHunter().getMaxDistance().isEnabled());
    }
    /** Hunter tracking range in blocks, -1 for unlimited. */
    public double hunterMaxDistance(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "distance-limits.hunter.max-distance.distance",
                global.getDistanceLimits().getHunter().getMaxDistance().getDistance());
    }
    /** True when the speedrunner min-distance cutoff applies. */
    public boolean speedrunnerMinDistanceEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "distance-limits.speedrunner.min-distance.enabled",
                global.getDistanceLimits().getSpeedrunner().getMinDistance().isEnabled());
    }
    /** Flat speedrunner tracking cutoff in blocks. */
    public double speedrunnerMinDistance(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "distance-limits.speedrunner.min-distance.distance",
                global.getDistanceLimits().getSpeedrunner().getMinDistance().getDistance());
    }
    /** True when the speedrunner max-distance cutoff applies. */
    public boolean speedrunnerMaxDistanceEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "distance-limits.speedrunner.max-distance.enabled",
                global.getDistanceLimits().getSpeedrunner().getMaxDistance().isEnabled());
    }
    /** Speedrunner tracking range in blocks, -1 for unlimited. */
    public double speedrunnerMaxDistance(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "distance-limits.speedrunner.max-distance.distance",
                global.getDistanceLimits().getSpeedrunner().getMaxDistance().getDistance());
    }
    /** True when signal interference can fail tracking. */
    public boolean interferenceEnabled(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "signal.interference.enabled",
                global.getSignal().getInterference().isEnabled());
    }
    /** True when glass and leaves do not count as cover. */
    public boolean undergroundIgnoreTransparent(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.underground.ignore-transparent",
                global.getSignal().getInterference().getUnderground().isIgnoreTransparent());
    }
    /** True when light level can interfere. */
    public boolean lightLevelEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.light-level.enabled",
                global.getSignal().getInterference().getLightLevel().isEnabled());
    }
    /** Minimum sky light before interference. */
    public int lightLevelMinSkyLight(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.interference.light-level.min-sky-light",
                global.getSignal().getInterference().getLightLevel().getMinSkyLight());
    }
    /** Minimum block light before interference. */
    public int lightLevelMinBlockLight(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.interference.light-level.min-block-light",
                global.getSignal().getInterference().getLightLevel().getMinBlockLight());
    }
    /** When dim light interferes: one or both readings unmet. */
    public SignalInterference.InterfereWhen lightLevelInterfereWhen(Integer lobby) {
        return interferenceEnum(SignalInterference.InterfereWhen.class, lobby,
                "light-level.interfere-when", global.getSignal().getInterference().getLightLevel().getInterfereWhen());
    }
    /** Which sides' light must pass. */
    public SignalInterference.CheckOn lightLevelCheckOn(Integer lobby) {
        return interferenceEnum(SignalInterference.CheckOn.class, lobby,
                "light-level.check-on", global.getSignal().getInterference().getLightLevel().getCheckOn());
    }
    /** True when overhead cover can interfere. */
    public boolean undergroundEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.underground.enabled",
                global.getSignal().getInterference().getUnderground().isEnabled());
    }
    /** Solid blocks above tolerated before interference. */
    public int undergroundMaxBlocksAbove(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.interference.underground.max-blocks-above",
                global.getSignal().getInterference().getUnderground().getMaxBlocksAbove());
    }
    /** Which sides' cover must pass. */
    public SignalInterference.CheckOn undergroundCheckOn(Integer lobby) {
        return interferenceEnum(SignalInterference.CheckOn.class, lobby,
                "underground.check-on", global.getSignal().getInterference().getUnderground().getCheckOn());
    }
    /** True when overhead water can interfere. */
    public boolean underwaterEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.underwater.enabled",
                global.getSignal().getInterference().getUnderwater().isEnabled());
    }
    /** Fluid blocks above tolerated before interference. */
    public int underwaterMaxBlocksAbove(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.interference.underwater.max-blocks-above",
                global.getSignal().getInterference().getUnderwater().getMaxBlocksAbove());
    }
    /** Which sides' water must pass. */
    public SignalInterference.CheckOn underwaterCheckOn(Integer lobby) {
        return interferenceEnum(SignalInterference.CheckOn.class, lobby,
                "underwater.check-on", global.getSignal().getInterference().getUnderwater().getCheckOn());
    }
    /** True when altitude outside the band can interfere. */
    public boolean altitudeEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.altitude.enabled",
                global.getSignal().getInterference().getAltitude().isEnabled());
    }
    /** Lower edge of the good-signal height band. */
    public int altitudeMinY(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.interference.altitude.min-y",
                global.getSignal().getInterference().getAltitude().getMinY());
    }
    /** Upper edge of the good-signal height band. */
    public int altitudeMaxY(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.interference.altitude.max-y",
                global.getSignal().getInterference().getAltitude().getMaxY());
    }
    /** Which sides' altitude must pass. */
    public SignalInterference.CheckOn altitudeCheckOn(Integer lobby) {
        return interferenceEnum(SignalInterference.CheckOn.class, lobby,
                "altitude.check-on", global.getSignal().getInterference().getAltitude().getCheckOn());
    }
    /** True when weather can interfere. */
    public boolean weatherEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.weather.enabled",
                global.getSignal().getInterference().getWeather().isEnabled());
    }
    /** Weather buckets that interfere, raw names. */
    public List<String> weatherInterfereDuring(Integer lobby) {
 return overrides.getStringList(lobby, BASE + "signal.interference.weather.interfere-during");
    }
    /** True when biomes can interfere. */
    public boolean biomeEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.biome.enabled",
                global.getSignal().getInterference().getBiome().isEnabled());
    }
    /** Biome keys with bad signal. */
    public List<String> biomeInterfereIn(Integer lobby) {
 return overrides.getStringList(lobby, BASE + "signal.interference.biome.interfere-in");
    }
    /** Which sides' biome must pass. */
    public SignalInterference.CheckOn biomeCheckOn(Integer lobby) {
        return interferenceEnum(SignalInterference.CheckOn.class, lobby,
                "biome.check-on", global.getSignal().getInterference().getBiome().getCheckOn());
    }
    /** True when holder movement can interfere. */
    public boolean movementEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.movement.enabled",
                global.getSignal().getInterference().getMovement().isEnabled());
    }
    /** Blocks moved from the press spot before interference. */
    public double movementThresholdBlocks(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "signal.interference.movement.threshold-blocks",
                global.getSignal().getInterference().getMovement().getThresholdBlocks());
    }
    /** Which sides' movement must pass. */
    public SignalInterference.CheckOn movementCheckOn(Integer lobby) {
        return interferenceEnum(SignalInterference.CheckOn.class, lobby,
                "movement.check-on", global.getSignal().getInterference().getMovement().getCheckOn());
    }
    /** True when line of sight can interfere. */
    public boolean lineOfSightEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.line-of-sight.enabled",
                global.getSignal().getInterference().getLineOfSight().isEnabled());
    }
    /** When sight interferes: target visible or hidden. */
    public SignalInterference.InterfereWhenVisible lineOfSightInterfereWhen(
            Integer lobby) {
        return interferenceEnum(SignalInterference.InterfereWhenVisible.class, lobby,
                "line-of-sight.interfere-when",
                global.getSignal().getInterference().getLineOfSight().getInterfereWhen());
    }
    /** Hard cap on the sight ray length. */
    public int lineOfSightMaxRayDistance(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.interference.line-of-sight.max-ray-distance",
                global.getSignal().getInterference().getLineOfSight()
                        .getMaxRayDistance());
    }
    /** True when invisibility can interfere. */
    public boolean invisibleEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.invisible.enabled",
                global.getSignal().getInterference().getInvisible().isEnabled());
    }
    /** Which sides' invisibility interferes. */
    public SignalInterference.CheckOn invisibleCheckOn(Integer lobby) {
        return interferenceEnum(SignalInterference.CheckOn.class, lobby,
                "invisible.check-on", global.getSignal().getInterference().getInvisible().getCheckOn());
    }
    /** True when low health can interfere. */
    public boolean statsHealthEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.player-stats.health.enabled",
                global.getSignal().getInterference().getPlayerStats().getHealth()
                        .isEnabled());
    }
    /** Minimum health before interference. */
    public int statsMinHealth(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.interference.player-stats.health.min-health",
                global.getSignal().getInterference().getPlayerStats().getHealth()
                        .getMinHealth());
    }
    /** Which sides' health must pass. */
    public SignalInterference.CheckOn statsHealthCheckOn(Integer lobby) {
        return interferenceEnum(SignalInterference.CheckOn.class, lobby,
                "player-stats.health.check-on", global.getSignal().getInterference()
                        .getPlayerStats().getHealth().getCheckOn());
    }
    /** True when low hunger can interfere. */
    public boolean statsHungerEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.player-stats.hunger.enabled",
                global.getSignal().getInterference().getPlayerStats().getHunger()
                        .isEnabled());
    }
    /** Minimum hunger before interference. */
    public int statsMinHunger(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.interference.player-stats.hunger.min-hunger",
                global.getSignal().getInterference().getPlayerStats().getHunger()
                        .getMinHunger());
    }
    /** Which sides' hunger must pass. */
    public SignalInterference.CheckOn statsHungerCheckOn(Integer lobby) {
        return interferenceEnum(SignalInterference.CheckOn.class, lobby,
                "player-stats.hunger.check-on", global.getSignal().getInterference()
                        .getPlayerStats().getHunger().getCheckOn());
    }
    /** True when low experience can interfere. */
    public boolean statsExperienceEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.player-stats.experience.enabled",
                global.getSignal().getInterference().getPlayerStats().getExperience()
                        .isEnabled());
    }
    /** Minimum experience level before interference. */
    public int statsMinExpLevel(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.interference.player-stats.experience.min-exp-level",
                global.getSignal().getInterference().getPlayerStats().getExperience()
                        .getMinExpLevel());
    }
    /** Which sides' level must pass. */
    public SignalInterference.CheckOn statsExperienceCheckOn(Integer lobby) {
        return interferenceEnum(SignalInterference.CheckOn.class, lobby,
                "player-stats.experience.check-on", global.getSignal().getInterference()
                        .getPlayerStats().getExperience().getCheckOn());
    }
    /** Enabled options that must interfere before tracking fails. */
    public int requiredToFail(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.interference.required-to-fail",
                global.getSignal().getInterference().getRequiredToFail());
    }
    /** Chance a bad signal still tracks anyway. */
    public double chanceToBypass(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "signal.interference.chance-to-bypass",
                global.getSignal().getInterference().getChanceToBypass());
    }
    /** True when a bad signal names its cause in the actionbar. */
    public boolean showReasonInActionbar(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.interference.show-reason-in-actionbar",
                global.getSignal().getInterference().isShowReasonInActionbar());
    }
    /** True when the needle readout drifts. */
    public boolean inaccuracyEnabled(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "signal.inaccuracy.enabled",
                global.getSignal().getInaccuracy().isEnabled());
    }
    /** Hole in the middle of the error donut. */
    public double inaccuracyInnerDeadzone(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "signal.inaccuracy.inner-deadzone",
                global.getSignal().getInaccuracy().getInnerDeadzone());
    }
    /** How far the readout can wander, as a share of true distance. */
    public double inaccuracyDriftRadius(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "signal.inaccuracy.drift-radius",
                global.getSignal().getInaccuracy().getDriftRadius());
    }
    /** Meters past which the error kicks in. */
    public double inaccuracyMinDistance(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "signal.inaccuracy.thresholds.min-distance",
                global.getSignal().getInaccuracy().getThresholds().getMinDistance());
    }
    /** Meters past which the error stops growing. */
    public double inaccuracyMaxDistance(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "signal.inaccuracy.thresholds.max-distance",
                global.getSignal().getInaccuracy().getThresholds().getMaxDistance());
    }
    /** Which readout drifts, raw name. */
    public String inaccuracyTarget(Integer lobby) {
        return overrides.getString(lobby,
                BASE + "signal.inaccuracy.inaccurate-on",
                global.getSignal().getInaccuracy().getInaccurateOn().name());
    }
    /** True when lingering targets shrink the error donut. */
    public boolean hotspotEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "signal.inaccuracy.accuracy-hotspot.enabled",
                global.getSignal().getInaccuracy().getAccuracyHotspot().isEnabled());
    }
    /** Seconds between recorded hotspot positions. */
    public int hotspotSampleInterval(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.inaccuracy.accuracy-hotspot.sample-interval",
                global.getSignal().getInaccuracy().getAccuracyHotspot()
                        .getSampleInterval());
    }
    /** Past positions remembered per target. */
    public int hotspotMaxPoints(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "signal.inaccuracy.accuracy-hotspot.max-points",
                global.getSignal().getInaccuracy().getAccuracyHotspot()
                        .getMaxPoints());
    }
    /** Meters within which points count as the same hotspot. */
    public double hotspotRadius(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "signal.inaccuracy.accuracy-hotspot.hotspot-radius",
                global.getSignal().getInaccuracy().getAccuracyHotspot()
                        .getHotspotRadius());
    }
    /** Inside share needed for the full hotspot effect. */
    public double hotspotFullAccuracyFraction(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "signal.inaccuracy.accuracy-hotspot.full-accuracy-fraction",
                global.getSignal().getInaccuracy().getAccuracyHotspot()
                        .getFullAccuracyFraction());
    }
    /** Error fraction removed at full hotspot effect. */
    public double hotspotMaxReduction(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "signal.inaccuracy.accuracy-hotspot.max-reduction",
                global.getSignal().getInaccuracy().getAccuracyHotspot()
                        .getMaxReduction());
    }
    /** True when tracking bars show the distance. */
    public boolean actionbarShowDistance(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "feedback.actionbar.show-distance",
                global.getFeedback().getActionbar().isShowDistance());
    }
    /** True when tracking bars show the accuracy percent. */
    public boolean accuracyEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "feedback.actionbar.show-accuracy.enabled",
                global.getFeedback().getActionbar().getShowAccuracy().isEnabled());
    }
    /** Hex color at zero accuracy. */
    public String accuracyInaccurateColor(Integer lobby) {
        return overrides.getString(lobby,
                BASE + "feedback.actionbar.show-accuracy.inaccurate-color",
                global.getFeedback().getActionbar().getShowAccuracy()
                        .getInaccurateColor());
    }
    /** Hex color at full accuracy. */
    public String accuracyAccurateColor(Integer lobby) {
        return overrides.getString(lobby,
                BASE + "feedback.actionbar.show-accuracy.accurate-color",
                global.getFeedback().getActionbar().getShowAccuracy()
                        .getAccurateColor());
    }
    /** True when tracking bars show delta triangles. */
    public boolean deltaEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "feedback.actionbar.show-distance-delta.enabled",
                global.getFeedback().getActionbar().getShowDistanceDelta()
                        .isEnabled());
    }
    /** Distance past which delta colors stop. */
    public double deltaMaxDistance(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "feedback.actionbar.show-distance-delta.max-distance",
                global.getFeedback().getActionbar().getShowDistanceDelta()
                        .getMaxDistance());
    }
    /** Deltas below this render the plain distance. */
    public double deltaMinDeltaToShow(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "feedback.actionbar.show-distance-delta.min-delta-to-show",
                global.getFeedback().getActionbar().getShowDistanceDelta()
                        .getMinDeltaToShow());
    }
    /** How long the delta shows, raw name: HOLD or BLINK. */
    public String deltaMode(Integer lobby) {
        return overrides.getString(lobby,
                BASE + "feedback.actionbar.show-distance-delta.mode",
                global.getFeedback().getActionbar().getShowDistanceDelta()
                        .getMode());
    }
    /** Seconds a BLINK delta stays visible. */
    public double deltaBlinkDurationSeconds(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "feedback.actionbar.show-distance-delta.blink-duration-seconds",
                global.getFeedback().getActionbar().getShowDistanceDelta()
                        .getBlinkDurationSeconds());
    }
    /** True when hunters see swapped delta formats. */
    public boolean deltaReverseOnHunter(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "feedback.actionbar.show-distance-delta.reverse-on-hunter",
                global.getFeedback().getActionbar().getShowDistanceDelta()
                        .isReverseOnHunter());
    }
    /** Format when the rounded distance grew. */
    public String deltaFurtherFormat(Integer lobby) {
        return overrides.getString(lobby,
                BASE + "feedback.actionbar.show-distance-delta.further-format",
                global.getFeedback().getActionbar().getShowDistanceDelta()
                        .getFurtherFormat());
    }
    /** Format when the rounded distance shrank. */
    public String deltaCloserFormat(Integer lobby) {
        return overrides.getString(lobby,
                BASE + "feedback.actionbar.show-distance-delta.closer-format",
                global.getFeedback().getActionbar().getShowDistanceDelta()
                        .getCloserFormat());
    }
    /** True when right-click manual refreshes run. */
    public boolean manualEnabled(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "actions.manual.enabled",
                global.getActions().getManual().isEnabled());
    }
    /** Seconds between accepted refresh clicks. */
    public double manualCooldownSeconds(Integer lobby) {
        return overrides.getDouble(lobby, BASE + "actions.manual.cooldown",
                global.getActions().getManual().getCooldown());
    }
    /** True when left-click cycles a manual target lock. */
    public boolean targetCyclingEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "actions.target-cycling.enabled",
                global.getActions().getTargetCycling().isEnabled());
    }
    /** Candidates the manual lock cycles through at most. */
    public int targetCyclingMaxTargets(Integer lobby) {
        return overrides.getInt(lobby,
                BASE + "actions.target-cycling.max-targets",
                global.getActions().getTargetCycling().getMaxTargets());
    }
    /** Seconds between manual target scrolls. */
    public double targetCyclingScrollCooldownSeconds(Integer lobby) {
        return overrides.getDouble(lobby,
                BASE + "actions.target-cycling.scroll-cooldown",
                global.getActions().getTargetCycling().getScrollCooldown());
    }
    /** True when shift-left-click toggles teammate tracking. */
    public boolean teammatesEnabled(Integer lobby) {
        return overrides.getBoolean(lobby, BASE + "actions.teammates.enabled",
                global.getActions().getTeammates().isEnabled());
    }
    /** True when compass actions chat the holder. */
    public boolean chatMessagesEnabled(Integer lobby) {
        return overrides.getBoolean(lobby,
                BASE + "feedback.chat-messages.enabled",
                global.getFeedback().getChatMessages().isEnabled());
    }
    private static final String INTERFERENCE = "signal.interference.";

    /** Resolves one interference enum leaf, failing closed to the typed global. */
    private <T extends Enum<T>> T interferenceEnum(Class<T> type, Integer lobby, String leaf,
            T fallback) {
        return parseEnum(type, overrides.getString(lobby, BASE + INTERFERENCE + leaf,
                fallback.name()), fallback);
    }

    /** Parses an override enum, failing closed to the typed global. */
    private static <T extends Enum<T>> T parseEnum(Class<T> type, String raw,
            T fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }
}
