package de.citybuild.core.api;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.api.events.PlotClaimEvent;
import de.citybuild.core.model.Plot;
import de.citybuild.core.model.Plot.PlotType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CityBuildAPIImpl implements CityBuildAPI {

    private final CityBuildCore plugin;
    private EconomyProvider economyProvider;

    public CityBuildAPIImpl(CityBuildCore plugin) {
        this.plugin = plugin;
    }

    // ── Plot-Zugriff ─────────────────────────────────────────────

    @Override
    public Optional<Plot> getPlotAt(Location loc) {
        return Optional.ofNullable(plugin.getPlotManager().getPlotAt(loc));
    }

    @Override
    public Optional<Plot> getPlotById(int id) {
        return plugin.getPlotManager().getById(id);
    }

    @Override
    public List<Plot> getPlotsByOwner(UUID uuid) {
        return plugin.getPlotManager().getPlotsByOwner(uuid);
    }

    @Override
    public List<Plot> getAllPlots() {
        return new ArrayList<>(plugin.getPlotManager().getAllPlots());
    }

    @Override
    public List<Plot> getForSalePlots() {
        return plugin.getPlotManager().getForSalePlots();
    }

    @Override
    public List<Plot> getUnclaimedPlots() {
        return plugin.getPlotManager().getAllPlots().stream()
                .filter(p -> p.getOwnerUUID() == null)
                .collect(Collectors.toList());
    }

    // ── Plot-Modifikation (für Addon-Plugins) ────────────────────

    @Override
    public boolean claimPlotForPlayer(Player player, int plotId) {
        return getPlotById(plotId).map(plot -> {
            return plugin.getPlotManager().claimPlot(player, plot) == de.citybuild.core.manager.PlotManager.ClaimResult.SUCCESS;
        }).orElse(false);
    }

    @Override
    public boolean unclaimPlot(int plotId) {
        return getPlotById(plotId).map(plot -> {
            if (plot.getOwnerUUID() == null) return false;
            // Admin-unclaim style: bypass owner check
            return plugin.getPlotManager().adminResetPlot(plotId);
        }).orElse(false);
    }

    @Override
    public boolean setPlotOwner(int plotId, UUID newOwner, String name) {
        return getPlotById(plotId).map(plot -> {
            plot.setOwnerUUID(newOwner);
            plot.setOwnerName(name);
            plugin.getPlotRepository().savePlot(plot);
            plugin.getPlotManager().updatePlotBorder(plot);
            return true;
        }).orElse(false);
    }

    @Override
    public boolean setPlotType(int plotId, PlotType type) {
        return getPlotById(plotId).map(plot -> {
            plot.setType(type);
            plugin.getPlotRepository().savePlot(plot);
            // Updating border can be useful if type changes terrain/fences
            return true;
        }).orElse(false);
    }

    @Override
    public boolean setPlotForSale(int plotId, boolean forSale, double price) {
        return getPlotById(plotId).map(plot -> {
            plot.setForSale(forSale);
            plot.setPrice(price);
            plugin.getPlotRepository().savePlot(plot);
            return true;
        }).orElse(false);
    }

    // ── Economy-Hook ─────────────────────────────────────────────

    @Override
    public void registerEconomyProvider(EconomyProvider provider) {
        this.economyProvider = provider;
        plugin.getLogger().info("Registered Economy provider: " + provider.getProviderName());
    }

    @Override
    public boolean hasEconomy() {
        return economyProvider != null;
    }

    @Override
    public double getBalance(UUID uuid) {
        return hasEconomy() ? economyProvider.getBalance(uuid) : 0.0;
    }

    @Override
    public boolean hasBalance(UUID uuid, double amount) {
        return hasEconomy() && economyProvider.hasBalance(uuid, amount);
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        return hasEconomy() && economyProvider.withdraw(uuid, amount);
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        return hasEconomy() && economyProvider.deposit(uuid, amount);
    }

    // ── Events abonnieren ────────────────────────────────────────

    @Override
    public void onPlotClaim(Consumer<PlotClaimEvent> handler) {
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onClaim(PlotClaimEvent event) {
                handler.accept(event);
            }
        }, plugin);
    }

    // ── Welt-Info ────────────────────────────────────────────────

    @Override
    public String getCityWorldName() {
        return plugin.getCoreConfig().getWorldName();
    }

    @Override
    public World getCityWorld() {
        return Bukkit.getWorld(getCityWorldName());
    }

    @Override
    public boolean isCityWorld(World world) {
        return world != null && world.getName().equals(getCityWorldName());
    }

    @Override
    public boolean isCityWorld(String worldName) {
        return worldName != null && worldName.equals(getCityWorldName());
    }

    // ── Generator-Info ───────────────────────────────────────────

    @Override
    public boolean isGenerationComplete() {
        // Simple heuristic: if city world exists and has lots of plots
        World w = getCityWorld();
        return w != null && plugin.getPlotManager().getPlotCount() > 0;
    }

    @Override
    public long getGenerationSeed() {
        return plugin.getConfig().getLong("generator.seed");
    }

    @Override
    public int getTotalPlotCount() {
        return plugin.getPlotManager().getPlotCount();
    }

    @Override
    public int getClaimedPlotCount() {
        return (int) plugin.getPlotManager().getAllPlots().stream()
                .filter(p -> p.getOwnerUUID() != null)
                .count();
    }

    @Override
    public int getUnclaimedPlotCount() {
        return (int) plugin.getPlotManager().getAllPlots().stream()
                .filter(p -> p.getOwnerUUID() == null)
                .count();
    }

    // ── Version ──────────────────────────────────────────────────

    @Override
    public String getCoreVersion() {
        return plugin.getDescription().getVersion();
    }
}
