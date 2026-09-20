package com.jruk8.jmanhunt.command;

import java.util.List;

/**
 * Result of resolving drill-down path segments against the live config:
 * the canonical path, whether it is an editable leaf or a section, and
 * any segments left over after the longest resolvable prefix.
 */
public record DrillResolve(String path, boolean leaf, boolean section, List<String> remainder) {
}