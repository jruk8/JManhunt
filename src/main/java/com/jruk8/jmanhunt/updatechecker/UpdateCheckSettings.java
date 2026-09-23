package com.jruk8.jmanhunt.updatechecker;

/** Update checker toggles: master switch plus per-severity switches. */
public record UpdateCheckSettings(boolean enabled, boolean major, boolean minor, boolean hotfix) {
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
