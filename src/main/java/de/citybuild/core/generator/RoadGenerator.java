package de.citybuild.core.generator;

import de.citybuild.core.model.RoadSegment;
import de.citybuild.core.model.RoadSegment.RoadType;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Generates a three-tier road network: main arteries via MST, secondary branches,
 * and tertiary connectors.  Also builds bridges where roads cross water.
 *
 * <p>Main roads connect district centres found via Poisson-disk sampling and
 * linked with Prim's MST algorithm, guaranteeing a fully-connected, loop-free
 * primary network.  Secondary roads branch perpendicularly from main roads at
 * random intervals.  Tertiary roads connect nearby secondary endpoints.</p>
 *
 * <p>All road geometry is pre-calculated in {@link #generateRoads(int)} without
 * world access; only {@link #placeRoadBlocks(World, List)} requires the main
 * thread.</p>
 */
public class RoadGenerator {

    // ── Configuration defaults ────────────────────────────────────────────────
    private static final int DISTRICT_MIN      = 8;
    private static final int DISTRICT_MAX      = 14;
    private static final int DISTRICT_MIN_DIST = 200;

    private static final int MAIN_WIDTH      = 8;
    private static final int SECONDARY_WIDTH = 5;
    private static final int TERTIARY_WIDTH  = 4;

    private static final int SECONDARY_INTERVAL_MIN = 60;
    private static final int SECONDARY_INTERVAL_MAX = 120;
    private static final int SECONDARY_LENGTH_MIN   = 80;
    private static final int SECONDARY_LENGTH_MAX   = 200;

    private static final int TERTIARY_CONNECT_DIST = 100;

    private static final int LAMP_INTERVAL = 8;

    // ── Dependencies ──────────────────────────────────────────────────────────
    private final Random          rng;
    private final TerrainGenerator terrain;
    private final WaterGenerator  water;

    /**
     * Flat set of all XZ positions occupied by any road (used by PlotSubdivider).
     */
    private final Set<Long> roadPositions = new HashSet<>();

    /**
     * Creates a new RoadGenerator.
     *
     * @param seed    random seed
     * @param terrain terrain generator (for surface height queries)
     * @param water   water generator (for bridge detection)
     */
    public RoadGenerator(long seed, TerrainGenerator terrain, WaterGenerator water) {
        this.rng     = new Random(seed ^ 0xF00DCAFE01234567L);
        this.terrain = terrain;
        this.water   = water;
    }

    /**
     * Computes the full road network for the given map radius and returns the
     * list of {@link RoadSegment} data objects.  No blocks are placed.
     *
     * <p>This method is safe to call off the main thread.</p>
     *
     * @param mapRadius half-side of the generated region in blocks
     * @return all road segments (main, secondary, tertiary)
     */
    public List<RoadSegment> generateRoads(int mapRadius) {
        List<int[]> centres = poissonDiskCentres(mapRadius);
        List<RoadSegment> roads = new ArrayList<>();

        // ── Tier 1: Prim MST main roads ──────────────────────────────────────
        List<int[][]> mstEdges = primMST(centres);
        List<int[][]> mainEdgeSegs = new ArrayList<>(); // store individual L-segments for Tier 2

        for (int[][] edge : mstEdges) {
            int[] a = edge[0], b = edge[1];
            boolean xFirst = rng.nextBoolean();

            int[] corner = xFirst ? new int[]{b[0], a[1]} : new int[]{a[0], b[1]};
            int[][] segs = {
                {a[0], a[1], corner[0], corner[1]},
                {corner[0], corner[1], b[0], b[1]}
            };

            for (int[] seg : segs) {
                int minX = Math.min(seg[0], seg[2]);
                int minZ = Math.min(seg[1], seg[3]);
                int maxX = Math.max(seg[0], seg[2]);
                int maxZ = Math.max(seg[1], seg[3]);
                int half = MAIN_WIDTH / 2;
                boolean horiz = (seg[1] == seg[3]); // constant Z → horizontal
                if (horiz) { minZ -= half; maxZ += half; }
                else       { minX -= half; maxX += half; }

                roads.add(new RoadSegment(minX, minZ, maxX, maxZ, MAIN_WIDTH, RoadType.MAIN));
                mainEdgeSegs.add(new int[]{seg[0], seg[1], seg[2], seg[3]});
                registerRoad(minX, minZ, maxX, maxZ);
            }
        }

        // ── Tier 2: Secondary branches ────────────────────────────────────────
        List<int[]> secEndpoints = new ArrayList<>();

        for (int[] seg : mainEdgeSegs) {
            addSecondaryBranches(seg, mapRadius, roads, secEndpoints);
        }

        // ── Tier 3: Tertiary connectors ───────────────────────────────────────
        addTertiaryConnectors(secEndpoints, mapRadius, roads);

        return roads;
    }

    /**
     * Places all road blocks (pavement, bürgersteig, lamps, bridges, crossings)
     * into the world.  <strong>Must be called on the main server thread.</strong>
     *
     * @param world target world
     * @param roads the road segments returned by {@link #generateRoads(int)}
     */
    public void placeRoadBlocks(World world, List<RoadSegment> roads) {
        Logger log = world.getServer().getLogger();
        for (RoadSegment seg : roads) {
            try {
                placeSegment(world, seg);
            } catch (Exception e) {
                log.warning("[RoadGenerator] Error placing segment " + seg + ": " + e.getMessage());
            }
        }
        log.info("[RoadGenerator] Road blocks placed (" + roads.size() + " segments).");
    }

    /**
     * Returns {@code true} when the block at (x, z) is part of any road segment.
     * Used by {@link PlotSubdivider} to detect road boundaries.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return whether this position is occupied by a road
     */
    public boolean isRoad(int x, int z) {
        return roadPositions.contains(encode(x, z));
    }

    // =========================================================================
    // Internal geometry helpers
    // =========================================================================

    /** Generates district centre points via Poisson-disk sampling. */
    private List<int[]> poissonDiskCentres(int mapRadius) {
        int count = DISTRICT_MIN + rng.nextInt(DISTRICT_MAX - DISTRICT_MIN + 1);
        List<int[]> pts = new ArrayList<>();
        int attempts    = 0;
        int maxAttempts = count * 50;

        while (pts.size() < count && attempts < maxAttempts) {
            attempts++;
            int limit = mapRadius - 100;
            int x = rng.nextInt(limit * 2 + 1) - limit;
            int z = rng.nextInt(limit * 2 + 1) - limit;

            boolean tooClose = false;
            for (int[] p : pts) {
                int dx = p[0] - x, dz = p[1] - z;
                if (Math.sqrt(dx * dx + dz * dz) < DISTRICT_MIN_DIST) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) pts.add(new int[]{x, z});
        }

        // Always include the city centre
        pts.add(0, new int[]{0, 0});
        return pts;
    }

    /**
     * Runs Prim's MST on the given list of points.
     *
     * @return list of edges, each being int[2][2] = {pointA, pointB}
     */
    private List<int[][]> primMST(List<int[]> pts) {
        int n = pts.size();
        boolean[] inTree = new boolean[n];
        double[] minDist = new double[n];
        int[]    parent  = new int[n];

        Arrays.fill(minDist, Double.MAX_VALUE);
        Arrays.fill(parent, -1);
        minDist[0] = 0;

        // minHeap: {dist*1000, nodeIndex}
        PriorityQueue<long[]> pq = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        pq.offer(new long[]{0L, 0L});

        List<int[][]> edges = new ArrayList<>();

        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int u = (int) top[1];
            if (inTree[u]) continue;
            inTree[u] = true;

            if (parent[u] >= 0) {
                edges.add(new int[][]{pts.get(parent[u]), pts.get(u)});
            }

            for (int v = 0; v < n; v++) {
                if (inTree[v]) continue;
                int[] pu = pts.get(u), pv = pts.get(v);
                int dx = pu[0] - pv[0], dz = pu[1] - pv[1];
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < minDist[v]) {
                    minDist[v] = d;
                    parent[v]  = u;
                    pq.offer(new long[]{(long)(d * 1000), v});
                }
            }
        }
        return edges;
    }

    /**
     * Adds secondary branch roads perpendicular to a given main-road segment.
     *
     * @param seg          the centerline of the main road (x1,z1,x2,z2)
     * @param mapRadius    map boundary
     * @param roads        output list to append segments to
     * @param endpoints    output list to append branch endpoints to
     */
    private void addSecondaryBranches(int[] seg, int mapRadius,
                                       List<RoadSegment> roads, List<int[]> endpoints) {
        int x1 = seg[0], z1 = seg[1], x2 = seg[2], z2 = seg[3];
        int length = (int) Math.sqrt((long)(x2-x1)*(x2-x1) + (long)(z2-z1)*(z2-z1));
        if (length == 0) return;

        boolean horiz = (z1 == z2); // constant Z → horizontal
        int interval = SECONDARY_INTERVAL_MIN + rng.nextInt(SECONDARY_INTERVAL_MAX - SECONDARY_INTERVAL_MIN + 1);

        for (int t = interval; t < length - interval; t += interval + rng.nextInt(40)) {
            // Position along the main segment
            int bx = x1 + (int)((double)(x2-x1)/length * t);
            int bz = z1 + (int)((double)(z2-z1)/length * t);

            int branchLen = SECONDARY_LENGTH_MIN + rng.nextInt(SECONDARY_LENGTH_MAX - SECONDARY_LENGTH_MIN + 1);
            int dir = rng.nextBoolean() ? 1 : -1;

            int ex, ez;
            if (horiz) {
                ex = bx;
                ez = bz + dir * branchLen;
                ez = Math.max(-mapRadius, Math.min(mapRadius, ez));
            } else {
                ex = bx + dir * branchLen;
                ez = bz;
                ex = Math.max(-mapRadius, Math.min(mapRadius, ex));
            }

            // Build the L-shaped secondary road (single axis since we branch perpendicularly)
            int minX = Math.min(bx, ex);
            int minZ = Math.min(bz, ez);
            int maxX = Math.max(bx, ex);
            int maxZ = Math.max(bz, ez);
            int half = SECONDARY_WIDTH / 2;
            if (horiz) { minX -= half; maxX += half; }   // branch goes along Z
            else       { minZ -= half; maxZ += half; }   // branch goes along X

            roads.add(new RoadSegment(minX, minZ, maxX, maxZ, SECONDARY_WIDTH, RoadType.SECONDARY));
            registerRoad(minX, minZ, maxX, maxZ);
            endpoints.add(new int[]{ex, ez});
        }
    }

    /**
     * Connects secondary endpoints that are closer than {@link #TERTIARY_CONNECT_DIST}
     * with tertiary road segments.
     */
    private void addTertiaryConnectors(List<int[]> endpoints, int mapRadius,
                                        List<RoadSegment> roads) {
        for (int i = 0; i < endpoints.size(); i++) {
            for (int j = i + 1; j < endpoints.size(); j++) {
                int[] a = endpoints.get(i), b = endpoints.get(j);
                int dx = a[0] - b[0], dz = a[1] - b[1];
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist < TERTIARY_CONNECT_DIST) {
                    int minX = Math.min(a[0], b[0]);
                    int minZ = Math.min(a[1], b[1]);
                    int maxX = Math.max(a[0], b[0]);
                    int maxZ = Math.max(a[1], b[1]);
                    int half = TERTIARY_WIDTH / 2;
                    // Connect L-shape: X then Z
                    int cornerX = b[0], cornerZ = a[1];
                    // Segment 1: a → corner (along X)
                    addTertiarySegment(Math.min(a[0], cornerX), a[1], Math.max(a[0], cornerX), a[1],
                        half, roads);
                    // Segment 2: corner → b (along Z)
                    addTertiarySegment(b[0], Math.min(a[1], b[1]), b[0], Math.max(a[1], b[1]),
                        half, roads);
                }
            }
        }
    }

    private void addTertiarySegment(int x1, int z1, int x2, int z2, int half, List<RoadSegment> roads) {
        int minX = Math.min(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxX = Math.max(x1, x2);
        int maxZ = Math.max(z1, z2);
        boolean horiz = (z1 == z2);
        if (horiz) { minZ -= half; maxZ += half; }
        else       { minX -= half; maxX += half; }
        roads.add(new RoadSegment(minX, minZ, maxX, maxZ, TERTIARY_WIDTH, RoadType.TERTIARY));
        registerRoad(minX, minZ, maxX, maxZ);
    }

    // =========================================================================
    // Block placement
    // =========================================================================

    /** Places one road segment's blocks into the world. */
    private void placeSegment(World world, RoadSegment seg) {
        boolean horiz = (seg.maxZ() - seg.minZ()) >= (seg.maxX() - seg.minX());
        int half = seg.width() / 2;

        for (int x = seg.minX(); x <= seg.maxX(); x++) {
            for (int z = seg.minZ(); z <= seg.maxZ(); z++) {
                if (water.isWater(x, z)) {
                    placeBridge(world, x, z, seg);
                    continue;
                }

                int surface = terrain.getHeightAt(x, z);
                boolean isSidewalk = isSidewalk(x, z, seg, horiz, half);

                Material pavement  = roadMaterial(seg.type(), false);
                Material sidewalk  = roadMaterial(seg.type(), true);
                Material toPlace   = isSidewalk ? sidewalk : pavement;

                world.getBlockAt(x, surface, z).setType(toPlace, false);
                // Clear one block above
                world.getBlockAt(x, surface + 1, z).setType(Material.AIR, false);

                // Crossings: chiselled stone bricks at inner corners of MAIN intersections
                if (seg.type() == RoadType.MAIN && isIntersectionCorner(x, z, seg, horiz, half)) {
                    world.getBlockAt(x, surface, z).setType(Material.CHISELED_STONE_BRICKS, false);
                }
            }
        }

        // Street lamps on MAIN roads
        if (seg.type() == RoadType.MAIN) {
            placeLamps(world, seg, horiz, half);
        }
    }

    /** Places a bridge over water at the given position for a road segment. */
    private void placeBridge(World world, int x, int z, RoadSegment seg) {
        int surface = terrain.getHeightAt(x, z);
        int bridgeY = surface; // water surface is at surface-1, bridge deck is at surface

        // Support pillar
        world.getBlockAt(x, bridgeY - 1, z).setType(Material.OAK_LOG, false);
        // Deck
        world.getBlockAt(x, bridgeY, z).setType(Material.OAK_PLANKS, false);
        // Clear above
        for (int y = bridgeY + 1; y <= bridgeY + 3; y++) {
            world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }

        // Rail fence on the edges
        boolean horiz = (seg.maxZ() - seg.minZ()) >= (seg.maxX() - seg.minX());
        int half = seg.width() / 2;
        boolean isEdge = horiz
            ? (x == seg.minX() || x == seg.maxX())
            : (z == seg.minZ() || z == seg.maxZ());

        if (isEdge) {
            world.getBlockAt(x, bridgeY + 1, z).setType(Material.OAK_FENCE, false);
        }
    }

    /**
     * Places street lamps every {@link #LAMP_INTERVAL} blocks along the sidewalk
     * of a main road.
     */
    private void placeLamps(World world, RoadSegment seg, boolean horiz, int half) {
        if (horiz) {
            // Road runs along Z; lamps placed along Z at x = minX and maxX
            for (int z = seg.minZ(); z <= seg.maxZ(); z += LAMP_INTERVAL) {
                placeLampAt(world, seg.minX(), z);
                placeLampAt(world, seg.maxX(), z);
            }
        } else {
            for (int x = seg.minX(); x <= seg.maxX(); x += LAMP_INTERVAL) {
                placeLampAt(world, x, seg.minZ());
                placeLampAt(world, x, seg.maxZ());
            }
        }
    }

    /** Places a single street lamp (2-high fence + sea lantern) at (x, z). */
    private void placeLampAt(World world, int x, int z) {
        if (water.isWater(x, z)) return;
        int y = terrain.getHeightAt(x, z);
        world.getBlockAt(x, y + 1, z).setType(Material.OAK_FENCE,   false);
        world.getBlockAt(x, y + 2, z).setType(Material.OAK_FENCE,   false);
        world.getBlockAt(x, y + 3, z).setType(Material.SEA_LANTERN, false);
    }

    // =========================================================================
    // Material helpers
    // =========================================================================

    private Material roadMaterial(RoadType type, boolean isSidewalk) {
        return switch (type) {
            case MAIN      -> isSidewalk ? Material.LIGHT_GRAY_CONCRETE : Material.GRAY_CONCRETE;
            case SECONDARY -> isSidewalk ? Material.COBBLESTONE         : Material.STONE_BRICKS;
            case TERTIARY  -> Material.COBBLESTONE;
        };
    }

    /** Returns true if the given position is the sidewalk strip (outermost block). */
    private boolean isSidewalk(int x, int z, RoadSegment seg, boolean horiz, int half) {
        if (seg.type() == RoadType.TERTIARY) return false;
        if (horiz) {
            return x == seg.minX() || x == seg.maxX();
        } else {
            return z == seg.minZ() || z == seg.maxZ();
        }
    }

    /** Returns true for positions that form intersection corner markers. */
    private boolean isIntersectionCorner(int x, int z, RoadSegment seg, boolean horiz, int half) {
        // Corner = at the end of the segment AND on the sidewalk edge
        boolean atEnd = horiz
            ? (z == seg.minZ() || z == seg.maxZ())
            : (x == seg.minX() || x == seg.maxX());
        boolean atSide = isSidewalk(x, z, seg, horiz, half);
        return atEnd && atSide;
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /** Registers every position within the bounding box as a road position. */
    private void registerRoad(int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                roadPositions.add(encode(x, z));
            }
        }
    }

    private static long encode(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
