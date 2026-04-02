package de.citybuild.addon.economy;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BalanceCommand implements CommandExecutor {

    private final SimpleEconomyProvider eco;

    public BalanceCommand(SimpleEconomyProvider eco) {
        this.eco = eco;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("balance")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            double balance = eco.getBalance(player.getUniqueId());
            player.sendMessage("§aDein Guthaben: §e" + eco.formatAmount(balance));
            return true;
        }

        if (command.getName().equalsIgnoreCase("pay")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            if (args.length < 2) {
                player.sendMessage("§cVerwendung: /pay <Spieler> <Betrag>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("§cSpieler nicht gefunden.");
                return true;
            }
            try {
                double amount = Double.parseDouble(args[1]);
                if (amount <= 0) {
                    player.sendMessage("§cBetrag muss größer als 0 sein.");
                    return true;
                }
                if (eco.withdraw(player.getUniqueId(), amount)) {
                    eco.deposit(target.getUniqueId(), amount);
                    player.sendMessage("§aDu hast §e" + eco.formatAmount(amount) + " §aan " + target.getName() + " gesendet.");
                    target.sendMessage("§aDu hast §e" + eco.formatAmount(amount) + " §avon " + player.getName() + " erhalten.");
                } else {
                    player.sendMessage("§cNicht genug Guthaben!");
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§cUngültiger Betrag.");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("eco")) {
            if (!sender.hasPermission("citybuild.eco.admin")) {
                sender.sendMessage("§cDazu hast du keine Rechte.");
                return true;
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target != null) {
                    try {
                        double amount = Double.parseDouble(args[2]);
                        eco.deposit(target.getUniqueId(), amount);
                        sender.sendMessage("§a" + eco.formatAmount(amount) + " an " + target.getName() + " gegeben.");
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cUngültiger Betrag.");
                    }
                } else {
                    sender.sendMessage("§cSpieler nicht gefunden.");
                }
                return true;
            }
            sender.sendMessage("§cVerwendung: /eco give <Spieler> <Betrag>");
            return true;
        }

        return false;
    }
}
