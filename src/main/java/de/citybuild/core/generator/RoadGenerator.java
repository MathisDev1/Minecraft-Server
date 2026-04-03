package de.citybuild.core.generator;

import de.citybuild.core.model.RoadSegment;
import de.citybuild.core.model.RoadSegment.RoadType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * Generates a structured, wide road network for CityBuild.
 * Uses premium materials and clear lane marking.
 */
public class RoadGenerator {

    private static final int DISTRICT_MIN      = 6;
    private static final int DISTRICT_MAX      = 10;
    private static final int DISTRICT_MIN_DIST = 150;

    private static final int MAIN_WIDTH      = 16;
    private static final int SECONDARY_WIDTH = 12;
    private static final int TERTIARY_WIDTH  = 8;

    private static final int SECONDARY_INTERVAL_MIN = 80;
    private static final int SECONDARY_INTERVAL_MAX = 140;
    private static final int SECONDARY_LENGTH_MIN   = 100;
    private static final int SECONDARY_LENGTH_MAX   = 150;

    private static final int TERTIARY_CONNECT_DIST = 80;
    private static final int LAMP_INTERVAL = 12;

    private final Random rng;
    private final TerrainGenerator terrain;
    private final Set<Long> roadPositions = new HashSet<>();

    public RoadGenerator(long seed, TerrainGenerator terrain) {
        this.rng     = new Random(seed ^ 0xF00DCAFE01234567L);
        this.terrain = terrain;
    }

    public List<RoadSegment> generateRoads(int mapRadius) {
        List<int[]> centres = poissonDiskCentres(mapRadius);
        List<RoadSegment> roads = new ArrayList<>();

        // Tier 1: Main roads (MST)
        List<int[][]> mstEdges = primMST(centres);
        List<int[]> mainEdgeSegs = new ArrayList<>();

        for (int[][] edge : mstEdges) {
            int[] a = edge[0], b = edge[1];
            boolean xFirst = rng.nextBoolean();
            int[] corner = xFirst ? new int[]{b[0], a[1]} : new int[]{a[0], b[1]};
            int[][] segs = {{a[0], a[1], corner[0], corner[1]}, {corner[0], corner[1], b[0], b[1]}};

            for (int[] seg : segs) {
                int minX = Math.min(seg[0], seg[2]);
                int minZ = Math.min(seg[1], seg[3]);
                int maxX = Math.max(seg[0], seg[2]);
                int maxZ = Math.max(seg[1], seg[3]);
                int half = MAIN_WIDTH / 2;
                if (seg[1] == seg[3]) { minZ -= half; maxZ += half; }
                else                  { minX -= half; maxX += half; }

                roads.add(new RoadSegment(minX, minZ, maxX, maxZ, MAIN_WIDTH, RoadType.MAIN));
                mainEdgeSegs.add(new int[]{seg[0], seg[1], seg[2], seg[3]});
                registerRoad(minX, minZ, maxX, maxZ);
            }
        }

        // Tier 2: Secondary branches
        List<int[]> secEndpoints = new ArrayList<>();
        for (int[] seg : mainEdgeSegs) {
            addSecondaryBranches(seg, mapRadius, roads, secEndpoints);
        }

        // Tier 3: Tertiary connectors
        addTertiaryConnectors(secEndpoints, mapRadius, roads);

        return roads;
    }

    public List<Runnable> getPlacementTasks(World world, List<RoadSegment> roads) {
        List<Runnable> tasks = new ArrayList<>();
        for (RoadSegment seg : roads) {
            tasks.add(() -> placeSegment(world, seg));
        }
        return tasks;
    }

    private void placeSegment(World world, RoadSegment seg) {
        boolean horiz = (seg.maxZ() - seg.minZ()) >= (seg.maxX() - seg.minX());
        int half = seg.width() / 2;

        for (int x = seg.minX(); x <= seg.maxX(); x++) {
            for (int z = seg.minZ(); z <= seg.maxZ(); z++) {
                int surface = terrain.getHeightAt(x, z);

                // Determine material
                int distFromCenter = horiz ? Math.abs(x - (seg.minX() + half)) : Math.abs(z - (seg.minZ() + half));
                Material mat;

                if (distFromCenter == half) {
                    mat = Material.STONE_BRICKS; // Curb
                } else if (distFromCenter > half - 3) {
                    mat = Material.SMOOTH_STONE; // Sidewalk
                } else if (distFromCenter == 0 && seg.type() == RoadType.MAIN && (horiz ? z % 4 : x % 4) < 2) {
                    mat = Material.YELLOW_CONCRETE; // Lane marker
                } else {
                    mat = Material.GRAY_CONCRETE; // Asphalt
                }

                world.getBlockAt(x, surface, z).setType(mat, false);
                world.getBlockAt(x, surface + 1, z).setType(Material.AIR, false);
            }
        }

        if (seg.type() == RoadType.MAIN) {
            placeLamps(world, seg, horiz, half);
        }
    }

    private void placeLamps(World world, RoadSegment seg, boolean horiz, int half) {
        for (int i = (horiz ? seg.minZ() : seg.minX()); i <= (horiz ? seg.maxZ() : seg.maxX()); i += LAMP_INTERVAL) {
            int x = horiz ? seg.minX() : i;
            int z = horiz ? i : seg.minZ();
            placeLampAt(world, x, z);
            
            x = horiz ? seg.maxX() : i;
            z = horiz ? i : seg.maxZ();
            placeLampAt(world, x, z);
        }
    }

    private void placeLampAt(World world, int x, int z) {
        int y = terrain.getHeightAt(x, z);
        world.getBlockAt(x, y + 1, z).setType(Material.STONE_BRICK_WALL, false);
        world.getBlockAt(x, y + 2, z).setType(Material.STONE_BRICK_WALL, false);
        world.getBlockAt(x, y + 3, z).setType(Material.SEA_LANTERN, false);
    }

    public boolean isRoad(int x, int z) {
        return roadPositions.contains(encode(x, z));
    }

    private List<int[]> poissonDiskCentres(int mapRadius) {
        int count = DISTRICT_MIN + rng.nextInt(DISTRICT_MAX - DISTRICT_MIN + 1);
        List<int[]> pts = new ArrayList<>();
        pts.add(new int[]{0, 0});
        for (int i = 0; i < count; i++) {
            int x = rng.nextInt(mapRadius * 2) - mapRadius;
            int z = rng.nextInt(mapRadius * 2) - mapRadius;
            pts.add(new int[]{x, z});
        }
        return pts;
    }

    private List<int[][]> primMST(List<int[]> pts) {
        int n = pts.size();
        boolean[] inTree = new boolean[n];
        double[] minDist = new double[n];
        int[] parent = new int[n];
        Arrays.fill(minDist, Double.MAX_VALUE);
        minDist[0] = 0;
        PriorityQueue<long[]> pq = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        pq.offer(new long[]{0, 0});
        List<int[][]> edges = new ArrayList<>();
        while (!pq.isEmpty()) {
            int u = (int) pq.poll()[1];
            if (inTree[u]) continue;
            inTree[u] = true;
            if (parent[u] != 0 || u != 0) edges.add(new int[][]{pts.get(parent[u]), pts.get(u)});
            for (int v = 0; v < n; v++) {
                if (inTree[v]) continue;
                double d = Math.sqrt(Math.pow(pts.get(u)[0] - pts.get(v)[0], 2) + Math.pow(pts.get(u)[1] - pts.get(v)[1], 2));
                if (d < minDist[v]) { minDist[v] = d; parent[v] = u; pq.offer(new long[]{(long)(d*100), v}); }
            }
        }
        return edges;
    }

    private void addSecondaryBranches(int[] seg, int mapRadius, List<RoadSegment> roads, List<int[]> endpoints) {
        int x1 = seg[0], z1 = seg[1], x2 = seg[2], z2 = seg[3];
        int len = (int) Math.sqrt(Math.pow(x1-x2, 2) + Math.pow(z1-z2, 2));
        boolean horiz = z1 == z2;
        for (int t = 40; t < len - 40; t += 100) {
            int bx = x1 + (int)((x2-x1)*(double)t/len);
            int bz = z1 + (int)((z2-z1)*(double)t/len);
            int ex = bx + (horiz ? 0 : (rng.nextBoolean() ? 100 : -100));
            int ez = bz + (horiz ? (rng.nextBoolean() ? 100 : -100) : 0);
            int minX = Math.min(bx, ex), maxX = Math.max(bx, ex);
            int minZ = Math.min(bz, ez), maxZ = Math.max(bz, ez);
            int half = SECONDARY_WIDTH / 2;
            if (horiz) { minX -= half; maxX += half; } else { minZ -= half; maxZ += half; }
            roads.add(new RoadSegment(minX, minZ, maxX, maxZ, SECONDARY_WIDTH, RoadType.SECONDARY));
            registerRoad(minX, minZ, maxX, maxZ);
            endpoints.add(new int[]{ex, ez});
        }
    }

    private void addTertiaryConnectors(List<int[]> endpoints, int mapRadius, List<RoadSegment> roads) {
        for (int i = 0; i < endpoints.size(); i++) {
            for (int j = i+1; j < endpoints.size(); j++) {
                if (Math.sqrt(Math.pow(endpoints.get(i)[0]-endpoints.get(j)[0], 2) + Math.pow(endpoints.get(i)[1]-endpoints.get(j)[1], 2)) < 150) {
                    int minX = Math.min(endpoints.get(i)[0], endpoints.get(j)[0]), maxX = Math.max(endpoints.get(i)[0], endpoints.get(j)[0]);
                    int minZ = Math.min(endpoints.get(i)[1], endpoints.get(j)[1]), maxZ = Math.max(endpoints.get(i)[1], endpoints.get(j)[1]);
                    roads.add(new RoadSegment(minX, minZ, maxX, maxZ, TERTIARY_WIDTH, RoadType.TERTIARY));
                    registerRoad(minX, minZ, maxX, maxZ);
                }
            }
        }
    }

    private void registerRoad(int minX, int minZ, int maxX, int maxZ) {
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) roadPositions.add(encode(x, z));
    }

    private long encode(int x, int z) { return ((long) x << 32) | (z & 0xFFFFFFFFL); }
}
