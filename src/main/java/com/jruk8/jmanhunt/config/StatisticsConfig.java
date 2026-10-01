package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;
import lombok.Getter;
import lombok.Setter;

/** Career statistics storage. */
@Getter
@Setter
@SuppressWarnings("FieldMayBeFinal")
public class StatisticsConfig extends OkaeriConfig {

    private boolean enabled = true;

    @Comment("SQLITE is local; POSTGRESQL shares statistics across servers. Case-insensitive.")
    private String type = "sqlite";

    private Sqlite sqlite = new Sqlite();
    private Postgresql postgresql = new Postgresql();

    @CustomKey("pool-size")
    private int poolSize = 4;

    /** SQLite file settings. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Sqlite extends OkaeriConfig {
        private String file = "statistics.db";

    }

    /** PostgreSQL connection settings. */
    @Getter
    @Setter
    @SuppressWarnings("FieldMayBeFinal")
    public static class Postgresql extends OkaeriConfig {
        private String host = "localhost";
        private int port = 5432;
        private String database = "jmanhunt";
        private String username = "jmanhunt";
        private String password = "change-me";
        private boolean ssl = false;

    }
}
