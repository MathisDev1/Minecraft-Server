package de.citybuild.core.generator;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.logging.Logger;

/**
 * Generates organic terrain with hills, flat city zones and smooth transitions.
 *
 * <p>Uses layered 2D Simplex Noise to produce height values, then places blocks
 * in the correct layer order (grass, dirt, stone, bedrock).
 * The city centre (radius ≤ 400) is flattened to Y 63; a linear blend is applied
 * in the 400–600 block annulus.</p>
 */
public class TerrainGenerator {

    private static final int BASE_Y      = 63;
    private static final int MAX_Y       = 82;
    private static final int MIN_Y       = 60;
    private static final int FLAT_RADIUS = 400;
    private static final int BLEND_OUTER = 600;

    private final SimplexNoise noiseA; // large hills   (0.008 scale)
    private final SimplexNoise noiseB; // medium detail (0.03  scale)
    private final SimplexNoise noiseC; // fine texture  (0.1   scale)

    /**
     * Cached height map.  Populated during {@link #generateTerrain(World, int)}.
     * Key encoding: see {@link #key(int, int)}.
     */
    private int[] heightMap;
    private int cacheOffsetX;
    private int cacheOffsetZ;
    private int cacheWidth;

    private int mapRadius;

    /**
     * Creates a new TerrainGenerator with the given seed.
     *
     * @param seed random seed for all noise layers
     */
    public TerrainGenerator(long seed) {
        noiseA = new SimplexNoise(seed);
        noiseB = new SimplexNoise(seed ^ 0xDEADBEEF12345678L);
        noiseC = new SimplexNoise(seed ^ 0xCAFEBABE87654321L);
    }

    /**
     * Generates and places terrain blocks in the given world for a square region
     * centred on (0, 0) with the specified radius.
     *
     * <p>This method performs the actual {@code world.getBlockAt().setType()} calls
     * and <strong>must</strong> be called on the main server thread.</p>
     *
     * @param world  target world
     * @param radius half-side length of the generated region in blocks
     */
    public void generateTerrain(World world, int radius) {
        this.mapRadius = radius;
        Logger log = Bukkit.getServer().getLogger();

        // Build the height cache first (pure computation — no world access)
        int side = radius * 2 + 1;
        this.cacheOffsetX = -radius;
        this.cacheOffsetZ = -radius;
        this.cacheWidth   = side;
        this.heightMap    = new int[side * side];

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                heightMap[cacheIndex(x, z)] = computeHeight(x, z);
            }
        }

        // Place blocks chunk by chunk
        int chunkMin = -radius >> 4;
        int chunkMax =  radius >> 4;

        for (int cx = chunkMin; cx <= chunkMax; cx++) {
            for (int cz = chunkMin; cz <= chunkMax; cz++) {
                Chunk chunk = world.getChunkAt(cx, cz);
                chunk.load(true);

                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        int worldX = cx * 16 + bx;
                        int worldZ = cz * 16 + bz;

                        if (Math.abs(worldX) > radius || Math.abs(worldZ) > radius) continue;

                        int surface = heightMap[cacheIndex(worldX, worldZ)];
                        placeColumn(world, worldX, worldZ, surface);
                    }
                }
            }
        }
        log.info("[TerrainGenerator] Terrain generation complete for radius " + radius + ".");
    }

    /**
     * Returns the cached surface Y coordinate at the given world position.
     * Call only after {@link #generateTerrain(World, int)} has completed.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return surface Y level
     */
    public int getHeightAt(int x, int z) {
        if (heightMap == null) return BASE_Y;
        int idx = cacheIndex(x, z);
        if (idx < 0 || idx >= heightMap.length) return BASE_Y;
        return heightMap[idx];
    }

    /**
     * Returns {@code true} when the given position lies within the flat city zone
     * (radius ≤ 400 from origin).
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return whether the position is inside the flat city zone
     */
    public boolean isFlat(int x, int z) {
        return Math.sqrt((double) x * x + (double) z * z) <= FLAT_RADIUS;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Computes the blended, clamped surface Y for (x, z). */
    private int computeHeight(int x, int z) {
        double raw = noiseA.noise(x * 0.008, z * 0.008) * 12.0
                   + noiseB.noise(x * 0.03,  z * 0.03)  *  4.0
                   + noiseC.noise(x * 0.1,   z * 0.1)   *  1.5;

        // raw is roughly in [-17.5, +17.5]; map to absolute Y
        double fullNoise = BASE_Y + raw;
        fullNoise = Math.max(MIN_Y, Math.min(MAX_Y, fullNoise));

        double dist = Math.sqrt((double) x * x + (double) z * z);
        double blend;
        if (dist <= FLAT_RADIUS) {
            blend = 0.0;                                                        // fully flat
        } else if (dist >= BLEND_OUTER) {
            blend = 1.0;                                                        // fully natural
        } else {
            blend = (dist - FLAT_RADIUS) / (BLEND_OUTER - FLAT_RADIUS);        // linear
        }

        return (int) Math.round(BASE_Y * (1.0 - blend) + fullNoise * blend);
    }

    /** Places the full column of blocks from Y=0 to the surface. */
    private void placeColumn(World world, int x, int z, int surface) {
        try {
            // Bedrock
            world.getBlockAt(x, world.getMinHeight(), z).setType(Material.BEDROCK, false);

            // Stone from min+1 to surface-4
            for (int y = world.getMinHeight() + 1; y <= surface - 4; y++) {
                world.getBlockAt(x, y, z).setType(Material.STONE, false);
            }

            // Dirt from surface-3 to surface-1
            for (int y = Math.max(world.getMinHeight() + 1, surface - 3); y <= surface - 1; y++) {
                world.getBlockAt(x, y, z).setType(Material.DIRT, false);
            }

            // Grass surface
            world.getBlockAt(x, surface, z).setType(Material.GRASS_BLOCK, false);

            // Clear everything above the surface up to BUILD_HEIGHT
            for (int y = surface + 1; y < world.getMaxHeight(); y++) {
                Material m = world.getBlockAt(x, y, z).getType();
                if (m != Material.AIR) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        } catch (Exception e) {
            Bukkit.getServer().getLogger().warning(
                "[TerrainGenerator] Error placing column at " + x + "," + z + ": " + e.getMessage());
        }
    }

    /** Maps world coordinates to a flat array index. */
    private int cacheIndex(int x, int z) {
        int lx = x - cacheOffsetX;
        int lz = z - cacheOffsetZ;
        return lx * cacheWidth + lz;
    }

    /** Encodes a coordinate pair into a long key. */
    @SuppressWarnings("unused")
    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
