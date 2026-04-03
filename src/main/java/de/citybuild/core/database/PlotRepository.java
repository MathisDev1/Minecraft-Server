package de.citybuild.core.database;

import de.citybuild.core.model.Plot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.logging.Logger;

/**
 * Provides all SQL operations for {@link Plot} objects.
 *
 * <p>Every public method delegates to {@link DatabaseManager} for connection
 * management and error handling.  Batch inserts use a direct {@link Connection}
 * for transaction control.</p>
 */
public class PlotRepository {

    private static final String INSERT_SQL =
        "INSERT OR IGNORE INTO cb_plots " +
        "(id,world_name,min_x,min_z,max_x,max_z,type,district_id,is_corner_lot," +
        " owner_uuid,owner_name,plot_name,price,for_sale,claimed_at," +
        " spawn_x,spawn_y,spawn_z,spawn_yaw,spawn_pitch,has_spawn) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private final DatabaseManager db;
    private final Logger          log;

    /**
     * Creates a new PlotRepository.
     *
     * @param db the database manager to delegate connections to
     */
    public PlotRepository(DatabaseManager db) {
        this.db  = db;
        this.log = Logger.getLogger("CityBuildCore");
    }

    // =========================================================================
    // Reads
    // =========================================================================

    /**
     * Loads all plots from the database, including their trusted and banned player sets.
     *
     * @return list of all persisted plots
     */
    public List<Plot> loadAll() {
        List<Plot> plots = db.executeQuery(
            "SELECT * FROM cb_plots",
            rs -> {
                List<Plot> list = new ArrayList<>();
                while (rs.next()) list.add(mapRow(rs));
                return list;
            }
        );
        if (plots == null) return Collections.emptyList();

        Map<Integer, Set<UUID>> trusted = loadPlayerSets("cb_plot_trusted");
        Map<Integer, Set<UUID>> banned  = loadPlayerSets("cb_plot_banned");

        for (Plot plot : plots) {
            Set<UUID> t = trusted.get(plot.getId());
            if (t != null) plot.getTrustedPlayers().addAll(t);
            Set<UUID> b = banned.get(plot.getId());
            if (b != null) plot.getBannedPlayers().addAll(b);
        }
        return plots;
    }

    // =========================================================================
    // Writes
    // =========================================================================

    /**
     * Inserts a single plot record (used after generator completes one plot).
     *
     * @param plot the plot to insert
     */
    public void insertPlot(Plot plot) {
        db.executeUpdate(INSERT_SQL,
            plot.getId(), plot.getWorldName(),
            plot.getMinX(), plot.getMinZ(), plot.getMaxX(), plot.getMaxZ(),
            plot.getType().name(), plot.getDistrictId(), plot.isCornerLot() ? 1 : 0,
            uuidStr(plot.getOwnerUUID()), plot.getOwnerName(), plot.getPlotName(),
            plot.getPrice(), plot.isForSale() ? 1 : 0, instantStr(plot.getClaimedAt()),
            plot.getSpawnX(), plot.getSpawnY(), plot.getSpawnZ(),
            plot.getSpawnYaw(), plot.getSpawnPitch(), plot.isHasCustomSpawn() ? 1 : 0
        );
    }

    /**
     * Updates all mutable fields of a plot and synchronises its trusted/banned sets.
     *
     * @param plot the plot to persist
     */
    public void savePlot(Plot plot) {
        db.executeUpdate(
            "UPDATE cb_plots SET " +
            "owner_uuid=?,owner_name=?,plot_name=?,price=?,for_sale=?,claimed_at=?," +
            "spawn_x=?,spawn_y=?,spawn_z=?,spawn_yaw=?,spawn_pitch=?,has_spawn=? " +
            "WHERE id=?",
            uuidStr(plot.getOwnerUUID()), plot.getOwnerName(), plot.getPlotName(),
            plot.getPrice(), plot.isForSale() ? 1 : 0, instantStr(plot.getClaimedAt()),
            plot.getSpawnX(), plot.getSpawnY(), plot.getSpawnZ(),
            plot.getSpawnYaw(), plot.getSpawnPitch(), plot.isHasCustomSpawn() ? 1 : 0,
            plot.getId()
        );
        savePlayerSet(plot.getId(), "cb_plot_trusted", plot.getTrustedPlayers());
        savePlayerSet(plot.getId(), "cb_plot_banned",  plot.getBannedPlayers());
    }

    /**
     * Inserts all plots in a single transaction for maximum insert performance
     * during map generation.
     *
     * @param plots the plots to insert
     */
    public void bulkInsertPlots(List<Plot> plots) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
                for (Plot plot : plots) {
                    ps.setInt(1,    plot.getId());
                    ps.setString(2, plot.getWorldName());
                    ps.setInt(3,    plot.getMinX());
                    ps.setInt(4,    plot.getMinZ());
                    ps.setInt(5,    plot.getMaxX());
                    ps.setInt(6,    plot.getMaxZ());
                    ps.setString(7, plot.getType().name());
                    ps.setInt(8,    plot.getDistrictId());
                    ps.setInt(9,    plot.isCornerLot() ? 1 : 0);
                    ps.setNull(10,  java.sql.Types.NULL); // owner_uuid
                    ps.setNull(11,  java.sql.Types.NULL); // owner_name
                    ps.setNull(12,  java.sql.Types.NULL); // plot_name
                    ps.setDouble(13, plot.getPrice());
                    ps.setInt(14,   plot.isForSale() ? 1 : 0);
                    ps.setNull(15,  java.sql.Types.NULL); // claimed_at
                    ps.setDouble(16, 0.0);                // spawn_x
                    ps.setDouble(17, 64.0);               // spawn_y
                    ps.setDouble(18, 0.0);                // spawn_z
                    ps.setDouble(19, 0.0);                // spawn_yaw
                    ps.setDouble(20, 0.0);                // spawn_pitch
                    ps.setInt(21,   0);                   // has_spawn
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.severe("Bulk insert failed: " + e.getMessage());
        }
    }

    /**
     * Removes a plot and its associated trusted/banned records from the database.
     *
     * @param id the plot ID to delete
     */
    public void deletePlot(int id) {
        db.executeUpdate("DELETE FROM cb_plot_trusted WHERE plot_id=?", id);
        db.executeUpdate("DELETE FROM cb_plot_banned  WHERE plot_id=?", id);
        db.executeUpdate("DELETE FROM cb_plots        WHERE id=?",      id);
    }

    // =========================================================================
    // Meta
    // =========================================================================

    /**
     * Reads a meta value from {@code cb_meta}.
     *
     * @param key the meta key
     * @return the stored value, or {@code null} if not present
     */
    public String getMeta(String key) {
        return db.executeQuery(
            "SELECT value FROM cb_meta WHERE key_name=?",
            rs -> rs.next() ? rs.getString("value") : null,
            key
        );
    }

    /**
     * Inserts or updates a meta key/value pair.
     *
     * @param key   meta key
     * @param value meta value
     */
    public void setMeta(String key, String value) {
        db.executeUpdate(
            "INSERT INTO cb_meta (key_name,value) VALUES (?,?) " +
            "ON CONFLICT(key_name) DO UPDATE SET value=excluded.value",
            key, value
        );
    }

    // =========================================================================
    // Players
    // =========================================================================

    /**
     * Inserts or updates a player record with the current timestamp.
     *
     * @param uuid player UUID
     * @param name current player name
     */
    public void upsertPlayer(UUID uuid, String name) {
        db.executeUpdate(
            "INSERT INTO cb_players (uuid,name,last_seen) VALUES (?,?,?) " +
            "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, last_seen=excluded.last_seen",
            uuid.toString(), name, Instant.now().toString()
        );
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Plot mapRow(ResultSet rs) throws SQLException {
        Plot plot = new Plot(
            rs.getInt("id"),
            rs.getString("world_name"),
            rs.getInt("min_x"), rs.getInt("min_z"),
            rs.getInt("max_x"), rs.getInt("max_z")
        );

        String typeStr = rs.getString("type");
        if (typeStr != null) {
            try { plot.setType(Plot.PlotType.valueOf(typeStr)); }
            catch (IllegalArgumentException ignored) {}
        }

        plot.setDistrictId(rs.getInt("district_id"));
        plot.setCornerLot(rs.getInt("is_corner_lot") == 1);

        String ownerUuid = rs.getString("owner_uuid");
        if (ownerUuid != null) {
            plot.setOwnerUUID(UUID.fromString(ownerUuid));
            plot.setOwnerName(rs.getString("owner_name"));
        }

        plot.setPlotName(rs.getString("plot_name"));
        plot.setPrice(rs.getDouble("price"));
        plot.setForSale(rs.getInt("for_sale") == 1);

        String claimedAt = rs.getString("claimed_at");
        if (claimedAt != null) plot.setClaimedAt(Instant.parse(claimedAt));

        plot.setSpawnX(rs.getDouble("spawn_x"));
        plot.setSpawnY(rs.getDouble("spawn_y"));
        plot.setSpawnZ(rs.getDouble("spawn_z"));
        plot.setSpawnYaw((float) rs.getDouble("spawn_yaw"));
        plot.setSpawnPitch((float) rs.getDouble("spawn_pitch"));
        plot.setHasCustomSpawn(rs.getInt("has_spawn") == 1);
        return plot;
    }

    private Map<Integer, Set<UUID>> loadPlayerSets(String table) {
        Map<Integer, Set<UUID>> result = db.executeQuery(
            "SELECT plot_id, player_uuid FROM " + table,
            rs -> {
                Map<Integer, Set<UUID>> map = new HashMap<>();
                while (rs.next()) {
                    int  pid  = rs.getInt("plot_id");
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    map.computeIfAbsent(pid, k -> new HashSet<>()).add(uuid);
                }
                return map;
            }
        );
        return result != null ? result : Collections.emptyMap();
    }

    private void savePlayerSet(int plotId, String table, Set<UUID> players) {
        db.executeUpdate("DELETE FROM " + table + " WHERE plot_id=?", plotId);
        for (UUID uuid : players) {
            db.executeUpdate(
                "INSERT OR IGNORE INTO " + table + " (plot_id, player_uuid) VALUES (?,?)",
                plotId, uuid.toString()
            );
        }
    }

    private static String uuidStr(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }

    private static String instantStr(Instant instant) {
        return instant != null ? instant.toString() : null;
    }
}
