package com.jruk8.jmanhunt.command;

/**
 * Named-player delivery behind {@code <pmessage>},
 * {@code <psound>}, {@code <ptitle>}, and {@code <pslot>} sets: the
 * tags name their audience, so delivery works in console-evaluated
 * runs with no executor. Implemented by the match and compass
 * layers so tags stay pure; managers own every Bukkit call. False
 * means the player is offline; callers warn.
 */
public interface PlayerSinks {

    /** Sends text to one online player, false when offline. */
    boolean message(String playerName, String text);

    /** Plays one sound for one online player, false when offline. */
    boolean sound(String playerName, String soundId, float pitch, float volume);

    /**
     * Sends a center-screen title to one online player: title plus
     * subtitle in written order, then stay, fade-in, and fade-out in
     * seconds. False when offline. Defaults to missing.
     */
    default boolean title(String playerName, String title, String subtitle, double staySeconds,
            double inSeconds, double outSeconds) {
        return false;
    }

    /**
     * Replaces one slot's contents for one online player, clamping
     * qty to 1 through the material max stack. The material key
     * arrives raw; backends match it case-blind. False when the
     * player is offline or the material is unknown, with no change.
     * Defaults to missing.
     */
    default boolean setSlot(String playerName, RosterValues.InventorySlot slot, String materialKey,
            int qty) {
        return false;
    }

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
