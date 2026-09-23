package com.jruk8.jmanhunt.updatechecker;

/** A found update: the admin-facing message plus the new version. */
public record UpdateAvailable(String message, String version) {
}
