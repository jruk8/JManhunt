package com.jruk8.jmanhunt;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Small always-SQLite store for engine state. It currently owns the
 * world-engine spiral cell index; other internal counters may move here
 * later. Career statistics live in {@link StatisticsRepository} instead.
 */
public final class EngineStateRepository implements AutoCloseable {
    private final HikariDataSource dataSource;

    private EngineStateRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static EngineStateRepository open(File dataFolder) throws SQLException {
        File database = new File(dataFolder, "engine.db");
        if (database.getParentFile() != null) {
            database.getParentFile().mkdirs();
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + database);
        config.setMaximumPoolSize(1);
        EngineStateRepository repository = new EngineStateRepository(new HikariDataSource(config));
        repository.initialize();
        return repository;
    }

    private Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initialize() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS engine_state ("
                    + "state_key VARCHAR(64) PRIMARY KEY, state_value BIGINT NOT NULL)");
        }
    }

    public synchronized long consumeWorldCellIndexes(int amount) throws SQLException {
        int consumed = Math.max(0, amount);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                long current = 0L;
                boolean hasValue;
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT state_value FROM engine_state WHERE state_key=?")) {
                    select.setString(1, "world_cell_index");
                    try (ResultSet result = select.executeQuery()) {
                        hasValue = result.next();
                        if (hasValue) {
                            current = result.getLong(1);
                        }
                    }
                }

                if (!hasValue) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO engine_state (state_key, state_value) VALUES (?, ?)")) {
                        insert.setString(1, "world_cell_index");
                        insert.setLong(2, 0L);
                        insert.executeUpdate();
                    }
                }

                long next = current + consumed;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE engine_state SET state_value=? WHERE state_key=?")) {
                    update.setLong(1, next);
                    update.setString(2, "world_cell_index");
                    update.executeUpdate();
                }

                connection.commit();
                return current;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override public void close() {
        dataSource.close();
    }
}
