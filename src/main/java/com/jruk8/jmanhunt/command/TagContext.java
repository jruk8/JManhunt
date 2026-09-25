package com.jruk8.jmanhunt.command;

import java.util.function.Consumer;

/**
 * Evaluation context for extended tags: the match scope plus the
 * container id ({@code <id>}) and the message/sound sinks. Managers
 * build one per dispatch; the parser stays pure behind it.
 */
public final class TagContext {

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

    private TagContext(ModifierTagScope scope, String containerId,
            Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound) {
        this.scope = scope;
        this.containerId = containerId;
        this.globalMessage = globalMessage;
        this.playerMessage = playerMessage;
        this.globalSound = globalSound;
        this.playerSound = playerSound;
    }

    /** Full context for one modifier or debuff dispatch. */
    public static TagContext of(ModifierTagScope scope, String containerId,
            Consumer<String> globalMessage, Consumer<String> playerMessage,
            SoundSink globalSound, SoundSink playerSound) {
        return new TagContext(scope, containerId, globalMessage, playerMessage,
                globalSound, playerSound);
    }

    /** Inert context for scope-only callers: empty id, silent sinks. */
    public static TagContext inert(ModifierTagScope scope) {
        return new TagContext(scope, "", text -> { }, text -> { },
                (id, pitch, volume) -> { }, (id, pitch, volume) -> { });
    }

    public ModifierTagScope scope() {
        return scope;
    }

    public String containerId() {
        return containerId;
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
