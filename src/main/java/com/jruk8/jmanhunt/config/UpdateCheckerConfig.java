package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import lombok.Getter;
import lombok.Setter;

/** Update checker toggles. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class UpdateCheckerConfig extends OkaeriConfig {

    @Comment("Default: true")
    private boolean enabled = true;

    @Comment("Which release severities notify. Hotfixes are silent by default.")
    private Releases releases = new Releases();

    /** Per-severity release toggles. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Releases extends OkaeriConfig {
        private boolean major = true;
        private boolean minor = true;
        private boolean hotfix = false;

    }
}
