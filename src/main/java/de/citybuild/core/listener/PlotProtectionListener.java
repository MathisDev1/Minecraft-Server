package de.citybuild.core.listener;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.model.Plot;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * Protects plots from block modification, explosions, fire spread, PvP and
 * bucket operations in the city world.
 */
public class PlotProtectionListener implements Listener {

    private final CityBuildCore plugin;

    /**
     * Creates a new PlotProtectionListener.
     *
     * @param plugin the owning plugin instance
     */
    public PlotProtectionListener(CityBuildCore plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Block events
    // =========================================================================

    /** Prevents breaking blocks on protected plots. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!inCityWorld(event.getBlock())) return;
        if (!canBuild(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getCoreConfig().getPrefix()
                + plugin.getCoreConfig().msg("no-permission"));
        }
    }

    /** Prevents placing blocks on protected plots. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!inCityWorld(event.getBlock())) return;
        if (!canBuild(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.getCoreConfig().getPrefix()
                + plugin.getCoreConfig().msg("no-permission"));
        }
    }

    /** Prevents interacting with interactable blocks (doors, levers, etc.) on protected plots. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || !inCityWorld(block)) return;
        if (!block.getType().isInteractable()) return;
        if (!canBuild(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    /** Strips any blocks belonging to a plot from an explosion's block list. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!event.getEntity().getWorld().getName()
                .equals(plugin.getCoreConfig().getWorldName())) return;
        event.blockList().removeIf(block -> {
            Plot plot = plugin.getPlotManager().getPlotAt(
                block.getWorld().getName(), block.getX(), block.getZ());
            return plot != null || plugin.getCoreConfig().isProtectUnclaimed();
        });
    }

    /** Prevents fire from spreading when configured. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (!inCityWorld(event.getBlock())) return;
        if (plugin.getCoreConfig().isPreventFireSpread()
                && event.getNewState().getType() == org.bukkit.Material.FIRE) {
            event.setCancelled(true);
        }
    }

    /** Prevents mobs from griefing (e.g. Endermen picking up blocks). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!inCityWorld(event.getBlock())) return;
        if (plugin.getCoreConfig().isPreventMobGrief()) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    // Entity events
    // =========================================================================

    /** Prevents PvP in the city world when configured. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getCoreConfig().isPreventPvP()) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (!event.getEntity().getWorld().getName()
                .equals(plugin.getCoreConfig().getWorldName())) return;
        event.setCancelled(true);
    }

    // =========================================================================
    // Bucket events
    // =========================================================================

    /** Prevents placing liquid with a bucket on protected plots. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        if (!inCityWorld(block)) return;
        if (!canBuild(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    /** Prevents filling a bucket from protected plots. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Block block = event.getBlock();
        if (!inCityWorld(block)) return;
        if (!canBuild(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(org.bukkit.event.block.BlockFromToEvent event) {
        Block from = event.getBlock();
        Block to = event.getToBlock();
        
        Plot fromPlot = plugin.getPlotManager().getPlotAt(from.getLocation());
        Plot toPlot = plugin.getPlotManager().getPlotAt(to.getLocation());
        
        // Prevent flow if it enters a different plot or enters a plot from outside
        if (fromPlot != toPlot) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Returns {@code true} if the player is allowed to modify the given block.
     *
     * <ul>
     *   <li>Players with {@code citybuild.bypass} always can build.</li>
     *   <li>On unclaimed land: allowed only if {@code protect-unclaimed} is {@code false}.</li>
     *   <li>On a claimed plot: allowed if the player is the owner or trusted.</li>
     * </ul>
     */
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

    private boolean inCityWorld(Block block) {
        return block.getWorld().getName().equals(plugin.getCoreConfig().getWorldName());
    }
}
