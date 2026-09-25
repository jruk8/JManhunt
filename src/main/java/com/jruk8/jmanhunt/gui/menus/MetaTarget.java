package com.jruk8.jmanhunt.gui.menus;

import java.util.Set;
import org.bukkit.Material;

/**
 * Display-data target behind the shared Meta quad: one modifier or one
 * preset. Reads tolerate missing entries as blank defaults; patches
 * assume the entry exists, like the editors that build the targets.
 */
public interface MetaTarget {

    /** Entry id, shown in the rename prompt. */
    String id();

    /** Display name, never blank. */
    String name();

    /** Description, or null when unset. */
    String description();

    /** Menu icon, never air. */
    Material item();

    /** Author line, or null when unset. */
    String author();

    void patchName(String name);

    /** Null clears the description. */
    void patchDescription(String description);

    void patchItem(Material item);

    /** Null clears the author. */
    void patchAuthor(String author);

    /** Sibling ids excluding this entry, for rename validation. */
    Set<String> takenIds();

    void rename(String newId);

    /** Display name of one id, for the renamed confirmation. */
    String displayName(String id);
}
