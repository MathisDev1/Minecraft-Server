package de.citybuild.addon.economy;

import de.citybuild.core.api.CityBuildAPI;
import de.citybuild.core.api.events.PlotClaimEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class CityBuildEconomy extends JavaPlugin implements Listener {

    private CityBuildAPI coreAPI;
    private SimpleEconomyProvider ecoProvider;

    @Override
    public void onEnable() {
        // 1. API laden
        coreAPI = Bukkit.getServicesManager().load(CityBuildAPI.class);
        if (coreAPI == null) {
            getLogger().severe("CityBuildCore API not found! Disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 2. Provider registrieren
        ecoProvider = new SimpleEconomyProvider(this);
        coreAPI.registerEconomyProvider(ecoProvider);

        // 3. Listener für PlotClaimEvent registrieren
        getServer().getPluginManager().registerEvents(this, this);

        // 4. Commands registrieren
        BalanceCommand cmd = new BalanceCommand(ecoProvider);
        getCommand("balance").setExecutor(cmd);
        getCommand("pay").setExecutor(cmd);
        getCommand("eco").setExecutor(cmd);

        getLogger().info("CityBuildEconomy enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("CityBuildEconomy disabled.");
    }

    @EventHandler
    public void onPlotClaim(PlotClaimEvent event) {
        getLogger().info("Player " + event.getPlayer().getName() + " claimed plot for " + event.getPrice());
    }
}
