package de.citybuild.core.listener;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.manager.PlotManager;
import de.citybuild.core.model.Plot;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Handles interactions with plot sales signs.
 */
public class PlotSignListener implements Listener {

    private final CityBuildCore plugin;

    public PlotSignListener(CityBuildCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null) return;
        
        if (block.getType() != Material.OAK_SIGN && block.getType() != Material.OAK_WALL_SIGN) return;
        
        if (!(block.getState() instanceof Sign sign)) return;
        
        // Check if it's a CityBuild Plot Sign
        if (!sign.getLine(0).contains("[GRUNDSTÜCK]")) return;
        
        Player player = event.getPlayer();
        Plot plot = plugin.getPlotManager().getPlotAt(block.getLocation());
        
        if (plot == null) return;
        
        if (plot.getOwnerUUID() != null) {
            player.sendMessage(plugin.getCoreConfig().getPrefix() + "§cDieses Grundstück ist bereits verkauft!");
            return;
        }

        // Attempt to buy
        PlotManager.ClaimResult result = plugin.getPlotManager().claimPlot(player, plot);
        
        switch (result) {
            case SUCCESS -> {
                player.sendMessage(plugin.getCoreConfig().getPrefix() + "§aDu hast das Grundstück #" + plot.getId() + " erfolgreich gekauft!");
                player.sendMessage(plugin.getCoreConfig().getPrefix() + "§7Der Zaun wurde entfernt. Viel Spaß beim Bauen!");
            }
            case NOT_ENOUGH_MONEY -> player.sendMessage(plugin.getCoreConfig().getPrefix() + "§cDu hast nicht genug Geld! Preis: " + plot.getPrice() + "$");
            case MAX_PLOTS_REACHED -> player.sendMessage(plugin.getCoreConfig().getPrefix() + "§cDu hast das Limit an Grundstücken erreicht!");
            case ALREADY_OWNED -> player.sendMessage(plugin.getCoreConfig().getPrefix() + "§cDieses Grundstück gehört bereits jemandem.");
            default -> player.sendMessage(plugin.getCoreConfig().getPrefix() + "§cKauf fehlgeschlagen: " + result.name());
        }
        
        event.setCancelled(true);
    }
}
