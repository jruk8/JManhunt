package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;

/** Update checker toggles. */
@SuppressWarnings("FieldMayBeFinal")
public class UpdateCheckerConfig extends OkaeriConfig {

    @Comment("Default: true")
    private boolean enabled = true;

    @Comment("Which release severities notify. Hotfixes are silent by default.")
    private Releases releases = new Releases();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Releases getReleases() {
        return releases;
    }

    public void setReleases(Releases releases) {
        this.releases = releases;
    }

    /** Per-severity release toggles. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Releases extends OkaeriConfig {
        private boolean major = true;
        private boolean minor = true;
        private boolean hotfix = false;

        public boolean isMajor() {
            return major;
        }

        public void setMajor(boolean major) {
            this.major = major;
        }

        public boolean isMinor() {
            return minor;
        }

        public void setMinor(boolean minor) {
            this.minor = minor;
        }

        public boolean isHotfix() {
            return hotfix;
        }

        public void setHotfix(boolean hotfix) {
            this.hotfix = hotfix;
        }
    }
}