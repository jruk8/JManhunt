package com.jruk8.jmanhunt.updatechecker;

import com.jruk8.jmanhunt.updatechecker.ports.UpdateCheckLogger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Three-part plugin version: major.minor.hotfix. Parsing scans the raw
 * text for x.y.z (a leading v and any trailing snapshot gibberish are
 * ignored), falls back to x.y, then x, then 1.0.0 with an error log.
 */
public record UpdateVersion(int major, int minor, int patch) {
    private static final Pattern TRIPLE = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");
    private static final Pattern PAIR = Pattern.compile("(\\d+)\\.(\\d+)");
    private static final Pattern SINGLE = Pattern.compile("(\\d+)");

    /** Parses a version from free text like a release tag. */
    public static UpdateVersion parse(String raw, UpdateCheckLogger logger) {
        String text = raw == null ? "" : raw;
        Matcher triple = TRIPLE.matcher(text);
        if (triple.find()) {
            return new UpdateVersion(number(triple.group(1)), number(triple.group(2)), number(triple.group(3)));
        }
        Matcher pair = PAIR.matcher(text);
        if (pair.find()) {
            return new UpdateVersion(number(pair.group(1)), number(pair.group(2)), 0);
        }
        Matcher single = SINGLE.matcher(text);
        if (single.find()) {
            return new UpdateVersion(number(single.group(1)), 0, 0);
        }
        logger.severe("Could not parse a version from '" + raw + "'; assuming 1.0.0.");
        return new UpdateVersion(1, 0, 0);
    }

    private static int number(String digits) {
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException overflow) {
            return Integer.MAX_VALUE;
        }
    }

    /** True when this version is strictly newer than the other. */
    public boolean isNewerThan(UpdateVersion other) {
        if (major != other.major) {
            return major > other.major;
        }
        if (minor != other.minor) {
            return minor > other.minor;
        }
        return patch > other.patch;
    }

    /**
     * Highest differing component over an older version: major, minor, or
     * hotfix. Null when the versions are equal. Pure for tests.
     */
    public String severityOver(UpdateVersion older) {
        if (major != older.major) {
            return "major";
        }
        if (minor != older.minor) {
            return "minor";
        }
        if (patch != older.patch) {
            return "hotfix";
        }
        return null;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
