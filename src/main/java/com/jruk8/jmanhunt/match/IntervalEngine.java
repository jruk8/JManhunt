package com.jruk8.jmanhunt.match;

import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-match interval engine state; each match runs its own chains. */
final class IntervalEngine {

    /** One interval chain owner: a modifier behavior by name and index. */
    record BehaviorChain(String name, int index) {
    }

    /** Running chain tasks by owner; insertion-ordered. */
    final Map<BukkitTask, BehaviorChain> tasks = new LinkedHashMap<>();
    /** Pending delayed dispatches by owner; insertion-ordered. */
    final Map<BukkitTask, BehaviorChain> delayed = new LinkedHashMap<>();
    /** Bumped every time interval chains are (re)started so stale firings stop. */
    long generation;
    /** Behaviors with per-executor timing: chain to player ids owning a chain. */
    final Map<BehaviorChain, Set<UUID>> executors = new HashMap<>();
    /** Behaviors with per-executor timing that currently own a console chain. */
    final Set<BehaviorChain> consoleChained = new HashSet<>();
}
