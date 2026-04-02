package de.citybuild.core.commands;

import de.citybuild.core.CityBuildCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles the {@code /spawn [plot]} command.
 *
 * <ul>
 *   <li>{@code /spawn} — Teleports to the server spawn in the city world.</li>
 *   <li>{@code /spawn plot} — Teleports to the custom spawn of the player's first owned plot.</li>
 * </ul>
 */
public class SpawnCommand implements CommandExecutor {

    private final CityBuildCore plugin;

    /**
     * Creates a new SpawnCommand.
     *
     * @param plugin the owning plugin instance
     */
    public SpawnCommand(CityBuildCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl verwenden.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("plot")) {
            plugin.getSpawnManager().teleportToPlotSpawn(player);
        } else {
            boolean ok = plugin.getSpawnManager().teleportToSpawn(player);
            if (!ok) {
                player.sendMessage(plugin.getCoreConfig().getPrefix()
                    + "§cDie City-Welt ist noch nicht geladen.");
            }
        }
        return true;
    }
}
