package com.jruk8.jmanhunt.command;

/** Parsed quickstart arguments: an optional percentage plus the force flag. */
public record QuickStartArgs(Integer percent, boolean force, boolean valid) {
}