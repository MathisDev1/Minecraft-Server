package de.citybuild.core.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a purchasable city plot (Grundstück) created by the generator.
 *
 * <p>A plot is axis-aligned rectangular region of the city map.  The ownerUUID
 * field is {@code null} for unclaimed plots.  Session-2 code will persist these
 * objects to the SQLite database via the CityBuildAPI.</p>
 */
public class Plot {

    /**
     * Broad category of a plot, controlling default price and allowed structures.
     */
    public enum PlotType {
        /** Standard residential building plot (default). */
        RESIDENTIAL,
        /** Commercial / shop plot. */
        COMMERCIAL,
        /** Public / government plot. */
        PUBLIC
    }

    // -------------------------------------------------------------------------
    // Core identity
    // -------------------------------------------------------------------------

    private int    id;
    private String worldName;
    private int    minX, minZ, maxX, maxZ;
    private PlotType type = PlotType.RESIDENTIAL;
    private int    districtId;
    private boolean isCornerLot;

    // -------------------------------------------------------------------------
    // Ownership
    // -------------------------------------------------------------------------

    private UUID    ownerUUID;   // null = unclaimed
    private String  ownerName;
    private String  plotName;
    private double  price;
    private boolean forSale;
    private Instant claimedAt;

    // -------------------------------------------------------------------------
    // Custom spawn
    // -------------------------------------------------------------------------

    private boolean hasCustomSpawn;
    private double  spawnX, spawnY, spawnZ;
    private float   spawnYaw, spawnPitch;

    // -------------------------------------------------------------------------
    // Player lists
    // -------------------------------------------------------------------------

    private final Set<UUID> trustedPlayers = new HashSet<>();
    private final Set<UUID> bannedPlayers  = new HashSet<>();

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * Creates a bare Plot with the mandatory geometry fields set.
     *
     * @param id        unique auto-increment identifier
     * @param worldName name of the world this plot belongs to
     * @param minX      minimum X coordinate (inclusive)
     * @param minZ      minimum Z coordinate (inclusive)
     * @param maxX      maximum X coordinate (inclusive)
     * @param maxZ      maximum Z coordinate (inclusive)
     */
    public Plot(int id, String worldName, int minX, int minZ, int maxX, int maxZ) {
        this.id        = id;
        this.worldName = worldName;
        this.minX      = minX;
        this.minZ      = minZ;
        this.maxX      = maxX;
        this.maxZ      = maxZ;
    }

    // =========================================================================
    // Geometry helpers
    // =========================================================================

    /**
     * Returns {@code true} when the given world position is within this plot's
     * bounding box.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return whether (x, z) lies inside this plot
     */
    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * Returns the area of this plot in blocks² (XZ plane only).
     *
     * @return plot area in blocks squared
     */
    public int getArea() {
        return (maxX - minX + 1) * (maxZ - minZ + 1);
    }

    /**
     * Returns the centre X coordinate of this plot.
     *
     * @return centre X
     */
    public int getCenterX() {
        return (minX + maxX) / 2;
    }

    /**
     * Returns the centre Z coordinate of this plot.
     *
     * @return centre Z
     */
    public int getCenterZ() {
        return (minZ + maxZ) / 2;
    }

    /**
     * Returns {@code true} when this plot borders two or more road segments.
     *
     * @return whether this is a corner lot
     */
    public boolean isCornerLot() {
        return isCornerLot;
    }

    // =========================================================================
    // Getters and setters
    // =========================================================================

    /** @return unique plot identifier */
    public int getId() { return id; }

    /** @param id new plot identifier */
    public void setId(int id) { this.id = id; }

    /** @return name of the world this plot belongs to */
    public String getWorldName() { return worldName; }

    /** @param worldName world name */
    public void setWorldName(String worldName) { this.worldName = worldName; }

    /** @return minimum X coordinate */
    public int getMinX() { return minX; }

    /** @param minX minimum X coordinate */
    public void setMinX(int minX) { this.minX = minX; }

    /** @return minimum Z coordinate */
    public int getMinZ() { return minZ; }

    /** @param minZ minimum Z coordinate */
    public void setMinZ(int minZ) { this.minZ = minZ; }

    /** @return maximum X coordinate */
    public int getMaxX() { return maxX; }

    /** @param maxX maximum X coordinate */
    public void setMaxX(int maxX) { this.maxX = maxX; }

    /** @return maximum Z coordinate */
    public int getMaxZ() { return maxZ; }

    /** @param maxZ maximum Z coordinate */
    public void setMaxZ(int maxZ) { this.maxZ = maxZ; }

    /** @return plot classification */
    public PlotType getType() { return type; }

    /** @param type plot classification */
    public void setType(PlotType type) { this.type = type; }

    /** @return district identifier this plot belongs to */
    public int getDistrictId() { return districtId; }

    /** @param districtId district identifier */
    public void setDistrictId(int districtId) { this.districtId = districtId; }

    /** @param isCornerLot whether this plot borders two or more roads */
    public void setCornerLot(boolean isCornerLot) { this.isCornerLot = isCornerLot; }

    /** @return UUID of the owner, or {@code null} if unclaimed */
    public UUID getOwnerUUID() { return ownerUUID; }

    /** @param ownerUUID owner UUID, or {@code null} to unclaim */
    public void setOwnerUUID(UUID ownerUUID) { this.ownerUUID = ownerUUID; }

    /** @return display name of the owner */
    public String getOwnerName() { return ownerName; }

    /** @param ownerName display name of the owner */
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    /** @return custom display name of this plot, or {@code null} */
    public String getPlotName() { return plotName; }

    /** @param plotName custom display name */
    public void setPlotName(String plotName) { this.plotName = plotName; }

    /** @return asking price of this plot in the server economy */
    public double getPrice() { return price; }

    /** @param price asking price */
    public void setPrice(double price) { this.price = price; }

    /** @return whether this plot is listed for sale */
    public boolean isForSale() { return forSale; }

    /** @param forSale sale listing flag */
    public void setForSale(boolean forSale) { this.forSale = forSale; }

    /** @return the instant this plot was claimed, or {@code null} if unclaimed */
    public Instant getClaimedAt() { return claimedAt; }

    /** @param claimedAt claim timestamp */
    public void setClaimedAt(Instant claimedAt) { this.claimedAt = claimedAt; }

    /** @return whether a custom spawn point has been configured */
    public boolean isHasCustomSpawn() { return hasCustomSpawn; }

    /** @param hasCustomSpawn custom spawn flag */
    public void setHasCustomSpawn(boolean hasCustomSpawn) { this.hasCustomSpawn = hasCustomSpawn; }

    /** @return X coordinate of the custom spawn point */
    public double getSpawnX() { return spawnX; }

    /** @param spawnX X coordinate */
    public void setSpawnX(double spawnX) { this.spawnX = spawnX; }

    /** @return Y coordinate of the custom spawn point */
    public double getSpawnY() { return spawnY; }

    /** @param spawnY Y coordinate */
    public void setSpawnY(double spawnY) { this.spawnY = spawnY; }

    /** @return Z coordinate of the custom spawn point */
    public double getSpawnZ() { return spawnZ; }

    /** @param spawnZ Z coordinate */
    public void setSpawnZ(double spawnZ) { this.spawnZ = spawnZ; }

    /** @return yaw of the custom spawn orientation */
    public float getSpawnYaw() { return spawnYaw; }

    /** @param spawnYaw yaw angle */
    public void setSpawnYaw(float spawnYaw) { this.spawnYaw = spawnYaw; }

    /** @return pitch of the custom spawn orientation */
    public float getSpawnPitch() { return spawnPitch; }

    /** @param spawnPitch pitch angle */
    public void setSpawnPitch(float spawnPitch) { this.spawnPitch = spawnPitch; }

    /** @return mutable set of players trusted on this plot */
    public Set<UUID> getTrustedPlayers() { return trustedPlayers; }

    /** @return mutable set of players banned from this plot */
    public Set<UUID> getBannedPlayers() { return bannedPlayers; }

    // =========================================================================
    // Object overrides
    // =========================================================================

    @Override
    public String toString() {
        return "Plot{id=" + id + ", world='" + worldName + "', ["
            + minX + "," + minZ + "] -> [" + maxX + "," + maxZ + "]"
            + ", owner=" + ownerUUID + '}';
    }
}
