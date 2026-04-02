package de.citybuild.core.util;

import de.citybuild.core.model.Plot;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MessageUtil {

    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.GERMANY));
    private static String prefix = "§8[§6CityBuild§8] §7";

    public static void setPrefix(String newPrefix) {
        prefix = color(newPrefix);
    }

    public static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static void send(CommandSender sender, String message) {
        sender.sendMessage(prefix + color(message));
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(color(title), color(subtitle), fadeIn, stay, fadeOut);
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(color(message));
    }

    public static List<String> formatPlotInfo(Plot plot, String currencySymbol) {
        List<String> info = new ArrayList<>();
        info.add("§7ID: §e" + plot.getId());
        
        int width = plot.getMaxX() - plot.getMinX() + 1;
        int length = plot.getMaxZ() - plot.getMinZ() + 1;
        info.add("§7Größe: §e" + width + "x" + length + " §7(" + plot.getArea() + " m²)");
        
        String owner = plot.getOwnerName() != null ? plot.getOwnerName() : "Niemand";
        info.add("§7Eigentümer: §a" + owner);
        
        if (plot.isForSale() || plot.getOwnerUUID() == null) {
            info.add("§7Preis: §e" + formatPrice(plot.getPrice(), currencySymbol));
        }
        
        info.add("§7Lage: §e" + formatCoords(plot.getCenterX(), plot.getCenterZ()));
        info.add("§7Distanz zum Zentrum: §e" + (int) PlotUtil.distanceFromCenter(plot) + " Blöcke");
        info.add("§7Typ: §a" + (plot.getType() != null ? plot.getType().name() : "Standard"));
        
        // Simple corner check logic (could be more complex)
        boolean isCorner = false;
        info.add("§7Corner Lot: §a" + (isCorner ? "Ja §7(+25% Wert)" : "Nein"));
        
        return info;
    }

    public static String formatPrice(double price, String symbol) {
        return PRICE_FORMAT.format(price) + " " + symbol;
    }

    public static String formatCoords(int x, int z) {
        return "X: " + x + ", Z: " + z;
    }

    public static ItemStack createGuiItem(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> coloredLore = new ArrayList<>();
            for (String lore : loreLines) {
                coloredLore.add("§7" + color(lore));
            }
            meta.setLore(coloredLore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
