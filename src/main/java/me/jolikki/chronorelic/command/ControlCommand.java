package me.jolikki.chronorelic.command;

import me.jolikki.chronorelic.manager.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class ControlCommand implements CommandExecutor {

    private final ConfigManager config;

    public ControlCommand(ConfigManager config) {
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendUsage(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("chronorelic.command.reload")) {
                sender.sendMessage(config.getString("messages.no-permission"));
                return true;
            }

            config.reload();
            sender.sendMessage(config.getString("messages.config-reloaded"));
            return true;
        }

        if (!args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(config.getString("messages.unknown-command"));
            return true;
        }

        if (!sender.hasPermission("chronorelic.command.give")) {
            sender.sendMessage(config.getString("messages.no-permission"));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        Player player = Bukkit.getPlayer(args[1]);
        if (player == null) {
            sender.sendMessage(config.getString("messages.player-not-found"));
            return true;
        }

        Material material = Material.matchMaterial(config.getString("relic.material"));
        if (material == null) {
            material = Material.DIAMOND;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            sender.sendMessage(config.getString("messages.item-create-error"));
            return true;
        }

        meta.setDisplayName(config.getString("relic.name"));
        List<String> lore = config.getStringList("relic.lore");
        meta.setLore(lore);
        item.setItemMeta(meta);

        player.getInventory().addItem(item);
        player.sendMessage(config.getString("messages.relic-given"));
        sender.sendMessage(config.getString("messages.relic-given-sender")
                .replace("{player}", player.getName()));

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(config.getString("messages.usage"));
        sender.sendMessage(ChatColor.GRAY + "/cr give <player>");
        sender.sendMessage(ChatColor.GRAY + "/cr reload");
    }
}
