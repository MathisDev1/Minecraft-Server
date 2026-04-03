package de.citybuild.core.generator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

/**
 * Generates a clean, flat terrain for CityBuild.
 * Hills, rivers, and lakes have been removed to ensure a structured building area.
 */
public class TerrainGenerator {

    private static final int SURFACE_Y = 64;

    public TerrainGenerator(long seed) {
        // Seed is kept for potential future biome-specific logic
    }

    /**
     * Returns a list of tasks for placing the terrain blocks.
     *
     * @param world     target world
     * @param mapRadius radius to generate
     * @return list of terrain placement tasks
     */
    public java.util.List<Runnable> getChunkTasks(World world, int mapRadius) {
        java.util.List<Runnable> tasks = new java.util.ArrayList<>();
        int minC = (-mapRadius >> 4);
        int maxC = (mapRadius >> 4);

        for (int cx = minC; cx <= maxC; cx++) {
            for (int cz = minC; cz <= maxC; cz++) {
                final int finalCx = cx;
                final int finalCz = cz;
                tasks.add(() -> {
                    if (!world.isChunkLoaded(finalCx, finalCz)) {
                        world.getChunkAt(finalCx, finalCz).load(true);
                    }
                    generateChunk(world, finalCx, finalCz);
                });
            }
        }
        return tasks;
    }

    private void generateChunk(World world, int cx, int cz) {
        int startX = cx << 4;
        int startZ = cz << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                placeColumn(world, startX + x, startZ + z);
            }
        }
    }

    private void placeColumn(World world, int x, int z) {
        try {
            // Bedrock at very bottom
            world.getBlockAt(x, world.getMinHeight(), z).setType(Material.BEDROCK, false);

            // Dirt layer
            for (int y = world.getMinHeight() + 1; y < SURFACE_Y; y++) {
                world.getBlockAt(x, y, z).setType(Material.DIRT, false);
            }

            // Grass surface
            world.getBlockAt(x, SURFACE_Y, z).setType(Material.GRASS_BLOCK, false);

            // Clear air above
            int airEnd = Math.min(world.getMaxHeight(), SURFACE_Y + 5);
            for (int y = SURFACE_Y + 1; y < airEnd; y++) {
                if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        } catch (Exception e) {
            Bukkit.getServer().getLogger().warning(
                "[TerrainGenerator] Error placing column at " + x + "," + z + ": " + e.getMessage());
        }
    }

    /** Helper for other generators to find the current height. */
    public int getHeightAt(int x, int z) {
        return SURFACE_Y;
    }
}
