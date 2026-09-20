package com.jruk8.jmanhunt.command;

import java.util.Optional;

/** Parsed end arguments: an optional instance id plus the immediate flag. */
public record EndArgs(Optional<String> instanceId, boolean immediate, boolean valid) {
}