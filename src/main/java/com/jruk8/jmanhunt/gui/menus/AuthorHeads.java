package com.jruk8.jmanhunt.gui.menus;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

/**
 * Author head profiles for the shared Meta quad. Authors resolve in
 * order: unset/blank/"none" show the default head, the plugin author
 * shows the owner's head by UUID, full and trimmed UUIDs show the
 * referenced profile (the client fetches the skin), and anything else
 * is a player name resolved against online and cached players.
 */
public final class AuthorHeads {

    /** Display author identifying the plugin itself in bundled entries. */
    public static final String PLUGIN_AUTHOR = "JManhunt";

    /** Minecraft UUID of the plugin owner, shown for the plugin author. */
    public static final String OWNER_PROFILE_ID = "8786a40f-8856-4d0e-8ad5-0c9d7a13d2e9";

    private AuthorHeads() {
    }

    /**
     * Head profile UUID for a UUID-form author, or empty for the default
     * head and for names (names resolve through Bukkit instead). Accepts
     * full dashed UUIDs and trimmed 32-hex strings. Pure for tests.
     */
    public static Optional<UUID> profileId(String author) {
        if (author == null || author.isBlank() || author.equalsIgnoreCase("none")) {
            return Optional.empty();
        }
        String trimmed = author.trim();
        if (PLUGIN_AUTHOR.equals(trimmed)) {
            return Optional.of(UUID.fromString(OWNER_PROFILE_ID));
        }
        try {
            return Optional.of(UUID.fromString(trimmed));
        } catch (IllegalArgumentException ignored) {
            // Not a dashed UUID; try the trimmed form below.
        }
        if (trimmed.matches("[0-9a-fA-F]{32}")) {
            return Optional.of(UUID.fromString(trimmed.substring(0, 8) + "-"
                    + trimmed.substring(8, 12) + "-" + trimmed.substring(12, 16) + "-"
                    + trimmed.substring(16, 20) + "-" + trimmed.substring(20, 32)));
        }
        return Optional.empty();
    }

    /**
     * Applies the author head profile to skull meta. Non-skull meta is
     * untouched, and default-head authors need no profile since a fresh
     * head has none. Names resolve to a cached UUID when one is known;
     * unknown names keep a name-only profile (default head, no crash).
     */
    public static void applyTo(ItemMeta meta, String author) {
        if (!(meta instanceof SkullMeta skull)) {
            return;
        }
        Optional<UUID> id = profileId(author);
        if (id.isPresent()) {
            skull.setOwnerProfile(Bukkit.createPlayerProfile(id.get()));
            return;
        }
        nameProfile(author).ifPresent(skull::setOwnerProfile);
    }

    private static Optional<PlayerProfile> nameProfile(String author) {
        if (author == null || author.isBlank() || author.equalsIgnoreCase("none")) {
            return Optional.empty();
        }
        String name = author.trim();
        UUID id = Bukkit.getPlayerUniqueId(name);
        if (id != null) {
            return Optional.of(Bukkit.createPlayerProfile(id));
        }
        return Optional.of(Bukkit.createPlayerProfile(name));
    }
}
