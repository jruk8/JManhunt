package com.jruk8.jmanhunt.compass;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure analysis-cost math. Bukkit-free: callers gather the holder's
 * stats, resolve the payment config, and apply the computed charge.
 */
public final class AnalysisCost {

    private AnalysisCost() {
    }

    /** Holder stats for one charge: health, saturation, food, exp level. */
    public record Stats(double health, double saturation, int foodLevel, int expLevel) {
    }

    /** Resolved payment config for one charge. */
    public record Payment(boolean saturationEnabled, int saturationValue,
            boolean healthEnabled, int healthValue, boolean canKill,
            boolean expEnabled, int expValue) {
    }

    /** Post-charge values, in the same order as the stats. */
    public record Charge(double health, double saturation, int foodLevel, int expLevel) {
    }

    /**
     * True when the cost-on option charges at the given point
     * (INITIATE or SUCCESS), matched case-insensitively. BOTH matches
     * both points; null and unrecognized values fall back to INITIATE.
     * Pure.
     */
    public static boolean chargesAt(String costOn, String point) {
        if (point == null) {
            return false;
        }
        if (point.equalsIgnoreCase("SUCCESS")) {
            return "SUCCESS".equalsIgnoreCase(costOn) || "BOTH".equalsIgnoreCase(costOn);
        }
        if (point.equalsIgnoreCase("INITIATE")) {
            return !"SUCCESS".equalsIgnoreCase(costOn);
        }
        return false;
    }

    /**
     * Cost types an enabled payment actually applies (ids matching the
     * cost-used sound suffixes), in health, hunger, experience order.
     * Zero-valued containers apply nothing. Pure.
     */
    public static List<String> appliedTypes(Payment payment) {
        List<String> types = new ArrayList<>();
        if (payment.healthEnabled() && payment.healthValue() > 0) {
            types.add("health");
        }
        if (payment.saturationEnabled() && payment.saturationValue() > 0) {
            types.add("saturation");
        }
        if (payment.expEnabled() && payment.expValue() > 0) {
            types.add("exp");
        }
        return types;
    }

    /**
     * Signal-reason ids for the enabled charges the stats cannot cover,
     * in health, hunger, experience order. Pure.
     */
    public static List<String> lacking(Stats stats, Payment payment) {
        List<String> lacking = new ArrayList<>();
        if (payment.healthEnabled() && stats.health() < payment.healthValue()) {
            lacking.add("low-health");
        }
        if (payment.saturationEnabled()
                && stats.saturation() + stats.foodLevel() < payment.saturationValue()) {
            lacking.add("hungry");
        }
        if (payment.expEnabled() && stats.expLevel() < payment.expValue()) {
            lacking.add("low-exp-level");
        }
        return lacking;
    }

    /**
     * Post-charge values. Saturation drains from the 0-40 hunger pool:
     * hidden saturation first, then the visible bar. Health floors at
     * half a heart unless can-kill allows death, and never below zero;
     * levels floor at zero. Short pools drain dry instead of going
     * negative. Pure.
     */
    public static Charge charge(Stats stats, Payment payment) {
        double saturation = stats.saturation();
        int foodLevel = stats.foodLevel();
        if (payment.saturationEnabled()) {
            double remaining = Math.max(0, payment.saturationValue());
            double satTake = Math.min(saturation, remaining);
            saturation -= satTake;
            remaining -= satTake;
            foodLevel -= (int) Math.min(foodLevel, remaining);
        }
        double health = stats.health();
        if (payment.healthEnabled()) {
            health -= Math.max(0, payment.healthValue());
            health = Math.max(payment.canKill() ? 0.0 : 1.0, health);
        }
        int expLevel = stats.expLevel();
        if (payment.expEnabled()) {
            expLevel = Math.max(0, expLevel - Math.max(0, payment.expValue()));
        }
        return new Charge(health, saturation, foodLevel, expLevel);
    }
}
