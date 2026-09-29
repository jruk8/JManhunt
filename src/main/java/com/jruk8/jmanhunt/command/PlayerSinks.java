package com.jruk8.jmanhunt.command;

/**
 * Named-player delivery behind {@code <pmessage>} and
 * {@code <psound>}: the tags name their audience, so delivery works
 * in console-evaluated runs with no executor. Implemented by the
 * match and compass layers so tags stay pure; managers own every
 * Bukkit call. False means the player is offline; callers warn.
 */
public interface PlayerSinks {

    /** Sends text to one online player, false when offline. */
    boolean message(String playerName, String text);

    /** Plays one sound for one online player, false when offline. */
    boolean sound(String playerName, String soundId, float pitch, float volume);

    /** Sinks that deliver nothing: every lookup misses. */
    static PlayerSinks inert() {
        return new PlayerSinks() {
            @Override
            public boolean message(String playerName, String text) {
                return false;
            }

            @Override
            public boolean sound(String playerName, String soundId, float pitch, float volume) {
                return false;
            }
        };
    }
}
