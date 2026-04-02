package de.citybuild.core.api;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.manager.PlotManager;
import de.citybuild.core.model.Plot;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API for CityBuild Core, registered in the Bukkit {@link org.bukkit.plugin.ServicesManager}
 * so that add-on plugins can obtain it via
 * {@code Bukkit.getServicesManager().load(CityBuildAPI.class)}.
 *
 * <p>Economy integration is opt-in: add-ons register an {@link EconomyProvider}
 * implementation; if none is registered all economy checks are bypassed.</p>
 */
public class CityBuildAPI {

    /**
     * Provider interface for economy integration.
     *
     * <p>Implement this and register it via {@link CityBuildAPI#registerEconomyProvider}
     * to enable purchase/sell money handling.</p>
     */
    public interface EconomyProvider {
        /**
         * Returns whether the player has at least {@code amount} currency.
         *
         * @param uuid   player UUID
         * @param amount required amount
         * @return {@code true} if the player can afford it
         */
        boolean hasBalance(UUID uuid, double amount);

        /**
         * Deducts {@code amount} from the player's account.
         *
         * @param uuid   player UUID
         * @param amount amount to deduct
         * @return {@code true} if the transaction succeeded
         */
        boolean withdraw(UUID uuid, double amount);

        /**
         * Credits {@code amount} to the player's account.
         *
         * @param uuid   player UUID
         * @param amount amount to credit
         * @return {@code true} if the transaction succeeded
         */
        boolean deposit(UUID uuid, double amount);

        /**
         * Returns the player's current balance.
         *
         * @param uuid player UUID
         * @return current balance
         */
        double getBalance(UUID uuid);
    }

    private final CityBuildCore plugin;
    private       EconomyProvider economyProvider;

    /**
     * Creates a new CityBuildAPI instance.
     *
     * @param plugin the owning plugin instance
     */
    public CityBuildAPI(CityBuildCore plugin) {
        this.plugin = plugin;
    }

    /** @return the plot manager */
    public PlotManager getPlotManager() {
        return plugin.getPlotManager();
    }

    /**
     * Returns the plot at the given location.
     *
     * @param loc the location to check
     * @return optional plot
     */
    public Optional<Plot> getPlotAt(Location loc) {
        return Optional.ofNullable(plugin.getPlotManager().getPlotAt(loc));
    }

    /**
     * Registers an economy provider.  Only one provider is active at a time;
     * subsequent calls replace the previous provider.
     *
     * @param provider the economy provider implementation
     */
    public void registerEconomyProvider(EconomyProvider provider) {
        this.economyProvider = provider;
    }

    /** @return {@code true} if an economy provider has been registered */
    public boolean hasEconomy() {
        return economyProvider != null;
    }

    /**
     * Returns whether the player has at least the given amount of currency.
     * Always returns {@code true} if no economy provider is registered.
     *
     * @param player the player
     * @param amount required amount
     * @return {@code true} if the player can afford it
     */
    public boolean hasBalance(Player player, double amount) {
        return economyProvider == null || economyProvider.hasBalance(player.getUniqueId(), amount);
    }

    /**
     * Withdraws the given amount from the player's account.
     * Does nothing if no economy provider is registered.
     *
     * @param player the player
     * @param amount amount to withdraw
     * @return {@code true} on success or if no economy provider is registered
     */
    public boolean withdrawBalance(Player player, double amount) {
        return economyProvider == null || economyProvider.withdraw(player.getUniqueId(), amount);
    }

    /**
     * Deposits the given amount into the player's account.
     * Does nothing if no economy provider is registered.
     *
     * @param player the player
     * @param amount amount to deposit
     * @return {@code true} on success or if no economy provider is registered
     */
    public boolean depositBalance(Player player, double amount) {
        return economyProvider == null || economyProvider.deposit(player.getUniqueId(), amount);
    }
}
