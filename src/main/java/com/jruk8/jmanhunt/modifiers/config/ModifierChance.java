package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;

/** Success chance per activation, from 0.0 to 1.0. Ignored by cleanup. */
@SuppressWarnings("FieldMayBeFinal")
public class ModifierChance extends OkaeriConfig {

    private Double chance;
    private String behavior;

    public Double getChance() {
        return chance;
    }

    public void setChance(Double chance) {
        this.chance = chance;
    }

    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }
}
