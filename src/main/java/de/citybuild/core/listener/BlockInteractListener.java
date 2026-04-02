package de.citybuild.core.listener;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.model.Plot;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.BlockInventoryHolder;

import java.util.UUID;

/**
 * Protects container and inventory blocks (chests, barrels, hoppers, etc.)
 * from being opened by unauthorised players.
 *
 * <p>This complements {@link PlotProtectionListener} which handles physical
 * block interaction events; this listener specifically prevents inventory
 * opens on protected plots.</p>
 */
public class BlockInteractListener implements Listener {

    private final CityBuildCore plugin;

    /**
     * Creates a new BlockInteractListener.
     *
     * @param plugin the owning plugin instance
     */
    public BlockInteractListener(CityBuildCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Cancels inventory opens for containers on plots the player may not build on.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // Only interested in block-backed inventories (chests, barrels, hoppers…)
        org.bukkit.inventory.InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof BlockInventoryHolder bih)) return;

        Block block = bih.getBlock();
        if (!block.getWorld().getName().equals(plugin.getCoreConfig().getWorldName())) return;

        if (!canBuild(player, block)) {
            event.setCancelled(true);
            player.sendMessage(plugin.getCoreConfig().getPrefix()
                + plugin.getCoreConfig().msg("no-permission"));
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private boolean canBuild(Player player, Block block) {
        if (player.hasPermission("citybuild.bypass")) return true;
        Plot plot = plugin.getPlotManager().getPlotAt(
            block.getWorld().getName(), block.getX(), block.getZ());
        if (plot == null) {
            return !plugin.getCoreConfig().isProtectUnclaimed();
        }
        UUID uuid = player.getUniqueId();
        return uuid.equals(plot.getOwnerUUID())
            || plot.getTrustedPlayers().contains(uuid);
    }
}
