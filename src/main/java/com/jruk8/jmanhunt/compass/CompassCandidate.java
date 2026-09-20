package com.jruk8.jmanhunt.compass;

import java.util.UUID;

/** One live, same-world opponent the compass could point at. */
public record CompassCandidate(UUID id, String name, double distance, double flatDistance) {
}
