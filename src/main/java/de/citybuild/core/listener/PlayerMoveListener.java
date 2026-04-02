package de.citybuild.core.listener;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.model.Plot;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Detects when a player enters or leaves a plot and displays a title message.
 *
 * <p>Only triggers when the player changes block coordinates (XZ) to avoid
 * excessive processing from look-direction changes.</p>
 */
public class PlayerMoveListener implements Listener {

    /** Sentinel value stored when a player is standing on a road (no plot). */
    private static final int NO_PLOT = -1;

    private final CityBuildCore       plugin;
    /** Maps player UUID to the ID of the last known plot they stood on. */
    private final Map<UUID, Integer>  lastPlotId = new ConcurrentHashMap<>();

    /**
     * Creates a new PlayerMoveListener.
     *
     * @param plugin the owning plugin instance
     */
    public PlayerMoveListener(CityBuildCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Removes tracking data for a player (called on quit).
     *
     * @param uuid the player's UUID
     */
    public void removePlayer(UUID uuid) {
        lastPlotId.remove(uuid);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only process when the player's block position changes
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        if (!event.getTo().getWorld().getName()
                .equals(plugin.getCoreConfig().getWorldName())) {
            return;
        }

        Player player   = event.getPlayer();
        UUID   uuid     = player.getUniqueId();
        Plot   nowPlot  = plugin.getPlotManager().getPlotAt(event.getTo());
        int    nowId    = nowPlot != null ? nowPlot.getId() : NO_PLOT;
        int    prevId   = lastPlotId.getOrDefault(uuid, Integer.MIN_VALUE);

        if (nowId == prevId) return; // still in same plot / still on road

        lastPlotId.put(uuid, nowId);

        if (nowPlot != null) {
            showPlotTitle(player, nowPlot);
        } else {
            showRoadTitle(player);
        }
    }

    // =========================================================================
    // Titles
    // =========================================================================

    private void showPlotTitle(Player player, Plot plot) {
        String titleText;
        if (plot.getPlotName() != null && !plot.getPlotName().isEmpty()) {
            titleText = "§6" + plot.getPlotName();
        } else {
            titleText = "§7Grundstück §e#" + plot.getId();
        }

        String subtitleText;
        if (plot.getOwnerName() != null) {
            subtitleText = "§7Eigentümer: §a" + plot.getOwnerName();
        } else {
            subtitleText = "§7§oUnbebaut — §e/plot buy";
        }

        player.showTitle(Title.title(
            LegacyComponentSerializer.legacySection().deserialize(titleText),
            LegacyComponentSerializer.legacySection().deserialize(subtitleText),
            Title.Times.times(
                Duration.ofMillis(10 * 50L),
                Duration.ofMillis(40 * 50L),
                Duration.ofMillis(10 * 50L)
            )
        ));
    }

    private void showRoadTitle(Player player) {
        player.showTitle(Title.title(
            LegacyComponentSerializer.legacySection().deserialize("§7Straße"),
            net.kyori.adventure.text.Component.empty(),
            Title.Times.times(
                Duration.ofMillis(5 * 50L),
                Duration.ofMillis(20 * 50L),
                Duration.ofMillis(5 * 50L)
            )
        ));
    }
}
