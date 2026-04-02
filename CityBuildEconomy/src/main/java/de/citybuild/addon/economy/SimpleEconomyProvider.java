package de.citybuild.addon.economy;

import de.citybuild.core.api.CityBuildAPI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;

public class SimpleEconomyProvider implements CityBuildAPI.EconomyProvider {

    private final CityBuildEconomy plugin;
    private final File dataFile;
    private final FileConfiguration data;
    private final double startingBalance = 1000.0;
    private final DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));

    public SimpleEconomyProvider(CityBuildEconomy plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "balances.yml");
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getProviderName() {
        return "CityBuildEconomy";
    }

    @Override
    public boolean hasBalance(UUID uuid, double amount) {
        return getBalance(uuid) >= amount;
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current >= amount) {
            data.set(uuid.toString(), current - amount);
            saveData();
            return true;
        }
        return false;
    }

    @Override
    public boolean deposit(UUID uuid, double amount) {
        double current = getBalance(uuid);
        data.set(uuid.toString(), current + amount);
        saveData();
        return true;
    }

    @Override
    public double getBalance(UUID uuid) {
        if (!data.contains(uuid.toString())) {
            data.set(uuid.toString(), startingBalance);
            saveData();
        }
        return data.getDouble(uuid.toString());
    }

    @Override
    public String formatAmount(double amount) {
        return format.format(amount) + " $";
    }
}
