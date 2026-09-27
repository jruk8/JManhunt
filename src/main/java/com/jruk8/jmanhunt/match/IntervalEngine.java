package com.jruk8.jmanhunt.match;

import org.bukkit.scheduler.BukkitTask;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-match interval engine state; each match runs its own chains. */
final class IntervalEngine {

    /** One interval chain owner: a modifier behavior by name and index. */
    record BehaviorChain(String name, int index) {
    }

    final List<BukkitTask> tasks = new ArrayList<>();
    final List<BukkitTask> delayed = new ArrayList<>();
    /** Bumped every time interval chains are (re)started so stale firings stop. */
    long generation;
    /** Behaviors with per-executor timing: chain to player ids owning a chain. */
    final Map<BehaviorChain, Set<UUID>> executors = new HashMap<>();
    /** Behaviors with per-executor timing that currently own a console chain. */
    final Set<BehaviorChain> consoleChained = new HashSet<>();
}
