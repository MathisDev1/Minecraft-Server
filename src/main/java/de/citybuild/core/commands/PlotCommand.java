package de.citybuild.core.commands;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.gui.PlotInfoGUI;
import de.citybuild.core.manager.PlotManager;
import de.citybuild.core.model.Plot;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles the {@code /plot} command and all its subcommands.
 *
 * <p>Aliases: {@code /p}, {@code /grundstueck}</p>
 */
public class PlotCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = Arrays.asList(
        "buy", "sell", "info", "setspawn", "trust", "untrust",
        "kick", "ban", "unban", "list", "near", "name"
    );

    private static final List<String> LIST_FILTERS =
        Arrays.asList("mine", "forsale", "free", "all");

    private static final List<String> PLAYER_SUBCOMMANDS =
        Arrays.asList("trust", "untrust", "kick", "ban", "unban");

    private final CityBuildCore plugin;

    /**
     * Creates a new PlotCommand.
     *
     * @param plugin the owning plugin instance
     */
    public PlotCommand(CityBuildCore plugin) {
        this.plugin = plugin;
    }

    // =========================================================================
    // Command dispatch
    // =========================================================================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler können diesen Befehl verwenden.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(prefix() + "§6/plot §e<" + String.join("|", SUBCOMMANDS) + ">");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "buy"     -> handleBuy(player);
            case "sell"    -> handleSell(player, args);
            case "info"    -> handleInfo(player, args);
            case "setspawn"-> handleSetSpawn(player);
            case "trust"   -> handleTrust(player, args, true);
            case "untrust" -> handleTrust(player, args, false);
            case "kick"    -> handleKick(player, args);
            case "ban"     -> handleBan(player, args, true);
            case "unban"   -> handleBan(player, args, false);
            case "list"    -> handleList(player, args);
            case "near"    -> handleNear(player);
            case "name"    -> handleName(player, args);
            default        -> player.sendMessage(prefix() + "§cUnbekannter Befehl.");
        }
        return true;
    }

    // =========================================================================
    // Subcommand handlers
    // =========================================================================

    /** /plot buy */
    private void handleBuy(Player player) {
        if (!player.hasPermission("citybuild.plot.buy")) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("no-permission"));
            return;
        }

        Plot plot = plugin.getPlotManager().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("not-on-plot"));
            return;
        }
        if (plot.getOwnerUUID() != null && !plot.isForSale()) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("already-owned"));
            return;
        }

        PlotManager.ClaimResult result = plugin.getPlotManager().claimPlot(player, plot);
        switch (result) {
            case SUCCESS -> {
                String priceStr = formatPrice(plot.getPrice());
                player.sendMessage(prefix() + plugin.getCoreConfig().msg("plot-bought",
                    "id", String.valueOf(plot.getId()),
                    "price", priceStr,
                    "currency", plugin.getCoreConfig().getCurrencySymbol()));
            }
            case ALREADY_OWNED ->
                player.sendMessage(prefix() + plugin.getCoreConfig().msg("already-owned"));
            case NOT_ENOUGH_MONEY -> {
                String needed = formatPrice(plugin.getPlotManager().calculatePrice(plot));
                player.sendMessage(prefix() + plugin.getCoreConfig().msg("not-enough-money",
                    "price", needed,
                    "currency", plugin.getCoreConfig().getCurrencySymbol()));
            }
            case MAX_PLOTS_REACHED ->
                player.sendMessage(prefix() + plugin.getCoreConfig().msg("max-plots",
                    "max", String.valueOf(plugin.getCoreConfig().getMaxPlotsPerPlayer())));
            case CANCELLED_BY_ADDON ->
                player.sendMessage(prefix() + "§cKauf durch ein Addon abgebrochen.");
        }
    }

    /** /plot sell [price] */
    private void handleSell(Player player, String[] args) {
        if (!player.hasPermission("citybuild.plot.sell")) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("no-permission"));
            return;
        }

        Plot plot = plugin.getPlotManager().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("not-on-plot"));
            return;
        }
        if (!player.getUniqueId().equals(plot.getOwnerUUID())) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("not-owner"));
            return;
        }

        if (args.length >= 2) {
            // Mark for sale at given price
            try {
                double price = Double.parseDouble(args[1]);
                if (price < 0) { player.sendMessage(prefix() + "§cPreis darf nicht negativ sein."); return; }
                plot.setForSale(true);
                plot.setPrice(price);
                plugin.getPlotRepository().savePlot(plot);
                player.sendMessage(prefix() + "§aGrundstück §e#" + plot.getId()
                    + " §azum Verkauf angeboten für §e" + formatPrice(price)
                    + plugin.getCoreConfig().getCurrencySymbol() + "§a.");
            } catch (NumberFormatException e) {
                player.sendMessage(prefix() + "§cUngültiger Preis.");
            }
        } else {
            // Give back to server — no refund
            if (plugin.getPlotManager().unclaimPlot(player, plot)) {
                player.sendMessage(prefix() + plugin.getCoreConfig().msg("plot-sold",
                    "id", String.valueOf(plot.getId())));
            } else {
                player.sendMessage(prefix() + "§cAufgabe des Grundstücks abgebrochen.");
            }
        }
    }

    /** /plot info [id] */
    private void handleInfo(Player player, String[] args) {
        if (args.length >= 2) {
            try {
                int id = Integer.parseInt(args[1]);
                plugin.getPlotManager().getById(id).ifPresentOrElse(
                    plot -> sendPlotInfo(player, plot),
                    () -> player.sendMessage(prefix() + "§cGrundstück §e#" + id + " §cnicht gefunden.")
                );
            } catch (NumberFormatException e) {
                player.sendMessage(prefix() + "§cUngültige ID.");
            }
            return;
        }

        Plot plot = plugin.getPlotManager().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("not-on-plot"));
            return;
        }
        new PlotInfoGUI(plugin, plot).open(player);
    }

    /** /plot setspawn */
    private void handleSetSpawn(Player player) {
        if (!player.hasPermission("citybuild.plot.setspawn")) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("no-permission"));
            return;
        }
        Plot plot = plugin.getPlotManager().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("not-on-plot"));
            return;
        }
        if (!player.getUniqueId().equals(plot.getOwnerUUID())) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("not-owner"));
            return;
        }
        org.bukkit.Location loc = player.getLocation();
        plot.setSpawnX(loc.getX());
        plot.setSpawnY(loc.getY());
        plot.setSpawnZ(loc.getZ());
        plot.setSpawnYaw(loc.getYaw());
        plot.setSpawnPitch(loc.getPitch());
        plot.setHasCustomSpawn(true);
        plugin.getPlotRepository().savePlot(plot);
        player.sendMessage(prefix() + plugin.getCoreConfig().msg("spawn-set"));
    }

    /** /plot trust <player> and /plot untrust <player> */
    private void handleTrust(Player player, String[] args, boolean trust) {
        if (!player.hasPermission("citybuild.plot.trust")) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("no-permission"));
            return;
        }
        String action = trust ? "trust" : "untrust";
        if (args.length < 2) {
            player.sendMessage(prefix() + "§cVerwendung: /plot " + action + " <Spieler>");
            return;
        }
        Plot plot = plotOnOwnedPlot(player);
        if (plot == null) return;

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(prefix() + "§cSpieler §e" + args[1] + " §cist nicht online.");
            return;
        }
        if (trust) {
            plot.getTrustedPlayers().add(target.getUniqueId());
            plugin.getPlotRepository().savePlot(plot);
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("player-trusted",
                "player", target.getName()));
        } else {
            plot.getTrustedPlayers().remove(target.getUniqueId());
            plugin.getPlotRepository().savePlot(plot);
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("player-kicked",
                "player", target.getName()));
        }
    }

    /** /plot kick <player> */
    private void handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(prefix() + "§cVerwendung: /plot kick <Spieler>");
            return;
        }
        Plot plot = plotOnOwnedPlot(player);
        if (plot == null) return;

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(prefix() + "§cSpieler nicht gefunden.");
            return;
        }
        plugin.getSpawnManager().teleportToSpawn(target);
        target.sendMessage(prefix() + "§cDu wurdest vom Grundstück entfernt.");
        player.sendMessage(prefix() + plugin.getCoreConfig().msg("player-kicked",
            "player", target.getName()));
    }

    /** /plot ban <player> and /plot unban <player> */
    private void handleBan(Player player, String[] args, boolean ban) {
        String action = ban ? "ban" : "unban";
        if (args.length < 2) {
            player.sendMessage(prefix() + "§cVerwendung: /plot " + action + " <Spieler>");
            return;
        }
        Plot plot = plotOnOwnedPlot(player);
        if (plot == null) return;

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            player.sendMessage(prefix() + "§cSpieler §e" + args[1] + " §cist nicht online.");
            return;
        }
        UUID targetUuid = target.getUniqueId();
        if (ban) {
            plot.getBannedPlayers().add(targetUuid);
            // Kick if currently standing on the plot
            Plot targetPlot = plugin.getPlotManager().getPlotAt(target.getLocation());
            if (plot.getId() == (targetPlot != null ? targetPlot.getId() : -1)) {
                plugin.getSpawnManager().teleportToSpawn(target);
                target.sendMessage(prefix() + "§cDu wurdest von diesem Grundstück verbannt.");
            }
        } else {
            plot.getBannedPlayers().remove(targetUuid);
        }
        plugin.getPlotRepository().savePlot(plot);
        player.sendMessage(prefix() + "§a" + target.getName()
            + " §a" + (ban ? "verbannt." : "entbannt."));
    }

    /** /plot list [mine|forsale|free|all] [page] */
    private void handleList(Player player, String[] args) {
        String filter = args.length >= 2 ? args[1].toLowerCase() : "all";
        int    page   = 1;
        if (args.length >= 3) {
            try { page = Math.max(1, Integer.parseInt(args[2])); }
            catch (NumberFormatException ignored) {}
        }

        List<Plot> plots;
        String     title;
        switch (filter) {
            case "mine" -> {
                plots = plugin.getPlotManager().getPlotsByOwner(player.getUniqueId());
                title = "Deine Grundstücke";
            }
            case "forsale" -> {
                plots = plugin.getPlotManager().getForSalePlots();
                title = "Zum Verkauf";
            }
            case "free" -> {
                plots = plugin.getPlotManager().getAllPlots().stream()
                    .filter(p -> p.getOwnerUUID() == null)
                    .collect(Collectors.toList());
                title = "Freie Grundstücke";
            }
            default -> {
                plots = List.copyOf(plugin.getPlotManager().getAllPlots());
                title = "Alle Grundstücke";
            }
        }

        int perPage = 10;
        int total   = plots.size();
        int pages   = Math.max(1, (total + perPage - 1) / perPage);
        page = Math.min(page, pages);

        player.sendMessage("§6§l" + title
            + " §7(Seite §e" + page + "§7/§e" + pages + "§7, §e" + total + " §7gesamt)");

        int start = (page - 1) * perPage;
        int end   = Math.min(start + perPage, total);
        for (int i = start; i < end; i++) {
            Plot p      = plots.get(i);
            String status;
            if (p.getOwnerUUID() == null) {
                status = "§aFrei";
            } else if (p.isForSale()) {
                status = "§eZu verkaufen §7(§e" + formatPrice(p.getPrice())
                    + plugin.getCoreConfig().getCurrencySymbol() + "§7)";
            } else {
                status = "§c" + p.getOwnerName();
            }
            player.sendMessage("§7#§e" + p.getId()
                + " §8[" + p.getCenterX() + "," + p.getCenterZ() + "] §r" + status);
        }
        if (pages > 1) {
            player.sendMessage("§7Seite " + page + "/" + pages
                + " — §e/plot list " + filter + " " + (page + 1 <= pages ? page + 1 : page));
        }
    }

    /** /plot near */
    private void handleNear(Player player) {
        List<Plot> near = plugin.getPlotManager()
            .getPlotsNear(player.getLocation(), 300)
            .stream()
            .filter(p -> p.getOwnerUUID() == null)
            .limit(5)
            .collect(Collectors.toList());

        if (near.isEmpty()) {
            player.sendMessage(prefix() + "§cKeine freien Grundstücke in der Nähe (300m).");
            return;
        }
        player.sendMessage("§6Nächste freie Grundstücke§7:");
        for (Plot p : near) {
            int dx   = p.getCenterX() - player.getLocation().getBlockX();
            int dz   = p.getCenterZ() - player.getLocation().getBlockZ();
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            player.sendMessage("§7  #§e" + p.getId()
                + " §8[" + p.getCenterX() + "," + p.getCenterZ() + "] §7~§e" + dist + "m");
        }
    }

    /** /plot name <Name> */
    private void handleName(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(prefix() + "§cVerwendung: /plot name <Name>");
            return;
        }
        Plot plot = plotOnOwnedPlot(player);
        if (plot == null) return;

        String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plot.setPlotName(name);
        plugin.getPlotRepository().savePlot(plot);
        player.sendMessage(prefix() + "§aGrundstück umbenannt: §e" + name);
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();

        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (PLAYER_SUBCOMMANDS.contains(sub)) {
                return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
            if (sub.equals("list")) {
                return LIST_FILTERS.stream()
                    .filter(f -> f.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Returns the plot the player is standing on if they own it, sending
     * appropriate error messages otherwise.
     */
    private Plot plotOnOwnedPlot(Player player) {
        Plot plot = plugin.getPlotManager().getPlotAt(player.getLocation());
        if (plot == null) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("not-on-plot"));
            return null;
        }
        if (!player.getUniqueId().equals(plot.getOwnerUUID())) {
            player.sendMessage(prefix() + plugin.getCoreConfig().msg("not-owner"));
            return null;
        }
        return plot;
    }

    private void sendPlotInfo(Player player, Plot plot) {
        String owner  = plot.getOwnerName() != null ? plot.getOwnerName() : "Niemand";
        String status = plot.getOwnerUUID() == null
            ? "§aFrei"
            : (plot.isForSale() ? "§eZu verkaufen" : "§cBelegt");
        player.sendMessage("§8§m──────────────────────────────");
        player.sendMessage("§6Grundstück §e#" + plot.getId()
            + (plot.getPlotName() != null ? " §7(§f" + plot.getPlotName() + "§7)" : ""));
        player.sendMessage("§7Koordinaten: §e[" + plot.getMinX() + ", " + plot.getMinZ()
            + "] → [" + plot.getMaxX() + ", " + plot.getMaxZ() + "]");
        player.sendMessage("§7Größe:       §e" + (plot.getMaxX() - plot.getMinX() + 1)
            + "x" + (plot.getMaxZ() - plot.getMinZ() + 1) + " §7(§e" + plot.getArea() + " §7m²)");
        player.sendMessage("§7Typ:         §e" + plot.getType().name());
        player.sendMessage("§7Eigentümer:  §e" + owner);
        player.sendMessage("§7Status:      " + status);
        player.sendMessage("§7Preis:       §e" + formatPrice(plot.getPrice())
            + plugin.getCoreConfig().getCurrencySymbol());
        player.sendMessage("§8§m──────────────────────────────");
    }

    private String prefix() {
        return plugin.getCoreConfig().getPrefix();
    }

    private static String formatPrice(double price) {
        return price == (long) price
            ? String.valueOf((long) price)
            : String.format("%.2f", price);
    }
}
