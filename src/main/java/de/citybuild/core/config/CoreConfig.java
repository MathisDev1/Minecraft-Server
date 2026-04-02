package de.citybuild.core.config;

import de.citybuild.core.CityBuildCore;
import org.bukkit.ChatColor;

/**
 * Typed wrapper around the plugin's {@code config.yml}.
 *
 * <p>All raw YAML values are exposed as strongly-typed Java methods.
 * Call {@link #reload()} after {@link CityBuildCore#reloadConfig()} to pick
 * up changes without restarting the server.</p>
 */
public class CoreConfig {

    private final CityBuildCore plugin;

    /**
     * Creates a new CoreConfig backed by the given plugin's config.
     *
     * @param plugin the owning plugin instance
     */
    public CoreConfig(CityBuildCore plugin) {
        this.plugin = plugin;
    }

    /** Reloads the underlying YAML from disk. */
    public void reload() {
        plugin.reloadConfig();
    }

    // ── World ─────────────────────────────────────────────────────────────────

    /** @return the name of the city world */
    public String getWorldName() {
        return plugin.getConfig().getString("world.name", "citybuild");
    }

    /** @return the world seed (0 = random) */
    public long getWorldSeed() {
        return plugin.getConfig().getLong("world.seed", 0L);
    }

    // ── Generator ─────────────────────────────────────────────────────────────

    /** @return map generation radius in blocks */
    public int getMapRadius() {
        return plugin.getConfig().getInt("generator.map-radius", 1500);
    }

    /** @return width of main roads in blocks */
    public int getRoadWidthMain() {
        return plugin.getConfig().getInt("generator.road-width-main", 8);
    }

    /** @return width of secondary roads in blocks */
    public int getRoadWidthSecondary() {
        return plugin.getConfig().getInt("generator.road-width-secondary", 5);
    }

    /** @return minimum plot size in blocks */
    public int getPlotMinSize() {
        return plugin.getConfig().getInt("generator.plot-min-size", 16);
    }

    /** @return maximum plot size in blocks */
    public int getPlotMaxSize() {
        return plugin.getConfig().getInt("generator.plot-max-size", 48);
    }

    /** @return number of city districts */
    public int getDistrictCount() {
        return plugin.getConfig().getInt("generator.district-count", 10);
    }

    // ── Plots ─────────────────────────────────────────────────────────────────

    /** @return base price per block squared */
    public double getPricePerBlock() {
        return plugin.getConfig().getDouble("plots.price-per-block", 10.0);
    }

    /** @return location price multiplier for central plots */
    public double getLocationMultiplier() {
        return plugin.getConfig().getDouble("plots.location-multiplier", 1.5);
    }

    /** @return maximum plots per player (0 = unlimited) */
    public int getMaxPlotsPerPlayer() {
        return plugin.getConfig().getInt("plots.max-plots-per-player", 3);
    }

    /** @return the currency symbol used in price displays */
    public String getCurrencySymbol() {
        return plugin.getConfig().getString("plots.currency-symbol", "$");
    }

    // ── Spawn ─────────────────────────────────────────────────────────────────

    /** @return spawn X coordinate */
    public double getSpawnX() { return plugin.getConfig().getDouble("spawn.x", 0.5); }

    /** @return spawn Y coordinate */
    public double getSpawnY() { return plugin.getConfig().getDouble("spawn.y", 65.0); }

    /** @return spawn Z coordinate */
    public double getSpawnZ() { return plugin.getConfig().getDouble("spawn.z", 0.5); }

    /** @return spawn yaw */
    public float getSpawnYaw() { return (float) plugin.getConfig().getDouble("spawn.yaw", 0.0); }

    /** @return spawn pitch */
    public float getSpawnPitch() { return (float) plugin.getConfig().getDouble("spawn.pitch", 0.0); }

    // ── Protection ────────────────────────────────────────────────────────────

    /** @return whether unclaimed plots are protected from modification */
    public boolean isProtectUnclaimed() {
        return plugin.getConfig().getBoolean("protection.protect-unclaimed", true);
    }

    /** @return whether PvP is prevented in the city world */
    public boolean isPreventPvP() {
        return plugin.getConfig().getBoolean("protection.prevent-pvp", true);
    }

    /** @return whether fire spread is prevented */
    public boolean isPreventFireSpread() {
        return plugin.getConfig().getBoolean("protection.prevent-fire-spread", true);
    }

    /** @return whether mob griefing (creeper explosions, endermen) is prevented */
    public boolean isPreventMobGrief() {
        return plugin.getConfig().getBoolean("protection.prevent-mob-grief", true);
    }

    // ── Messages ──────────────────────────────────────────────────────────────

    /** @return the color-translated chat prefix */
    public String getPrefix() {
        return ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("messages.prefix", "&8[&6City&eBuild&8] &r"));
    }

    /**
     * Returns a color-translated message from the {@code messages} section.
     *
     * @param key the message key, e.g. {@code "not-on-plot"}
     * @return the formatted message string
     */
    public String msg(String key) {
        String raw = plugin.getConfig().getString("messages." + key,
            "&cMissing message: " + key);
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    /**
     * Returns a color-translated message with placeholder substitution.
     *
     * @param key              the message key under {@code messages.*}
     * @param placeholderPairs alternating key/value pairs, e.g. {@code "id","42","price","500"}
     * @return the message with all {@code {placeholder}} tokens replaced
     */
    public String msg(String key, String... placeholderPairs) {
        String raw = plugin.getConfig().getString("messages." + key,
            "&cMissing message: " + key);
        for (int i = 0; i + 1 < placeholderPairs.length; i += 2) {
            raw = raw.replace("{" + placeholderPairs[i] + "}", placeholderPairs[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    // ── Database ──────────────────────────────────────────────────────────────

    /** @return the database type, either {@code "SQLITE"} or {@code "MYSQL"} */
    public String getDbType() {
        return plugin.getConfig().getString("database.type", "SQLITE").toUpperCase();
    }

    /** @return MySQL host */
    public String getMysqlHost() {
        return plugin.getConfig().getString("database.mysql.host", "localhost");
    }

    /** @return MySQL port */
    public int getMysqlPort() {
        return plugin.getConfig().getInt("database.mysql.port", 3306);
    }

    /** @return MySQL database name */
    public String getMysqlDatabase() {
        return plugin.getConfig().getString("database.mysql.database", "citybuild");
    }

    /** @return MySQL username */
    public String getMysqlUsername() {
        return plugin.getConfig().getString("database.mysql.username", "root");
    }

    /** @return MySQL password */
    public String getMysqlPassword() {
        return plugin.getConfig().getString("database.mysql.password", "");
    }

    /** @return MySQL connection pool size */
    public int getMysqlPoolSize() {
        return plugin.getConfig().getInt("database.mysql.pool-size", 10);
    }
}
