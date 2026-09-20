package com.jruk8.jmanhunt.match;

/**
 * Alternate win conditions, each owned by one side. Speedrunners get the
 * exit, survival clock, item, advancement, and mob conditions; hunters get
 * the expiry clock, item, and mob conditions.
 */
public enum WinCondition {
    EXIT_END,
    SURVIVE_TIME,
    ACQUIRE_ITEM,
    REACH_ADVANCEMENT,
    KILL_MOB,
    TIME_LIMIT
}
