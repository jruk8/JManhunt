package com.jruk8.jmanhunt.stats;

import com.jruk8.jmanhunt.core.JManhuntPlugin;
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
public final class StatisticsRepository implements AutoCloseable {
    private final JManhuntPlugin plugin;
    private final boolean postgres;
    private final HikariDataSource dataSource;

    private StatisticsRepository(JManhuntPlugin plugin, boolean postgres, HikariDataSource dataSource) {
        this.plugin = plugin;
        this.postgres = postgres;
        this.dataSource = dataSource;
    }

    public static StatisticsRepository open(JManhuntPlugin plugin) throws SQLException {
        String type = plugin.getConfig().getString("statistics.type", "sqlite").toLowerCase();
        if (type.equals("sqlite")) {
            String file = plugin.getConfig().getString("statistics.sqlite.file", "statistics.db");
            File database = new File(plugin.getDataFolder(), file);
            if (database.getParentFile() != null) {
                database.getParentFile().mkdirs();
            }
            StatisticsRepository repository = new StatisticsRepository(plugin, false,
                    dataSource("jdbc:sqlite:" + database, "", "",
                            plugin.getConfig().getInt("statistics.pool-size", 4)));
            repository.initialize();
            return repository;
        }
        if (type.equals("postgresql") || type.equals("postgres")) {
            String host = plugin.getConfig().getString("statistics.postgresql.host", "localhost");
            int port = plugin.getConfig().getInt("statistics.postgresql.port", 5432);
            String database = plugin.getConfig().getString("statistics.postgresql.database", "jmanhunt");
            String user = plugin.getConfig().getString("statistics.postgresql.username", "jmanhunt");
            String pass = plugin.getConfig().getString("statistics.postgresql.password", "change-me");
            boolean ssl = plugin.getConfig().getBoolean("statistics.postgresql.ssl", false);
            String jdbc = "jdbc:postgresql://" + host + ":" + port + "/" + database + "?sslmode="
                    + (ssl ? "require" : "disable");
            StatisticsRepository repository = new StatisticsRepository(plugin, true,
                    dataSource(jdbc, user, pass, plugin.getConfig().getInt("statistics.pool-size", 4)));
            repository.initialize();
            return repository;
        }
        throw new SQLException("Unsupported statistics.type: " + type);
    }

    private static HikariDataSource dataSource(String url, String username, String password, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setMaximumPoolSize(Math.max(1, poolSize));
        if (!username.isEmpty()) {
            config.setUsername(username);
        }
        if (!password.isEmpty()) {
            config.setPassword(password);
        }
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
                    + "speedrunner_wins INTEGER NOT NULL DEFAULT 0, sessions INTEGER NOT NULL DEFAULT 0, "
                    + "speedrunner_sessions INTEGER NOT NULL DEFAULT 0, hunter_sessions INTEGER NOT NULL DEFAULT 0, "
                    + "deaths INTEGER NOT NULL DEFAULT 0, updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }
    }

    public CareerStats load(UUID uuid) throws SQLException {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT player_name, time_speedrunner, time_hunter, kills, hunter_kills, speedrunner_kills, "
                        + "final_kills, damage_dealt, hunter_wins, speedrunner_wins, sessions, "
                        + "speedrunner_sessions, hunter_sessions, deaths FROM jmanhunt_player_stats WHERE uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return new CareerStats();
                }
                CareerStats stats = new CareerStats();
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
                stats.wins = stats.hunterWins + stats.speedrunnerWins;
                stats.sessions = result.getInt(11);
                stats.speedrunnerSessions = result.getInt(12);
                stats.hunterSessions = result.getInt(13);
                stats.deaths = result.getInt(14);
                return stats;
            }
        }
    }

    public void save(UUID uuid, CareerStats stats) throws SQLException {
        String sql = postgres
                ? "INSERT INTO jmanhunt_player_stats (uuid,player_name,time_speedrunner,time_hunter,kills,"
                + "hunter_kills,speedrunner_kills,final_kills,damage_dealt,hunter_wins,speedrunner_wins,"
                + "sessions,"
                + "speedrunner_sessions,hunter_sessions,deaths) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT (uuid) DO UPDATE SET player_name=EXCLUDED.player_name,"
                + "time_speedrunner=EXCLUDED.time_speedrunner,"
                + "time_hunter=EXCLUDED.time_hunter,kills=EXCLUDED.kills,hunter_kills=EXCLUDED.hunter_kills,"
                + "speedrunner_kills=EXCLUDED.speedrunner_kills,final_kills=EXCLUDED.final_kills,"
                + "damage_dealt=EXCLUDED.damage_dealt,"
                + "hunter_wins=EXCLUDED.hunter_wins,speedrunner_wins=EXCLUDED.speedrunner_wins,"
                + "sessions=EXCLUDED.sessions,speedrunner_sessions=EXCLUDED.speedrunner_sessions,"
                + "hunter_sessions=EXCLUDED.hunter_sessions,deaths=EXCLUDED.deaths,updated_at=CURRENT_TIMESTAMP"
                : "INSERT OR REPLACE INTO jmanhunt_player_stats (uuid,player_name,time_speedrunner,time_hunter,"
                + "kills,hunter_kills,"
                + "speedrunner_kills,final_kills,damage_dealt,hunter_wins,speedrunner_wins,sessions,"
                + "speedrunner_sessions,hunter_sessions,deaths) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
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
            statement.setInt(12, stats.sessions);
            statement.setInt(13, stats.speedrunnerSessions);
            statement.setInt(14, stats.hunterSessions);
            statement.setInt(15, stats.deaths);
            statement.executeUpdate();
        }
    }

    public void increment(UUID uuid, CareerStats delta) throws SQLException {
        String sql = "INSERT INTO jmanhunt_player_stats (uuid,player_name,time_speedrunner,time_hunter,"
                + "kills,hunter_kills,"
                + "speedrunner_kills,final_kills,damage_dealt,hunter_wins,speedrunner_wins,sessions,"
                + "speedrunner_sessions,hunter_sessions,deaths) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                + "ON CONFLICT (uuid) DO UPDATE SET player_name=EXCLUDED.player_name,"
                + "time_speedrunner=jmanhunt_player_stats.time_speedrunner+EXCLUDED.time_speedrunner,"
                + "time_hunter=jmanhunt_player_stats.time_hunter+EXCLUDED.time_hunter,"
                + "kills=jmanhunt_player_stats.kills+EXCLUDED.kills,"
                + "hunter_kills=jmanhunt_player_stats.hunter_kills+EXCLUDED.hunter_kills,"
                + "speedrunner_kills=jmanhunt_player_stats.speedrunner_kills+EXCLUDED.speedrunner_kills,"
                + "final_kills=jmanhunt_player_stats.final_kills+EXCLUDED.final_kills,"
                + "damage_dealt=jmanhunt_player_stats.damage_dealt+EXCLUDED.damage_dealt,"
                + "hunter_wins=jmanhunt_player_stats.hunter_wins+EXCLUDED.hunter_wins,"
                + "speedrunner_wins=jmanhunt_player_stats.speedrunner_wins+EXCLUDED.speedrunner_wins,"
                + "sessions=jmanhunt_player_stats.sessions+EXCLUDED.sessions,"
                + "speedrunner_sessions=jmanhunt_player_stats.speedrunner_sessions+EXCLUDED.speedrunner_sessions,"
                + "hunter_sessions=jmanhunt_player_stats.hunter_sessions+EXCLUDED.hunter_sessions,"
                + "deaths=jmanhunt_player_stats.deaths+EXCLUDED.deaths,updated_at=CURRENT_TIMESTAMP";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString()); statement.setString(2, delta.player);
            statement.setLong(3, delta.timeSpeedrunner); statement.setLong(4, delta.timeHunter);
            statement.setInt(5, delta.kills); statement.setInt(6, delta.hunterKills);
            statement.setInt(7, delta.speedrunnerKills);
            statement.setInt(8, delta.finalKills); statement.setDouble(9, delta.damage);
            statement.setInt(10, delta.hunterWins); statement.setInt(11, delta.speedrunnerWins);
            statement.setInt(12, delta.sessions); statement.setInt(13, delta.speedrunnerSessions);
            statement.setInt(14, delta.hunterSessions); statement.setInt(15, delta.deaths);
            statement.executeUpdate();
        }
    }

    @Override public void close() { dataSource.close(); }
}
