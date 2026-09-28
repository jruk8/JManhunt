package com.jruk8.jmanhunt.command;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Evaluation context for extended tags: the match scope plus the
 * container id ({@code <id>}), the message/sound sinks, and the
 * stat/flag backends. Managers build one per dispatch run with
 * {@link #run}; the parser stays pure behind it.
 */
public final class TagContext {

    /** Match id for runs outside any match: stats miss, flags gate. */
    public static final long NO_MATCH = -1L;

    /** Plays one sound for its audience. */
    public interface SoundSink {
        void play(String soundId, float pitch, float volume);
    }

    /**
     * Where one tag line comes from: the modifier id, the behavior
     * index in that modifier, the command list name, and the 0-based
     * line index in that list. Managers stamp the current line before
     * each evaluation; loop-limit and null-line diagnostics read it.
     * Non-modifier runs (compass debuffs) use a negative behavior
     * index and the dispatch name for both id and list.
     */
    public record Provenance(String modifierId, int behaviorIndex, String listName, int lineIndex) {
        /** Base provenance before any line runs: line unknown. */
        public static Provenance of(String modifierId, int behaviorIndex, String listName) {
            return new Provenance(modifierId, behaviorIndex, listName, -1);
        }

        /** Same source with the 0-based line index filled in. */
        public Provenance withLine(int lineIndex) {
            return new Provenance(modifierId, behaviorIndex, listName, lineIndex);
        }

        /**
         * One-line source for severe logs: the behavior part drops
         * out for non-modifier runs.
         */
        public String describe() {
            if (behaviorIndex < 0) {
                return "modifier '" + modifierId + "', list '" + listName
                        + "', line " + lineIndex + " (0-based)";
            }
            return "modifier '" + modifierId + "', behavior " + behaviorIndex
                    + ", list '" + listName + "', line " + lineIndex + " (0-based)";
        }
    }

    private final ModifierTagScope scope;
    private final String containerId;
    private final Consumer<String> globalMessage;
    private final Consumer<String> playerMessage;
    private final SoundSink globalSound;
    private final SoundSink playerSound;
    private final BiConsumer<String, String> losePlayer;
    private final BiConsumer<String, String> winMatch;
    private final long matchId;
    private final TagBackends backends;
    private final List<String> eventArgs;
    private final Map<String, String> localFlags;
    private final Deque<String> loopItems;
    private final Consumer<String> loopLimit;
    private final Consumer<String> roleMessage;
    private final SoundSink roleSound;
    private Provenance provenance;

    private TagContext(ModifierTagScope scope, String containerId,
            Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound,
            BiConsumer<String, String> losePlayer, BiConsumer<String, String> winMatch,
            long matchId, TagBackends backends, List<String> eventArgs,
            Map<String, String> localFlags, Consumer<String> loopLimit,
            Consumer<String> roleMessage, SoundSink roleSound) {
        this.scope = scope;
        this.containerId = containerId;
        this.globalMessage = globalMessage;
        this.playerMessage = playerMessage;
        this.globalSound = globalSound;
        this.playerSound = playerSound;
        this.losePlayer = losePlayer;
        this.winMatch = winMatch;
        this.matchId = matchId;
        this.backends = backends;
        this.eventArgs = eventArgs;
        this.localFlags = localFlags;
        this.loopItems = new ArrayDeque<>();
        this.loopLimit = loopLimit;
        this.roleMessage = roleMessage;
        this.roleSound = roleSound;
        this.provenance = Provenance.of(containerId, -1, "");
    }

    /**
     * Full run context for one modifier or debuff dispatch: match id
     * (or {@link #NO_MATCH}), shared backends, and a fresh
     * {@code <lflag>} map that dies with the run. Event args default
     * to empty (every {@code <args>} index reads {@code "null"}).
     */
    public static TagContext run(ModifierTagScope scope, String containerId,
            Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound,
            BiConsumer<String, String> losePlayer, BiConsumer<String, String> winMatch,
            long matchId, TagBackends backends) {
        return run(scope, containerId, globalMessage, playerMessage,
                globalSound, playerSound, losePlayer, winMatch,
                matchId, backends, List.of());
    }

    /**
     * Full run context with trigger event args behind
     * {@code <args:index>}. The list is copied and frozen. Loop
     * limits resolve to {@code "null"} with no match response.
     */
    public static TagContext run(ModifierTagScope scope, String containerId,
            Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound,
            BiConsumer<String, String> losePlayer, BiConsumer<String, String> winMatch,
            long matchId, TagBackends backends, List<String> eventArgs) {
        return run(scope, containerId, globalMessage, playerMessage,
                globalSound, playerSound, losePlayer, winMatch,
                matchId, backends, eventArgs, detail -> { });
    }

    /**
     * Full run context with a loop-limit sink behind over-step
     * {@code <while>} and {@code <for>} loops: managers log, tell
     * the match, and cancel it. Role tags stay silent.
     */
    public static TagContext run(ModifierTagScope scope, String containerId,
            Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound,
            BiConsumer<String, String> losePlayer, BiConsumer<String, String> winMatch,
            long matchId, TagBackends backends, List<String> eventArgs,
            Consumer<String> loopLimit) {
        return run(scope, containerId, globalMessage, playerMessage,
                globalSound, playerSound, losePlayer, winMatch,
                matchId, backends, eventArgs, loopLimit,
                text -> { }, (id, pitch, volume) -> { });
    }

    /**
     * Full run context with role sinks behind {@code <rmessage>} and
     * {@code <rsound>}: managers reach the executor role members.
     */
    public static TagContext run(ModifierTagScope scope, String containerId,
            Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound,
            BiConsumer<String, String> losePlayer, BiConsumer<String, String> winMatch,
            long matchId, TagBackends backends, List<String> eventArgs,
            Consumer<String> loopLimit, Consumer<String> roleMessage, SoundSink roleSound) {
        return new TagContext(scope, containerId, globalMessage, playerMessage,
                globalSound, playerSound, losePlayer, winMatch,
                matchId, backends, List.copyOf(eventArgs), new HashMap<>(), loopLimit,
                roleMessage, roleSound);
    }

    /** Full context for one modifier or debuff dispatch. */
    public static TagContext of(ModifierTagScope scope, String containerId,
            Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound) {
        return new TagContext(scope, containerId, globalMessage, playerMessage,
                globalSound, playerSound, (player, reason) -> { }, (role, reason) -> { },
                NO_MATCH, TagBackends.inert(), List.of(), new HashMap<>(), detail -> { },
                text -> { }, (id, pitch, volume) -> { });
    }

    /** Inert context for scope-only callers: empty id, silent sinks. */
    public static TagContext inert(ModifierTagScope scope) {
        return new TagContext(scope, "", text -> { }, text -> { },
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                (player, reason) -> { }, (role, reason) -> { },
                NO_MATCH, TagBackends.inert(), List.of(), new HashMap<>(), detail -> { },
                text -> { }, (id, pitch, volume) -> { });
    }

    public ModifierTagScope scope() {
        return scope;
    }

    public String containerId() {
        return containerId;
    }

    /** Match backing stats and flags, or {@link #NO_MATCH}. */
    public long matchId() {
        return matchId;
    }

    /** Stat values behind {@code <pstat>} and {@code <gstat>}. */
    public StatValues statValues() {
        return backends.stats();
    }

    /** Shared store behind {@code <gflag>} and {@code <pflag>}. */
    public FlagStore flagStore() {
        return backends.flags();
    }

    /** Placeholder expansion behind raw spans and {@code <placeholder>}. */
    public PlaceholderResolver placeholders() {
        return backends.placeholders();
    }

    /** This run's {@code <lflag>} map, discarded after dispatch. */
    public Map<String, String> localFlags() {
        return localFlags;
    }

    /** Match roster reads for roster tags. */
    public RosterValues roster() {
        return backends.roster();
    }

    /** This trigger's event args behind {@code <args:index>}. */
    public List<String> eventArgs() {
        return eventArgs;
    }

    /** Source line stamped by the manager before each evaluation. */
    public Provenance provenance() {
        return provenance;
    }

    /** Stamps the source line; managers call this per line. */
    public void setProvenance(Provenance provenance) {
        this.provenance = provenance;
    }

    /** Pushes one for-loop item; nested loops see the innermost. */
    public void pushLoopItem(String item) {
        loopItems.push(item);
    }

    /** Pops one for-loop item; callers pair pushes with pops. */
    public void popLoopItem() {
        loopItems.pop();
    }

    /** Innermost for-loop item, or empty outside any for loop. */
    public Optional<String> loopItem() {
        return loopItems.isEmpty() ? Optional.empty() : Optional.of(loopItems.peek());
    }

    /** Fires the loop-limit response behind an over-step loop. */
    public void loopLimitExceeded(String detail) {
        loopLimit.accept(detail);
    }

    public void sendGlobalMessage(String text) {
        globalMessage.accept(text);
    }

    public void sendPlayerMessage(String text) {
        playerMessage.accept(text);
    }

    /** Sends one message to the executor role members. */
    public void sendRoleMessage(String text) {
        roleMessage.accept(text);
    }

    public void playGlobalSound(String soundId, float pitch, float volume) {
        globalSound.play(soundId, pitch, volume);
    }

    public void playPlayerSound(String soundId, float pitch, float volume) {
        playerSound.play(soundId, pitch, volume);
    }

    /** Plays one sound for the executor role members. */
    public void playRoleSound(String soundId, float pitch, float volume) {
        roleSound.play(soundId, pitch, volume);
    }

    /** Eliminates one player by name, behind {@code <loseplayer>}. */
    public void losePlayer(String playerName, String reason) {
        losePlayer.accept(playerName, reason);
    }

    /**
     * Ends the match for one role, behind {@code <win:ROLE,reason>}.
     * The role is the canonical upper-case name.
     */
    public void winMatch(String role, String reason) {
        winMatch.accept(role, reason);
    }
}
