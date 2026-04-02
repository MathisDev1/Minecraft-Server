package de.citybuild.core.api;

import de.citybuild.core.api.events.PlotClaimEvent;
import de.citybuild.core.model.Plot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public interface CityBuildAPI {

    // ── Plot-Zugriff ─────────────────────────────────────────────
    Optional<Plot> getPlotAt(Location loc);
    Optional<Plot> getPlotById(int id);
    List<Plot> getPlotsByOwner(UUID uuid);
    List<Plot> getAllPlots();
    List<Plot> getForSalePlots();
    List<Plot> getUnclaimedPlots();

    // ── Plot-Modifikation (für Addon-Plugins) ────────────────────
    boolean claimPlotForPlayer(Player player, int plotId);
    boolean unclaimPlot(int plotId);
    boolean setPlotOwner(int plotId, UUID newOwner, String name);
    boolean setPlotType(int plotId, de.citybuild.core.model.Plot.PlotType type);
    boolean setPlotForSale(int plotId, boolean forSale, double price);

    // ── Economy-Hook ─────────────────────────────────────────────
    void registerEconomyProvider(EconomyProvider provider);
    boolean hasEconomy();
    double getBalance(UUID uuid);
    boolean hasBalance(UUID uuid, double amount);
    boolean withdraw(UUID uuid, double amount);
    boolean deposit(UUID uuid, double amount);

    // ── Events abonnieren ────────────────────────────────────────
    void onPlotClaim(Consumer<PlotClaimEvent> handler);

    // ── Welt-Info ────────────────────────────────────────────────
    String getCityWorldName();
    World getCityWorld();
    boolean isCityWorld(World world);
    boolean isCityWorld(String worldName);

    // ── Generator-Info ───────────────────────────────────────────
    boolean isGenerationComplete();
    long getGenerationSeed();
    int getTotalPlotCount();
    int getClaimedPlotCount();
    int getUnclaimedPlotCount();

    // ── Version ──────────────────────────────────────────────────
    String getCoreVersion();

    interface EconomyProvider {
        String getProviderName();
        boolean hasBalance(UUID uuid, double amount);
        boolean withdraw(UUID uuid, double amount);
        boolean deposit(UUID uuid, double amount);
        double getBalance(UUID uuid);
        String formatAmount(double amount);
    }
}
