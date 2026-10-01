package com.jruk8.jmanhunt.compass;

import com.jruk8.jmanhunt.command.CommandPlaceholders;
import com.jruk8.jmanhunt.command.EngineEscapes;
import com.jruk8.jmanhunt.command.FlagStore;
import com.jruk8.jmanhunt.command.ModifierTagScope;
import com.jruk8.jmanhunt.command.PlaceholderResolver;
import com.jruk8.jmanhunt.command.QuietConsoleDispatch;
import com.jruk8.jmanhunt.command.RosterValues;
import com.jruk8.jmanhunt.command.StatValues;
import com.jruk8.jmanhunt.command.TagBackends;
import com.jruk8.jmanhunt.command.TagControlFlow;
import com.jruk8.jmanhunt.command.TagContext;
import com.jruk8.jmanhunt.command.TagExpressions;
import com.jruk8.jmanhunt.core.JManhuntLogger;
import com.jruk8.jmanhunt.core.JManhuntPlaceholders;
import com.jruk8.jmanhunt.core.PlaceholderPass;
import com.jruk8.jmanhunt.core.TaskScheduler;
import com.jruk8.jmanhunt.lobby.config.CompassSettingsFacade;
import com.jruk8.jmanhunt.match.GameInstance;
import com.jruk8.jmanhunt.match.GameManager;
import com.jruk8.jmanhunt.match.MatchRosterValues;
import com.jruk8.jmanhunt.match.NamedPlayerSinks;
import com.jruk8.jmanhunt.message.CompassMessages;
import com.jruk8.jmanhunt.message.MessageService;
import com.jruk8.jmanhunt.message.ModifiersMessages;
import com.jruk8.jmanhunt.message.SoundService;
import com.jruk8.jmanhunt.player.FakeSpectatorService;
import com.jruk8.jmanhunt.player.PlayerStateStore;
import com.jruk8.jmanhunt.player.Role;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Analysis runs: the lag timer before a right-click refresh resolves,
 * plus the configured debuff commands. Extracted from the lock
 * service; locks keep the lock, scroll, and teammate logic.
 */
final class CompassAnalysisRunner {
    /** Chat, sound, and log output. */
    record RunnerFeedback(MessageService messages, CompassMessages compass,
            ModifiersMessages modifiers, SoundService sounds, JManhuntLogger log) {
    }

    /** Resolution callbacks plus the timer they run on. */
    record RunnerCallbacks(Consumer<Player> clickResolver, Consumer<Player> refresher,
            Consumer<Player> analysisStarter, AnalysisHost analysisHost, TaskScheduler tasks) {
    }

    /** Bars and click stamps shared with the facade. */
    record RunnerShared(Map<UUID, Component> actionbars, Map<UUID, Long> sharedClicks) {
    }

    /** Role, visibility, and placeholder reads. */
    record RunnerData(PlayerStateStore states, FakeSpectatorService fakes,
            java.util.function.Supplier<JManhuntPlaceholders> placeholders) {
    }

    private final CompassSettingsFacade settings;
    private final RunnerFeedback feedback;
    private final RunnerCallbacks callbacks;
    private final RunnerShared shared;
    private final RunnerData data;
    private final Set<UUID> analyzing = new HashSet<>();
    /** Analysis generation per holder; stale tick tasks cancel themselves. */
    private final Map<UUID, Long> generations = new HashMap<>();
    private GameManager game;

    CompassAnalysisRunner(CompassSettingsFacade settings, RunnerFeedback feedback,
            RunnerCallbacks callbacks, RunnerShared shared, RunnerData data) {
        this.settings = settings;
        this.feedback = feedback;
        this.callbacks = callbacks;
        this.shared = shared;
        this.data = data;
    }

    /** Wires the game after construction; doom and costs need matches. */
    void setGameManager(GameManager game) {
        this.game = game;
    }

    /** Origin lobby of the holder's match, or null outside matches. */
    private Integer lobbyOf(Player holder) {
        return game == null ? null : game.lobbyOfPlayer(holder.getUniqueId());
    }

    /** True while the holder's analysis runs. */
    boolean isAnalyzing(UUID holderId) {
        return analyzing.contains(holderId);
    }

    /**
     * Purposeful analysis lag before a right-click refresh resolves:
     * shows "Analyzing...", ticks the analysis sound on the configured
     * interval, counts one repeating timer down, then refreshes. Each
     * tick samples movement and requires the compass in the main hand;
     * every tenth tick re-checks doom. No second analysis starts while
     * one runs. Runs stamp the shared click cooldown at resolution, so
     * the full cooldown runs after the refresh, and close with the
     * outcome click sound.
     */
    void startAnalysis(Player holder) {
        UUID id = holder.getUniqueId();
        if (!analyzing.add(id)) {
            return;
        }
        callbacks.analysisStarter().accept(holder);
        long generation = generations.merge(id, 1L, Long::sum);
        Integer lobby = lobbyOf(holder);
        double effectiveDelay = AnalysisTiming.jitteredDelay(
                settings.analysisDelaySeconds(lobby),
                settings.analysisDelayDeviationSeconds(lobby),
                ThreadLocalRandom.current().nextDouble());
        double multiplier = cancelEarlyMultiplier(lobby);
        if (callbacks.analysisHost().analysisDoomed(holder)) {
            effectiveDelay = effectiveDelay * multiplier;
        }
        runAnalysisDebuffs(holder, effectiveDelay);
        shared.actionbars().put(id, feedback.messages()
                .componentRaw(feedback.compass().getAnalyzingActionbar()));
        feedback.sounds().playSound(holder, "compass.analysis");
        long intervalTicks = AnalysisTiming.analysisTickInterval(
                CompassLockService.clampedSoundInterval(
                        settings.analysisSoundIntervalSeconds(lobby)));
        long[] remaining = {AnalysisTiming.analyzeDelayTicks(effectiveDelay)};
        long[] elapsed = {0L};
        boolean[] doomed = {false};
        callbacks.tasks().runTimer(task -> {
            if (!analyzing.contains(id) || generations.getOrDefault(id, 0L) != generation) {
                task.cancel();
                return;
            }
            if (tickAnalysisOnline(task, id, holder, remaining, elapsed, doomed, multiplier,
                    intervalTicks)) {
                return;
            }
            remaining[0]--;
            if (remaining[0] <= 0L) {
                task.cancel();
                resolveAnalysis(holder, id);
            }
        }, 1L, 1L);
    }

    /** One online analysis tick; true when the run ended inside it. */
    private boolean tickAnalysisOnline(BukkitTask task, UUID id, Player holder, long[] remaining,
            long[] elapsed, boolean[] doomed, double multiplier, long intervalTicks) {
        if (!holder.isOnline()) {
            return false;
        }
        callbacks.analysisHost().sampleAnalysisMovement(holder);
        if (!callbacks.analysisHost().isMainhandCompass(holder)) {
            cancelAnalysis(task, id, holder);
            return true;
        }
        elapsed[0]++;
        if (elapsed[0] % 10L == 0L) {
            boolean nowDoomed = callbacks.analysisHost().analysisDoomed(holder);
            if (nowDoomed && !doomed[0]) {
                remaining[0] = AnalysisTiming.shortenedTicks(remaining[0], multiplier);
            }
            doomed[0] = nowDoomed;
        }
        if (elapsed[0] % intervalTicks == 0L) {
            feedback.sounds().playSound(holder, "compass.analysis");
        }
        return false;
    }

    /** Cancels an analysis, pushing the bar at once for compassless holders. Always on. */
    private void cancelAnalysis(BukkitTask task, UUID id, Player holder) {
        task.cancel();
        analyzing.remove(id);
        generations.merge(id, 1L, Long::sum);
        shared.sharedClicks().put(id, System.currentTimeMillis());
        callbacks.analysisHost().cancelAnalysisSnapshots(id);
        CompassMessages compass = feedback.compass();
        shared.actionbars().put(id, feedback.messages().componentRaw(
                compass.getBadSignalReasonActionbar(), Map.of("reason",
                        compass.getSignalReason().getOrDefault("cancelled", "cancelled"))));
        if (holder.isOnline()) {
            holder.sendActionBar(shared.actionbars().get(id));
            feedback.sounds().playSound(holder, "compass.failure");
        }
    }

    /** Completes an in-flight analysis, charging SUCCESS costs first. */
    private void resolveAnalysis(Player holder, UUID id) {
        analyzing.remove(id);
        if (!AnalysisResolution.stampClickOnSuccessfulCost(callbacks.analysisHost(),
                shared.sharedClicks(), holder, id)) {
            return;
        }
        if (holder.isOnline()) {
            callbacks.clickResolver().accept(holder);
        } else {
            callbacks.refresher().accept(holder);
        }
        boolean live = game != null && game.instanceOf(holder.getUniqueId()).isPresent();
        if (!live || !data.states().role(holder).isParticipant()) {
            shared.actionbars().remove(id);
        }
    }

    /** Cancel-early multiplier: 1.0 when the option is disabled. */
    private double cancelEarlyMultiplier(Integer lobby) {
        if (!settings.cancelEarlyEnabled(lobby)) {
            return 1.0;
        }
        double multiplier = settings.cancelEarlyTimeMultiplier(lobby);
        return Math.min(1.0, Math.max(0.0, multiplier));
    }

    /**
     * Runs the configured analysis debuff commands for a participant
     * holder: the shared player list plus their own role list, resolved
     * modifier-style and dispatched as console.
     */
    private void runAnalysisDebuffs(Player holder, double effectiveDelaySeconds) {
        Integer lobby = lobbyOf(holder);
        if (!settings.analysisDebuffsEnabled(lobby)) {
            return;
        }
        Role holderRole = data.states().role(holder);
        if (!holderRole.isParticipant()) {
            return;
        }
        double delaySeconds = effectiveDelaySeconds;
        List<String> commands = debuffCommands(lobby, holderRole);
        Location location = holder.getLocation();
        TagContext context = debuffContext(holder);
        for (int lineIndex = 0; lineIndex < commands.size(); lineIndex++) {
            String command = commands.get(lineIndex);
            stampProvenance(context, lineIndex);
            if (command.isBlank()) {
                continue;
            }
            if (TagControlFlow.isExitMisuse(command)) {
                context.scope().warn("'exit' must stand alone on its line, skipping: " + command);
                continue;
            }
            try {
                String parsed = CommandPlaceholders.replace(
                        CommandPlaceholders.withDuration(command, delaySeconds),
                        holder.getName(), location.getX(), location.getY(), location.getZ(), context);
                parsed = context.placeholders().resolve(parsed, holder.getName());
                if (TagControlFlow.isExit(parsed)) {
                    return;
                }
                if (TagControlFlow.isExitMisuse(parsed)) {
                    context.scope().warn("'exit' must stand alone on its line, skipping: " + command);
                    continue;
                }
                dispatchDebuffLine(parsed, context);
            } catch (Exception exception) {
                feedback.log().severe(
                        "Failed to run analysis debuff command '" + command + "'. Skipping..",
                        exception);
            }
        }
    }

    /** Stamps the debuff line provenance for loop-limit diagnostics. */
    private static void stampProvenance(TagContext context, int lineIndex) {
        context.setProvenance(TagContext.Provenance.of("debuffs", -1, "debuffs").withLine(lineIndex));
    }

    /**
     * Dispatches one parsed debuff line as console. A line that
     * resolved to pure {@code "null"} warns with the source line and
     * never dispatches.
     */
    private void dispatchDebuffLine(String parsed, TagContext context) {
        Optional<String> dispatchable = TagControlFlow.dispatchableLine(parsed);
        if (dispatchable.isPresent() && TagControlFlow.isPureNull(dispatchable.get())) {
            feedback.log().warning("Skipping command that resolved to pure \"null\" at "
                    + context.provenance().describe() + ".");
            return;
        }
        dispatchable.ifPresent(QuietConsoleDispatch::dispatch);
    }

    /** Shared player debuffs plus the holder's own role list. */
    private List<String> debuffCommands(Integer lobby, Role holderRole) {
        List<String> commands = new ArrayList<>(
                settings.debuffCommandsPlayer(lobby));
        commands.addAll(holderRole == Role.HUNTER
                ? settings.debuffCommandsHunter(lobby)
                : settings.debuffCommandsSpeedrunner(lobby));
        return commands;
    }

    /** Tag context for one debuff run: {@code <id>} is {@code debuffs}. */
    private TagContext debuffContext(Player holder) {
        ModifierTagScope scope = ModifierTagScope.executor(holder.getName(),
                EngineEscapes.restoring(feedback.log()::warning));
        long matchId = game == null ? TagContext.NO_MATCH
                : game.instanceOf(holder.getUniqueId()).map(GameInstance::matchId)
                        .orElse(TagContext.NO_MATCH);
        StatValues stats = game == null ? StatValues.inert() : game.matchStatValues(matchId);
        FlagStore flags = game == null ? new FlagStore() : game.flagStore();
        JManhuntPlaceholders available = data.placeholders().get();
        PlaceholderResolver placeholderPass = available == null
                ? PlaceholderResolver.inert()
                : new PlaceholderPass(available);
        RosterValues roster = game == null ? RosterValues.inert()
                : new MatchRosterValues(game, data.states(), data.fakes(), matchId);
        MessageService messages = feedback.messages();
        SoundService sounds = feedback.sounds();
        TagBackends backends = new TagBackends(stats, flags, placeholderPass, roster,
                NamedPlayerSinks.of(messages, feedback.modifiers(), sounds,
                        feedback.log()::warning, "debuffs"));
        return TagContext.run(new TagContext.TagIdentity(scope, "debuffs"),
                TagContext.TagSinks.simple(
                        text -> messages.broadcastText(formatEngineMessage(text)),
                        text -> messages.sendText(holder, formatEngineMessage(text)),
                        (soundId, pitch, volume) -> playGlobalSound(soundId, pitch, volume),
                        (soundId, pitch, volume) -> {
                            if (!sounds.isValidSound(soundId)) {
                                warnInvalidSound(soundId);
                                return;
                            }
                            sounds.playCustomSound(holder, soundId, pitch, volume);
                        },
                        scope),
                new TagContext.TagRole(
                        (role, text) -> scope.warn(
                                "Tag <rmessage> only works in modifiers: skipped."),
                        (role, soundId, pitch, volume) -> scope.warn(
                                "Tag <rsound> only works in modifiers: skipped.")),
                new TagContext.TagMatch(matchId, backends, List.of(),
                        detail -> loopLimitExceeded(detail, matchId),
                        (target, reason) -> scope.warn(
                                "Tag <loseplayer> only works in modifiers: skipped."),
                        (role, reason) -> scope.warn(
                                "Tag <win> only works in modifiers: skipped.")));
    }

    /**
     * Loop-limit sink for debuff lines: without a live match there is
     * nothing to cancel, so the source line is only logged.
     */
    private void loopLimitExceeded(String detail, long matchId) {
        feedback.log().severe("JMHScript loop exceeded 1000 steps at " + detail);
        if (game == null || matchId == TagContext.NO_MATCH) {
            return;
        }
        Optional<GameInstance> instance = game.instance(matchId);
        if (instance.isEmpty()) {
            return;
        }
        String text = feedback.modifiers().getLoopLimit();
        for (Player player : game.onlineParticipants(matchId)) {
            feedback.messages().sendText(player, text);
        }
        game.cancel(instance.get());
    }

    private String formatEngineMessage(String text) {
        return NamedPlayerSinks.formatEngineMessage(feedback.modifiers(), feedback.messages(), text);
    }

    private void playGlobalSound(String soundId, float pitch, float volume) {
        if (!feedback.sounds().isValidSound(soundId)) {
            warnInvalidSound(soundId);
            return;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            feedback.sounds().playCustomSound(online, soundId, pitch, volume);
        }
    }

    private void warnInvalidSound(String soundId) {
        feedback.log().warning("modifier \"debuffs\" tried playing invalid sound \""
                + soundId + "\"");
    }
}
