package de.citybuild.core.generator;

import de.citybuild.core.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Identifies the open space between roads and subdivides it into city plots.
 *
 * <p>The algorithm scans the map on a coarse grid (step = minimum plot size).
 * Each unvisited, non-road cell is expanded rectangularly until roads or the
 * map boundary are reached, producing a "city block".  Each block is then
 * subdivided into individual {@link Plot} objects.  Plots that overlap water
 * by more than 30 % are discarded.</p>
 */
public class PlotSubdivider {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int SCAN_STEP     = 16;   // grid scan step (= min plot size)
    private static final int PLOT_MIN      = 16;
    private static final int PLOT_MAX      = 48;
    private static final int PLOT_REMNANT  = 12;   // sliver smaller than this merges with neighbour
    private static final double WATER_THRESHOLD = 0.30;

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final Random        rng;
    private final RoadGenerator roads;
    private final WaterGenerator water;

    /**
     * Creates a new PlotSubdivider.
     *
     * @param seed  random seed
     * @param roads road generator (for {@code isRoad()} queries)
     * @param water water generator (for water-coverage filtering)
     */
    public PlotSubdivider(long seed, RoadGenerator roads, WaterGenerator water) {
        this.rng   = new Random(seed ^ 0x0F0F0F0F0F0F0F0FL);
        this.roads = roads;
        this.water = water;
    }

    /**
     * Scans the generated city area and returns a list of sized, numbered plots.
     *
     * <p>This method does not access the world and is safe to call off the main
     * thread.</p>
     *
     * @param world     target world (used only for its name)
     * @param mapRadius half-side of the generated region in blocks
     * @return list of all valid plots
     */
    public List<Plot> subdivide(World world, int mapRadius) {
        List<Plot> result = new ArrayList<>();
        Set<Long>  visited = new HashSet<>();
        int plotId = 1;

        for (int x = -mapRadius + SCAN_STEP; x < mapRadius - SCAN_STEP; x += SCAN_STEP) {
            for (int z = -mapRadius + SCAN_STEP; z < mapRadius - SCAN_STEP; z += SCAN_STEP) {

                if (roads.isRoad(x, z)) continue;
                if (visited.contains(encode(x, z))) continue;

                // ── Expand block boundaries ───────────────────────────────────
                int blockMinX = expandLeft(x, z, mapRadius);
                int blockMaxX = expandRight(x, z, mapRadius);
                int blockMinZ = expandFront(x, z, mapRadius);
                int blockMaxZ = expandBack(x, z, mapRadius);

                int blockW = blockMaxX - blockMinX;
                int blockD = blockMaxZ - blockMinZ;

                if (blockW < PLOT_MIN || blockD < PLOT_MIN) {
                    markVisited(visited, blockMinX, blockMinZ, blockMaxX, blockMaxZ);
                    continue;
                }

                // ── Subdivide block into individual plots ─────────────────────
                List<int[]> rects = subdivideBlock(blockMinX, blockMinZ, blockMaxX, blockMaxZ);

                for (int[] r : rects) {
                    // r = {minX, minZ, maxX, maxZ}
                    if (waterCoverage(r[0], r[1], r[2], r[3]) > WATER_THRESHOLD) continue;

                    Plot plot = new Plot(plotId++, world.getName(), r[0], r[1], r[2], r[3]);
                    plot.setCornerLot(isCornerLot(r[0], r[1], r[2], r[3]));
                    result.add(plot);
                }

                markVisited(visited, blockMinX, blockMinZ, blockMaxX, blockMaxZ);
            }
        }

        return result;
    }

    /**
     * Returns a list of tasks for placing plot borders.
     *
     * @param world target world
     * @param plots plots to border
     * @return list of plot border placement tasks
     */
    public java.util.List<Runnable> getBorderTasks(World world, List<Plot> plots) {
        java.util.List<Runnable> tasks = new java.util.ArrayList<>();
        for (Plot plot : plots) {
            tasks.add(() -> {
                try {
                    drawBorder(world, plot);
                } catch (Exception e) {
                    Bukkit.getServer().getLogger().warning("[PlotSubdivider] Error placing border for plot " + plot.getId()
                        + ": " + e.getMessage());
                }
            });
        }
        return tasks;
    }

    // =========================================================================
    // Block expansion helpers
    // =========================================================================

    /** Expands leftward (negative X) until a road or boundary is found. */
    private int expandLeft(int x, int z, int mapRadius) {
        for (int bx = x - SCAN_STEP; bx > -mapRadius; bx -= SCAN_STEP) {
            if (roads.isRoad(bx, z)) return bx + SCAN_STEP;
        }
        return -mapRadius;
    }

    private int expandRight(int x, int z, int mapRadius) {
        for (int bx = x + SCAN_STEP; bx < mapRadius; bx += SCAN_STEP) {
            if (roads.isRoad(bx, z)) return bx - SCAN_STEP;
        }
        return mapRadius;
    }

    private int expandFront(int x, int z, int mapRadius) {
        for (int bz = z - SCAN_STEP; bz > -mapRadius; bz -= SCAN_STEP) {
            if (roads.isRoad(x, bz)) return bz + SCAN_STEP;
        }
        return -mapRadius;
    }

    private int expandBack(int x, int z, int mapRadius) {
        for (int bz = z + SCAN_STEP; bz < mapRadius; bz += SCAN_STEP) {
            if (roads.isRoad(x, bz)) return bz - SCAN_STEP;
        }
        return mapRadius;
    }

    // =========================================================================
    // Block subdivision
    // =========================================================================

    /**
     * Subdivides a city block into plot rectangles.
     * Chooses the cut axis by which dimension is larger.
     *
     * @return list of int[]{minX, minZ, maxX, maxZ}
     */
    private List<int[]> subdivideBlock(int minX, int minZ, int maxX, int maxZ) {
        List<int[]> result = new ArrayList<>();
        subdivideRecursive(minX, minZ, maxX, maxZ, result);
        return result;
    }

    private void subdivideRecursive(int minX, int minZ, int maxX, int maxZ, List<int[]> out) {
        int w = maxX - minX;
        int d = maxZ - minZ;

        if (w <= PLOT_MAX && d <= PLOT_MAX) {
            // This rectangle is an individual plot
            out.add(new int[]{minX, minZ, maxX, maxZ});
            return;
        }

        if (w >= d) {
            // Cut along X
            int cut = chooseCut(minX, maxX);
            if (cut <= 0) {
                out.add(new int[]{minX, minZ, maxX, maxZ});
                return;
            }
            int leftW  = cut - minX;
            int rightW = maxX - cut;
            if (leftW < PLOT_REMNANT)  { subdivideRecursive(minX, minZ, maxX, maxZ, out); return; }
            if (rightW < PLOT_REMNANT) { subdivideRecursive(minX, minZ, maxX, maxZ, out); return; }
            subdivideRecursive(minX, minZ, cut, maxZ, out);
            subdivideRecursive(cut,  minZ, maxX, maxZ, out);
        } else {
            // Cut along Z
            int cut = chooseCut(minZ, maxZ);
            if (cut <= 0) {
                out.add(new int[]{minX, minZ, maxX, maxZ});
                return;
            }
            int frontD = cut - minZ;
            int backD  = maxZ - cut;
            if (frontD < PLOT_REMNANT) { subdivideRecursive(minX, minZ, maxX, maxZ, out); return; }
            if (backD  < PLOT_REMNANT) { subdivideRecursive(minX, minZ, maxX, maxZ, out); return; }
            subdivideRecursive(minX, minZ, maxX, cut, out);
            subdivideRecursive(minX, cut,  maxX, maxZ, out);
        }
    }

    /**
     * Picks a random cut point between {@code min} and {@code max} that yields
     * two pieces each at least {@link #PLOT_MIN} wide, biased toward PLOT_MIN–PLOT_MAX.
     *
     * @return cut coordinate, or -1 if no valid cut exists
     */
    private int chooseCut(int min, int max) {
        int span = max - min;
        if (span < PLOT_MIN * 2) return -1;
        int lo = min + PLOT_MIN;
        int hi = max - PLOT_MIN;
        return lo + rng.nextInt(hi - lo + 1);
    }

    // =========================================================================
    // Water coverage
    // =========================================================================

    private double waterCoverage(int minX, int minZ, int maxX, int maxZ) {
        int total = 0, wet = 0;
        for (int x = minX; x <= maxX; x += 2) {
            for (int z = minZ; z <= maxZ; z += 2) {
                total++;
                if (water.isWater(x, z)) wet++;
            }
        }
        return total == 0 ? 0 : (double) wet / total;
    }

    // =========================================================================
    // Corner-lot detection
    // =========================================================================

    /**
     * A plot is a corner lot when it has road neighbours on two or more sides.
     */
    private boolean isCornerLot(int minX, int minZ, int maxX, int maxZ) {
        int sides = 0;
        if (hasRoadAlongX(minX - 1, minZ, maxZ)) sides++;
        if (hasRoadAlongX(maxX + 1, minZ, maxZ)) sides++;
        if (hasRoadAlongZ(minZ - 1, minX, maxX)) sides++;
        if (hasRoadAlongZ(maxZ + 1, minX, maxX)) sides++;
        return sides >= 2;
    }

    private boolean hasRoadAlongX(int x, int minZ, int maxZ) {
        for (int z = minZ; z <= maxZ; z += SCAN_STEP) {
            if (roads.isRoad(x, z)) return true;
        }
        return false;
    }

    private boolean hasRoadAlongZ(int z, int minX, int maxX) {
        for (int x = minX; x <= maxX; x += SCAN_STEP) {
            if (roads.isRoad(x, z)) return true;
        }
        return false;
    }

    // =========================================================================
    // Border placement
    // =========================================================================

    private void drawBorder(World world, Plot plot) {
        int minX = plot.getMinX();
        int minZ = plot.getMinZ();
        int maxX = plot.getMaxX();
        int maxZ = plot.getMaxZ();

        // North/South edges (varying X, fixed Z)
        for (int x = minX; x <= maxX; x++) {
            placeFenceOrCorner(world, x, minZ, x == minX || x == maxX);
            placeFenceOrCorner(world, x, maxZ, x == minX || x == maxX);
        }
        // West/East edges (fixed X, varying Z), skip already-placed corners
        for (int z = minZ + 1; z < maxZ; z++) {
            placeFenceOrCorner(world, minX, z, false);
            placeFenceOrCorner(world, maxX, z, false);
        }
    }

    private void placeFenceOrCorner(World world, int x, int z, boolean isCorner) {
        int surface = getBlockSurface(world, x, z);
        Material mat = isCorner ? Material.BRICKS : Material.OAK_FENCE;
        world.getBlockAt(x, surface + 1, z).setType(mat, false);
    }

    private int getBlockSurface(World world, int x, int z) {
        for (int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
            Material m = world.getBlockAt(x, y, z).getType();
            if (!m.isAir() && m != Material.WATER) return y;
        }
        return world.getMinHeight() + 1;
    }

    // =========================================================================
    // Utility
    // =========================================================================

    private void markVisited(Set<Long> visited, int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x += SCAN_STEP) {
            for (int z = minZ; z <= maxZ; z += SCAN_STEP) {
                visited.add(encode(x, z));
            }
        }
    }

    private static long encode(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
