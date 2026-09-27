package com.jruk8.jmanhunt.command;

import java.util.Optional;

/** Parsed end arguments: an optional instance id or all, plus the immediate flag. */
public record EndArgs(Optional<String> instanceId, boolean immediate, boolean all, boolean valid) {
}
