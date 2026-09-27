package com.jruk8.jmanhunt.stats;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Map;

/**
 * Speedrunner progression: the rank of the highest reliable vanilla
 * advancement milestone a player holds. Higher won more: Got Iron (2)
 * outranks Got Wood (1). Shared by the end-screen stats and the
 * spectator spawn pick, so both rank identically.
 */
public final class ProgressionRank {

    /** Milestone keys in rank order, 1-based by position. */
    public static final Map<String, String> MILESTONES = Map.of(
            "got_wood", "story/mine_wood",
            "got_iron", "story/smelt_iron",
            "entered_nether", "story/enter_the_nether",
            "found_bastion", "nether/find_bastion",
            "found_fortress", "nether/find_fortress",
            "entered_stronghold", "story/follow_ender_eye",
            "entered_end", "story/enter_the_end");

    /** Ordered milestone keys, rank 1 first. */
    public static final List<String> ORDERED_KEYS = List.of(
            "got_wood", "got_iron", "entered_nether", "found_bastion",
            "found_fortress", "entered_stronghold", "entered_end");

    private ProgressionRank() {
    }

    /** Rank value plus the milestone key that earned it, if any. */
    public record Rank(int value, String key) {
    }

    /** Highest held milestone rank; zero with a null key when none. */
    public static Rank of(Player player) {
        int value = 0;
        String key = null;
        int rank = 0;
        for (String milestone : ORDERED_KEYS) {
            rank++;
            var advancement = Bukkit.getAdvancement(
                    new NamespacedKey("minecraft", MILESTONES.get(milestone)));
            if (advancement != null && player.getAdvancementProgress(advancement).isDone()) {
                value = rank;
                key = milestone;
            }
        }
        return new Rank(value, key);
    }
}
