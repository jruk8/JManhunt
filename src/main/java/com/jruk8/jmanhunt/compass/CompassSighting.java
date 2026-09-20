package com.jruk8.jmanhunt.compass;

import java.util.UUID;

/** One recorded last-seen location the compass could fall back to. */
public record CompassSighting(UUID ownerId, String name, double distance) {
}
