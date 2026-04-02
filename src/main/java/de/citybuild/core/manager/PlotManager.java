package de.citybuild.core.manager;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.api.events.PlotClaimEvent;
import de.citybuild.core.api.events.PlotUnclaimEvent;
import de.citybuild.core.config.CoreConfig;
import de.citybuild.core.database.PlotRepository;
import de.citybuild.core.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Holds all plots in memory and provides spatial lookups, claim logic and
 * batch-persistence helpers.
 *
 * <p>The spatial index partitions the XZ plane into 32-block buckets so that
 * {@link #getPlotAt(String, int, int)} is O(1) rather than O(n).</p>
 */
public class PlotManager {

    /** Result codes returned by {@link #claimPlot(Player, Plot)}. */
    public enum ClaimResult {
        SUCCESS, ALREADY_OWNED, NOT_ENOUGH_MONEY, MAX_PLOTS_REACHED, CANCELLED_BY_ADDON
    }

    private static final int BUCKET_SIZE = 32;

    private final CityBuildCore  plugin;
    private final PlotRepository repository;
    private final CoreConfig     config;

    /** Primary store by plot ID. */
    private final Map<Integer, Plot>      byId    = new ConcurrentHashMap<>();
    /** Spatial index: "cx:cz" → plots whose bounding box touches that bucket. */
    private final Map<String, List<Plot>> spatial = new ConcurrentHashMap<>();

    /**
     * Creates a new PlotManager.
     *
     * @param plugin     the owning plugin instance
     * @param repository data-access object for persistence
     * @param config     typed config wrapper
     */
    public PlotManager(CityBuildCore plugin, PlotRepository repository, CoreConfig config) {
        this.plugin     = plugin;
        this.repository = repository;
        this.config     = config;
    }

    // =========================================================================
    // Registration
    // =========================================================================

    /**
     * Registers a single plot into the in-memory cache and spatial index.
     *
     * @param plot the plot to register
     */
    public void registerPlot(Plot plot) {
        byId.put(plot.getId(), plot);
        indexPlot(plot);
    }

    /**
     * Registers many plots at once (e.g. after city generation or DB load).
     *
     * @param plots plots to register
     */
    public void bulkRegisterPlots(List<Plot> plots) {
        for (Plot plot : plots) registerPlot(plot);
    }

    // =========================================================================
    // Spatial lookup
    // =========================================================================

    /**
     * Returns the plot occupying the given world position, or {@code null}.
     *
     * @param world world name
     * @param x     block X
     * @param z     block Z
     * @return the plot at (x, z), or {@code null} if none
     */
    public Plot getPlotAt(String world, int x, int z) {
        List<Plot> bucket = spatial.get(bucketKey(x, z));
        if (bucket == null) return null;
        for (Plot p : bucket) {
            if (p.getWorldName().equals(world) && p.contains(x, z)) return p;
        }
        return null;
    }

    /**
     * Returns the plot at the given Bukkit location, or {@code null}.
     *
     * @param loc the location to check
     * @return the plot, or {@code null}
     */
    public Plot getPlotAt(Location loc) {
        if (loc.getWorld() == null) return null;
        return getPlotAt(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockZ());
    }

    /**
     * Returns the plot with the given ID wrapped in an {@link Optional}.
     *
     * @param id plot identifier
     * @return optional plot
     */
    public Optional<Plot> getById(int id) {
        return Optional.ofNullable(byId.get(id));
    }

    /**
     * Returns an unmodifiable view of all registered plots.
     *
     * @return all plots
     */
    public Collection<Plot> getAllPlots() {
        return Collections.unmodifiableCollection(byId.values());
    }

    // =========================================================================
    // Claim logic
    // =========================================================================

    /**
     * Attempts to claim a plot for the given player.
     *
     * <ol>
     *   <li>Checks whether the plot is already owned.</li>
     *   <li>Checks whether the player has reached their plot limit.</li>
     *   <li>Checks economy balance (if an economy provider is registered).</li>
     *   <li>Fires a cancellable {@link PlotClaimEvent}.</li>
     *   <li>Deducts balance and marks the plot as owned.</li>
     * </ol>
     *
     * @param player the claiming player
     * @param plot   the plot to claim
     * @return the result of the claim attempt
     */
    public ClaimResult claimPlot(Player player, Plot plot) {
        if (plot.getOwnerUUID() != null && !plot.isForSale()) {
            return ClaimResult.ALREADY_OWNED;
        }

        int max = config.getMaxPlotsPerPlayer();
        if (max > 0 && getPlotsByOwner(player.getUniqueId()).size() >= max) {
            return ClaimResult.MAX_PLOTS_REACHED;
        }

        double price = calculatePrice(plot);

        if (plugin.getAPI().hasEconomy() && !plugin.getAPI().hasBalance(player, price)) {
            return ClaimResult.NOT_ENOUGH_MONEY;
        }

        PlotClaimEvent event = new PlotClaimEvent(player, plot, price);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return ClaimResult.CANCELLED_BY_ADDON;
        }

        price = event.getPrice();
        if (plugin.getAPI().hasEconomy()) {
            plugin.getAPI().withdrawBalance(player, price);
        }

        plot.setOwnerUUID(player.getUniqueId());
        plot.setOwnerName(player.getName());
        plot.setPrice(price);
        plot.setForSale(false);
        plot.setClaimedAt(Instant.now());
        repository.savePlot(plot);
        updatePlotBorder(plot);
        return ClaimResult.SUCCESS;
    }

    /**
     * Unclaims a plot, returning it to the server with no refund.
     *
     * @param player the requesting player (must be the owner)
     * @param plot   the plot to unclaim
     * @return {@code true} if the plot was unclaimed, {@code false} if the player is not the owner
     */
    public boolean unclaimPlot(Player player, Plot plot) {
        if (!player.getUniqueId().equals(plot.getOwnerUUID())) return false;
 
        PlotUnclaimEvent event = new PlotUnclaimEvent(player, plot);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
 
        resetPlotData(plot);
        repository.savePlot(plot);
        updatePlotBorder(plot);
        return true;
    }

    // =========================================================================
    // Admin operations
    // =========================================================================

    /**
     * Resets a plot to unclaimed state (admin use, bypasses ownership check).
     *
     * @param id the plot ID
     * @return {@code true} if the plot was found and reset
     */
    public boolean adminResetPlot(int id) {
        Plot plot = byId.get(id);
        if (plot == null) return false;
        resetPlotData(plot);
        repository.savePlot(plot);
        updatePlotBorder(plot);
        return true;
    }

    /**
     * Removes a plot completely from cache and database.
     *
     * @param id the plot ID
     * @return {@code true} if the plot was found and deleted
     */
    public boolean adminDeletePlot(int id) {
        Plot plot = byId.remove(id);
        if (plot == null) return false;
        removeFromSpatialIndex(plot);
        repository.deletePlot(id);
        return true;
    }

    // =========================================================================
    // Price calculation
    // =========================================================================

    /**
     * Calculates the purchase price for a plot.
     *
     * <p>Plots closer to the world centre (0, 0) are more expensive, scaled
     * linearly between 1.0 and {@link CoreConfig#getLocationMultiplier()}.</p>
     *
     * @param plot the plot to price
     * @return the calculated price
     */
    public double calculatePrice(Plot plot) {
        int radius = config.getMapRadius();
        if (radius <= 0) radius = 1500;
        double dist = Math.sqrt(
            (double) plot.getCenterX() * plot.getCenterX() +
            (double) plot.getCenterZ() * plot.getCenterZ()
        );
        double distFraction = Math.min(1.0, dist / radius);
        double locMult = 1.0 + (config.getLocationMultiplier() - 1.0) * (1.0 - distFraction);
        return plot.getArea() * config.getPricePerBlock() * locMult;
    }

    // =========================================================================
    // Queries
    // =========================================================================

    /**
     * Returns all plots owned by the given player.
     *
     * @param uuid player UUID
     * @return list of owned plots
     */
    public List<Plot> getPlotsByOwner(UUID uuid) {
        return byId.values().stream()
            .filter(p -> uuid.equals(p.getOwnerUUID()))
            .collect(Collectors.toList());
    }

    /**
     * Returns all plots currently listed for sale.
     *
     * @return for-sale plots
     */
    public List<Plot> getForSalePlots() {
        return byId.values().stream()
            .filter(Plot::isForSale)
            .collect(Collectors.toList());
    }

    /**
     * Returns plots sorted by distance to the given location, within {@code radius} blocks.
     *
     * @param loc    centre location
     * @param radius search radius in blocks
     * @return nearby plots, nearest first
     */
    public List<Plot> getPlotsNear(Location loc, int radius) {
        int bx = loc.getBlockX(), bz = loc.getBlockZ();
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "";
        return byId.values().stream()
            .filter(p -> p.getWorldName().equals(world))
            .filter(p -> {
                int dx = p.getCenterX() - bx, dz = p.getCenterZ() - bz;
                return dx * dx + dz * dz <= (long) radius * radius;
            })
            .sorted(Comparator.comparingDouble(p -> {
                int dx = p.getCenterX() - bx, dz = p.getCenterZ() - bz;
                return Math.sqrt(dx * dx + dz * dz);
            }))
            .collect(Collectors.toList());
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    /**
     * Saves every registered plot to the database (called on plugin disable).
     */
    public void saveAll() {
        for (Plot plot : byId.values()) {
            repository.savePlot(plot);
        }
    }

    /** @return total number of registered plots */
    public int getPlotCount() {
        return byId.size();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private void indexPlot(Plot plot) {
        int cx1 = Math.floorDiv(plot.getMinX(), BUCKET_SIZE);
        int cz1 = Math.floorDiv(plot.getMinZ(), BUCKET_SIZE);
        int cx2 = Math.floorDiv(plot.getMaxX(), BUCKET_SIZE);
        int cz2 = Math.floorDiv(plot.getMaxZ(), BUCKET_SIZE);
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                spatial.computeIfAbsent(cx + ":" + cz, k -> new ArrayList<>()).add(plot);
            }
        }
    }

    private void removeFromSpatialIndex(Plot plot) {
        int cx1 = Math.floorDiv(plot.getMinX(), BUCKET_SIZE);
        int cz1 = Math.floorDiv(plot.getMinZ(), BUCKET_SIZE);
        int cx2 = Math.floorDiv(plot.getMaxX(), BUCKET_SIZE);
        int cz2 = Math.floorDiv(plot.getMaxZ(), BUCKET_SIZE);
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                List<Plot> bucket = spatial.get(cx + ":" + cz);
                if (bucket != null) bucket.remove(plot);
            }
        }
    }

    private String bucketKey(int x, int z) {
        return Math.floorDiv(x, BUCKET_SIZE) + ":" + Math.floorDiv(z, BUCKET_SIZE);
    }

    private void resetPlotData(Plot plot) {
        plot.setOwnerUUID(null);
        plot.setOwnerName(null);
        plot.setPlotName(null);
        plot.setForSale(false);
        plot.setPrice(0);
        plot.setClaimedAt(null);
        plot.setHasCustomSpawn(false);
        plot.getTrustedPlayers().clear();
        plot.getBannedPlayers().clear();
    }

    /**
     * Updates the physical border of a plot asynchronously.
     * Unclaimed plots get OAK_FENCE, claimed plots get NETHER_BRICK_FENCE.
     *
     * @param plot the plot whose border should be updated
     */
    public void updatePlotBorder(Plot plot) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.World world = Bukkit.getWorld(plot.getWorldName());
            if (world == null) return;

            Material fenceMat = plot.getOwnerUUID() == null ? Material.OAK_FENCE : Material.NETHER_BRICK_FENCE;
            
            // Edges (North/South)
            for (int x = plot.getMinX(); x <= plot.getMaxX(); x++) {
                setFenceOrCorner(world, x, plot.getMinZ(), x == plot.getMinX() || x == plot.getMaxX(), fenceMat);
                setFenceOrCorner(world, x, plot.getMaxZ(), x == plot.getMinX() || x == plot.getMaxX(), fenceMat);
            }
            // Edges (West/East) - skip corners
            for (int z = plot.getMinZ() + 1; z < plot.getMaxZ(); z++) {
                setFenceOrCorner(world, plot.getMinX(), z, false, fenceMat);
                setFenceOrCorner(world, plot.getMaxX(), z, false, fenceMat);
            }
        });
    }

    private void setFenceOrCorner(org.bukkit.World world, int x, int z, boolean isCorner, Material fenceMat) {
        int y = getSurfaceY(world, x, z);
        Material mat = isCorner ? Material.BRICKS : fenceMat;
        world.getBlockAt(x, y + 1, z).setType(mat, false);
    }

    private int getSurfaceY(org.bukkit.World world, int x, int z) {
        for (int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
            Block b = world.getBlockAt(x, y, z);
            if (!b.getType().isAir() && b.getType() != Material.WATER) return y;
        }
        return 64;
    }
}
