package com.jruk8.jmanhunt.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.CustomKey;

/** Career statistics storage. */
@SuppressWarnings("FieldMayBeFinal")
public class StatisticsConfig extends OkaeriConfig {

    private boolean enabled = true;

    @Comment("SQLITE is local; POSTGRESQL shares statistics across servers. Case-insensitive.")
    private String type = "sqlite";

    private Sqlite sqlite = new Sqlite();
    private Postgresql postgresql = new Postgresql();

    @CustomKey("pool-size")
    private int poolSize = 4;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Sqlite getSqlite() {
        return sqlite;
    }

    public void setSqlite(Sqlite sqlite) {
        this.sqlite = sqlite;
    }

    public Postgresql getPostgresql() {
        return postgresql;
    }

    public void setPostgresql(Postgresql postgresql) {
        this.postgresql = postgresql;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    /** SQLite file settings. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Sqlite extends OkaeriConfig {
        private String file = "statistics.db";

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }
    }

    /** PostgreSQL connection settings. */
    @SuppressWarnings("FieldMayBeFinal")
    public static class Postgresql extends OkaeriConfig {
        private String host = "localhost";
        private int port = 5432;
        private String database = "jmanhunt";
        private String username = "jmanhunt";
        private String password = "change-me";
        private boolean ssl = false;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isSsl() {
            return ssl;
        }

        public void setSsl(boolean ssl) {
            this.ssl = ssl;
        }
    }
}