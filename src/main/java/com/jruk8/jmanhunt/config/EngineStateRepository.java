package com.jruk8.jmanhunt.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Small always-SQLite store for engine state. It owns the world-engine
 * spiral cell index plus the live end-dimension reservations; other
 * internal counters may move here later. Career statistics live in
 * {@link StatisticsRepository} instead.
 */
public final class EngineStateRepository implements AutoCloseable {
    private static final String CRASH_FLAG_KEY = "crash_flag";
    private static final String SETUP_DONE_KEY = "setup_done";
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
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS modifier_editor_memory ("
                    + "player_uuid TEXT PRIMARY KEY, role TEXT NOT NULL, remember INTEGER NOT NULL, "
                    + "cmd1 TEXT NOT NULL, cmd2 TEXT NOT NULL, cmd3 TEXT NOT NULL, "
                    + "cmd4 TEXT NOT NULL, cmd5 TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS crash_cleanup ("
                    + "uuid TEXT PRIMARY KEY, added_at INTEGER NOT NULL)");
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

    /**
     * Role-less crash cleanup roster: every speedrunner, hunter, and
     * spectator currently in match state. Roles are memory-only and die
     * with a crash, so the bare row is the whole signal: a row present
     * at join means that player still needs their items and stats wiped
     * back to normal. Re-marking refreshes the timestamp.
     */
    public synchronized void markCrashCleanup(UUID playerId) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement update = connection.prepareStatement(
                        "INSERT INTO crash_cleanup (uuid, added_at) VALUES (?, ?) "
                                + "ON CONFLICT (uuid) DO UPDATE SET added_at=EXCLUDED.added_at")) {
            update.setString(1, playerId.toString());
            update.setLong(2, System.currentTimeMillis());
            update.executeUpdate();
        }
    }

    /** Drops one player from the crash cleanup roster after a clean exit or wipe. */
    public synchronized void clearCrashCleanup(UUID playerId) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM crash_cleanup WHERE uuid=?")) {
            delete.setString(1, playerId.toString());
            delete.executeUpdate();
        }
    }

    /** Every UUID currently on the crash cleanup roster. Corrupt rows are skipped. */
    public synchronized Set<UUID> crashCleanupIds() throws SQLException {
        Set<UUID> ids = new LinkedHashSet<>();
        try (Connection connection = connection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT uuid FROM crash_cleanup");
                ResultSet result = select.executeQuery()) {
            while (result.next()) {
                try {
                    ids.add(UUID.fromString(result.getString(1)));
                } catch (IllegalArgumentException corrupt) {
                    // A row we cannot parse can never match a joiner; leave it.
                }
            }
        }
        return ids;
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
        putFlag(CRASH_FLAG_KEY, crashed);
    }

    /**
     * True once setup finished anywhere: a setup session started, the
     * world engine was observed enabled, or Setup First was dismissed.
     * Missing keys read false so fresh installs see the panel once.
     */
    public synchronized boolean getSetupDone() throws SQLException {
        try (Connection connection = connection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT state_value FROM engine_state WHERE state_key=?")) {
            select.setString(1, SETUP_DONE_KEY);
            try (ResultSet result = select.executeQuery()) {
                return result.next() && result.getLong(1) != 0L;
            }
        }
    }

    public synchronized void setSetupDone(boolean done) throws SQLException {
        putFlag(SETUP_DONE_KEY, done);
    }

    /**
     * Test-a-Command memory for one player: the role, the remember
     * toggle, and the five raw command boxes.
     */
    public record EditorMemory(String role, boolean remember, List<String> commands) {
    }

    /** Test-a-Command memory for one player, or empty when never stored. */
    public synchronized Optional<EditorMemory> getEditorMemory(UUID playerId)
            throws SQLException {
        try (Connection connection = connection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT role, remember, cmd1, cmd2, cmd3, cmd4, cmd5 "
                                + "FROM modifier_editor_memory WHERE player_uuid=?")) {
            select.setString(1, playerId.toString());
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                List<String> commands = new ArrayList<>();
                for (int index = 3; index <= 7; index++) {
                    commands.add(result.getString(index));
                }
                return Optional.of(new EditorMemory(result.getString(1),
                        result.getInt(2) != 0, commands));
            }
        }
    }

    /** Stores Test-a-Command memory for one player, replacing any row. */
    public synchronized void putEditorMemory(UUID playerId, EditorMemory memory)
            throws SQLException {
        List<String> commands = new ArrayList<>(memory.commands());
        while (commands.size() < 5) {
            commands.add("");
        }
        try (Connection connection = connection();
                PreparedStatement update = connection.prepareStatement(
                        "INSERT INTO modifier_editor_memory (player_uuid, role, remember, "
                                + "cmd1, cmd2, cmd3, cmd4, cmd5) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                                + "ON CONFLICT (player_uuid) DO UPDATE SET role=EXCLUDED.role, "
                                + "remember=EXCLUDED.remember, cmd1=EXCLUDED.cmd1, "
                                + "cmd2=EXCLUDED.cmd2, cmd3=EXCLUDED.cmd3, cmd4=EXCLUDED.cmd4, "
                                + "cmd5=EXCLUDED.cmd5")) {
            update.setString(1, playerId.toString());
            update.setString(2, memory.role());
            update.setInt(3, memory.remember() ? 1 : 0);
            for (int index = 0; index < 5; index++) {
                String command = commands.get(index);
                update.setString(4 + index, command == null ? "" : command);
            }
            update.executeUpdate();
        }
    }

    private void putFlag(String key, boolean value) throws SQLException {
        try (Connection connection = connection();
                PreparedStatement update = connection.prepareStatement(
                        "INSERT INTO engine_state (state_key, state_value) VALUES (?, ?) "
                                + "ON CONFLICT (state_key) DO UPDATE SET state_value=EXCLUDED.state_value")) {
            update.setString(1, key);
            update.setLong(2, value ? 1L : 0L);
            update.executeUpdate();
        }
    }

    @Override public void close() {
        dataSource.close();
    }
}
