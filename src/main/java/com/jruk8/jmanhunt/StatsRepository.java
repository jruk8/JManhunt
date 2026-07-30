package com.jruk8.jmanhunt;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/** Persistent career-statistics storage for local and shared deployments. */
public final class StatsRepository implements AutoCloseable {
    private final JManhuntPlugin plugin;
    private final boolean postgres;
    private final HikariDataSource dataSource;

    private StatsRepository(JManhuntPlugin plugin, boolean postgres, HikariDataSource dataSource) {
        this.plugin = plugin;
        this.postgres = postgres;
        this.dataSource = dataSource;
    }

    public static StatsRepository open(JManhuntPlugin plugin) throws SQLException {
        String type = plugin.getConfig().getString("database.type", "sqlite").toLowerCase();
        if (type.equals("sqlite")) {
            String file = plugin.getConfig().getString("database.sqlite.file", "jmanhunt.db");
            File database = new File(plugin.getDataFolder(), file);
            if (database.getParentFile() != null) database.getParentFile().mkdirs();
            StatsRepository repository = new StatsRepository(plugin, false,
                    dataSource("jdbc:sqlite:" + database, "", "", plugin.getConfig().getInt("database.pool-size", 4)));
            repository.initialize();
            return repository;
        }
        if (type.equals("postgresql") || type.equals("postgres")) {
            String host = plugin.getConfig().getString("database.postgresql.host", "localhost");
            int port = plugin.getConfig().getInt("database.postgresql.port", 5432);
            String database = plugin.getConfig().getString("database.postgresql.database", "jmanhunt");
            String user = plugin.getConfig().getString("database.postgresql.username", "jmanhunt");
            String pass = plugin.getConfig().getString("database.postgresql.password", "change-me");
            boolean ssl = plugin.getConfig().getBoolean("database.postgresql.ssl", false);
            String jdbc = "jdbc:postgresql://" + host + ":" + port + "/" + database + "?sslmode="
                    + (ssl ? "require" : "disable");
            StatsRepository repository = new StatsRepository(plugin, true,
                    dataSource(jdbc, user, pass, plugin.getConfig().getInt("database.pool-size", 4)));
            repository.initialize();
            return repository;
        }
        throw new SQLException("Unsupported database.type: " + type);
    }

    private static HikariDataSource dataSource(String url, String username, String password, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setMaximumPoolSize(Math.max(1, poolSize));
        if (!username.isEmpty()) config.setUsername(username);
        if (!password.isEmpty()) config.setPassword(password);
        return new HikariDataSource(config);
    }

    private Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    private void initialize() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS jmanhunt_player_stats ("
                    + "uuid VARCHAR(36) PRIMARY KEY, player_name VARCHAR(16) NOT NULL, "
                    + "time_speedrunner BIGINT NOT NULL DEFAULT 0, time_hunter BIGINT NOT NULL DEFAULT 0, "
                    + "kills INTEGER NOT NULL DEFAULT 0, hunter_kills INTEGER NOT NULL DEFAULT 0, "
                    + "speedrunner_kills INTEGER NOT NULL DEFAULT 0, final_kills INTEGER NOT NULL DEFAULT 0, "
                    + "damage_dealt DOUBLE PRECISION NOT NULL DEFAULT 0, hunter_wins INTEGER NOT NULL DEFAULT 0, "
                    + "speedrunner_wins INTEGER NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS jmanhunt_state ("
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
                        "SELECT state_value FROM jmanhunt_state WHERE state_key=?")) {
                    select.setString(1, "world_cell_index");
                    try (ResultSet result = select.executeQuery()) {
                        hasValue = result.next();
                        if (hasValue) current = result.getLong(1);
                    }
                }

                if (!hasValue) {
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO jmanhunt_state (state_key, state_value) VALUES (?, ?)")) {
                        insert.setString(1, "world_cell_index");
                        insert.setLong(2, 0L);
                        insert.executeUpdate();
                    }
                }

                long next = current + consumed;
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE jmanhunt_state SET state_value=? WHERE state_key=?")) {
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

    public StatsManager.CareerStats load(UUID uuid) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT player_name, time_speedrunner, time_hunter, kills, hunter_kills, speedrunner_kills, "
                        + "final_kills, damage_dealt, hunter_wins, speedrunner_wins FROM jmanhunt_player_stats WHERE uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return new StatsManager.CareerStats();
                StatsManager.CareerStats stats = new StatsManager.CareerStats();
                stats.player = result.getString(1);
                stats.timeSpeedrunner = result.getLong(2);
                stats.timeHunter = result.getLong(3);
                stats.kills = result.getInt(4);
                stats.hunterKills = result.getInt(5);
                stats.speedrunnerKills = result.getInt(6);
                stats.finalKills = result.getInt(7);
                stats.damage = result.getDouble(8);
                stats.hunterWins = result.getInt(9);
                stats.speedrunnerWins = result.getInt(10);
                return stats;
            }
        }
    }

    public void save(UUID uuid, StatsManager.CareerStats stats) throws SQLException {
        String sql = postgres
                ? "INSERT INTO jmanhunt_player_stats (uuid,player_name,time_speedrunner,time_hunter,kills,hunter_kills,"
                + "speedrunner_kills,final_kills,damage_dealt,hunter_wins,speedrunner_wins) VALUES (?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT (uuid) DO UPDATE SET player_name=EXCLUDED.player_name,time_speedrunner=EXCLUDED.time_speedrunner,"
                + "time_hunter=EXCLUDED.time_hunter,kills=EXCLUDED.kills,hunter_kills=EXCLUDED.hunter_kills,"
                + "speedrunner_kills=EXCLUDED.speedrunner_kills,final_kills=EXCLUDED.final_kills,damage_dealt=EXCLUDED.damage_dealt,"
                + "hunter_wins=EXCLUDED.hunter_wins,speedrunner_wins=EXCLUDED.speedrunner_wins,updated_at=CURRENT_TIMESTAMP"
                : "INSERT OR REPLACE INTO jmanhunt_player_stats (uuid,player_name,time_speedrunner,time_hunter,kills,hunter_kills,"
                + "speedrunner_kills,final_kills,damage_dealt,hunter_wins,speedrunner_wins) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, stats.player);
            statement.setLong(3, stats.timeSpeedrunner);
            statement.setLong(4, stats.timeHunter);
            statement.setInt(5, stats.kills);
            statement.setInt(6, stats.hunterKills);
            statement.setInt(7, stats.speedrunnerKills);
            statement.setInt(8, stats.finalKills);
            statement.setDouble(9, stats.damage);
            statement.setInt(10, stats.hunterWins);
            statement.setInt(11, stats.speedrunnerWins);
            statement.executeUpdate();
        }
    }

    public void increment(UUID uuid, StatsManager.CareerStats delta) throws SQLException {
        String sql = "INSERT INTO jmanhunt_player_stats (uuid,player_name,time_speedrunner,time_hunter,kills,hunter_kills,"
                + "speedrunner_kills,final_kills,damage_dealt,hunter_wins,speedrunner_wins) VALUES (?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT (uuid) DO UPDATE SET player_name=EXCLUDED.player_name,"
                + "time_speedrunner=jmanhunt_player_stats.time_speedrunner+EXCLUDED.time_speedrunner,"
                + "time_hunter=jmanhunt_player_stats.time_hunter+EXCLUDED.time_hunter,"
                + "kills=jmanhunt_player_stats.kills+EXCLUDED.kills,hunter_kills=jmanhunt_player_stats.hunter_kills+EXCLUDED.hunter_kills,"
                + "speedrunner_kills=jmanhunt_player_stats.speedrunner_kills+EXCLUDED.speedrunner_kills,"
                + "final_kills=jmanhunt_player_stats.final_kills+EXCLUDED.final_kills,"
                + "damage_dealt=jmanhunt_player_stats.damage_dealt+EXCLUDED.damage_dealt,"
                + "hunter_wins=jmanhunt_player_stats.hunter_wins+EXCLUDED.hunter_wins,"
                + "speedrunner_wins=jmanhunt_player_stats.speedrunner_wins+EXCLUDED.speedrunner_wins,updated_at=CURRENT_TIMESTAMP";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString()); statement.setString(2, delta.player);
            statement.setLong(3, delta.timeSpeedrunner); statement.setLong(4, delta.timeHunter);
            statement.setInt(5, delta.kills); statement.setInt(6, delta.hunterKills); statement.setInt(7, delta.speedrunnerKills);
            statement.setInt(8, delta.finalKills); statement.setDouble(9, delta.damage);
            statement.setInt(10, delta.hunterWins); statement.setInt(11, delta.speedrunnerWins);
            statement.executeUpdate();
        }
    }

    @Override public void close() { dataSource.close(); }
}
