package de.citybuild.core.util;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.List;

public class GeneratorUtil {

    public static int getSurfaceY(World world, int x, int z) {
        for (int y = world.getMaxHeight() - 1; y >= world.getMinHeight(); y--) {
            Block b = world.getBlockAt(x, y, z);
            if (!b.getType().isAir() && b.getType() != Material.WATER) {
                return y;
            }
        }
        return 64;
    }

    public static void loadChunkSafe(World world, int chunkX, int chunkZ, Runnable callback) {
        world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
            if (callback != null) {
                callback.run();
            }
        });
    }

    public static int getDistrictId(int x, int z, List<de.citybuild.core.generator.District> districts) {
        if (districts == null || districts.isEmpty()) return 0;
        
        int bestId = 0;
        double minDistance = Double.MAX_VALUE;
        
        for (de.citybuild.core.generator.District d : districts) {
            double dist = Math.sqrt(Math.pow(d.getX() - x, 2) + Math.pow(d.getZ() - z, 2));
            if (dist < minDistance) {
                minDistance = dist;
                bestId = d.getId();
            }
        }
        return bestId;
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        x = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return x * x * (3 - 2 * x);
    }

    public static long encodeXZ(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static int decodeX(long encoded) {
        return (int) (encoded >> 32);
    }

    public static int decodeZ(long encoded) {
        return (int) encoded;
    }
}
