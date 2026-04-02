package de.citybuild.core.gui;

import de.citybuild.core.CityBuildCore;
import de.citybuild.core.manager.PlotManager;
import de.citybuild.core.model.Plot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 3-row (27-slot) chest GUI that displays plot information.
 *
 * <p>Create an instance with {@link #PlotInfoGUI(CityBuildCore, Plot, Player)} and
 * call {@link #open(Player)} to show it.  Register {@link GuiListener} once in
 * {@code CityBuildCore.onEnable()} so that click events are dispatched back to
 * the correct GUI instance.</p>
 *
 * <h3>Layout</h3>
 * <pre>
 * [0-Info] [1-Preis] [2-Größe] [3-Typ] [ ] [ ] [ ] [ ] [8-Schließen]
 * [ ]      [10..14 Trusted Spieler]     [ ] [ ] [ ] [ ] [ ]
 * [ ]      [22-Kauf/Info]               [ ] [ ] [ ] [ ] [26-Verlassen]
 * </pre>
 */
public class PlotInfoGUI implements InventoryHolder {

    /** Tracks currently open GUIs by player UUID. */
    private static final Map<UUID, PlotInfoGUI> OPEN = new ConcurrentHashMap<>();

    private final CityBuildCore plugin;
    private final Plot          plot;
    private final Inventory     inventory;

    /**
     * Creates a new PlotInfoGUI for the given plot and player.
     *
     * @param plugin the owning plugin instance
     * @param plot   the plot to display
     */
    public PlotInfoGUI(CityBuildCore plugin, Plot plot) {
        this.plugin    = plugin;
        this.plot      = plot;
        this.inventory = Bukkit.createInventory(this, 27,
            LegacyComponentSerializer.legacySection()
                .deserialize("§6Grundstück §e#" + plot.getId()));
        buildInventory();
    }

    /** Opens the inventory for the viewer and registers it as their active GUI. */
    public void open(Player player) {
        OPEN.put(player.getUniqueId(), this);
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // =========================================================================
    // Building the inventory
    // =========================================================================

    private void buildInventory() {
        String currency = plugin.getCoreConfig().getCurrencySymbol();
        double price    = plot.getOwnerUUID() == null
            ? plugin.getPlotManager().calculatePrice(plot)
            : plot.getPrice();
        int    w        = plot.getMaxX() - plot.getMinX() + 1;
        int    d        = plot.getMaxZ() - plot.getMinZ() + 1;

        // Slot 0 — Plot identity
        String plotTitle = plot.getPlotName() != null
            ? "§6" + plot.getPlotName()
            : "§7Grundstück §e#" + plot.getId();
        inventory.setItem(0, item(Material.PAPER, plotTitle,
            "§7Welt:   §e" + plot.getWorldName(),
            "§7Min:    §e[" + plot.getMinX() + ", " + plot.getMinZ() + "]",
            "§7Max:    §e[" + plot.getMaxX() + ", " + plot.getMaxZ() + "]"
        ));

        // Slot 1 — Price
        String priceFormatted = formatPrice(price) + currency;
        double perM2 = plot.getArea() > 0 ? price / plot.getArea() : 0;
        inventory.setItem(1, item(Material.GOLD_INGOT, "§6Preis: §e" + priceFormatted,
            "§7Preis pro m²: §e" + formatPrice(perM2) + currency
        ));

        // Slot 2 — Size
        String cornerInfo = plot.isCornerLot() ? "§7Ecklot: §aJa" : "§7Ecklot: §cNein";
        inventory.setItem(2, item(Material.MAP,
            "§7Größe: §e" + w + "x" + d + " §7= §e" + plot.getArea() + " m²",
            cornerInfo
        ));

        // Slot 3 — Type
        inventory.setItem(3, item(Material.GRASS_BLOCK,
            "§7Typ: §e" + plot.getType().name()
        ));

        // Slots 10–14 — Trusted players as skulls
        List<UUID> trusted = new ArrayList<>(plot.getTrustedPlayers());
        for (int i = 0; i < Math.min(5, trusted.size()); i++) {
            UUID   uuid  = trusted.get(i);
            inventory.setItem(10 + i, skull(uuid));
        }

        // Slot 13 — Buy button (if unclaimed) or owner info (if claimed)
        if (plot.getOwnerUUID() == null) {
            inventory.setItem(13, item(Material.LIME_DYE,
                "§a§lGrundstück kaufen",
                "§7Preis: §e" + priceFormatted,
                "§7Klicke zum Kaufen!"
            ));
        } else {
            String owner = plot.getOwnerName() != null ? plot.getOwnerName() : "Unbekannt";
            inventory.setItem(13, item(Material.BOOK,
                "§7Eigentümer: §a" + owner,
                "§7Gekauft am: §e" + (plot.getClaimedAt() != null
                    ? plot.getClaimedAt().toString().substring(0, 10) : "?"),
                "§7Vertraute: §e" + plot.getTrustedPlayers().size()
            ));
        }

        // Slot 22 — Close
        inventory.setItem(22, item(Material.BARRIER, "§c§lSchließen"));

        // Slot 26 — Leave to spawn
        inventory.setItem(26, item(Material.ENDER_PEARL, "§6Zum Spawn",
            "§7Teleportiert dich zum Spawn."
        ));

        // Fill empty slots with glass panes
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    // =========================================================================
    // Click handling
    // =========================================================================

    void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        switch (event.getSlot()) {
            case 13 -> {
                if (plot.getOwnerUUID() == null) {
                    player.closeInventory();
                    PlotManager.ClaimResult result =
                        plugin.getPlotManager().claimPlot(player, plot);
                    String prefix = plugin.getCoreConfig().getPrefix();
                    String currency = plugin.getCoreConfig().getCurrencySymbol();
                    switch (result) {
                        case SUCCESS -> player.sendMessage(prefix
                            + plugin.getCoreConfig().msg("plot-bought",
                                "id", String.valueOf(plot.getId()),
                                "price", formatPrice(plot.getPrice()),
                                "currency", currency));
                        case ALREADY_OWNED ->
                            player.sendMessage(prefix
                                + plugin.getCoreConfig().msg("already-owned"));
                        case NOT_ENOUGH_MONEY -> player.sendMessage(prefix
                            + plugin.getCoreConfig().msg("not-enough-money",
                                "price", formatPrice(plugin.getPlotManager().calculatePrice(plot)),
                                "currency", currency));
                        case MAX_PLOTS_REACHED -> player.sendMessage(prefix
                            + plugin.getCoreConfig().msg("max-plots",
                                "max", String.valueOf(
                                    plugin.getCoreConfig().getMaxPlotsPerPlayer())));
                        case CANCELLED_BY_ADDON ->
                            player.sendMessage(prefix + "§cKauf durch ein Addon abgebrochen.");
                    }
                }
            }
            case 22 -> player.closeInventory();
            case 26 -> {
                player.closeInventory();
                plugin.getSpawnManager().teleportToSpawn(player);
            }
        }
    }

    // =========================================================================
    // Item helpers
    // =========================================================================

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta  meta  = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(LegacyComponentSerializer.legacySection().deserialize(name));
        if (lore.length > 0) {
            List<Component> loreList = new ArrayList<>();
            for (String line : lore) {
                loreList.add(LegacyComponentSerializer.legacySection().deserialize(line));
            }
            meta.lore(loreList);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack skull(UUID uuid) {
        ItemStack stack = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) stack.getItemMeta();
        if (meta == null) return stack;
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.displayName(LegacyComponentSerializer.legacySection()
            .deserialize("§7Vertraut: §a" + Bukkit.getOfflinePlayer(uuid).getName()));
        stack.setItemMeta(meta);
        return stack;
    }

    private static String formatPrice(double price) {
        return price == (long) price
            ? String.valueOf((long) price)
            : String.format("%.2f", price);
    }

    // =========================================================================
    // Static listener (registered once in CityBuildCore)
    // =========================================================================

    /**
     * Central listener that dispatches inventory events to the correct
     * {@link PlotInfoGUI} instance. Register once in {@code CityBuildCore.onEnable()}.
     */
    public static class GuiListener implements Listener {

        /** @param event the click event */
        @EventHandler
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) return;
            PlotInfoGUI gui = OPEN.get(player.getUniqueId());
            if (gui == null) return;
            if (!event.getInventory().equals(gui.inventory)) return;
            gui.handleClick(event);
        }

        /** @param event the close event */
        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (event.getPlayer() instanceof Player player) {
                OPEN.remove(player.getUniqueId());
            }
        }
    }
}
