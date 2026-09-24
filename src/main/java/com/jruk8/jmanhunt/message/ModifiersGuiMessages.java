package com.jruk8.jmanhunt.message;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Modifiers GUI labels. */
@SuppressWarnings("FieldMayBeFinal")
public class ModifiersGuiMessages extends OkaeriConfig {

    @CustomKey("title-main")
    private String titleMain = "Modifiers";

    @CustomKey("title-modifiers")
    private String titleModifiers = "Modifiers";

    @CustomKey("title-presets")
    private String titlePresets = "Presets";

    @CustomKey("to-modifiers")
    private String toModifiers = "Modifiers";

    @CustomKey("to-modifiers-lore")
    private String toModifiersLore = "{enabled}/{total} enabled";

    @CustomKey("to-presets")
    private String toPresets = "Presets";

    @CustomKey("to-presets-lore")
    private String toPresetsLore = "{enabled}/{total} enabled";

    @CustomKey("scroll-up")
    private String scrollUp = "Scroll up";

    @CustomKey("scroll-down")
    private String scrollDown = "Scroll down";

    @CustomKey("back")
    private String back = "Back";

    @CustomKey("toggle-all")
    private String toggleAll = "Toggle all";

    @CustomKey("toggle-all-modifiers-lore")
    private String toggleAllModifiersLore = "{total} modifiers";

    @CustomKey("toggle-all-presets-lore")
    private String toggleAllPresetsLore = "{total} presets";

    @CustomKey("preset-more")
    private String presetMore = "and {count} more";

    @CustomKey("state-on")
    private String stateOn = "<green>Enabled";

    @CustomKey("state-off")
    private String stateOff = "<red>Disabled";
}
