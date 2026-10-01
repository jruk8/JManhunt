package com.jruk8.jmanhunt.updatechecker;

/** Update checker toggles: master switch plus per-severity switches. */
public record UpdateCheckSettings(boolean enabled, boolean major, boolean minor, boolean hotfix) {
    /** Reads the toggles from the update-checker section. */
    public static UpdateCheckSettings fromRoot(com.jruk8.jmanhunt.config.UpdateCheckerConfig checker) {
        var releases = checker.getReleases();
        return new UpdateCheckSettings(checker.isEnabled(), releases.isMajor(),
                releases.isMinor(), releases.isHotfix());
    }

    /** True when the severity ("major", "minor", "hotfix") may notify. */
    public boolean allows(String severity) {
        return switch (severity) {
            case "major" -> major;
            case "minor" -> minor;
            case "hotfix" -> hotfix;
            default -> false;
        };
    }
}
