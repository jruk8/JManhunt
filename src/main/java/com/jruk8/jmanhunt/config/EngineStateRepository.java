package com.jruk8.jmanhunt.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small always-SQLite store for engine state. It owns the world-engine
 * spiral cell index plus the live end-dimension reservations; other
 * internal counters may move here later. Career statistics live in
 * {@link StatisticsRepository} instead.
 */
public final class EngineStateRepository implements AutoCloseable {
    private static final String CRASH_FLAG_KEY = "crash_flag";
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
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS end_reservations ("
                    + "match_id BIGINT PRIMARY KEY, world_name VARCHAR(128) NOT NULL)");
        }
    }

    public synchronized long getWorldCellIndex() throws SQLException {
        try (Connection connection = connection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT state_value FROM engine_state WHERE state_key=?")) {
            select.setString(1, "world_cell_index");
            try (ResultSet result = select.executeQuery()) {
                return result.next() ? result.getLong(1) : 0L;
            }
        }
    }

    public synchronized void setWorldCellIndex(long value) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement update = connection.prepareStatement(
                        "INSERT INTO engine_state (state_key, state_value) VALUES (?, ?) "
                                + "ON CONFLICT (state_key) DO UPDATE SET state_value=EXCLUDED.state_value")) {
            update.setString(1, "world_cell_index");
            update.setLong(2, Math.max(0L, value));
            update.executeUpdate();
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

    public synchronized void putEndReservation(long matchId, String worldName) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement update = connection.prepareStatement(
                        "INSERT INTO end_reservations (match_id, world_name) VALUES (?, ?) "
                                + "ON CONFLICT (match_id) DO UPDATE SET world_name=EXCLUDED.world_name")) {
            update.setLong(1, matchId);
            update.setString(2, worldName);
            update.executeUpdate();
        }
    }

    public synchronized void removeEndReservation(long matchId) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM end_reservations WHERE match_id=?")) {
            delete.setLong(1, matchId);
            delete.executeUpdate();
        }
    }

    public synchronized Map<Long, String> endReservations() throws SQLException {
        Map<Long, String> rows = new LinkedHashMap<>();
        try (Connection connection = connection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT match_id, world_name FROM end_reservations");
                ResultSet result = select.executeQuery()) {
            while (result.next()) {
                rows.put(result.getLong(1), result.getString(2));
            }
        }
        return rows;
    }

    /** Drops every end reservation, used after a crash leaves them stale. */
    public synchronized void clearEndReservations() throws SQLException {
        try (Connection connection = connection();
                PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM end_reservations")) {
            delete.executeUpdate();
        }
    }

    /**
     * True when the previous run never shut down cleanly. The flag is set
     * on every enable and cleared on every disable, so a set flag at
     * enable time means the server crashed (or was killed) mid-run.
     */
    public synchronized boolean getCrashFlag() throws SQLException {
        try (Connection connection = connection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT state_value FROM engine_state WHERE state_key=?")) {
            select.setString(1, CRASH_FLAG_KEY);
            try (ResultSet result = select.executeQuery()) {
                return result.next() && result.getLong(1) != 0L;
            }
        }
    }

    public synchronized void setCrashFlag(boolean crashed) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement update = connection.prepareStatement(
                        "INSERT INTO engine_state (state_key, state_value) VALUES (?, ?) "
                                + "ON CONFLICT (state_key) DO UPDATE SET state_value=EXCLUDED.state_value")) {
            update.setString(1, CRASH_FLAG_KEY);
            update.setLong(2, crashed ? 1L : 0L);
            update.executeUpdate();
        }
    }

    @Override public void close() {
        dataSource.close();
    }
}
