package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;

/** How many lines PICK_RANDOM draws, and per whom it draws them. */
@SuppressWarnings("FieldMayBeFinal")
public class ModifierPickRandom extends OkaeriConfig {

    private Integer count;
    private String behavior;

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }
}
