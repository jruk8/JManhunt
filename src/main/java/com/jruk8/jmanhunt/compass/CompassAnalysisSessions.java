package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.lobby.config.CompassSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.message.CompassMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Analysis sessions: press-time snapshots, movement sampling, doom
 * checks, and INITIATE/SUCCESS costs. Implements the host hooks the
 * lock service's timer calls back into.
 */
final class CompassAnalysisSessions implements AnalysisHost {

    /** Poverty feedback chat plus texts.sounds(). */
    record SessionTexts(MessageService messages, CompassMessages compass, SoundService sounds) {
    }

    /** Resolution inputs: targets, verdicts, services.items(). */
    record SessionServices(CompassTargetService targets, CompassSignalService signal,
            CompassItemService items) {
    }

    /** Role plus fake-spectator reads. */
    record SessionPlayers(PlayerStateStore states, FakeSpectatorService fakes) {
    }

    private final CompassSettingsFacade settings;
    private final SessionTexts texts;
    private final SessionServices services;
    private final SessionPlayers players;
    private final Map<UUID, Component> compassActionbars;
    /** Last cost-too-high block per holder; gates refreshes while fresh. */
    private final Map<UUID, Long> lastFailure = new HashMap<>();
    /** Analysis press-time holder spots, consumed by one resolution. */
    private final Map<UUID, Location> analysisSpots = new HashMap<>();
    /** Analysis press-time target spots per holder, consumed by one resolution. */
    private final Map<UUID, Map<UUID, Location>> analysisTargets = new HashMap<>();
    /** Longest squared analysis displacement per holder, consumed by one resolution. */
    private final Map<UUID, Double> analysisMaxSq = new HashMap<>();
    private CompassLockService locks;
    private GameManager game;

    CompassAnalysisSessions(CompassSettingsFacade settings, SessionTexts texts,
            SessionServices services, SessionPlayers players,
            Map<UUID, Component> compassActionbars) {
        this.settings = settings;
        this.texts = texts;
        this.services = services;
        this.players = players;
        this.compassActionbars = compassActionbars;
    }

    /** Failure stamps, exposed for tests. */
    Map<UUID, Long> lastFailureStamps() {
        return lastFailure;
    }

    /** True when a recent cost-too-high block still holds the holder. */
    boolean failureBlocked(UUID holderId, Integer lobby, long now) {
        return failureBlockedAt(now, lastFailure.getOrDefault(holderId, 0L),
                failureCooldownSeconds(lobby));
    }

    /**
     * True when the failure stamp still holds: a positive cooldown with
     * a stamp inside its window. Pure for tests.
     */
    static boolean failureBlockedAt(long now, long lastFailureMillis, double failureSeconds) {
        if (failureSeconds <= 0 || lastFailureMillis <= 0) {
            return false;
        }
        return !CompassManager.shouldRefresh(now, lastFailureMillis, (long) (failureSeconds * 1000));
    }

    /** Wires the locks after construction; doom checks narrow through them. */
    void setLockService(CompassLockService locks) {
        this.locks = locks;
    }

    /** Wires the game after construction so sessions resolve within one match. */
    void setGameManager(GameManager game) {
        this.game = game;
    }

    /** Origin lobby of the holder's match, or null outside matches. */
    private Integer lobbyOf(Player holder) {
        return game == null ? null : game.lobbyOfPlayer(holder.getUniqueId());
    }

    /**
     * Press-time target spot for one pick: null when the pick carries
     * no target id or no snapshot is pending.
     */
    /** True when press-time snapshots are pending for one resolution. */
    boolean hasAnalysisSnapshots(UUID holderId) {
        return analysisSpots.containsKey(holderId);
    }

    Location targetPressSpot(UUID holderId, UUID pickId) {
        if (pickId == null) {
            return null;
        }
        return analysisTargets.getOrDefault(holderId, Map.of()).get(pickId);
    }

/**
 * Snapshots the analysis press: the holder's spot plus every
 * candidate's spot, so the delayed resolution compares
 * press-time positions instead of moved ones.
 */
void beginAnalysisSpot(Player holder) {
    UUID id = holder.getUniqueId();
    analysisSpots.put(id, holder.getLocation().clone());
    analysisMaxSq.put(id, 0.0);
    if (game == null) {
        return;
    }
    Optional<GameInstance> match = game.instanceOf(id);
    if (match.isEmpty()) {
        return;
    }
    int cap = CompassCache.clampMaxTargets(
            settings.targetCyclingMaxTargets(lobbyOf(holder)));
    Map<UUID, Location> spots = new HashMap<>();
    for (CompassSnapshot snap : services.targets().collectSnapshots(holder, Role.HUNTER, match.get(), cap)) {
        spots.put(snap.id(), snap.location());
    }
    for (CompassSnapshot snap : services.targets().collectSnapshots(holder, Role.SPEEDRUNNER, match.get(), cap)) {
        spots.put(snap.id(), snap.location());
    }
    analysisTargets.put(id, spots);
}

@Override
public void sampleAnalysisMovement(Player holder) {
    UUID id = holder.getUniqueId();
    analysisMaxSq.put(id, CompassSignalService.maxSoFar(
            analysisMaxSq.getOrDefault(id, 0.0), analysisSpots.get(id),
            holder.getLocation()));
}

/**
 * Longest analysis displacement in blocks: the sampled maximum
 * folded once more with the live spot, square-rooted a single
 * time. Zero outside analyses.
 */
double analysisMaxMoved(Player holder) {
    UUID id = holder.getUniqueId();
    double maxSq = CompassSignalService.maxSoFar(analysisMaxSq.getOrDefault(id, 0.0),
            analysisSpots.get(id), holder.getLocation());
    return Math.sqrt(maxSq);
}

    /**
     * Doom-check pick: the lock-narrowed opponents and sightings
     * resolved to one pick. Empty when any gate fails.
     */
    private Optional<CompassPick> resolveDoomPick(Player holder) {
        if (CompassManager.isVanillaSpectator(holder) || holder.isDead() || game == null) {
            return Optional.empty();
        }
        Role holderRole = players.states().role(holder);
        if (!holderRole.isParticipant()
                || players.fakes().isFakeSpectator(holder)) {
            return Optional.empty();
        }
        Optional<GameInstance> match = game.instanceOf(holder.getUniqueId());
        if (match.isEmpty()) {
            return Optional.empty();
        }
        Role targetRole = locks.targetRole(holder);
        List<CompassCandidate> opponents =
                services.targets().collectOpponents(holder, targetRole, match.get());
        List<CompassSighting> sightings = services.targets().collectSightings(holder, targetRole,
                match.get(), holder.getLocation());
        CompassLockService.LockedTargets narrowed =
                locks.narrowToLock(holder.getUniqueId(), opponents, sightings);
        return Optional.of(CompassManager.resolveCompassPick(settings,
                match.get().originLobbyId(), holderRole, narrowed.opponents(),
                narrowed.sightings()));
    }

@Override
public boolean analysisDoomed(Player holder) {
    Optional<CompassPick> pick = resolveDoomPick(holder);
    if (pick.isEmpty()) {
        return false;
    }
    // Every available target inside the min gate or past the max
    // gate: resolutions judge press-time snapshots, so a gated
    // press can never recover and exits early like interference.
    if (pick.get().kind() == CompassPick.Kind.NEARBY
            || pick.get().kind() == CompassPick.Kind.TOO_FAR) {
        return true;
    }
    UUID id = holder.getUniqueId();
    Location targetPress = pick.get().id() == null ? null
            : analysisTargets.getOrDefault(id, Map.of()).get(pick.get().id());
    return services.signal().reasonForPick(holder, resolutionSpot(holder), targetPress,
            pick.get(), analysisMaxMoved(holder)).isPresent();
}

@Override
public boolean noLocationAvailable(Player holder) {
    return resolveDoomPick(holder)
            .map(pick -> pick.kind() == CompassPick.Kind.NONE)
            .orElse(false);
}

@Override
public boolean isMainhandCompass(Player holder) {
    return services.items().isCompass(holder.getInventory().getItemInMainHand());
}

@Override
public void cancelAnalysisSnapshots(UUID holderId) {
    analysisSpots.remove(holderId);
    analysisTargets.remove(holderId);
    analysisMaxSq.remove(holderId);
}

@Override
public boolean trySuccessCost(Player holder) {
    if (!holder.isOnline()) {
        return true;
    }
    return tryCost(holder, "SUCCESS");
}

/**
 * Charges the INITIATE cost when configured. False when the holder
 * cannot pay and cancel-when-poor aborts the analysis outright.
 */
boolean tryInitiateCost(Player holder) {
    return tryCost(holder, "INITIATE");
}

/**
 * Charges one cost point: no-op when cost is disabled or the point
 * is not listed. Poor holders abort with an actionbar message when
 * cancel-when-poor holds, else pay whatever they have.
 */
private boolean tryCost(Player holder, String point) {
    Integer lobby = lobbyOf(holder);
    if (!settings.analysisCostEnabled(lobby)) {
        return true;
    }
    if (!AnalysisCost.chargesAt(settings.analysisCostOn(lobby), point)) {
        return true;
    }
    AnalysisCost.Payment payment = costPayment(lobby);
    AnalysisCost.Stats stats = new AnalysisCost.Stats(holder.getHealth(),
            holder.getSaturation(), holder.getFoodLevel(), holder.getLevel());
    List<String> lacking = AnalysisCost.lacking(stats, payment);
    if (!lacking.isEmpty() && settings.povertyCancelWhenPoor(lobby)) {
        showCostTooHigh(holder, lacking, settings.povertyShowReason(lobby));
        texts.sounds().playSound(holder, "compass.cost-too-high");
        recordFailure(holder.getUniqueId(), lobby);
        return false;
    }
    AnalysisCost.Charge charge = AnalysisCost.charge(stats, payment);
    holder.setSaturation((float) charge.saturation());
    holder.setFoodLevel(charge.foodLevel());
    holder.setLevel(charge.expLevel());
    holder.setHealth(charge.health());
    playUsedSound(holder, payment);
    return true;
}

    /** Stamps the failure cooldown when it is enabled (positive). */
    private void recordFailure(UUID holderId, Integer lobby) {
        if (failureCooldownSeconds(lobby) > 0) {
            lastFailure.put(holderId, System.currentTimeMillis());
        }
    }

    private double failureCooldownSeconds(Integer lobby) {
        return settings.analysisFailureCooldownSeconds(lobby);
    }

    /** Plays exactly one used sound for the applied cost types, picked at random. */
    private void playUsedSound(Player holder, AnalysisCost.Payment payment) {
        List<String> types = AnalysisCost.appliedTypes(payment);
        if (types.isEmpty()) {
            return;
        }
        texts.sounds().playSound(holder, "compass.cost-used-" + pickUsedType(types, ThreadLocalRandom.current()));
    }

    /**
     * One uniform random pick from the applied cost types. Pure for
     * tests; single-type lists always yield their only type.
     */
    static String pickUsedType(List<String> types, Random random) {
        return types.get(random.nextInt(types.size()));
    }

/** Resolved payment containers for one charge. */
private AnalysisCost.Payment costPayment(Integer lobby) {
    return new AnalysisCost.Payment(
            settings.saturationChargeEnabled(lobby),
            settings.saturationChargeValue(lobby),
            settings.healthChargeEnabled(lobby),
            settings.healthChargeValue(lobby),
            settings.healthChargeCanKill(lobby),
            settings.expChargeEnabled(lobby),
            settings.expChargeValue(lobby));
}

/**
 * Actionbar-only poverty message, with each lacking charge named
 * when show-reason holds. Never chats.
 */
private void showCostTooHigh(Player holder, List<String> lacking, boolean showReason) {
    String text = texts.compass().getAnalysisCostTooHigh();
    if (showReason && !lacking.isEmpty()) {
        String reasons = lacking.stream()
                .map(id -> texts.compass().getSignalReason().getOrDefault(id, id))
                .collect(Collectors.joining(", "));
        text += " (" + reasons + ")";
    }
    compassActionbars.put(holder.getUniqueId(), texts.messages().renderLiteral(text, Map.of()));
}
/** Resolution spot for one holder: press-time when analyzing, live otherwise. */
Location resolutionSpot(Player holder) {
    return CompassManager.effectiveSpot(analysisSpots.get(holder.getUniqueId()), holder.getLocation());
}
}
