package de.citybuild.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.citybuild.core.CityBuildCore;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Manages the HikariCP connection pool for SQLite (default) or MySQL.
 *
 * <p>All SQL operations should be performed through {@link #executeUpdate} and
 * {@link #executeQuery} rather than obtaining raw {@link Connection} objects,
 * except for batch operations in {@link PlotRepository}.</p>
 */
public class DatabaseManager {

    /**
     * Functional interface for mapping a {@link ResultSet} to a value.
     *
     * @param <T> the mapped return type
     */
    @FunctionalInterface
    public interface ResultSetHandler<T> {
        /**
         * Maps the given result set to a value.
         *
         * @param rs the result set to read
         * @return the mapped value
         * @throws SQLException on any SQL error
         */
        T handle(ResultSet rs) throws SQLException;
    }

    private final CityBuildCore    plugin;
    private final Logger           log;
    private       HikariDataSource dataSource;

    /**
     * Creates a new DatabaseManager.
     *
     * @param plugin the owning plugin instance
     */
    public DatabaseManager(CityBuildCore plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    /**
     * Initialises the HikariCP connection pool and creates all required tables.
     *
     * @return {@code true} on success, {@code false} if setup failed
     */
    public boolean initialize() {
        try {
            HikariConfig cfg  = new HikariConfig();
            String       type = plugin.getCoreConfig().getDbType();

            if ("MYSQL".equals(type)) {
                String host = plugin.getCoreConfig().getMysqlHost();
                int    port = plugin.getCoreConfig().getMysqlPort();
                String db   = plugin.getCoreConfig().getMysqlDatabase();
                cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db
                    + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
                cfg.setUsername(plugin.getCoreConfig().getMysqlUsername());
                cfg.setPassword(plugin.getCoreConfig().getMysqlPassword());
                cfg.setMaximumPoolSize(plugin.getCoreConfig().getMysqlPoolSize());
                cfg.setDriverClassName("com.mysql.cj.jdbc.Driver");
            } else {
                plugin.getDataFolder().mkdirs();
                File dbFile = new File(plugin.getDataFolder(), "citybuild.db");
                cfg.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
                cfg.setMaximumPoolSize(1);
                cfg.setDriverClassName("org.sqlite.JDBC");
            }

            cfg.setPoolName("CityBuild-Pool");
            cfg.setConnectionTestQuery("SELECT 1");
            cfg.setConnectionTimeout(10_000);
            dataSource = new HikariDataSource(cfg);

            createTables();
            log.info("Database initialised (" + type + ").");
            return true;
        } catch (Exception e) {
            log.severe("Failed to initialise database: " + e.getMessage());
            return false;
        }
    }

    /**
     * Closes the connection pool gracefully.
     */
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * Borrows a {@link Connection} from the pool.
     *
     * @return an active connection
     * @throws SQLException if no connection is available
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Executes an INSERT / UPDATE / DELETE statement.
     *
     * @param sql    parameterised SQL
     * @param params positional bind parameters
     * @return number of affected rows, or {@code -1} on error
     */
    public int executeUpdate(String sql, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("SQL error [update]: " + e.getMessage() + "\n  SQL: " + sql);
            return -1;
        }
    }

    /**
     * Executes a SELECT statement and maps the {@link ResultSet} via the supplied handler.
     *
     * @param sql     parameterised SQL
     * @param handler maps the result set to the desired type
     * @param params  positional bind parameters
     * @param <T>     return type
     * @return mapped value, or {@code null} on error
     */
    public <T> T executeQuery(String sql, ResultSetHandler<T> handler, Object... params) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return handler.handle(rs);
            }
        } catch (SQLException e) {
            log.severe("SQL error [query]: " + e.getMessage() + "\n  SQL: " + sql);
            return null;
        }
    }

    // =========================================================================
    // Table setup
    // =========================================================================

    private void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            conn.createStatement().executeUpdate(
                "CREATE TABLE IF NOT EXISTS cb_plots (" +
                "  id            INTEGER PRIMARY KEY," +
                "  world_name    TEXT    NOT NULL," +
                "  min_x         INTEGER NOT NULL," +
                "  min_z         INTEGER NOT NULL," +
                "  max_x         INTEGER NOT NULL," +
                "  max_z         INTEGER NOT NULL," +
                "  type          TEXT    DEFAULT 'RESIDENTIAL'," +
                "  district_id   INTEGER DEFAULT 0," +
                "  is_corner_lot INTEGER DEFAULT 0," +
                "  owner_uuid    TEXT," +
                "  owner_name    TEXT," +
                "  plot_name     TEXT," +
                "  price         REAL    DEFAULT 0," +
                "  for_sale      INTEGER DEFAULT 0," +
                "  claimed_at    TEXT," +
                "  spawn_x       REAL    DEFAULT 0," +
                "  spawn_y       REAL    DEFAULT 64," +
                "  spawn_z       REAL    DEFAULT 0," +
                "  spawn_yaw     REAL    DEFAULT 0," +
                "  spawn_pitch   REAL    DEFAULT 0," +
                "  has_spawn     INTEGER DEFAULT 0" +
                ")"
            );
            conn.createStatement().executeUpdate(
                "CREATE TABLE IF NOT EXISTS cb_plot_trusted (" +
                "  plot_id     INTEGER NOT NULL," +
                "  player_uuid TEXT    NOT NULL," +
                "  PRIMARY KEY (plot_id, player_uuid)" +
                ")"
            );
            conn.createStatement().executeUpdate(
                "CREATE TABLE IF NOT EXISTS cb_plot_banned (" +
                "  plot_id     INTEGER NOT NULL," +
                "  player_uuid TEXT    NOT NULL," +
                "  PRIMARY KEY (plot_id, player_uuid)" +
                ")"
            );
            conn.createStatement().executeUpdate(
                "CREATE TABLE IF NOT EXISTS cb_players (" +
                "  uuid      TEXT PRIMARY KEY," +
                "  name      TEXT NOT NULL," +
                "  last_seen TEXT" +
                ")"
            );
            conn.createStatement().executeUpdate(
                "CREATE TABLE IF NOT EXISTS cb_meta (" +
                "  key_name TEXT PRIMARY KEY," +
                "  value    TEXT NOT NULL" +
                ")"
            );
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                ps.setNull(i + 1, java.sql.Types.NULL);
            } else {
                ps.setObject(i + 1, params[i]);
            }
        }
    }
}
