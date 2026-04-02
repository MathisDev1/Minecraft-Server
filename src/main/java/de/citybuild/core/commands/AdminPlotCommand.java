package de.citybuild.core.commands;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.model.Plot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the {@code /adminplot} command (alias: {@code /ap}).
 *
 * <p>Requires the {@code citybuild.admin} permission for all subcommands.</p>
 *
 * <ul>
 *   <li>{@code reset <id>} — Resets a plot to unclaimed state.</li>
 *   <li>{@code delete <id>} — Permanently removes a plot from cache and DB.</li>
 *   <li>{@code info <id>} — Shows full debug information about a plot.</li>
 *   <li>{@code setspawn} — Sets the server spawn to the admin's current position.</li>
 *   <li>{@code reload} — Reloads the plugin config.</li>
 *   <li>{@code stats} — Displays plot statistics.</li>
 * </ul>
 */
public class AdminPlotCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS =
        Arrays.asList("reset", "delete", "info", "setspawn", "reload", "stats");

    private final CityBuildCore plugin;

    /**
     * Creates a new AdminPlotCommand.
     *
     * @param plugin the owning plugin instance
     */
    public AdminPlotCommand(CityBuildCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("citybuild.admin")) {
            sender.sendMessage(plugin.getCoreConfig().getPrefix()
                + plugin.getCoreConfig().msg("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reset"    -> handleReset(sender, args);
            case "delete"   -> handleDelete(sender, args);
            case "info"     -> handleInfo(sender, args);
            case "setspawn" -> handleSetSpawn(sender);
            case "reload"   -> handleReload(sender);
            case "stats"    -> handleStats(sender);
            default         -> sendUsage(sender);
        }
        return true;
    }

    // =========================================================================
    // Subcommand handlers
    // =========================================================================

    private void handleReset(CommandSender sender, String[] args) {
        Integer id = parseId(sender, args);
        if (id == null) return;
        if (plugin.getPlotManager().adminResetPlot(id)) {
            sender.sendMessage(plugin.getCoreConfig().getPrefix()
                + "§aGrundstück §e#" + id + " §azurückgesetzt.");
        } else {
            sender.sendMessage(plugin.getCoreConfig().getPrefix()
                + "§cGrundstück §e#" + id + " §cnicht gefunden.");
        }
    }

    private void handleDelete(CommandSender sender, String[] args) {
        Integer id = parseId(sender, args);
        if (id == null) return;
        if (plugin.getPlotManager().adminDeletePlot(id)) {
            sender.sendMessage(plugin.getCoreConfig().getPrefix()
                + "§aGrundstück §e#" + id + " §agelöscht.");
        } else {
            sender.sendMessage(plugin.getCoreConfig().getPrefix()
                + "§cGrundstück §e#" + id + " §cnicht gefunden.");
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Integer id = parseId(sender, args);
        if (id == null) return;
        plugin.getPlotManager().getById(id).ifPresentOrElse(
            plot -> sendPlotDebug(sender, plot),
            () -> sender.sendMessage(plugin.getCoreConfig().getPrefix()
                + "§cGrundstück §e#" + id + " §cnicht gefunden.")
        );
    }

    private void handleSetSpawn(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur Spieler können den Spawn setzen.");
            return;
        }
        plugin.getSpawnManager().setServerSpawn(player);
        sender.sendMessage(plugin.getCoreConfig().getPrefix()
            + plugin.getCoreConfig().msg("spawn-set"));
    }

    private void handleReload(CommandSender sender) {
        plugin.getCoreConfig().reload();
        sender.sendMessage(plugin.getCoreConfig().getPrefix()
            + "§aKonfiguration neu geladen.");
    }

    private void handleStats(CommandSender sender) {
        Collection<Plot> all  = plugin.getPlotManager().getAllPlots();
        long claimed   = all.stream().filter(p -> p.getOwnerUUID() != null).count();
        long forSale   = all.stream().filter(Plot::isForSale).count();
        long free      = all.stream().filter(p -> p.getOwnerUUID() == null).count();

        sender.sendMessage("§8§m──────────────────────────────");
        sender.sendMessage("§6CityBuild Statistiken");
        sender.sendMessage("§7Gesamt:     §e" + all.size());
        sender.sendMessage("§7Belegt:     §e" + claimed);
        sender.sendMessage("§7Zu verkaufen: §e" + forSale);
        sender.sendMessage("§7Frei:       §e" + free);
        sender.sendMessage("§8§m──────────────────────────────");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void sendPlotDebug(CommandSender sender, Plot plot) {
        sender.sendMessage("§8§m──────────────────────────────");
        sender.sendMessage("§6Debug: Grundstück §e#" + plot.getId());
        sender.sendMessage("§7Welt:        §e" + plot.getWorldName());
        sender.sendMessage("§7Min:         §e[" + plot.getMinX() + ", " + plot.getMinZ() + "]");
        sender.sendMessage("§7Max:         §e[" + plot.getMaxX() + ", " + plot.getMaxZ() + "]");
        sender.sendMessage("§7Fläche:      §e" + plot.getArea() + " m²");
        sender.sendMessage("§7Typ:         §e" + plot.getType().name());
        sender.sendMessage("§7Distrikt:    §e" + plot.getDistrictId());
        sender.sendMessage("§7Ecklot:      §e" + plot.isCornerLot());
        sender.sendMessage("§7Eigentümer:  §e" + (plot.getOwnerName() != null ? plot.getOwnerName() : "keiner"));
        sender.sendMessage("§7UUID:        §e" + (plot.getOwnerUUID() != null ? plot.getOwnerUUID() : "-"));
        sender.sendMessage("§7Name:        §e" + (plot.getPlotName() != null ? plot.getPlotName() : "-"));
        sender.sendMessage("§7Preis:       §e" + plot.getPrice());
        sender.sendMessage("§7Zu verkaufen: §e" + plot.isForSale());
        sender.sendMessage("§7Vertraute:   §e" + plot.getTrustedPlayers().size());
        sender.sendMessage("§7Gebannte:    §e" + plot.getBannedPlayers().size());
        sender.sendMessage("§7Spawn:       §e" + plot.isHasCustomSpawn());
        sender.sendMessage("§8§m──────────────────────────────");
    }

    private Integer parseId(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getCoreConfig().getPrefix()
                + "§cVerwendung: /" + args[0] + " <ID>");
            return null;
        }
        try {
            return Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getCoreConfig().getPrefix() + "§cUngültige ID.");
            return null;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6/adminplot §e<reset|delete|info|setspawn|reload|stats>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!sender.hasPermission("citybuild.admin")) return Collections.emptyList();
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
