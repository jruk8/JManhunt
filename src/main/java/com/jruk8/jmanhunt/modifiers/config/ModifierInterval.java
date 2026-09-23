package com.jruk8.jmanhunt.modifiers.config;

import eu.okaeri.configs.OkaeriConfig;

/** Interval cadence in seconds, plus jitter and timer behavior. */
@SuppressWarnings("FieldMayBeFinal")
public class ModifierInterval extends OkaeriConfig {

    private Double interval;
    private Double deviation;
    private String behavior;

    public Double getInterval() {
        return interval;
    }

    public void setInterval(Double interval) {
        this.interval = interval;
    }

    public Double getDeviation() {
        return deviation;
    }

    public void setDeviation(Double deviation) {
        this.deviation = deviation;
    }

    public String getBehavior() {
        return behavior;
    }

    public void setBehavior(String behavior) {
        this.behavior = behavior;
    }
}
