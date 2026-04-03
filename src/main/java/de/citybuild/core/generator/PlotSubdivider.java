package de.citybuild.core.generator;

import de.citybuild.core.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Subdivides the city space into a structured unit-based grid of plots.
 * Each plot is enclosed by a fence and features a sales sign.
 */
public class PlotSubdivider {

    private static final int UNIT_SIZE = 22; // Each plot unit is 22x22 blocks
    private static final int GRID_STEP = 24; // Unit size + 2 block gap

    private final Random rng;
    private final RoadGenerator roads;

    public PlotSubdivider(long seed, RoadGenerator roads) {
        this.rng   = new Random(seed ^ 0x0F0F0F0F0F0F0F0FL);
        this.roads = roads;
    }

    public List<Plot> subdivide(World world, int mapRadius) {
        List<Plot> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        int plotId = 1;

        // Scan the world in a regular grid
        for (int x = -mapRadius + 10; x < mapRadius - 10; x += GRID_STEP) {
            for (int z = -mapRadius + 10; z < mapRadius - 10; z += GRID_STEP) {
                
                // If any part of the 22x22 area overlaps a road, skip it
                if (overlapsRoad(x, z, UNIT_SIZE)) continue;
                
                // Determine plot size (1x1 or 2x2 units)
                int sizeUnits = (rng.nextDouble() < 0.2) ? 2 : 1; 
                int width = sizeUnits * UNIT_SIZE + (sizeUnits - 1) * (GRID_STEP - UNIT_SIZE);
                
                // Check if the larger plot would overlap a road or boundary
                if (sizeUnits == 2 && (x + width > mapRadius || overlapsRoad(x, z, width))) {
                    width = UNIT_SIZE;
                    sizeUnits = 1;
                }

                int minX = x;
                int minZ = z;
                int maxX = x + width - 1;
                int maxZ = z + width - 1;

                Plot plot = new Plot(plotId++, world.getName(), minX, minZ, maxX, maxZ);
                // Assign a price based on size
                plot.setPrice(sizeUnits == 1 ? 500.0 : 1500.0);
                plot.setForSale(true);
                
                result.add(plot);
                
                // Mark area as visited (for simplified grid, not strictly needed but good for consistency)
                markVisited(visited, minX, minZ, maxX, maxZ);
            }
        }

        return result;
    }

    private boolean overlapsRoad(int startX, int startZ, int size) {
        for (int x = startX - 2; x < startX + size + 2; x++) {
            for (int z = startZ - 2; z < startZ + size + 2; z++) {
                if (roads.isRoad(x, z)) return true;
            }
        }
        return false;
    }

    public List<Runnable> getBorderTasks(World world, List<Plot> plots) {
        List<Runnable> tasks = new ArrayList<>();
        for (Plot plot : plots) {
            tasks.add(() -> drawPlotFeatures(world, plot));
        }
        return tasks;
    }

    private void drawPlotFeatures(World world, Plot plot) {
        int minX = plot.getMinX();
        int minZ = plot.getMinZ();
        int maxX = plot.getMaxX();
        int maxZ = plot.getMaxZ();
        int y = 64; // Flat world height

        // 1. Draw Perimeter Fence
        for (int x = minX; x <= maxX; x++) {
            placeFence(world, x, y + 1, minZ);
            placeFence(world, x, y + 1, maxZ);
        }
        for (int z = minZ + 1; z < maxZ; z++) {
            placeFence(world, minX, y + 1, z);
            placeFence(world, maxX, y + 1, z);
        }

        // 2. Place Sales Sign
        // Find a suitable spot for the sign (near a road)
        placeSalesSign(world, plot, y);
    }

    private void placeFence(World world, int x, int y, int z) {
        world.getBlockAt(x, y, z).setType(Material.OAK_FENCE, false);
    }

    private void placeSalesSign(World world, Plot plot, int y) {
        // Simple logic: Place sign at the middle of the front edge (minZ)
        int signX = (plot.getMinX() + plot.getMaxX()) / 2;
        int signZ = plot.getMinZ();
        
        Block block = world.getBlockAt(signX, y + 1, signZ);
        block.setType(Material.OAK_SIGN, false);
        
        if (block.getState() instanceof Sign sign) {
            sign.setLine(0, "§b[GRUNDSTÜCK]");
            sign.setLine(1, "§7ID: #" + plot.getId());
            sign.setLine(2, "§2Preis: " + (int)plot.getPrice() + "$");
            sign.setLine(3, "§8Rechtsklick");
            sign.update();
        }
    }

    private void markVisited(Set<Long> visited, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                visited.add(((long) x << 32) | (z & 0xFFFFFFFFL));
            }
        }
    }
}
