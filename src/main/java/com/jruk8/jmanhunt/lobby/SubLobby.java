package com.jruk8.jmanhunt.lobby;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One sublobby: a queue epoch within a parent lobby. Under the SUBLOBBY
 * policy the parent never hosts directly; every match runs as
 * L{lobby-id}-{sublobby-id}, with sublobby ids starting at 0.
 */
public record SubLobby(int parentLobbyId, int subId) {
    private static final Pattern FORMAT = Pattern.compile("^L(\\d+)-(\\d+)$");

    /** Renders as L{lobby-id}-{sublobby-id}. Pure for tests. */
    public String format() {
        return "L" + parentLobbyId + "-" + subId;
    }

    /** Parses the format() shape back; empty on anything else. Pure for tests. */
    public static Optional<SubLobby> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(raw.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SubLobby(
                    Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
        } catch (NumberFormatException overlarge) {
            return Optional.empty();
        }
    }
}
