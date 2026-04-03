package de.citybuild.core;

import de.citybuild.core.api.CityBuildAPI;
import de.citybuild.core.api.CityBuildAPIImpl;
import de.citybuild.core.commands.AdminPlotCommand;
import de.citybuild.core.commands.PlotCommand;
import de.citybuild.core.commands.SetupCommand;
import de.citybuild.core.commands.SpawnCommand;
import de.citybuild.core.config.CoreConfig;
import de.citybuild.core.database.DatabaseManager;
import de.citybuild.core.database.PlotRepository;
import de.citybuild.core.gui.PlotInfoGUI;
import de.citybuild.core.listener.BlockInteractListener;
import de.citybuild.core.listener.PlayerJoinListener;
import de.citybuild.core.listener.PlayerMoveListener;
import de.citybuild.core.listener.PlotProtectionListener;
import de.citybuild.core.listener.PlotSignListener;
import de.citybuild.core.manager.PlotManager;
import de.citybuild.core.manager.SpawnManager;
import de.citybuild.core.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Main plugin class for CityBuild Core.
 *
 * <p>Bootstraps all managers, repositories, commands and listeners in the correct order.</p>
 */
public class CityBuildCore extends JavaPlugin {

    private static CityBuildCore instance;

    private CoreConfig      coreConfig;
    private DatabaseManager databaseManager;
    private PlotRepository  plotRepository;
    private PlotManager     plotManager;
    private SpawnManager    spawnManager;
    private CityBuildAPI    api;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Config
        saveDefaultConfig();
        coreConfig = new CoreConfig(this);

        // 2. Database – disable plugin on failure
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            getLogger().severe("Database initialization failed — disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        plotRepository = new PlotRepository(databaseManager);

        // 3. Managers
        plotManager  = new PlotManager(this, plotRepository, coreConfig);
        spawnManager = new SpawnManager(this, coreConfig);

        // 4. API
        api = new CityBuildAPIImpl(this);
        getServer().getServicesManager().register(
            CityBuildAPI.class, api, this, ServicePriority.Normal);

        // 5. Commands
        getCommand("cbsetup").setExecutor(new SetupCommand(this));
        PlotCommand plotCmd = new PlotCommand(this);
        getCommand("plot").setExecutor(plotCmd);
        getCommand("plot").setTabCompleter(plotCmd);
        getCommand("spawn").setExecutor(new SpawnCommand(this));
        AdminPlotCommand adminCmd = new AdminPlotCommand(this);
        getCommand("adminplot").setExecutor(adminCmd);
        getCommand("adminplot").setTabCompleter(adminCmd);

        // 6. Listeners
        PlayerMoveListener moveListener = new PlayerMoveListener(this);
        getServer().getPluginManager().registerEvents(new PlotProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(moveListener, this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, moveListener), this);
        getServer().getPluginManager().registerEvents(new BlockInteractListener(this), this);
        getServer().getPluginManager().registerEvents(new PlotInfoGUI.GuiListener(), this);
        getServer().getPluginManager().registerEvents(new PlotSignListener(this), this);

        // 7. Load existing world, or wait for /cbsetup
        World cityWorld = Bukkit.getWorld(coreConfig.getWorldName());
        if (cityWorld != null) {
            List<Plot> plots = plotRepository.loadAll();
            plotManager.bulkRegisterPlots(plots);
        } else {
            getLogger().info("City world '" + coreConfig.getWorldName()
                + "' not found. Use /cbsetup generate to create it.");
        }

        getLogger().info("CityBuildCore enabled. " + plotManager.getPlotCount() + " plots loaded.");
    }

    @Override
    public void onDisable() {
        if (plotManager != null) {
            plotManager.saveAll();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
    }

    /** @return the singleton plugin instance */
    public static CityBuildCore getInstance() { return instance; }

    /** @return the typed config wrapper */
    public CoreConfig getCoreConfig() { return coreConfig; }

    /** @return the database manager */
    public DatabaseManager getDatabaseManager() { return databaseManager; }

    /** @return the plot repository */
    public PlotRepository getPlotRepository() { return plotRepository; }

    /** @return the in-memory plot manager */
    public PlotManager getPlotManager() { return plotManager; }

    /** @return the spawn manager */
    public SpawnManager getSpawnManager() { return spawnManager; }

    /** @return the CityBuild API instance */
    public CityBuildAPI getAPI() { return api; }
}
