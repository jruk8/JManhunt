package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.CustomKey;

/** Shared line-selection settings for one commands block. */
@SuppressWarnings("FieldMayBeFinal")
public class ModifierExecution extends OkaeriConfig {

    private String selection;

    @CustomKey("pick-random")
    private ModifierPickRandom pickRandom;

    public String getSelection() {
        return selection;
    }

    public void setSelection(String selection) {
        this.selection = selection;
    }

    public ModifierPickRandom getPickRandom() {
        return pickRandom;
    }

    public void setPickRandom(ModifierPickRandom pickRandom) {
        this.pickRandom = pickRandom;
    }
}
