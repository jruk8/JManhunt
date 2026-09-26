package com.jruk8.jmanhunt.gui.menus;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Author head profiles for the shared Meta quad. Unset authors show the
 * default player head; the plugin author shows the owner's head by UUID
 * (permanent, unlike names); every other author keeps the default head.
 */
public final class AuthorHeads {

    /** Display author identifying the plugin itself in bundled entries. */
    public static final String PLUGIN_AUTHOR = "JManhunt";

    /** Minecraft UUID of the plugin owner, shown for the plugin author. */
    public static final String OWNER_PROFILE_ID = "8786a40f-8856-4d0e-8ad5-0c9d7a13d2e9";

    private AuthorHeads() {
    }

    /**
     * Head profile UUID for an author, or empty for the default head.
     * Unset, blank, and "none" authors all map to the default head. Pure
     * for tests.
     */
    public static Optional<UUID> profileId(String author) {
        if (author == null || author.isBlank() || author.equalsIgnoreCase("none")) {
            return Optional.empty();
        }
        if (PLUGIN_AUTHOR.equals(author)) {
            return Optional.of(UUID.fromString(OWNER_PROFILE_ID));
        }
        return Optional.empty();
    }

    /**
     * Applies the author head profile to skull meta. Non-skull meta is
     * untouched, and default-head authors need no profile since a fresh
     * head has none.
     */
    public static void applyTo(ItemMeta meta, String author) {
        if (!(meta instanceof SkullMeta skull)) {
            return;
        }
        profileId(author).ifPresent(id -> skull.setOwnerProfile(Bukkit.createPlayerProfile(id)));
    }
}
