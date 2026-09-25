package com.jruk8.jmanhunt.command;

import java.util.HashMap;
import java.util.Map;
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

    private final ModifierTagScope scope;
    private final String containerId;
    private final Consumer<String> globalMessage;
    private final Consumer<String> playerMessage;
    private final SoundSink globalSound;
    private final SoundSink playerSound;
    private final long matchId;
    private final TagBackends backends;
    private final Map<String, String> localFlags;

    private TagContext(ModifierTagScope scope, String containerId,
            Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound,
            long matchId, TagBackends backends,
            Map<String, String> localFlags) {
        this.scope = scope;
        this.containerId = containerId;
        this.globalMessage = globalMessage;
        this.playerMessage = playerMessage;
        this.globalSound = globalSound;
        this.playerSound = playerSound;
        this.matchId = matchId;
        this.backends = backends;
        this.localFlags = localFlags;
    }

    /**
     * Full run context for one modifier or debuff dispatch: match id
     * (or {@link #NO_MATCH}), shared backends, and a fresh
     * {@code <lflag>} map that dies with the run.
     */
    public static TagContext run(ModifierTagScope scope, String containerId,
            Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound,
            long matchId, TagBackends backends) {
        return new TagContext(scope, containerId, globalMessage, playerMessage,
                globalSound, playerSound, matchId, backends, new HashMap<>());
    }

    /** Full context for one modifier or debuff dispatch. */
    public static TagContext of(ModifierTagScope scope, String containerId,
            Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound) {
        return new TagContext(scope, containerId, globalMessage, playerMessage,
                globalSound, playerSound, NO_MATCH, TagBackends.inert(), new HashMap<>());
    }

    /** Inert context for scope-only callers: empty id, silent sinks. */
    public static TagContext inert(ModifierTagScope scope) {
        return new TagContext(scope, "", text -> { }, text -> { },
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { },
                NO_MATCH, TagBackends.inert(), new HashMap<>());
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

    public void sendGlobalMessage(String text) {
        globalMessage.accept(text);
    }

    public void sendPlayerMessage(String text) {
        playerMessage.accept(text);
    }

    public void playGlobalSound(String soundId, float pitch, float volume) {
        globalSound.play(soundId, pitch, volume);
    }

    public void playPlayerSound(String soundId, float pitch, float volume) {
        playerSound.play(soundId, pitch, volume);
    }
}
