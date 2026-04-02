package de.citybuild.core.listener;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.model.Plot;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

/**
 * Handles player join and quit events.
 *
 * <p>On join: upserts the player record, teleports new players to spawn, and
 * refreshes the owner name in all their plots (handles name changes).</p>
 *
 * <p>On quit: removes the player's last-plot tracking entry from
 * {@link PlayerMoveListener}.</p>
 */
public class PlayerJoinListener implements Listener {

    private final CityBuildCore      plugin;
    private final PlayerMoveListener moveListener;

    /**
     * Creates a new PlayerJoinListener.
     *
     * @param plugin       the owning plugin instance
     * @param moveListener the move listener whose per-player state must be cleaned up on quit
     */
    public PlayerJoinListener(CityBuildCore plugin, PlayerMoveListener moveListener) {
        this.plugin       = plugin;
        this.moveListener = moveListener;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player  player    = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();

        // 1. Upsert player record in DB
        plugin.getPlotRepository().upsertPlayer(player.getUniqueId(), player.getName());

        // 2. Teleport new players to server spawn after a short delay
        if (firstJoin) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.getSpawnManager().teleportToSpawn(player);
                }
            }, 5L);
        }

        // 3. Update owner_name in all plots if the player changed their name
        List<Plot> owned = plugin.getPlotManager().getPlotsByOwner(player.getUniqueId());
        for (Plot plot : owned) {
            if (!player.getName().equals(plot.getOwnerName())) {
                plot.setOwnerName(player.getName());
                plugin.getPlotRepository().savePlot(plot);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        moveListener.removePlayer(event.getPlayer().getUniqueId());
    }
}
