package de.citybuild.core.manager;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.config.CoreConfig;
import de.citybuild.core.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handles all spawn and teleport logic for players.
 *
 * <p>The server spawn coordinates are read from {@code config.yml} and
 * written back via {@link #setServerSpawn(Player)}.</p>
 */
public class SpawnManager {

    private final CityBuildCore plugin;
    private final CoreConfig    config;

    /**
     * Creates a new SpawnManager.
     *
     * @param plugin the owning plugin instance
     * @param config the typed config wrapper
     */
    public SpawnManager(CityBuildCore plugin, CoreConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Teleports the player to the configured server spawn in the city world.
     *
     * @param player the player to teleport
     * @return {@code true} if the teleport succeeded, {@code false} if the city world is not loaded
     */
    public boolean teleportToSpawn(Player player) {
        World world = Bukkit.getWorld(config.getWorldName());
        if (world == null) return false;
        player.teleport(new Location(world,
            config.getSpawnX(), config.getSpawnY(), config.getSpawnZ(),
            config.getSpawnYaw(), config.getSpawnPitch()));
        return true;
    }

    /**
     * Teleports the player to the custom spawn of their first owned plot.
     * Falls back to the server spawn if the player owns no plots or the world
     * is not loaded.
     *
     * @param player the player to teleport
     */
    public void teleportToPlotSpawn(Player player) {
        List<Plot> owned = plugin.getPlotManager().getPlotsByOwner(player.getUniqueId());
        if (owned.isEmpty()) {
            teleportToSpawn(player);
            return;
        }

        Plot  plot  = owned.get(0);
        World world = Bukkit.getWorld(plot.getWorldName());
        if (world == null) {
            teleportToSpawn(player);
            return;
        }

        if (plot.isHasCustomSpawn()) {
            player.teleport(new Location(world,
                plot.getSpawnX(), plot.getSpawnY(), plot.getSpawnZ(),
                plot.getSpawnYaw(), plot.getSpawnPitch()));
        } else {
            double x = plot.getCenterX() + 0.5;
            double z = plot.getCenterZ() + 0.5;
            int    y = world.getHighestBlockYAt((int) x, (int) z) + 1;
            player.teleport(new Location(world, x, y, z));
        }
    }

    /**
     * Saves the player's current location as the new server spawn in config.
     *
     * @param player the player whose position becomes the new spawn
     */
    public void setServerSpawn(Player player) {
        Location loc = player.getLocation();
        plugin.getConfig().set("spawn.x",     loc.getX());
        plugin.getConfig().set("spawn.y",     loc.getY());
        plugin.getConfig().set("spawn.z",     loc.getZ());
        plugin.getConfig().set("spawn.yaw",   (double) loc.getYaw());
        plugin.getConfig().set("spawn.pitch", (double) loc.getPitch());
        plugin.saveConfig();
    }
}
