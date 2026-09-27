package com.jruk8.jmanhunt.compass;

import java.util.UUID;

/** One trackable player identity, with no location attached. */
public record CompassIdentity(UUID id, String name) {
}
