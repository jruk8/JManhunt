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
    final List<BukkitTask> tasks = new ArrayList<>();
    final List<BukkitTask> delayed = new ArrayList<>();
    /** Bumped every time interval chains are (re)started so stale firings stop. */
    long generation;
    /** Modifiers with per-executor timing: name to player ids owning a chain. */
    final Map<String, Set<UUID>> executors = new HashMap<>();
    /** Modifiers with per-executor timing that currently own a console chain. */
    final Set<String> consoleChained = new HashSet<>();
}
