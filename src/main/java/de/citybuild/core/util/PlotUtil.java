package de.citybuild.core.util;

import de.citybuild.core.manager.PlotManager;
import de.citybuild.core.model.Plot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PlotUtil {

    public static double distanceFromCenter(Plot plot) {
        int x = plot.getCenterX();
        int z = plot.getCenterZ();
        return Math.sqrt(x * x + z * z);
    }

    public static Optional<Plot> findNearestUnclaimed(Location from, PlotManager manager) {
        return manager.getAllPlots().stream()
                .filter(p -> p.getOwnerUUID() == null && (!p.isForSale() || p.getPrice() == 0))
                .min(Comparator.comparingDouble(p -> {
                    int dx = p.getCenterX() - from.getBlockX();
                    int dz = p.getCenterZ() - from.getBlockZ();
                    return Math.sqrt(dx * dx + dz * dz);
                }));
    }

    public static boolean areAdjacent(Plot a, Plot b) {
        if (!a.getWorldName().equals(b.getWorldName())) return false;
        
        boolean xOverlap = a.getMaxX() >= b.getMinX() - 1 && a.getMinX() <= b.getMaxX() + 1;
        boolean zOverlap = a.getMaxZ() >= b.getMinZ() - 1 && a.getMinZ() <= b.getMaxZ() + 1;
        
        return xOverlap && zOverlap && !(a.getMaxX() >= b.getMinX() && a.getMinX() <= b.getMaxX() && a.getMaxZ() >= b.getMinZ() && a.getMinZ() <= b.getMaxZ());
    }

    public static String boundsString(Plot plot) {
        return "[X: " + plot.getMinX() + "→" + plot.getMaxX() + ", Z: " + plot.getMinZ() + "→" + plot.getMaxZ() + "]";
    }

    public static void ejectPlayer(Player player, Plot plot, World world) {
        // Find nearest block outside plot
        int cx = plot.getCenterX();
        int cz = plot.getCenterZ();
        
        int r = Math.max(plot.getMaxX() - plot.getMinX(), plot.getMaxZ() - plot.getMinZ()) / 2 + 3;
        
        for (int i = 0; i <= r; i++) {
            for (int dx = -i; dx <= i; dx++) {
                for (int dz = -i; dz <= i; dz++) {
                    if (Math.abs(dx) == i || Math.abs(dz) == i) {
                        int nx = cx + dx;
                        int nz = cz + dz;
                        if (!plot.contains(nx, nz)) {
                            int y = GeneratorUtil.getSurfaceY(world, nx, nz);
                            player.teleport(new Location(world, nx + 0.5, y + 1, nz + 0.5));
                            return;
                        }
                    }
                }
            }
        }
    }

    public static List<Player> getPlayersOnPlot(Plot plot) {
        List<Player> players = new ArrayList<>();
        World world = org.bukkit.Bukkit.getWorld(plot.getWorldName());
        if (world == null) return players;
        
        for (Player p : world.getPlayers()) {
            Location loc = p.getLocation();
            if (plot.contains(loc.getBlockX(), loc.getBlockZ())) {
                players.add(p);
            }
        }
        return players;
    }
}
