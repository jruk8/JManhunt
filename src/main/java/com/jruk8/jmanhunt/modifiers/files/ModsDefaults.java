package com.jruk8.jmanhunt.modifiers.files;

import java.util.List;

/**
 * Bundled default ids per kind, matching the files under
 * {@code src/main/resources/mods/}. A unit test asserts both
 * directions so the list and the resources cannot drift.
 */
public final class ModsDefaults {

    /** Bundled modifier ids, seeded when mods/modifiers is created. */
    public static final List<String> MODIFIERS = List.of(
            "everyone-gets-beef",
            "speedrunners-speed-potion",
            "hunters-wooden-sword",
            "hunter-start-debuffs",
            "speedrunner-start-buffs",
            "full-leather-kit",
            "speedrunner-health-advantage",
            "perma-night",
            "random-mob-spawner",
            "random-item-giver",
            "random-start-resources",
            "gear-dice",
            "regen-on-kill",
            "diamond-on-advancement",
            "fireres-on-nether-enter",
            "tpall-on-end",
            "start-with-luckyblocks-requires-challenges-addon",
            "get-stronger-on-kill",
            "speedrunner-gapple-on-low-hp");

    /** Bundled preset ids, seeded when mods/presets is created. */
    public static final List<String> PRESETS = List.of(
            "vanilla-plus",
            "speedrunner-buffs",
            "chaos-mode");

    private ModsDefaults() {
    }

    /** Bundled default ids for one kind. */
    public static List<String> ids(ModFileKind kind) {
        return kind == ModFileKind.MODIFIER ? MODIFIERS : PRESETS;
    }
}
